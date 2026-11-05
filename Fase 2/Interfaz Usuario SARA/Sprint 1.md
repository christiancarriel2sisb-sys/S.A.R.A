# Guía de Portabilidad Directa: Fase 2 (Web/Node/Whisper) a Android Nativo (Kotlin)

**Autor:** Christian  
**Proyecto:** Robot Sara - Módulo Conversacional  
**Objetivo:** Reemplazar el stack distribuido (`Angular` + `Node.js proxy` + `OpenAI Whisper API` + `@google/genai`) por una aplicación Android autónoma en Kotlin, ejecutada 100% en el celular.

---

## 1. Tabla de Equivalencias de Arquitectura

El proyecto web actual dependía de tres saltos de red (`Angular -> Node Server -> Whisper / Gemini`). En Android, el teléfono procesa la voz de forma local y conecta directamente con la API de Gemini:

| Componente Original (Fase 2 Web/Node) | Equivalente Nativo en Android (Kotlin) | Ventaja Clave |
| :--- | :--- | :--- |
| **`server/sara-prompt.js`** | Objeto Kotlin `SaraPrompt.kt` | Mantiene el mismo `SYSTEM_PROMPT`, saludo, disculpa y rechazo estricto. |
| **`server/index.js` (`/api/whisper-stt`)** | `android.speech.SpeechRecognizer` | Se elimina el servidor Node y el costo/latencia de enviar buffers binarios a Whisper. |
| **`server/index.js` (`/api/chat` y `/check`)** | `GeminiApiService` con **Ktor** o **Retrofit + OkHttp** | Conexión directa y asíncrona con el endpoint de Gemini (`v1beta/models/...:generateContent`). |
| **`web/whisper-audio.service.ts`** | `VoiceInputManager.kt` (`RecognitionListener`) | Captura continua sin necesidad de instanciar `MediaRecorder` ni compilar blobs de audio. |
| **`web/gemini-connection.service.ts`** | `GeminiRepository.kt` + `EncryptedSharedPreferences` | Almacenamiento seguro de la API key con Android Keystore. |
| **`web/core/adapters/text-utils.ts`** | Archivo de utilidades `TextUtils.kt` | Funciones `stripMarkdown()` y `splitSentences()` portadas a expresiones regulares de Kotlin. |
| **`web/core/adapters/voice-picker.ts`** | `TextToSpeechManager.kt` | Búsqueda y fijación del motor TTS nativo configurado en español femenino (`Locale("es", "CL")` o `"es-ES"`). |
| **`web/app.component.ts` (Navegación)** | `NavHost` con **Jetpack Compose Navigation** | Pestañas nativas: Pantalla 1 (Dictado local) y Pantalla 2 (Conversación Sara). |

---

## 2. Portabilidad de Archivos Clave

### 2.1 Prompt del Sistema (`SaraPrompt.kt`)
Se traslada íntegramente la lógica declarada en tu `sara-prompt.js`:

```kotlin
package com.sara.robot.core

object SaraPrompt {
    const val SYSTEM_PROMPT = """
        Eres Sara, un robot de asistencia cotidiana amable, empático y respetuoso.
        Reglas de respuesta:
        1. Responde siempre en español con frases cortas (de 1 a 3 frases como máximo).
        2. No utilices formato Markdown (nada de negritas, asteriscos, viñetas ni encabezados).
        3. No generes bloques de código, URLs ni intentes procesar imágenes.
        4. Si el usuario consulta sobre diagnósticos médicos, prescripción o dosificación de fármacos, rechaza amablemente dar indicaciones clínicas y sugiérele explícitamente acudir a un médico especialista.
    """

    const val SALUDO_INICIAL = "Hola, soy Sara. ¿En qué puedo ayudarte hoy?"
    const val MENSAJE_DISCULPA = "Disculpa, no logré entenderte bien. ¿Podrías repetirlo?"
    const val MENSAJE_RECHAZO_CLINICO = "No puedo darte indicaciones médicas ni recetarte fármacos. Por favor, consulta a un médico especialista."
}
```

### 2.2 Utilidades de Texto (`TextUtils.kt`)
Reemplazo directo de `text-utils.ts`:

