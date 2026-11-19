package com.sara.robot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sara.robot.core.AppLogger
import com.sara.robot.core.SaraPrompt
import com.sara.robot.core.TextUtils
import com.sara.robot.data.GeminiRepository
import com.sara.robot.data.SecurePreferences
import com.sara.robot.voice.TextToSpeechManager
import com.sara.robot.voice.VoiceInputManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class ChatMessage(
    val role: String, // "user" o "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface ConnectionStatus {
    object Idle : ConnectionStatus
    object Checking : ConnectionStatus
    data class Connected(val latencyMs: Long) : ConnectionStatus
    data class Error(val error: String) : ConnectionStatus
}

data class SaraUiState(
    val faceState: String = "IDLE", // "IDLE", "LISTENING", "THINKING", "SPEAKING", "ERROR"
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val isSpeaking: Boolean = false,
    val messages: List<ChatMessage> = listOf(
        ChatMessage(role = "model", text = SaraPrompt.SALUDO_INICIAL)
    ),
    val lastRecognizedText: String = "",
    val showFallbackMenu: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Idle,
    val hasApiKey: Boolean = false,
    val errorMessage: String? = null
)

class SaraViewModel(application: Application) : AndroidViewModel(application) {

    private val securePreferences = SecurePreferences(application)
    private val geminiRepository = GeminiRepository(securePreferences.getApiKey())

    // Gestores Multimedia según guía técnica modulo de musica.md
    val reproductorManager = com.sara.robot.music.ReproductorManager(application)
    val youTubeMusicRepository = com.sara.robot.music.YouTubeMusicRepository(securePreferences.getGoogleCloudApiKey())

    // Repositorio de Chat Privado y Contactos Familiares
    val chatRepository = com.sara.robot.data.ChatRepository(application)

    // Repositorio de Recordatorios con persistencia directa en BD y subproceso en RAM
    val recordatorioRepository = com.sara.robot.data.RecordatorioRepository(application)

    // Repositorio de Biometría Facial MobileFaceNet y Consentimiento Informado
    val biometriaRepository = com.sara.robot.data.BiometriaRepository(application)

    private var voiceInputManager: VoiceInputManager? = null
    private var textToSpeechManager: TextToSpeechManager? = null
    private var serialDispatcher: com.sara.robot.navigation.actuator.SerialDispatcher? = null

    // Control de streaming y cancelación reactiva (Fase 1: Job Cancelable)
    private var streamJob: Job? = null

    // Acumulador de fragmentos para oraciones (Fase 2: Sentence Chunking)
    private val sentenceBuffer = StringBuilder()

    // Historial de oraciones efectivamente habladas por TTS (Fase 5: Context Tracking)
    private val spokenSentences = mutableListOf<String>()
    private var utteranceCounter = 0

    private val _uiState = MutableStateFlow(
        SaraUiState(hasApiKey = securePreferences.hasApiKey())
    )
    val uiState: StateFlow<SaraUiState> = _uiState.asStateFlow()

