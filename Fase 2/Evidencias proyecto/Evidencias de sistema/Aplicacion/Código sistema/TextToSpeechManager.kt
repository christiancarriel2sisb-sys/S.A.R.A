package com.sara.robot.voice

import android.content.Context
import android.media.audiofx.AcousticEchoCanceler
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.sara.robot.core.AppLogger
import com.sara.robot.core.TextUtils
import java.util.Locale

class TextToSpeechManager(
    context: Context,
    private val onAllSpeakingComplete: () -> Unit
) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var activeUtterancesCount = 0
    private var echoCanceler: AcousticEchoCanceler? = null

    var onSentenceDoneListener: ((utteranceId: String, text: String) -> Unit)? = null
    var onSentenceStartListener: ((utteranceId: String, text: String) -> Unit)? = null

    // Mapa para asociar utteranceId con el texto de la oración hablada
    private val utteranceTextMap = mutableMapOf<String, String>()

    init {
        AppLogger.info("TTS", "Inicializando motor TextToSpeech...")
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = Locale("es", "CL")
                val langResult = tts?.setLanguage(locale)
                AppLogger.info("TTS", "Motor TTS listo. Idioma es-CL configurado (código: $langResult)")

                // Seleccionar voz femenina si está disponible
                try {
                    val femaleVoice = tts?.voices?.find { voice ->
                        voice.locale.language == "es" && voice.name.contains("female", ignoreCase = true)
                    }
                    if (femaleVoice != null) {
                        tts?.voice = femaleVoice
                        AppLogger.info("TTS", "Voz femenina asignada: ${femaleVoice.name}")
                    } else {
                        AppLogger.info("TTS", "Usando voz por defecto en español")
                    }
                } catch (e: Exception) {
                    AppLogger.warn("TTS", "No se pudieron listar las voces: ${e.message}")
                }

                isInitialized = true
                AppLogger.success("TTS", "TextToSpeech inicializado correctamente")
            } else {
                AppLogger.error("TTS", "Fallo al inicializar TextToSpeech. Código: $status")
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val id = utteranceId ?: ""
                val text = utteranceTextMap[id] ?: ""
                AppLogger.info("TTS", "Iniciando verbalización de oración: \"$text\"")
                onSentenceStartListener?.invoke(id, text)
            }

            override fun onDone(utteranceId: String?) {
                val id = utteranceId ?: ""
                val text = utteranceTextMap.remove(id) ?: ""
                AppLogger.info("TTS", "Oración completada ($id): \"$text\"")
                onSentenceDoneListener?.invoke(id, text)

                synchronized(this@TextToSpeechManager) {
                    activeUtterancesCount--
                    if (activeUtterancesCount <= 0) {
                        activeUtterancesCount = 0
                        AppLogger.success("TTS", "Todas las oraciones verbalizadas con éxito")
                        onAllSpeakingComplete()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val id = utteranceId ?: ""
                utteranceTextMap.remove(id)
                AppLogger.error("TTS", "Error durante la verbalización (utteranceId: $id)")
                synchronized(this@TextToSpeechManager) {
                    activeUtterancesCount--
                    if (activeUtterancesCount <= 0) {
                        activeUtterancesCount = 0
                        onAllSpeakingComplete()
                    }
                }
            }
        })
    }

    /**
     * Envía una oración a la cola de audio (QUEUE_ADD) para encadenar respuestas fluidas (Fase 2).
     */
    fun speakSentence(rawSentence: String, utteranceId: String) {
        if (!isInitialized) {
            AppLogger.warn("TTS", "Intento de hablar antes de que TTS estuviera inicializado")
            return
        }
        val cleanText = TextUtils.stripMarkdown(rawSentence).trim()
        if (cleanText.isEmpty()) return

        synchronized(this) {
            activeUtterancesCount++
            utteranceTextMap[utteranceId] = cleanText
        }

        AppLogger.info("TTS", "Encolando en TTS (QUEUE_ADD): \"$cleanText\" [id=$utteranceId]")
        tts?.speak(cleanText, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    /**
     * Verbaliza texto completo en modo reemplazo directo (QUEUE_FLUSH).
     */
    fun speak(rawText: String) {
        if (!isInitialized) {
            AppLogger.warn("TTS", "Intento de hablar antes de que TTS estuviera inicializado")
            return
        }
        val cleanText = TextUtils.stripMarkdown(rawText).trim()
        if (cleanText.isEmpty()) return

        synchronized(this) {
            activeUtterancesCount = 1
            utteranceTextMap["SARA_SPEECH_SINGLE"] = cleanText
        }

        AppLogger.info("TTS", "Reproduciendo voz única (QUEUE_FLUSH): \"$cleanText\"")
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "SARA_SPEECH_SINGLE")
    }

    /**
     * Detiene en seco la salida física de audio y vacía la cola pendiente de TTS (Fase 4: Kill Switch).
     */
    fun stop() {
        synchronized(this) {
            activeUtterancesCount = 0
            utteranceTextMap.clear()
        }
        tts?.stop()
        AppLogger.info("TTS", "TextToSpeech detenido en seco y buffers vaciados")
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        echoCanceler?.release()
        AppLogger.info("TTS", "TextToSpeech cerrado")
    }
}
