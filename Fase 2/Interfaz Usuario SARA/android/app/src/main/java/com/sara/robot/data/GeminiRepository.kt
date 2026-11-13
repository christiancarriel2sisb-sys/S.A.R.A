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
                })
            })
        }

        return JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", SaraPrompt.SYSTEM_PROMPT)))
            })
            put("contents", contentsArray)
            put("tools", toolsArray)
        }
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