    init {
        AppLogger.info("App", "Iniciando SaraViewModel con soporte Barge-In (Iteración 1)...")
        val apiKey = securePreferences.getApiKey()
        if (apiKey.isNotBlank()) {
            val masked = if (apiKey.length > 8) "${apiKey.take(4)}...${apiKey.takeLast(4)}" else "***"
            AppLogger.success("App", "API Key cargada desde entorno: $masked")
            checkConnection()
        } else {
            AppLogger.error("App", "No se encontró GEMINI_API_KEY en variables de entorno ni en .env")
        }

        try {
            serialDispatcher = com.sara.robot.navigation.actuator.SerialDispatcher(application, viewModelScope)
        } catch (e: Exception) {
            AppLogger.warn("SaraViewModel", "No se pudo inicializar SerialDispatcher: ${e.message}")
        }

        // Inicializar síntesis de voz nativa con soporte de oraciones encadenadas y ducking de música
        textToSpeechManager = TextToSpeechManager(application) {
            // Callback: Todas las oraciones han terminado de hablar
            voiceInputManager?.isSaraSpeaking = false
            reproductorManager.atenuarParaVoz(false)

            val isMusicActive = reproductorManager.playerState.value.isPlaying ||
                    reproductorManager.playerState.value.currentVideoId.isNotBlank()

            if (isMusicActive) {
                // Si la música está activa, Sara se queda en silencio (IDLE) sin escuchar ni pensar
                voiceInputManager?.stopListening()
                _uiState.update {
                    it.copy(
                        isSpeaking = false,
                        isListening = false,
                        isThinking = false,
                        faceState = "IDLE"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSpeaking = false,
                        isThinking = false,
                        faceState = "LISTENING"
                    )
                }
                startListening()
            }
        }

        // Listener de Function Calling para reproducción multimedia y mensajería familiar
        geminiRepository.onFunctionCallListener = { name, args ->
            when (name) {
                "controlar_multimedia" -> {
                    val accion = args?.optString("accion", "reproducir") ?: "reproducir"
                    val query = args?.optString("query", "") ?: ""
                    AppLogger.success("SaraViewModel", "Tool 'controlar_multimedia' invocada: accion=$accion, query='$query'")
                    ejecutarComandoMultimedia(accion, query)
                }
                "enviar_mensaje_familiar" -> {
                    val destinatario = args?.optString("destinatario", "") ?: ""
                    val mensaje = args?.optString("mensaje", "") ?: ""
                    AppLogger.success("SaraViewModel", "Tool 'enviar_mensaje_familiar' invocada: destinatario=$destinatario, mensaje='$mensaje'")
                    if (destinatario.isNotBlank() && mensaje.isNotBlank()) {
                        viewModelScope.launch {
                            val result = chatRepository.enviarMensajeDesdePaciente(destinatario, mensaje)
                            result.onSuccess {
                                hablarTextoDirecto("Listo, le envié tu mensaje a $destinatario")
                            }.onFailure {
                                hablarTextoDirecto("No encontré a $destinatario en tus contactos familiares")
                            }
                        }
                    }
                }
                "crear_recordatorio" -> {
                    val descripcion = args?.optString("descripcion", "Recordatorio") ?: "Recordatorio"
                    val fechaStr = args?.optString("fecha", "") ?: ""
                    val horaStr = args?.optString("hora", "") ?: ""
                    AppLogger.success("SaraViewModel", "Tool 'crear_recordatorio' invocada: desc='$descripcion', fecha='$fechaStr', hora='$horaStr'")
                    ejecutarCreacionRecordatorioDesdeTool(descripcion, fechaStr, horaStr)
                }
            }
        }

        // Inicializar datos del chat y observador reactivo de mensajes entrantes
        viewModelScope.launch {
            chatRepository.seedDefaultDataIfEmpty()
            iniciarObservadorMensajesEntrantes()
        }

        // Inicializar repositorio de recordatorios: purga de testeos y subproceso diario de carga en RAM
        viewModelScope.launch {
            val prefs = application.getSharedPreferences("sara_reminders_prefs", android.content.Context.MODE_PRIVATE)
            recordatorioRepository.purgarParaNuevosTesteosSiEsNecesario(prefs)
            recordatorioRepository.iniciarSubprocesoDiario(viewModelScope)
        }

        textToSpeechManager?.onSentenceDoneListener = { _, sentenceText ->
            // Registrar oración que realmente alcanzó a salir por el parlante
            synchronized(spokenSentences) {
                spokenSentences.add(sentenceText)
                updateLastModelMessage(spokenSentences.joinToString(" "))
            }
        }

        // Inicializar reconocimiento de voz nativo con detección de interrupción (Fase 3: Barge-In Detector)
        voiceInputManager = VoiceInputManager(
            context = application,
            onResultText = { recognizedText ->
                _uiState.update { it.copy(isListening = false, lastRecognizedText = recognizedText) }
                processUserMessage(recognizedText)
            },
            onErrorCountExceeded = {
                _uiState.update {
                    it.copy(
                        isListening = false,
                        faceState = "IDLE",
                        showFallbackMenu = false,
                        errorMessage = "No se detectó voz clara. Toca la pantalla para intentar de nuevo."
                    )
                }
            }
        ).apply {
            onBargeInDetected = {
                // Interrupción instantánea al detectar voz del usuario mientras Sara habla
                interruptSara()
            }
        }
    }

    fun startListening() {
        if (_uiState.value.isSpeaking || _uiState.value.isThinking) {
            interruptSara()
            return
        }
        _uiState.update { it.copy(isListening = true, faceState = "LISTENING", errorMessage = null) }
        voiceInputManager?.startListening()
    }

    fun stopListening() {
        voiceInputManager?.stopListening()
        _uiState.update { it.copy(isListening = false) }
    }

    /**
     * Rutina de Cancelación Instantánea (The Kill Switch - Fase 4).
     * Detiene la descarga de red de Gemini, apaga la salida física de audio,
     * limpia los buffers y guarda en el historial solo lo que el usuario realmente escuchó.
     */
    fun interruptSara() {
        if (streamJob == null && !_uiState.value.isSpeaking && !_uiState.value.isThinking) {
            return
        }

        AppLogger.warn("BargeIn", "⚡ ¡Interrupción (Barge-In) ejecutada! Cancelando Gemini, silenciando audio y limpiando buffers...")

        // 1. Cancelar la conexión de red con Gemini inmediatamente
        streamJob?.cancel()
        streamJob = null

        // 2. Detener en seco la salida física de audio
        textToSpeechManager?.stop()

        // 3. Limpiar buffers de oraciones pendientes
        sentenceBuffer.clear()

        // 4. Actualizar el contexto histórico guardando solo lo hablado (Fase 5)
        finalizeInterruptedMessage()

        // 5. Cambiar estado visual del robot y activar micrófono de inmediato
        voiceInputManager?.isSaraSpeaking = false
        _uiState.update {
            it.copy(
                isSpeaking = false,
                isThinking = false,
                isListening = true,
                faceState = "LISTENING"
            )
        }

        voiceInputManager?.startListening()
    }

    private fun finalizeInterruptedMessage() {
        synchronized(spokenSentences) {
            val spokenText = if (spokenSentences.isNotEmpty()) {
                "${spokenSentences.joinToString(" ")} [interrumpido por el usuario]"
            } else {
                "[interrumpido por el usuario]"
            }
            updateLastModelMessage(spokenText)
        }
    }

    private fun updateLastModelMessage(newText: String) {
        val currentMessages = _uiState.value.messages.toMutableList()
        if (currentMessages.isNotEmpty() && currentMessages.last().role == "model") {
            currentMessages[currentMessages.size - 1] = currentMessages.last().copy(text = newText)
            _uiState.update { it.copy(messages = currentMessages) }
        }
    }

