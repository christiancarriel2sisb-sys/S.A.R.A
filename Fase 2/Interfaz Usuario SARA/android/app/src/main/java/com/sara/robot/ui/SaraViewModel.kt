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

    private var voiceInputManager: VoiceInputManager? = null
    private var textToSpeechManager: TextToSpeechManager? = null

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

        // Inicializar síntesis de voz nativa con soporte de oraciones encadenadas
        textToSpeechManager = TextToSpeechManager(application) {
            // Callback: Todas las oraciones han terminado de hablar
            voiceInputManager?.isSaraSpeaking = false
            _uiState.update { it.copy(isSpeaking = false, faceState = "LISTENING") }
            startListening()
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

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
        voiceInputManager?.stopListening()
        textToSpeechManager?.shutdown()
    }
}
