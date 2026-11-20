package com.sara.robot.data

import com.sara.robot.core.AppLogger
import com.sara.robot.core.SaraPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class GeminiRepository(private var apiKey: String = "") {

    var onFunctionCallListener: ((name: String, args: JSONObject?) -> Unit)? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun updateApiKey(newApiKey: String) {
        this.apiKey = newApiKey.trim()
        AppLogger.info("Gemini", "API Key actualizada en repositorio")
    }

    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    // Equivalente a /api/gemini/check
    suspend fun checkConnection(): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        AppLogger.info("Gemini", "Verificando conexión con API de Gemini (ping)...")
        val start = System.currentTimeMillis()
        return@withContext try {
            val response = executePrompt("Hola")
            val latency = System.currentTimeMillis() - start
            AppLogger.success("Gemini", "Conexión con Gemini verificada OK. Latencia: ${latency}ms")
            Pair(response.isNotBlank(), latency)
        } catch (e: Exception) {
            AppLogger.error("Gemini", "Fallo al verificar conexión con Gemini: ${e.message}")
            Pair(false, 0L)
        }
    }

    // Flujo con Streaming SSE continuo y cancelable en tiempo real (Fase 1: Barge-In)
    fun streamMessage(
        history: List<Pair<String, String>>,
        userMessage: String
    ): Flow<String> = flow {
        if (apiKey.isBlank()) {
            val err = "API Key de Gemini no configurada."
            AppLogger.error("Gemini", err)
            throw IllegalStateException(err)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse&key=$apiKey"
        val payload = buildPayload(userMessage, history)

        val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        AppLogger.info("Gemini", "Iniciando streaming SSE con Gemini...")

        val call = client.newCall(request)
        val response = call.execute()

        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            response.close()
            val err = "Error HTTP ${response.code} en stream: $errBody"
            AppLogger.error("Gemini", err)
            throw Exception(err)
        }

        val responseBody = response.body ?: throw Exception("Cuerpo de respuesta vacío")

        try {
            BufferedReader(InputStreamReader(responseBody.byteStream(), Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    currentCoroutineContext().ensureActive() // Cooperativo con cancelación de Coroutine

                    val currentLine = line?.trim() ?: continue
                    if (currentLine.startsWith("data:")) {
                        val jsonStr = currentLine.removePrefix("data:").trim()
                        if (jsonStr.isNotBlank()) {
                            try {
                                val json = JSONObject(jsonStr)
                                val candidates = json.optJSONArray("candidates")
                                if (candidates != null && candidates.length() > 0) {
                                    val candidate = candidates.getJSONObject(0)
                                    val content = candidate.optJSONObject("content")
                                    val parts = content?.optJSONArray("parts")
                                    if (parts != null && parts.length() > 0) {
                                        for (p in 0 until parts.length()) {
                                            val part = parts.getJSONObject(p)
                                            val chunkText = part.optString("text", "")
                                            if (chunkText.isNotEmpty()) {
                                                emit(chunkText)
                                            }
                                            val functionCall = part.optJSONObject("functionCall")
                                            if (functionCall != null) {
                                                val callName = functionCall.optString("name", "")
                                                val callArgs = functionCall.optJSONObject("args")
                                                AppLogger.info("Gemini", "FunctionCall detectado en stream: $callName args=$callArgs")
                                                onFunctionCallListener?.invoke(callName, callArgs)
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Línea de metadatos o fragmento incompleto
                            }
                        }
                    }
                }
            }
        } finally {
            responseBody.close()
            response.close()
            AppLogger.info("Gemini", "Stream SSE cerrado")
        }
    }.flowOn(Dispatchers.IO)

    // Llamada tradicional bloqueante (Fallback)
    suspend fun sendMessage(history: List<Pair<String, String>>, userMessage: String): String = withContext(Dispatchers.IO) {
        AppLogger.info("Gemini", "Enviando mensaje a Gemini (bloqueante): \"$userMessage\"")
        return@withContext executePrompt(userMessage, history)
    }

    private fun buildPayload(message: String, history: List<Pair<String, String>>): JSONObject {
        val contentsArray = JSONArray()

        for ((role, text) in history) {
            val roleFormatted = if (role == "user") "user" else "model"
            contentsArray.put(JSONObject().apply {
                put("role", roleFormatted)
                put("parts", JSONArray().put(JSONObject().put("text", text)))
            })
        }

        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", message)))
        })

        // Herramientas declaradas según el Paso 3 de modulo de musica.md
        val toolsArray = JSONArray().apply {
            put(JSONObject().apply {
                put("functionDeclarations", JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", "controlar_multimedia")
                        put("description", "Reproduce música, pausa o busca canciones según la petición del usuario.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("accion", JSONObject().apply {
                                    put("type", "STRING")
                                    put("enum", JSONArray().put("reproducir").put("pausar").put("detener").put("reanudar"))
                                    put("description", "Acción a realizar sobre la música")
                                })
                                put("query", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "Nombre de la canción, artista o género a buscar")
                                })
                            })
                            put("required", JSONArray().put("accion"))
                        })
                    })
                    put(JSONObject().apply {
                        put("name", "enviar_mensaje_familiar")
                        put("description", "Envía un mensaje privado a un familiar o contacto de emergencia cuando el paciente lo solicita.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("destinatario", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "Nombre del familiar o contacto (ej. Claudia, Pedro)")
                                })
                                put("mensaje", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "Texto del mensaje a enviar")
                                })
                            })
                            put("required", JSONArray().put("destinatario").put("mensaje"))
                        })
                    })
                    put(JSONObject().apply {
                        put("name", "crear_recordatorio")
                        put("description", "Crea y almacena un recordatorio o alarma clínica en la base de datos cuando el usuario o paciente lo solicita.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("descripcion", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "Motivo o texto del recordatorio (ej. Tomar paracetamol, Control médico)")
                                })
                                put("fecha", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "Fecha en formato YYYY-MM-DD (ej. 2026-11-18). Si es para hoy usar la fecha actual; si es para mañana, el día siguiente.")
                                })
                                put("hora", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "Hora del recordatorio en formato 24 horas HH:mm (ej. 15:30, 09:00, 20:00)")
                                })
                            })
                            put("required", JSONArray().put("descripcion").put("hora"))
                        })
                    })
                })
            })
        }

        val currentDateTimeStr = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val systemPromptWithDate = "${SaraPrompt.SYSTEM_PROMPT}\n[Fecha y hora actual del sistema: $currentDateTimeStr]"

        return JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemPromptWithDate)))
            })
            put("contents", contentsArray)
            put("tools", toolsArray)
        }
    }

    /**
     * Cuando Jev detecta la intención CHAT_FAMILIAR, utiliza la API de Gemini para separar
     * con precisión el destinatario (para quién es el mensaje) y el contenido limpio del mensaje.
     */
    suspend fun extraerDestinatarioYMensajeChat(
        userText: String,
        contactosDisponibles: List<String>
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            AppLogger.warn("GeminiChatExtractor", "GEMINI_API_KEY vacía; se usará extractor local de respaldo.")
            return@withContext null
        }

        val listaContactosStr = if (contactosDisponibles.isNotEmpty()) {
            contactosDisponibles.joinToString(", ")
        } else {
            "Claudia Pérez, Rodrigo Muñoz, Patricia Díaz"
        }

        val promptExtraccion = """
            El paciente adulto mayor dijo la siguiente frase para enviar un mensaje a un familiar:
            "$userText"

            Contactos familiares registrados en la base de datos:
            [$listaContactosStr]

            Tu tarea es identificar únicamente:
            1. "destinatario": El nombre del familiar al que va dirigido el mensaje (si coincide con alguno de la lista, usa su nombre registrado).
            2. "mensaje": El contenido limpio del mensaje que el paciente quiere decirle (sin frases de comando como "dile a...", "mándale un mensaje que...").

            Invoca la herramienta enviar_mensaje_familiar o responde exclusivamente con un objeto JSON válido con las claves "destinatario" y "mensaje".
        """.trimIndent()

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val payload = buildPayload(promptExtraccion, emptyList())
            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(requestBody).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    AppLogger.warn("GeminiChatExtractor", "HTTP ${response.code} en extracción de chat: $errBody")
                    return@withContext null
                }

                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val candidates = json.optJSONArray("candidates") ?: return@withContext null
                if (candidates.length() == 0) return@withContext null
                val parts = candidates.getJSONObject(0)
                    .optJSONObject("content")
                    ?.optJSONArray("parts") ?: return@withContext null

                var rawTextReply = ""
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    val functionCall = part.optJSONObject("functionCall")
                    if (functionCall != null) {
                        val callName = functionCall.optString("name", "")
                        val args = functionCall.optJSONObject("args")
                        if (callName == "enviar_mensaje_familiar" && args != null) {
                            val dest = args.optString("destinatario", "").trim()
                            val msg = args.optString("mensaje", "").trim()
                            if (dest.isNotBlank() && msg.isNotBlank()) {
                                AppLogger.success(
                                    "GeminiChatExtractor",
                                    "Gemini FunctionCall extrajo -> Destinatario: '$dest' | Mensaje: '$msg'"
                                )
                                return@withContext Pair(dest, msg)
                            }
                        }
                    }
                    val txt = part.optString("text", "")
                    if (txt.isNotBlank()) {
                        rawTextReply += txt
                    }
                }

                // Si Gemini respondió como JSON en texto plano en lugar de FunctionCall
                if (rawTextReply.isNotBlank()) {
                    val cleanJson = rawTextReply
                        .replace("```json", "", ignoreCase = true)
                        .replace("```", "")
                        .trim()
                    val firstBrace = cleanJson.indexOf('{')
                    val lastBrace = cleanJson.lastIndexOf('}')
                    if (firstBrace != -1 && lastBrace > firstBrace) {
                        val obj = JSONObject(cleanJson.substring(firstBrace, lastBrace + 1))
                        val dest = obj.optString("destinatario", "").trim()
                        val msg = obj.optString("mensaje", "").trim()
                        if (dest.isNotBlank() && msg.isNotBlank()) {
                            AppLogger.success(
                                "GeminiChatExtractor",
                                "Gemini JSON extrajo -> Destinatario: '$dest' | Mensaje: '$msg'"
                            )
                            return@withContext Pair(dest, msg)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.warn("GeminiChatExtractor", "Fallo al extraer destinatario/mensaje con Gemini: ${e.message}")
        }
        return@withContext null
    }

    private fun executePrompt(message: String, history: List<Pair<String, String>> = emptyList()): String {
        if (apiKey.isBlank()) {
            val err = "API Key de Gemini no configurada."
            AppLogger.error("Gemini", err)
            throw IllegalStateException(err)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val payload = buildPayload(message, history)

        val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                val err = "Error HTTP ${response.code} desde Gemini: $errBody"
                AppLogger.error("Gemini", err)
                throw Exception(err)
            }
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val candidate = json.getJSONArray("candidates").getJSONObject(0)
            val content = candidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            var reply = ""
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val text = part.optString("text", "")
                if (text.isNotBlank()) {
                    reply += text
                }
                val functionCall = part.optJSONObject("functionCall")
                if (functionCall != null) {
                    val callName = functionCall.optString("name", "")
                    val callArgs = functionCall.optJSONObject("args")
                    AppLogger.info("Gemini", "FunctionCall detectado en respuesta: $callName args=$callArgs")
                    onFunctionCallListener?.invoke(callName, callArgs)
                }
            }
            AppLogger.success("Gemini", "Respuesta recibida de Gemini: \"$reply\"")
            return reply
        }
    }
}
