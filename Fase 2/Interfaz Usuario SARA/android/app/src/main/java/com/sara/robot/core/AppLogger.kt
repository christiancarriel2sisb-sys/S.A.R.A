package com.sara.robot.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    INFO, SUCCESS, WARN, ERROR
}

data class LogEntry(
    val timestamp: String,
    val tag: String,
    val message: String,
    val level: LogLevel
)

/**
 * Gestor centralizado de logging reactivo para Sara Android.
 * Permite monitorear cada paso del ciclo de vida: arranque, permisos, TTS, STT (micrófono),
 * llamadas HTTP a Gemini, transcripciones y errores en tiempo real.
 */
object AppLogger {
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun log(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            tag = tag,
            message = message,
            level = level
        )
        // Imprimir también en Logcat estándar de Android
        when (level) {
            LogLevel.INFO -> android.util.Log.i("SARA_$tag", message)
            LogLevel.SUCCESS -> android.util.Log.i("SARA_$tag", "✓ $message")
            LogLevel.WARN -> android.util.Log.w("SARA_$tag", message)
            LogLevel.ERROR -> android.util.Log.e("SARA_$tag", message)
        }
        _logs.update { current ->
            (current + entry).takeLast(200) // Guardar hasta 200 eventos en memoria
        }
    }

    fun info(tag: String, message: String) = log(tag, message, LogLevel.INFO)
    fun success(tag: String, message: String) = log(tag, message, LogLevel.SUCCESS)
    fun warn(tag: String, message: String) = log(tag, message, LogLevel.WARN)
    fun error(tag: String, message: String) = log(tag, message, LogLevel.ERROR)

    fun clear() {
        _logs.value = emptyList()
    }
}