    fun dismissFallbackMenu() {
        _uiState.update { it.copy(showFallbackMenu = false) }
    }

    fun setApiKey(apiKey: String) {
        securePreferences.setApiKey(apiKey)
        geminiRepository.updateApiKey(apiKey)
        _uiState.update { it.copy(hasApiKey = apiKey.isNotBlank()) }
    }

    fun checkConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(connectionStatus = ConnectionStatus.Checking) }
            val (connected, latency) = geminiRepository.checkConnection()
            _uiState.update {
                it.copy(
                    connectionStatus = if (connected) {
                        ConnectionStatus.Connected(latency)
                    } else {
                        ConnectionStatus.Error("Error al conectar con Gemini")
                    }
                )
            }
        }
    }

    /**
     * Procesa la entrada del usuario mediante streaming continuo y fragmentación de oraciones (Fases 1 y 2).
     */
    fun processUserMessage(rawText: String) {
        val userText = rawText.trim()
        if (userText.isBlank()) return

        // Si había una respuesta en curso, cancelarla antes de iniciar la nueva
        if (streamJob != null || _uiState.value.isSpeaking) {
            interruptSara()
        }

        // Detección directa de comando multimedia para arranque instantáneo (sub-1s)
        if (isMusicCommand(userText)) {
            val updatedMessages = _uiState.value.messages +
                    ChatMessage(role = "user", text = userText)
            _uiState.update { it.copy(messages = updatedMessages) }
            val (accion, query) = parseMusicIntent(userText)
            ejecutarComandoMultimedia(accion, query)
            return
        }

        // Detección directa de mensaje a familiar para respuesta instantánea (0 TOKENS)
        if (isChatCommand(userText)) {
            val updatedMessages = _uiState.value.messages +
                    ChatMessage(role = "user", text = userText)
            _uiState.update { it.copy(messages = updatedMessages) }
            ejecutarComandoChat(userText)
            return
        }

        // Comando directo para vaciar todos los recordatorios (petición de testeo)
        if (isClearRemindersCommand(userText)) {
            val updatedMessages = _uiState.value.messages +
                    ChatMessage(role = "user", text = userText)
            _uiState.update { it.copy(messages = updatedMessages) }
            ejecutarLimpiarRecordatorios()
            return
        }

        // Detección directa de comando de recordatorio para guardado en BD y alerta en RAM (0 TOKENS)
        if (isReminderCommand(userText)) {
            val updatedMessages = _uiState.value.messages +
                    ChatMessage(role = "user", text = userText)
            _uiState.update { it.copy(messages = updatedMessages) }
            ejecutarComandoRecordatorio(userText)
            return
        }

        // 1. Añadir mensaje de usuario y crear entrada para modelo
        val updatedMessages = _uiState.value.messages +
                ChatMessage(role = "user", text = userText) +
                ChatMessage(role = "model", text = "")

        _uiState.update {
            it.copy(
                messages = updatedMessages,
                isThinking = true,
                faceState = "THINKING",
                errorMessage = null
            )
        }

        // Reiniciar estado de buffers de oraciones
        synchronized(spokenSentences) {
            spokenSentences.clear()
        }
        sentenceBuffer.clear()
        utteranceCounter = 0

        // 2. Obtener historial contextual (mínimo 3 turnos / pares anteriores)
        val historyPairs = updatedMessages
            .dropLast(2) // excluir el nuevo turno user + model
            .takeLast(6)
            .map { Pair(it.role, it.text) }

        // 3. Ejecutar streaming reactivo cancelable (Job anclado)
        streamJob = viewModelScope.launch {
            try {
                geminiRepository.streamMessage(historyPairs, userText).collect { chunk ->
                    sentenceBuffer.append(chunk)

                    // Fragmentar en oraciones completas por puntuación (. ? ! \n)
                    val sentences = extractSentences(sentenceBuffer)
                    for (sentence in sentences) {
                        val clean = TextUtils.stripMarkdown(sentence).trim()
                        if (clean.isNotEmpty()) {
                            utteranceCounter++
                            val utteranceId = "SARA_STREAM_$utteranceCounter"

                            _uiState.update {
                                it.copy(
                                    isThinking = false,
                                    isSpeaking = true,
                                    faceState = "SPEAKING"
                                )
                            }
                            voiceInputManager?.isSaraSpeaking = true
                            textToSpeechManager?.speakSentence(clean, utteranceId)
                        }
                    }
                }

                // Despachar cualquier oración restante al finalizar el stream
                val remaining = sentenceBuffer.toString().trim()
                if (remaining.isNotEmpty()) {
                    val clean = TextUtils.stripMarkdown(remaining).trim()
                    if (clean.isNotEmpty()) {
                        utteranceCounter++
                        val utteranceId = "SARA_STREAM_$utteranceCounter"
                        _uiState.update {
                            it.copy(
                                isThinking = false,
                                isSpeaking = true,
                                faceState = "SPEAKING"
                            )
                        }
                        voiceInputManager?.isSaraSpeaking = true
                        textToSpeechManager?.speakSentence(clean, utteranceId)
                    }
                    sentenceBuffer.clear()
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                AppLogger.info("BargeIn", "Streaming cancelado cooperativamente por interrupción del usuario")
            } catch (e: Exception) {
                AppLogger.error("Gemini", "Error durante streaming: ${e.message}")
                val apology = SaraPrompt.MENSAJE_DISCULPA
                updateLastModelMessage(apology)
                _uiState.update {
                    it.copy(
                        isThinking = false,
                        isSpeaking = true,
                        faceState = "ERROR",
                        errorMessage = e.localizedMessage
                    )
                }
                textToSpeechManager?.speak(apology)
            } finally {
                streamJob = null
            }
        }
    }

    /**
     * Extrae oraciones completas del buffer basadas en terminadores: . ? ! o saltos de línea.
     */
    private fun extractSentences(buffer: StringBuilder): List<String> {
        val sentences = mutableListOf<String>()
        val text = buffer.toString()
        val regex = Regex(".*?[.!?\\n]+(\\s+|$)")
        var lastEnd = 0

        for (match in regex.findAll(text)) {
            val sentence = match.value.trim()
            if (sentence.isNotEmpty()) {
                sentences.add(sentence)
            }
            lastEnd = match.range.last + 1
        }

        if (lastEnd > 0) {
            buffer.delete(0, lastEnd)
        }
        return sentences
    }

    fun enviarComando(comando: String) {
        val frame = if (comando.endsWith("\n")) comando else "$comando\n"
        serialDispatcher?.postCommand(frame)
        AppLogger.info("SaraViewModel", "Comando serial manual enviado: $frame")
    }

    fun hablarRecordatorio(mensaje: String) {
        val textoAviso = "Atención: Tienes un recordatorio programado. $mensaje"
        AppLogger.success("SaraViewModel", "Sara activada por recordatorio: '$textoAviso'")
        _uiState.update {
            it.copy(
                isSpeaking = true,
                faceState = "SPEAKING",
                messages = it.messages + ChatMessage(role = "model", text = textoAviso)
            )
        }
        reproductorManager.atenuarParaVoz(true)
        textToSpeechManager?.speak(textoAviso)
    }

    /**
     * Ejecuta comandos multimedia (reproducir, pausar, reanudar, detener)
     * solicitados por Gemini Function Calling o detección de intención por voz.
     */
    fun ejecutarComandoMultimedia(accion: String, query: String = "") {
        viewModelScope.launch {
            when (accion.lowercase().trim()) {
                "reproducir" -> {
                    // Cancelar cualquier pensamiento o streaming de Gemini inmediatamente
                    streamJob?.cancel()
                    streamJob = null

                    val searchTarget = if (query.isNotBlank()) query.trim() else "música instrumental relajante"
                    AppLogger.info("SaraViewModel", "Solicitud de reproducción musical: '$searchTarget'")
                    val aviso = "Buscando $searchTarget para ti."
                    _uiState.update {
                        it.copy(
                            messages = it.messages + ChatMessage(role = "model", text = aviso),
                            isSpeaking = true,
                            isThinking = false,
                            isListening = false,
                            faceState = "SPEAKING"
                        )
                    }
                    reproductorManager.atenuarParaVoz(true)
                    textToSpeechManager?.speak(aviso)

                    val track = youTubeMusicRepository.buscarYPreparar(searchTarget)
                    if (track != null) {
                        reproductorManager.reproducirPista(track)

                        // CANCIÓN LISTA: Sara deja de pensar, deja de escuchar y se silencia en IDLE
                        voiceInputManager?.stopListening()
                        voiceInputManager?.isSaraSpeaking = false
                        reproductorManager.atenuarParaVoz(false)
                        _uiState.update {
                            it.copy(
                                isThinking = false,
                                isListening = false,
                                isSpeaking = false,
                                faceState = "IDLE"
                            )
                        }
                        AppLogger.success("SaraViewModel", "Pista lista. Sara pasa a modo silencioso (IDLE) sin escuchar.")
                    } else {
                        val fallo = "No pude encontrar la canción $searchTarget en YouTube. Por favor intenta con otro nombre."
                        _uiState.update {
                            it.copy(
                                isThinking = false,
                                isListening = false,
                                isSpeaking = true,
                                faceState = "SPEAKING"
                            )
                        }
                        textToSpeechManager?.speak(fallo)
                    }
                }
                "pausar" -> {
                    reproductorManager.pausar()
                    val aviso = "Música pausada."
                    _uiState.update { it.copy(messages = it.messages + ChatMessage(role = "model", text = aviso)) }
                    textToSpeechManager?.speak(aviso)
                }
                "reanudar" -> {
                    reproductorManager.reanudar()
                    val aviso = "Reanudando la música."
                    _uiState.update { it.copy(messages = it.messages + ChatMessage(role = "model", text = aviso)) }
                    textToSpeechManager?.speak(aviso)
                }
                "detener" -> {
                    reproductorManager.detener()
                    val aviso = "Música detenida."
                    _uiState.update { it.copy(messages = it.messages + ChatMessage(role = "model", text = aviso)) }
                    textToSpeechManager?.speak(aviso)
                }
            }
        }
    }

    fun isMusicCommand(text: String): Boolean {
        val t = text.lowercase().trim()
        return t.startsWith("pon ") || t.startsWith("reproduce") || t.startsWith("toca ") ||
                t.contains("música") || t.contains("musica") || t.contains("canción") || t.contains("cancion") ||
                t.contains("pausa") || t.contains("detén") || t.contains("deten") || t.contains("para la música")
    }

    fun parseMusicIntent(text: String): Pair<String, String> {
        val t = text.lowercase().trim()
        if (t.contains("pausa")) return "pausar" to ""
        if (t.contains("detén") || t.contains("deten") || t.contains("para la música") || t.contains("para la musica") || t.contains("stop")) return "detener" to ""
        if (t.contains("reanuda") || t.contains("continua") || t.contains("sigue")) return "reanudar" to ""

        var query = t
        val prefixes = listOf(
            "pon música de", "pon musica de", "pon música", "pon musica",
            "pon una canción de", "pon una cancion de", "pon una canción", "pon una cancion",
            "pon la canción de", "pon la cancion de", "pon la canción", "pon la cancion",
            "pon a", "pon",
            "reproduce a", "reproduce la canción de", "reproduce la cancion de", "reproduce la canción", "reproduce la cancion", "reproduce",
            "toca música de", "toca musica de", "toca a", "toca"
        )
        for (prefix in prefixes) {
            if (query.startsWith(prefix)) {
                query = query.removePrefix(prefix).trim()
                break
            } else if (query.contains(prefix)) {
                query = query.substringAfter(prefix).trim()
                break
            }
        }
        return "reproducir" to query
    }

    fun hablarTextoDirecto(texto: String) {
        _uiState.update {
            it.copy(
                isSpeaking = true,
                isThinking = false,
                isListening = false,
                faceState = "SPEAKING",
                messages = it.messages + ChatMessage(role = "model", text = texto)
            )
        }
        reproductorManager.atenuarParaVoz(true)
        textToSpeechManager?.speak(texto)
    }

    var isPatientModeActive: Boolean = false
        set(value) {
            field = value
            if (value) {
                verificarMensajesNoLeidosPendientes()
            }
        }

    fun verificarMensajesNoLeidosPendientes() {
        viewModelScope.launch {
            val noLeidos = chatRepository.getMensajesNoLeidosParaPacienteDirecto()
            for (msg in noLeidos) {
                val contacto = chatRepository.getContactoById(msg.Contacto_emergencia_id_contacto)
                val remitenteNombre = contacto?.nombre ?: "tu familiar"
                val aviso = "Tienes un mensaje nuevo de $remitenteNombre: \"${msg.mensaje}\""
                AppLogger.success("SaraViewModel", "Mensaje pendiente detectado para el paciente: $aviso")
                hablarTextoDirecto(aviso)
                chatRepository.marcarMensajeComoLeido(msg.id_chat)
            }
        }
    }

    private fun iniciarObservadorMensajesEntrantes() {
        viewModelScope.launch {
            chatRepository.getMensajesNoLeidosParaPaciente().collect { mensajesNoLeidos ->
                if (isPatientModeActive) {
                    for (msg in mensajesNoLeidos) {
                        val contacto = chatRepository.getContactoById(msg.Contacto_emergencia_id_contacto)
                        val remitenteNombre = contacto?.nombre ?: "tu familiar"
                        val aviso = "Tienes un mensaje nuevo de $remitenteNombre: \"${msg.mensaje}\""
                        AppLogger.success("SaraViewModel", "Mensaje entrante detectado para el paciente: $aviso")
                        hablarTextoDirecto(aviso)
                        chatRepository.marcarMensajeComoLeido(msg.id_chat)
                    }
                }
            }
        }
    }

    fun isChatCommand(text: String): Boolean {
        val t = text.lowercase().trim()
        return t.contains("dile a ") || t.contains("avísale a ") || t.contains("avisale a ") ||
                t.contains("mándale a ") || t.contains("mandale a ") || t.contains("mándale un mensaje a ") ||
                t.contains("mandale un mensaje a ") || t.contains("envíale a ") || t.contains("enviale a ") ||
                t.contains("envíale un mensaje a ") || t.contains("enviale un mensaje a ") ||
                t.contains("escríbele a ") || t.contains("escribe a ") || t.contains("mándale decir a ") ||
                t.contains("mandale decir a ")
    }

    fun parseChatIntent(text: String): Pair<String, String>? {
        val t = text.trim()
        val lower = t.lowercase()
        val triggers = listOf(
            "mándale un mensaje a ", "mandale un mensaje a ",
            "envíale un mensaje a ", "enviale un mensaje a ",
            "mándale decir a ", "mandale decir a ",
            "dile a ", "avísale a ", "avisale a ",
            "mándale a ", "mandale a ",
            "envíale a ", "enviale a ",
            "escríbele a ", "escribe a "
        )

        for (trigger in triggers) {
            val idx = lower.indexOf(trigger)
            if (idx != -1) {
                val afterTrigger = t.substring(idx + trigger.length).trim()
                val splitWords = listOf(" que ", " diciendo que ", ": ")
                for (split in splitWords) {
                    val splitIdx = afterTrigger.indexOf(split, ignoreCase = true)
                    if (splitIdx != -1) {
                        val destinatario = afterTrigger.substring(0, splitIdx).trim()
                        val mensaje = afterTrigger.substring(splitIdx + split.length).trim()
                        if (destinatario.isNotBlank() && mensaje.isNotBlank()) {
                            return destinatario to mensaje
                        }
                    }
                }
                val words = afterTrigger.split(" ", limit = 2)
                if (words.size == 2) {
                    return words[0].trim() to words[1].trim()
                }
            }
        }
        return null
    }

    fun ejecutarComandoChat(userText: String) {
        val parsed = parseChatIntent(userText)
        if (parsed == null) {
            hablarTextoDirecto("¿A quién deseas enviarle el mensaje y qué te gustaría decirle?")
            return
        }

        val (destinatario, mensaje) = parsed
        AppLogger.info("SaraViewModel", "Comando de chat detectado (0 tokens): destinatario='$destinatario', mensaje='$mensaje'")

        viewModelScope.launch {
            val result = chatRepository.enviarMensajeDesdePaciente(destinatario, mensaje)
            result.onSuccess {
                val respuesta = "Listo, le dejé tu mensaje a $destinatario: \"$mensaje\""
                hablarTextoDirecto(respuesta)
            }.onFailure { err ->
                val avisoError = when (err) {
                    is NoSuchElementException -> "No encontré a $destinatario en tus contactos familiares. Puedes agregarlo en la vista familiar o de administrador."
                    else -> "No pude enviar el mensaje a $destinatario: ${err.message}"
                }
                hablarTextoDirecto(avisoError)
            }
        }
    }

    fun isClearRemindersCommand(text: String): Boolean {
        val t = text.lowercase().trim()
        return (t.contains("elimina") || t.contains("borra") || t.contains("vacía") || t.contains("vacia") || t.contains("limpia")) &&
                (t.contains("recordatorio") || t.contains("alarma") || t.contains("recordatorios") || t.contains("alarmas")) &&
                (t.contains("todo") || t.contains("todas"))
    }

    fun ejecutarLimpiarRecordatorios() {
        viewModelScope.launch {
            recordatorioRepository.limpiarTodosLosRecordatorios()
            hablarTextoDirecto("Listo, he eliminado todos los recordatorios de la base de datos y del buffer para tus testeos.")
        }
    }

    fun isReminderCommand(text: String): Boolean {
        val t = text.lowercase().trim()
        return t.contains("recuérdame") || t.contains("recuerdame") ||
                t.contains("recuérdale") || t.contains("recuerdale") ||
                t.contains("recuerda") || t.contains("recordar") ||
                t.contains("recordatorio") || t.contains("alarma") ||
                t.contains("avísame") || t.contains("avisame") ||
                t.contains("avísale") || t.contains("avisale") ||
                t.contains("despiértame") || t.contains("despiertame")
    }

    fun parseReminderIntent(text: String): Pair<String, LocalDateTime>? {
        val t = text.lowercase().trim()
        val now = LocalDateTime.now()

        var targetDate = now.toLocalDate()
        if (t.contains("pasado mañana") || t.contains("pasado manana")) {
            targetDate = targetDate.plusDays(2)
        } else if (t.contains("mañana") || t.contains("manana")) {
            targetDate = targetDate.plusDays(1)
        }

        val wordsToNumbers = mapOf(
            "un" to 1, "una" to 1, "uno" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
            "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10,
            "once" to 11, "doce" to 12, "quince" to 15, "veinte" to 20, "treinta" to 30
        )

        var targetTime: LocalTime? = null

        // 1. Tiempos relativos (minutos, segundos, horas)
        val regexMin = Regex("en (\\d+|un|una|uno|dos|tres|cuatro|cinco|diez|quince|veinte|treinta) (?:minutos?|min)")
        val matchMin = regexMin.find(t)
        if (matchMin != null) {
            val token = matchMin.groupValues[1]
            val mins = token.toLongOrNull() ?: wordsToNumbers[token]?.toLong() ?: 5L
            return extractReminderDescription(text) to now.plusMinutes(mins).withNano(0)
        }

        val regexSec = Regex("en (\\d+|un|una|uno|dos|tres|cuatro|cinco|diez|quince|veinte|treinta) (?:segundos?|seg)")
        val matchSec = regexSec.find(t)
        if (matchSec != null) {
            val token = matchSec.groupValues[1]
            val secs = token.toLongOrNull() ?: wordsToNumbers[token]?.toLong() ?: 30L
            return extractReminderDescription(text) to now.plusSeconds(secs).withNano(0)
        }

        if (t.contains("en media hora")) {
            return extractReminderDescription(text) to now.plusMinutes(30).withNano(0)
        }
        if (t.contains("en un cuarto de hora")) {
            return extractReminderDescription(text) to now.plusMinutes(15).withNano(0)
        }

        val regexHour = Regex("en (\\d+|un|una|dos|tres) (?:horas?|h)")
        val matchHour = regexHour.find(t)
        if (matchHour != null) {
            val token = matchHour.groupValues[1]
            val hours = token.toLongOrNull() ?: wordsToNumbers[token]?.toLong() ?: 1L
            return extractReminderDescription(text) to now.plusHours(hours).withNano(0)
        }

        // 2. Horas en formato digital (ej. 15:30, 08:00, 3:45)
        val regexDigitalTime = Regex("(?:a|para|de)?\\s*(?:las?)?\\s*(\\d{1,2})[:.](\\d{2})")
        val matchDigital = regexDigitalTime.find(t)
        if (matchDigital != null) {
            var h = matchDigital.groupValues[1].toIntOrNull() ?: 0
            val m = matchDigital.groupValues[2].toIntOrNull() ?: 0

            val isManana = t.contains("de la mañana") || t.contains("de la manana") || t.contains("am") || t.contains("a.m.")
            val isTarde = t.contains("de la tarde") || t.contains("pm") || t.contains("p.m.")
            val isNoche = t.contains("de la noche")

            if ((isTarde || isNoche) && h < 12) {
                h += 12
            } else if (!isManana && h < 12) {
                // Adaptación inteligente al horario actual
                if (h + 12 > now.hour || (h + 12 == now.hour && m > now.minute)) {
                    h += 12
                }
            }

            targetTime = LocalTime.of(h.coerceIn(0, 23), m.coerceIn(0, 59))
        }

        // 3. Horas coloquiales (ej. "a las tres", "para las 4 y media", "tipo 5")
        if (targetTime == null) {
            val regexHoraColoquial = Regex("(?:a|para|de|hacia)?\\s*(?:las?|la)?\\s*(\\d{1,2}|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)")
            val matchColoquial = regexHoraColoquial.find(t)
            if (matchColoquial != null) {
                val hourToken = matchColoquial.groupValues[1]
                var rawHour = hourToken.toIntOrNull() ?: wordsToNumbers[hourToken] ?: 12

                var minute = 0
                if (t.contains("y media")) minute = 30
                if (t.contains("y cuarto")) minute = 15

                val regexConMin = Regex("con (\\d{1,2})")
                val matchCon = regexConMin.find(t)
                if (matchCon != null) {
                    minute = matchCon.groupValues[1].toIntOrNull() ?: minute
                }

                val isTarde = t.contains("de la tarde") || t.contains("pm") || t.contains("p.m.")
                val isNoche = t.contains("de la noche")
                val isManana = t.contains("de la mañana") || t.contains("de la manana") || t.contains("am") || t.contains("a.m.")

                if ((isTarde || isNoche) && rawHour < 12) {
                    rawHour += 12
                } else if (!isManana && rawHour < 12) {
                    // Si rawHour + 12 está todavía por delante en el día de hoy, asumimos hoy en la tarde/noche
                    if (rawHour + 12 > now.hour || (rawHour + 12 == now.hour && minute > now.minute)) {
                        rawHour += 12
                    } else if (rawHour <= 7 && now.hour >= 11) {
                        rawHour += 12
                    }
                }

                targetTime = LocalTime.of(rawHour.coerceIn(0, 23), minute.coerceIn(0, 59))
            }
        }

        if (targetTime == null) return null

        var targetDateTime = LocalDateTime.of(targetDate, targetTime).withSecond(0).withNano(0)
        // Si no especificó día y la hora calculada ya pasó hoy, se programa para mañana automáticamente
        if (!t.contains("hoy") && !t.contains("mañana") && !t.contains("manana") && targetDateTime.isBefore(now)) {
            targetDateTime = targetDateTime.plusDays(1)
        }

        val desc = extractReminderDescription(text)
        return desc to targetDateTime
    }

    fun extractReminderDescription(original: String): String {
        var clean = original
        val removePatterns = listOf(
            Regex("(?i)sara[, ]*"),
            Regex("(?i)por favor[, ]*"),
            Regex("(?i)recuérdame( que| de| para)?"),
            Regex("(?i)recuerdame( que| de| para)?"),
            Regex("(?i)recuérdale( que| de| para)?"),
            Regex("(?i)recuerdale( que| de| para)?"),
            Regex("(?i)recuerda( que| de| para)?"),
            Regex("(?i)(crea|crear|pon|poner|anota|anotar|agrega|agregar) un recordatorio( que| de| para)?"),
            Regex("(?i)(pon|crea|poner|crear) una alarma( que| de| para)?"),
            Regex("(?i)avísame( que| de| para)?"),
            Regex("(?i)avisame( que| de| para)?"),
            Regex("(?i)(?:a|para|de)?\\s*(?:las?)?\\s*\\d{1,2}[:.]\\d{2}"),
            Regex("(?i)(?:a|para|de)?\\s*(?:las?|la)?\\s*(\\d{1,2}|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)(?: y media| y cuarto| con \\d{1,2})?"),
            Regex("(?i)de la (tarde|noche|mañana|manana)"),
            Regex("(?i)en (\\d+|un|una|uno|dos|tres|cuatro|cinco|diez|quince|veinte|treinta) (?:minutos?|segundos?|horas?)"),
            Regex("(?i)en media hora"),
            Regex("(?i)en un cuarto de hora"),
            Regex("(?i)pasado mañana"),
            Regex("(?i)pasado manana"),
            Regex("(?i)mañana"),
            Regex("(?i)manana"),
            Regex("(?i)hoy")
        )

        for (p in removePatterns) {
            clean = clean.replace(p, " ")
        }

        clean = clean.trim().removePrefix("de ").removePrefix("que ").removePrefix("para ").removePrefix(":").trim()
        clean = clean.replace(Regex("\\s+"), " ")

        return if (clean.isNotBlank()) clean else "Recordatorio médico"
    }

    fun ejecutarComandoRecordatorio(userText: String) {
        // Estructura de Log Requerida - Paso 1: Recordatorio recibido
        AppLogger.info("Recordatorio", "Recordatorio recibido: \"$userText\"")

        val parsed = parseReminderIntent(userText)
        if (parsed == null) {
            val t = userText.lowercase().trim()
            val hasTimeClues = t.contains(" a ") || t.contains(" para ") || t.contains(" en ") ||
                    t.any { it.isDigit() } || t.contains("hora") || t.contains("minuto")

            if (!hasTimeClues) {
                val desc = extractReminderDescription(userText)
                hablarTextoDirecto("¿Para qué hora o en cuántos minutos deseas tu recordatorio de $desc?")
                return
            }

            AppLogger.info("Recordatorio", "Comando de recordatorio complejo, procesando con Gemini Function Calling...")
            val updatedMessages = _uiState.value.messages +
                    ChatMessage(role = "user", text = userText) +
                    ChatMessage(role = "model", text = "")
            _uiState.update { it.copy(messages = updatedMessages, isThinking = true, faceState = "THINKING") }
            synchronized(spokenSentences) { spokenSentences.clear() }
            sentenceBuffer.clear()
            utteranceCounter = 0
            val historyPairs = updatedMessages.dropLast(2).takeLast(6).map { Pair(it.role, it.text) }

            streamJob = viewModelScope.launch {
                try {
                    geminiRepository.streamMessage(historyPairs, userText).collect { chunk ->
                        sentenceBuffer.append(chunk)
                        val sentences = extractSentences(sentenceBuffer)
                        for (sentence in sentences) {
                            val clean = TextUtils.stripMarkdown(sentence).trim()
                            if (clean.isNotEmpty()) {
                                utteranceCounter++
                                val utteranceId = "SARA_STREAM_$utteranceCounter"
                                _uiState.update { it.copy(isThinking = false, isSpeaking = true, faceState = "SPEAKING") }
                                voiceInputManager?.isSaraSpeaking = true
                                textToSpeechManager?.speakSentence(clean, utteranceId)
                            }
                        }
                    }
                    val remaining = sentenceBuffer.toString().trim()
                    if (remaining.isNotEmpty()) {
                        val clean = TextUtils.stripMarkdown(remaining).trim()
                        if (clean.isNotEmpty()) {
                            utteranceCounter++
                            val utteranceId = "SARA_STREAM_$utteranceCounter"
                            _uiState.update { it.copy(isThinking = false, isSpeaking = true, faceState = "SPEAKING") }
                            voiceInputManager?.isSaraSpeaking = true
                            textToSpeechManager?.speakSentence(clean, utteranceId)
                        }
                        sentenceBuffer.clear()
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    AppLogger.info("BargeIn", "Streaming cancelado")
                } catch (e: Exception) {
                    AppLogger.error("Recordatorio", "Error durante streaming de recordatorio: ${e.message}")
                } finally {
                    streamJob = null
                }
            }
            return
        }

        val (descripcion, fechaHora) = parsed
        AppLogger.info("Recordatorio", "Hora y descripción interpretadas con éxito: '$descripcion' para $fechaHora")

        viewModelScope.launch {
            val result = recordatorioRepository.crearYGuardarRecordatorio(descripcion, fechaHora)
            result.onSuccess {
                val horaFmt = fechaHora.format(DateTimeFormatter.ofPattern("HH:mm"))
                val diaDesc = if (fechaHora.toLocalDate() == LocalDate.now()) "hoy" else "mañana"
                val confirmacion = "Listo, he guardado tu recordatorio para $diaDesc a las $horaFmt: \"$descripcion\""
                hablarTextoDirecto(confirmacion)
            }.onFailure { err ->
                AppLogger.error("Recordatorio", "Error al guardar recordatorio: ${err.message}")
                hablarTextoDirecto("No pude guardar el recordatorio: ${err.message}")
            }
        }
    }

    fun ejecutarCreacionRecordatorioDesdeTool(descripcion: String, fechaStr: String, horaStr: String) {
        // Estructura de Log Requerida - Paso 1: Recordatorio recibido (desde Tool)
        AppLogger.info("Recordatorio", "Recordatorio recibido (vía Gemini Tool): '$descripcion' para $fechaStr $horaStr")

        viewModelScope.launch {
            val hoy = LocalDate.now()
            val fecha = if (fechaStr.isNotBlank()) {
                try {
                    LocalDate.parse(fechaStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (_: Exception) { hoy }
            } else hoy

            val hora = if (horaStr.isNotBlank()) {
                try {
                    val cleanHora = if (horaStr.contains(":")) horaStr.trim() else "${horaStr.trim()}:00"
                    LocalTime.parse(cleanHora, DateTimeFormatter.ofPattern("H:m"))
                } catch (_: Exception) {
                    LocalTime.now().plusMinutes(10)
                }
            } else LocalTime.now().plusMinutes(10)

            val fechaHora = LocalDateTime.of(fecha, hora)
            val result = recordatorioRepository.crearYGuardarRecordatorio(descripcion, fechaHora)
            result.onSuccess {
                val horaFmt = fechaHora.format(DateTimeFormatter.ofPattern("HH:mm"))
                val diaDesc = if (fecha == hoy) "hoy" else "el ${fecha.format(DateTimeFormatter.ofPattern("dd/MM"))}"
                val confirmacion = "Listo, he guardado tu recordatorio para $diaDesc a las $horaFmt: \"$descripcion\""
                hablarTextoDirecto(confirmacion)
            }.onFailure { err ->
                AppLogger.error("Recordatorio", "Error al guardar recordatorio desde tool: ${err.message}")
                hablarTextoDirecto("No pude guardar el recordatorio: ${err.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
        voiceInputManager?.stopListening()
        textToSpeechManager?.shutdown()
        serialDispatcher?.shutdown()
        reproductorManager.liberar()
    }
}
