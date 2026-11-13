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

        // Listener de Function Calling para reproducción multimedia
        geminiRepository.onFunctionCallListener = { name, args ->
            if (name == "controlar_multimedia") {
                val accion = args?.optString("accion", "reproducir") ?: "reproducir"
                val query = args?.optString("query", "") ?: ""
                AppLogger.success("SaraViewModel", "Tool 'controlar_multimedia' invocada: accion=$accion, query='$query'")
                ejecutarComandoMultimedia(accion, query)
            }
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

    fun wakeUp() {
        if (_uiState.value.faceState == "IDLE" || _uiState.value.faceState == "ERROR") {
            startListening()
        } else if (_uiState.value.isSpeaking || _uiState.value.isThinking) {
            interruptSara()
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

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
        voiceInputManager?.stopListening()
        textToSpeechManager?.shutdown()
        serialDispatcher?.shutdown()
        reproductorManager.liberar()
    }
}
