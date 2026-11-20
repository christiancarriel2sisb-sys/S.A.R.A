package com.sara.robot.voice

import android.content.Context
import android.content.Intent
import android.media.audiofx.AcousticEchoCanceler
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.sara.robot.core.AppLogger
import java.util.Locale

class VoiceInputManager(
    private val context: Context,
    private val onResultText: (String) -> Unit,
    private val onErrorCountExceeded: () -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var failureCount = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    // Callbacks para soporte de interrupción (Barge-In)
    var onBargeInDetected: (() -> Unit)? = null
    var isSaraSpeaking: Boolean = false

    init {
        // Verificar disponibilidad de cancelación de eco acústico por hardware (AcousticEchoCanceler)
        if (AcousticEchoCanceler.isAvailable()) {
            AppLogger.success("VoiceInput", "AcousticEchoCanceler está DISPONIBLE por hardware en este dispositivo")
        } else {
            AppLogger.info("VoiceInput", "AcousticEchoCanceler no disponible por hardware (emulador/dispositivo estándar)")
        }
    }

    fun startListening() {
        // SpeechRecognizer debe instanciarse y ejecutarse en el MainThread
        mainHandler.post {
            val isAvailable = SpeechRecognizer.isRecognitionAvailable(context)
            AppLogger.info("VoiceInput", "Iniciando escucha. Reconocimiento disponible en SO: $isAvailable (SaraSpeaking=$isSaraSpeaking)")

            if (!isAvailable) {
                AppLogger.error(
                    "VoiceInput",
                    "SpeechRecognizer NO está disponible. Asegúrate de tener un emulador con Google APIs o Google Play Store."
                )
                onErrorCountExceeded()
                return@post
            }

            stopListeningInternal()

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        AppLogger.info("VoiceInput", "Micrófono listo para recibir voz (onReadyForSpeech)")
                    }

                    override fun onBeginningOfSpeech() {
                        AppLogger.info("VoiceInput", "Voz humana detectada (onBeginningOfSpeech)")
                        // Si Sara está hablando mientras el usuario habla -> ¡Barge-In instantáneo!
                        if (isSaraSpeaking) {
                            AppLogger.warn("BargeIn", "¡Voz del usuario detectada mientras Sara hablaba! Disparando interrupción...")
                            onBargeInDetected?.invoke()
                        }
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Si el nivel de decibeles de voz humana sube considerablemente durante el habla de Sara
                        if (isSaraSpeaking && rmsdB > 8.0f) {
                            AppLogger.warn("BargeIn", "Nivel RMS elevado ($rmsdB dB) mientras Sara habla. Disparando interrupción...")
                            onBargeInDetected?.invoke()
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        AppLogger.info("VoiceInput", "Fin de segmento de voz (onEndOfSpeech), decodificando...")
                    }

                    override fun onError(error: Int) {
                        val errorMsg = getErrorMessage(error)
                        AppLogger.warn("VoiceInput", "Error de captura de voz: código $error ($errorMsg)")

                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            failureCount++
                        } else if (error == 12 || error == 13) {
                            AppLogger.error(
                                "VoiceInput",
                                "El modelo de idioma no está disponible localmente. Se ajustará al idioma por defecto del sistema."
                            )
                            failureCount += 2
                        } else {
                            failureCount++
                        }

                        if (failureCount >= 3) {
                            AppLogger.warn("VoiceInput", "Umbral de fallos alcanzado.")
                            onErrorCountExceeded()
                            failureCount = 0
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        failureCount = 0
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        AppLogger.success("VoiceInput", "Voz reconocida con éxito: \"$text\"")

                        if (text.isNotBlank()) {
                            onResultText(text)
                        } else {
                            AppLogger.warn("VoiceInput", "Resultado de voz recibido pero venía vacío")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!partial.isNullOrBlank()) {
                            AppLogger.info("VoiceInput", "Transcripción parcial: \"$partial\"")
                            if (isSaraSpeaking) {
                                AppLogger.warn("BargeIn", "Transcripción parcial detectada mientras Sara hablaba: \"$partial\"")
                                onBargeInDetected?.invoke()
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val defaultLocale = Locale.getDefault()
                val langTag = if (defaultLocale.language == "es") defaultLocale.toLanguageTag() else "es-ES"

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                speechRecognizer?.startListening(intent)

            } catch (e: Exception) {
                AppLogger.error("VoiceInput", "Excepción al inicializar SpeechRecognizer: ${e.message}")
            }
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            AppLogger.warn("VoiceInput", "Error al destruir recognizer: ${e.message}")
        }
        speechRecognizer = null
    }

    fun stopListening() {
        mainHandler.post {
            AppLogger.info("VoiceInput", "Deteniendo escucha de voz")
            stopListeningInternal()
        }
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Error de grabación de audio (código 3)"
            SpeechRecognizer.ERROR_CLIENT -> "Error del cliente de reconocimiento (código 5)"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permiso de micrófono no concedido RECORD_AUDIO (código 9)"
            SpeechRecognizer.ERROR_NETWORK -> "Error de red (código 2)"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tiempo de espera de red agotado (código 1)"
            SpeechRecognizer.ERROR_NO_MATCH -> "No hubo coincidencia de audio/voz inteligible (código 7)"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado (código 8)"
            SpeechRecognizer.ERROR_SERVER -> "Error en el servidor de Google Speech (código 4)"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó habla / silencio (código 6)"
            10 -> "ERROR_TOO_MANY_REQUESTS (código 10)"
            11 -> "ERROR_SERVER_DISCONNECTED (código 11)"
            12 -> "ERROR_LANGUAGE_NOT_SUPPORTED (código 12)"
            13 -> "ERROR_LANGUAGE_UNAVAILABLE (código 13)"
            14 -> "ERROR_CANNOT_CHECK_SUPPORT (código 14)"
            else -> "Error desconocido ($errorCode)"
        }
    }
}
