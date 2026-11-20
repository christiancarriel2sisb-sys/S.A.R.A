package com.sara.robot.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel {
    INFO, SUCCESS, WARN, ERROR
}

/**
 * Módulos independientes del sistema S.A.R.A.
 * Cada módulo posee su propio buffer de logs aislado y su propio estado de salud
 * supervisado en segundo plano por el subproceso detector de errores.
 */
enum class SaraModule(val displayName: String, val shortCode: String, val description: String) {
    CONVERSACION_IA(
        displayName = "Módulo de Conversación y Voz",
        shortCode = "CONVERSACION",
        description = "Conexión con API de Gemini, síntesis TTS y reconocimiento de voz STT"
    ),
    VISION_BIOMETRIA(
        displayName = "Módulo de Reconocimiento Facial",
        shortCode = "VISION_FACIAL",
        description = "Cámara frontal 640x360, ML Kit Face Detection y vectores MobileFaceNet (192-d)"
    ),
    RECORDATORIOS(
        displayName = "Módulo de Recordatorios y Alarmas",
        shortCode = "RECORDATORIOS",
        description = "Persistencia en SQLite, subproceso diario en buffer RAM y AlarmManager"
    ),
    MUSICA_MULTIMEDIA(
        displayName = "Módulo de Música y Multimedia",
        shortCode = "MUSICA",
        description = "Búsqueda YouTube Data API v3 y reproducción en segundo plano"
    ),
    CHAT_FAMILIAR(
        displayName = "Módulo de Chat y Familiares",
        shortCode = "CHAT_FAMILIAR",
        description = "Mensajería privada local Paciente-Familiar y contactos de emergencia"
    ),
    NAVEGACION_HARDWARE(
        displayName = "Módulo de Navegación y Hardware",
        shortCode = "NAVEGACION",
        description = "Control motriz, comunicación serial USB-OTG con Arduino y seguridad"
    ),
    ORQUESTADOR_JEV(
        displayName = "Módulo Red Neuronal Jev (Intenciones)",
        shortCode = "RED_JEV",
        description = "Clasificador neuronal on-device con diccionario TF-IDF y Softmax"
    ),
    SISTEMA_BD(
        displayName = "Módulo de Sistema y Base de Datos",
        shortCode = "SISTEMA_BD",
        description = "Room SQLite (sara_local_db), permisos de Android y ciclo de vida"
    )
}

/**
 * Estado operativo de cada módulo para el panel de Control de Sara:
 * - OPERANDO (Luz Verde): El módulo ha ejecutado operaciones exitosas sin errores activos.
 * - DORMIDO (Luz Plateada): El módulo está en espera/reposo sin actividad reciente ni errores.
 * - DETENIDO (Luz Roja): El subproceso detectó errores críticos en el log del módulo.
 */
enum class ModuleOperationalState(val label: String) {
    OPERANDO("Operando"),
    DORMIDO("Dormido"),
    DETENIDO("Detenido")
}

data class LogEntry(
    val id: Long,
    val epochMs: Long,
    val timestamp: String,
    val tag: String,
    val message: String,
    val level: LogLevel,
    val module: SaraModule
)

data class ModuleMonitorStatus(
    val module: SaraModule,
    val state: ModuleOperationalState = ModuleOperationalState.DORMIDO,
    val logs: List<LogEntry> = emptyList(),
    val detectedErrors: List<LogEntry> = emptyList(),
    val lastActivityMs: Long = 0L,
    val lastErrorSummary: String? = null
)

/**
 * Gestor centralizado e independiente de logging por módulo con Subproceso Detector de Errores.
 *
 * Arquitectura de Eficiencia (O(1) Incremental Event-Driven):
 * 1. Cada log emitido se clasifica instantáneamente en su `SaraModule` correspondiente y se almacena
 *    en un buffer independiente por módulo (`moduleLogs`).
 * 2. Un subproceso asíncrono en segundo plano (`errorDetectionScope` en `Dispatchers.Default`)
 *    consume una cola reactiva no bloqueante (`Channel.UNLIMITED`) e inspecciona únicamente
 *    los nuevos eventos (incremental delta) sin re-escanear listas completas.
 * 3. Si detecta un evento `LogLevel.ERROR` o un patrón de fallo crítico (ej. desconexión de Gemini API,
 *    fallo de cámara, excepción SQL), conmuta el estado del módulo a `DETENIDO` (Luz Roja) y
 *    habilita la generación del informe técnico en formato Markdown (`.md`).
 */