```kotlin
package com.sara.robot.core

object TextUtils {
    // Elimina caracteres de Markdown para evitar que el TTS lea "asterisco asterisco"
    fun stripMarkdown(text: String): String {
        return text
            .replace(Regex("[*#_`~>\\[\\]()]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // Divide respuestas en oraciones si se requiere verbalización progresiva
    fun splitSentences(text: String): List<String> {
        return text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
    }
}
```

### 2.3 Manejo de Voz Nativa (`VoiceInputManager.kt`)
Reemplaza a `whisper-audio.service.ts` y al endpoint `/api/whisper-stt`:

```kotlin
package com.sara.robot.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class VoiceInputManager(
    private val context: Context,
    private val onResultText: (String) -> Unit,
    private val onErrorCountExceeded: () -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var failureCount = 0

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return

        stopListening()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    failureCount++
                    if (failureCount >= 2) {
                        onErrorCountExceeded() // Dispara menú alternativo en pantalla (CU-1)
                        failureCount = 0
                    }
                }

                override fun onResults(results: Bundle?) {
                    failureCount = 0
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        onResultText(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "CL").toString())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
```

### 2.4 Síntesis de Voz Femenina (`TextToSpeechManager.kt`)
Reemplaza `voice-picker.ts` y `SpeechSynthesisAdapter`:

```kotlin
package com.sara.robot.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.sara.robot.core.TextUtils
import java.util.Locale

class TextToSpeechManager(context: Context, private val onSpeakingComplete: () -> Unit) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = Locale("es", "CL")
                tts?.language = locale

                // Seleccionar voz femenina si está disponible en el motor del SO
                val femaleVoice = tts?.voices?.find { voice ->
                    voice.locale.language == "es" && voice.name.contains("female", ignoreCase = true)
                }
                femaleVoice?.let { tts?.voice = it }

                isInitialized = true
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                onSpeakingComplete()
            }
            override fun onError(utteranceId: String?) {}
        })
    }

    fun speak(rawText: String) {
        if (!isInitialized) return
        val cleanText = TextUtils.stripMarkdown(rawText)
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "SARA_SPEECH_ID")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
```

### 2.5 Servicio Directo de Gemini (`GeminiRepository.kt`)
Reemplaza `/api/chat` y `/api/gemini/check` de tu servidor Node.js:

```kotlin
package com.sara.robot.data

import com.sara.robot.core.SaraPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiRepository(private var apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Equivalente a /api/gemini/check
    suspend fun checkConnection(): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        return@withContext try {
            val response = executePrompt("Hola")
            val latency = System.currentTimeMillis() - start
            Pair(response.isNotBlank(), latency)
        } catch (e: Exception) {
            Pair(false, 0L)
        }
    }

    // Equivalente a /api/chat
    suspend fun sendMessage(history: List<Pair<String, String>>, userMessage: String): String = withContext(Dispatchers.IO) {
        return@withContext executePrompt(userMessage, history)
    }

    private fun executePrompt(message: String, history: List<Pair<String, String>> = emptyList()): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

        val contentsArray = JSONArray()

        // Historial contextual (mínimo 3 turnos)
        for ((role, text) in history) {
            val roleFormatted = if (role == "user") "user" else "model"
            contentsArray.put(JSONObject().apply {
                put("role", roleFormatted)
                put("parts", JSONArray().put(JSONObject().put("text", text)))
            })
        }

        // Mensaje actual
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", message)))
        })

        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", SaraPrompt.SYSTEM_PROMPT)))
            })
            put("contents", contentsArray)
        }

        val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Error Gemini: ${response.code}")
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val candidate = json.getJSONArray("candidates").getJSONObject(0)
            return candidate.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        }
    }
}
```

---

## 3. Navegación en Jetpack Compose (Reemplazo de Angular Routes)

Tu `app.component.ts` tenía una barra superior para alternar entre `/dictado` (Fase 1) y `/gemini` (Fase 2). En Android nativo se implementa con una barra superior (`TopAppBar`) o `NavigationBar`:

```kotlin
@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(1) } // 0: Dictado, 1: Sara Conversación

    Scaffold(
        topBar = {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Dictado (Fase 1)") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Sara IA (Fase 2)") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> DictadoScreen()
                1 -> SaraGeminiScreen()
            }
        }
    }
}
```

---

## 4. Resumen de Flujo de Ejecución en Android

```
[Usuario habla] 
       │
       ▼
VoiceInputManager (SpeechRecognizer Nativo en español)
       │ (Texto transcrito, sin enviar audio por red)
       ▼
SaraViewModel (Valida historial, turnos y reglas)
       │
       ▼
GeminiRepository (HTTP directo a Google con System Prompt y API Key)
       │
       ▼
TextUtils.stripMarkdown() (Limpia la respuesta de 1 a 3 frases)
       │
       ▼
TextToSpeechManager (Voz femenina del teléfono verbaliza el mensaje)
```