object AppLogger {
    private val idCounter = AtomicLong(1L)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val reportDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    // Buffer global (para vista consolidada)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    // Estado independiente de cada módulo (logs separados + estado Operando/Dormido/Detenido + errores detectados)
    private val _moduleStatuses = MutableStateFlow<Map<SaraModule, ModuleMonitorStatus>>(
        SaraModule.entries.associateWith { mod ->
            ModuleMonitorStatus(module = mod, state = ModuleOperationalState.DORMIDO)
        }
    )
    val moduleStatuses: StateFlow<Map<SaraModule, ModuleMonitorStatus>> = _moduleStatuses.asStateFlow()

    // Subproceso dedicado en segundo plano para detección incremental de errores
    private val errorDetectorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val inspectionQueue = Channel<LogEntry>(Channel.UNLIMITED)

    // Tiempo tras el cual un módulo sin errores ni actividad pasa de OPERANDO a DORMIDO (90 seg)
    private const val IDLE_TIMEOUT_MS = 90_000L

    init {
        startErrorDetectionSubprocess()
    }

    /**
     * Subproceso de monitoreo y detección de errores en segundo plano.
     * Escucha de forma reactiva cada entrada nueva (complejidad O(1) por log, 0% CPU en reposo)
     * y verifica periódicamente la transición a estado DORMIDO por inactividad.
     */
    private fun startErrorDetectionSubprocess() {
        // 1. Consumidor reactivo O(1) de entradas de log
        errorDetectorScope.launch {
            for (entry in inspectionQueue) {
                inspectEntryForErrors(entry)
            }
        }

        // 2. Watchdog ligero cada 15 segundos para pasar módulos inactivos (sin errores) a DORMIDO
        errorDetectorScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15_000L)
                val now = System.currentTimeMillis()
                _moduleStatuses.update { currentMap ->
                    currentMap.mapValues { (_, status) ->
                        if (status.state == ModuleOperationalState.OPERANDO &&
                            status.detectedErrors.isEmpty() &&
                            status.lastActivityMs > 0L &&
                            (now - status.lastActivityMs) > IDLE_TIMEOUT_MS
                        ) {
                            status.copy(state = ModuleOperationalState.DORMIDO)
                        } else {
                            status
                        }
                    }
                }
            }
        }
    }

    /**
     * Evalúa si una entrada de log representa un fallo que detiene el módulo.
     */
    private fun inspectEntryForErrors(entry: LogEntry) {
        val msgLower = entry.message.lowercase()
        val isCriticalError = entry.level == LogLevel.ERROR ||
                msgLower.contains("error al conectar") ||
                msgLower.contains("fallo al") ||
                msgLower.contains("no se encontró gemini_api_key") ||
                msgLower.contains("exception") ||
                msgLower.contains("http 4") ||
                msgLower.contains("http 5")

        _moduleStatuses.update { currentMap ->
            val currentStatus = currentMap[entry.module] ?: ModuleMonitorStatus(module = entry.module)
            val updatedModuleLogs = (currentStatus.logs + entry).takeLast(120)

            val newStatus = if (isCriticalError) {
                val updatedErrors = (currentStatus.detectedErrors + entry).takeLast(50)
                currentStatus.copy(
                    state = ModuleOperationalState.DETENIDO,
                    logs = updatedModuleLogs,
                    detectedErrors = updatedErrors,
                    lastActivityMs = entry.epochMs,
                    lastErrorSummary = "[${entry.timestamp}] ${entry.message}"
                )
            } else {
                // Si el módulo estaba DETENIDO por un error previo, permanece en rojo (DETENIDO)
                // hasta que el administrador/sistema lo limpie o notifique, o si recibe un SUCCESS explícito de recuperación
                val recovered = entry.level == LogLevel.SUCCESS &&
                        (msgLower.contains("conectad") || msgLower.contains("inicializad") || msgLower.contains("vinculad") || msgLower.contains("exitosamente"))

                val nextState = when {
                    currentStatus.state == ModuleOperationalState.DETENIDO && !recovered -> ModuleOperationalState.DETENIDO
                    else -> ModuleOperationalState.OPERANDO
                }
                val nextErrors = if (recovered) emptyList() else currentStatus.detectedErrors
                val nextSummary = if (recovered) null else currentStatus.lastErrorSummary

                currentStatus.copy(
                    state = nextState,
                    logs = updatedModuleLogs,
                    detectedErrors = nextErrors,
                    lastActivityMs = entry.epochMs,
                    lastErrorSummary = nextSummary
                )
            }

            currentMap + (entry.module to newStatus)
        }
    }

    /**
     * Clasifica automáticamente el tag del log hacia su módulo independiente correspondiente.
     */
    fun resolveModule(tag: String): SaraModule {
        val t = tag.lowercase()
        return when {
            t.contains("gemini") || t.contains("tts") || t.contains("voice") ||
                    t.contains("bargein") || t.contains("saraviewmodel") || t.contains("sarascreen") -> SaraModule.CONVERSACION_IA

            t.contains("vision") || t.contains("biometria") || t.contains("facenet") ||
                    t.contains("face") || t.contains("camera") -> SaraModule.VISION_BIOMETRIA

            t.contains("recordatorio") || t.contains("reminder") || t.contains("alarm") -> SaraModule.RECORDATORIOS

            t.contains("music") || t.contains("reproductor") || t.contains("youtube") ||
                    t.contains("exoplayer") -> SaraModule.MUSICA_MULTIMEDIA

            t.contains("chat") || t.contains("contacto") || t.contains("familiar") -> SaraModule.CHAT_FAMILIAR

            t.contains("serial") || t.contains("arduino") || t.contains("motor") ||
                    t.contains("safety") || t.contains("nav") -> SaraModule.NAVEGACION_HARDWARE

            t.contains("jev") || t.contains("neural") || t.contains("intent") ||
                    t.contains("orchestrator") -> SaraModule.ORQUESTADOR_JEV

            else -> SaraModule.SISTEMA_BD
        }
    }

    fun log(tag: String, message: String, level: LogLevel = LogLevel.INFO, explicitModule: SaraModule? = null) {
        val now = System.currentTimeMillis()
        val module = explicitModule ?: resolveModule(tag)
        val entry = LogEntry(
            id = idCounter.getAndIncrement(),
            epochMs = now,
            timestamp = timeFormat.format(Date(now)),
            tag = tag,
            message = message,
            level = level,
            module = module
        )

        when (level) {
            LogLevel.INFO -> android.util.Log.i("SARA_${module.shortCode}_$tag", message)
            LogLevel.SUCCESS -> android.util.Log.i("SARA_${module.shortCode}_$tag", "✓ $message")
            LogLevel.WARN -> android.util.Log.w("SARA_${module.shortCode}_$tag", message)
            LogLevel.ERROR -> android.util.Log.e("SARA_${module.shortCode}_$tag", "✖ $message")
        }

        _logs.update { current ->
            (current + entry).takeLast(300)
        }

        // Enviar al subproceso detector de errores de forma no bloqueante (O(1))
        inspectionQueue.trySend(entry)
    }

    fun info(tag: String, message: String) = log(tag, message, LogLevel.INFO)
    fun success(tag: String, message: String) = log(tag, message, LogLevel.SUCCESS)
    fun warn(tag: String, message: String) = log(tag, message, LogLevel.WARN)
    fun error(tag: String, message: String) = log(tag, message, LogLevel.ERROR)

    /**
     * Permite restablecer el estado de un módulo detenido tras revisar/notificar sus errores.
     */
    fun resetModuleErrorState(module: SaraModule) {
        _moduleStatuses.update { currentMap ->
            val curr = currentMap[module] ?: return@update currentMap
            currentMap + (module to curr.copy(
                state = ModuleOperationalState.DORMIDO,
                detectedErrors = emptyList(),
                lastErrorSummary = null
            ))
        }
    }

    /**
     * Genera un informe detallado en formato Markdown (.md) con todos los errores detectados
     * por el subproceso en el log independiente del módulo afectado, y lo guarda en disco.
     */
    fun generarInformeErrorMarkdown(context: Context, module: SaraModule): Pair<File, String> {
        val status = _moduleStatuses.value[module] ?: ModuleMonitorStatus(module = module)
        val now = Date()
        val dateStr = reportDateFormat.format(now)
        val fileTimeStr = fileTimestampFormat.format(now)

        val mdContent = buildString {
            appendLine("# 🚨 Informe de Incidencia y Diagnóstico de Módulo - S.A.R.A")
            appendLine()
            appendLine("- **Módulo Afectado:** `${module.displayName}` (`${module.shortCode}`)")
            appendLine("- **Descripción del Subsistema:** ${module.description}")
            appendLine("- **Estado Detectado:** `🔴 DETENIDO (FALLO DETECTADO EN LOG)`")
            appendLine("- **Fecha y Hora del Reporte:** $dateStr")
            appendLine("- **Total de Errores Capturados por el Subproceso:** ${status.detectedErrors.size}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## 1. Errores Críticos Detectados por el Subproceso")
            appendLine()
            if (status.detectedErrors.isEmpty()) {
                appendLine("> No se registran entradas de nivel ERROR en el buffer actual.")
            } else {
                appendLine("| ID Evento | Hora | Tag | Nivel | Detalle del Error |")
                appendLine("| :--- | :--- | :--- | :--- | :--- |")
                for (err in status.detectedErrors) {
                    val safeMsg = err.message.replace("|", "\\|").replace("\n", " ")
                    appendLine("| `#${err.id}` | `${err.timestamp}` | `${err.tag}` | **${err.level}** | $safeMsg |")
                }
            }
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## 2. Trazabilidad Completa del Log Independiente del Módulo (Últimos 25 Eventos)")
            appendLine()
            appendLine("```text")
            val recentLogs = status.logs.takeLast(25)
            if (recentLogs.isEmpty()) {
                appendLine("[Sin eventos registrados en el buffer del módulo]")
            } else {
                for (entry in recentLogs) {
                    appendLine("[${entry.timestamp}] [${entry.level}] [${entry.tag}] ${entry.message}")
                }
            }
            appendLine("```")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## 3. Diagnóstico Automático Preliminar")
            appendLine()
            when (module) {
                SaraModule.CONVERSACION_IA -> {
                    appendLine("- **Causa Probable:** Fallo de conexión con Google Gemini API (API Key inválida/ausente, cuota excedida HTTP 429 o corte de red Wi-Fi) o timeout en SpeechRecognizer.")
                    appendLine("- **Acción Recomendada:** Verificar variable `GEMINI_API_KEY` en `SecurePreferences` / `.env` y comprobar conectividad a internet.")
                }
                SaraModule.VISION_BIOMETRIA -> {
                    appendLine("- **Causa Probable:** Sensor de cámara no disponible en el dispositivo/emulador o error de intérprete TensorFlow Lite (`mobilefacenet.tflite`).")
                    appendLine("- **Acción Recomendada:** Verificar permisos `android.permission.CAMERA` y configuración de cámara del AVD.")
                }
                SaraModule.RECORDATORIOS -> {
                    appendLine("- **Causa Probable:** Error de formato de fecha/hora o restricción de `SCHEDULE_EXACT_ALARM` en Android 14+.")
                    appendLine("- **Acción Recomendada:** Verificar integridad de la tabla `recordatorios` en SQLite y permisos de alarma exacta.")
                }
                SaraModule.MUSICA_MULTIMEDIA -> {
                    appendLine("- **Causa Probable:** Cuota o clave `GOOGLE_CLOUD_API_KEY` de YouTube Data API v3 rechazada o error de red en WebView/ExoPlayer.")
                    appendLine("- **Acción Recomendada:** Revisar credenciales de Google Cloud Console para YouTube Data API v3.")
                }
                else -> {
                    appendLine("- **Causa Probable:** Excepción en tiempo de ejecución capturada en el flujo del módulo `${module.shortCode}`.")
                    appendLine("- **Acción Recomendada:** Revisar la traza del punto 2 para identificar el parámetro o recurso faltante.")
                }
            }
            appendLine()
            appendLine("---")
            appendLine("*Generado automáticamente por el Subproceso de Monitoreo de Salud de S.A.R.A.*")
        }

        val reportsDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "reportes_errores").apply {
            if (!exists()) mkdirs()
        }
        val reportFile = File(reportsDir, "informe_error_${module.shortCode.lowercase()}_$fileTimeStr.md")
        reportFile.writeText(mdContent, Charsets.UTF_8)

        return Pair(reportFile, mdContent)
    }

    fun clear() {
        _logs.value = emptyList()
        _moduleStatuses.value = SaraModule.entries.associateWith { mod ->
            ModuleMonitorStatus(module = mod, state = ModuleOperationalState.DORMIDO)
        }
    }
}
