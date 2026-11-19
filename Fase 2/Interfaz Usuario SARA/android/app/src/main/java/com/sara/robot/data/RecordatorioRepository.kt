package com.sara.robot.data

import android.content.Context
import android.content.SharedPreferences
import com.sara.robot.core.AppLogger
import com.sara.robot.data.local.database.SaraAppDatabase
import com.sara.robot.data.local.entity.PacienteEntity
import com.sara.robot.data.local.entity.RecordatorioEntity
import com.sara.robot.reminders.AlarmScheduler
import com.sara.robot.reminders.InMemoryReminder
import com.sara.robot.reminders.ReminderMemoryBuffer
import com.sara.robot.reminders.ReminderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Repositorio de Recordatorios Clínicos para SARA.
 * Administra el almacenamiento persistente en SQLite (Room) en la tabla `recordatorios`,
 * y orquesta el subproceso en segundo plano que carga las alertas de cada día en el buffer
 * temporal de memoria RAM (ReminderMemoryBuffer) para el consumo de Sara y AlarmManager.
 */
class RecordatorioRepository(private val context: Context) {

    private val db = SaraAppDatabase.getDatabase(context)
    private val recordatorioDao = db.recordatorioDao()
    private val pacienteDao = db.pacienteDao()
    private val scheduler = AlarmScheduler(context)

    companion object {
        val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val DATE_PREFIX_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private const val PREF_KEY_PURGED = "has_purged_for_user_tests_v1"
    }

    private var backgroundJob: Job? = null

    /**
     * Inserta un recordatorio directamente en la tabla SQLite de Room (`recordatorios`).
     * Flujo de alertas: Si el recordatorio corresponde al día de hoy, se añade al buffer
     * temporal en RAM y se programa en el AlarmManager nativo de Android.
     */
    suspend fun crearYGuardarRecordatorio(
        descripcion: String,
        fechaHora: LocalDateTime,
        pacienteId: Long = 1L
    ): Result<RecordatorioEntity> = withContext(Dispatchers.IO) {
        try {
            val cleanDesc = descripcion.trim()
            if (cleanDesc.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("La descripción no puede estar vacía"))
            }

            // Asegurar que exista el paciente base (Christian Carriel)
            asegurarPacienteBase(pacienteId)

            val fechaStr = fechaHora.format(DATE_TIME_FORMATTER)

            // Estructura de Log Requerida - Paso 1: Recordatorio recibido
            AppLogger.info(
                "Recordatorio",
                "Recordatorio recibido: \"$cleanDesc\" (programado para: $fechaStr)"
            )

            val entidad = RecordatorioEntity(
                descripcion = cleanDesc,
                fecha_recor = fechaStr,
                Paciente_Id_paciente = pacienteId
            )

            // 1. Insertar directamente a la base de datos (todos los recordatorios van a BD)
            val idInsertado = recordatorioDao.insertRecordatorio(entidad)
            val recordatorioGuardado = entidad.copy(id_recordatorio = idInsertado)

            // Estructura de Log Requerida - Paso 2: Recordatorio ingresado en la base de datos
            AppLogger.success(
                "Recordatorio",
                "Recordatorio ingresado en la base de datos: ID=$idInsertado | \"$cleanDesc\" | Fecha: $fechaStr"
            )

            // 2. Flujo de Alertas: Si es para hoy, agregarlo al buffer temporal en RAM y programar alarma
            val hoy = LocalDate.now()
            if (fechaHora.toLocalDate() == hoy) {
                val targetEpoch = fechaHora.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val nowMillis = System.currentTimeMillis()

                if (targetEpoch > nowMillis) {
                    val inMemory = ReminderMemoryBuffer.addOrUpdateReminder(
                        id = idInsertado,
                        title = cleanDesc,
                        targetTimeMillis = targetEpoch,
                        status = ReminderStatus.PROGRAMADA
                    )
                    scheduler.schedule(inMemory)
                    val seg = (targetEpoch - nowMillis) / 1000
                    AppLogger.success(
                        "Recordatorio",
                        "Alarma programada en buffer de RAM: ID=$idInsertado | \"$cleanDesc\" | Disparo en $seg seg"
                    )
                } else {
                    // Si la hora ya pasó hoy, se deja registrada en el buffer como DISPARADA
                    ReminderMemoryBuffer.addOrUpdateReminder(
                        id = idInsertado,
                        title = cleanDesc,
                        targetTimeMillis = targetEpoch,
                        status = ReminderStatus.DISPARADA
                    )
                }
            } else {
                AppLogger.info(
                    "Recordatorio",
                    "🗓️ Recordatorio para fecha futura (${fechaHora.toLocalDate()}): Almacenado en BD. El subproceso lo cargará a la RAM al comenzar ese día."
                )
            }

            Result.success(recordatorioGuardado)
        } catch (e: Exception) {
            AppLogger.error("Recordatorio", "Error al ingresar recordatorio en la base de datos: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Subproceso: Al comenzar el día, toma todos los recordatorios que correspondan a esa fecha
     * desde la base de datos SQLite y los pasa al buffer temporal en memoria RAM (ReminderMemoryBuffer).
     */
    suspend fun sincronizarRecordatoriosDelDia(fecha: LocalDate = LocalDate.now()): List<InMemoryReminder> = withContext(Dispatchers.IO) {
        val datePrefix = fecha.format(DATE_PREFIX_FORMATTER)
        AppLogger.info("RecordatorioRepo", "🔄 [Subproceso] Sincronizando recordatorios del día $datePrefix desde BD hacia el buffer en RAM...")

        val registros = recordatorioDao.getRecordatoriosDelDia(datePrefix)
        AppLogger.info("RecordatorioRepo", "📦 [SQLite] Encontrados ${registros.size} recordatorios en BD para la fecha $datePrefix")

        // Vaciar buffer de RAM para mantener únicamente las alertas del día
        ReminderMemoryBuffer.clearAll()

        val cargados = mutableListOf<InMemoryReminder>()
        val nowMillis = System.currentTimeMillis()

        for (reg in registros) {
            val targetDateTime = parseFechaHora(reg.fecha_recor)
            if (targetDateTime != null) {
                val epochMillis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val status = if (epochMillis > nowMillis) ReminderStatus.PROGRAMADA else ReminderStatus.DISPARADA

                val inMemory = ReminderMemoryBuffer.addOrUpdateReminder(
                    id = reg.id_recordatorio,
                    title = reg.descripcion,
                    targetTimeMillis = epochMillis,
                    status = status
                )
                cargados.add(inMemory)

                // Si está en el futuro, programar en AlarmManager nativo
                if (status == ReminderStatus.PROGRAMADA) {
                    scheduler.schedule(inMemory)
                }
            } else {
                AppLogger.warn("RecordatorioRepo", "No se pudo parsear fecha del recordatorio ID=${reg.id_recordatorio}: '${reg.fecha_recor}'")
            }
        }

        AppLogger.success(
            "RecordatorioRepo",
            "✅ [Subproceso] Buffer temporal en RAM actualizado: ${cargados.size} alertas para el día $datePrefix (Activas: ${cargados.count { it.status == ReminderStatus.PROGRAMADA }})"
        )
        cargados
    }

    /**
     * Inicia el subproceso de sincronización diaria en segundo plano:
     * 1. Sincroniza de inmediato el buffer en RAM para el día actual.
     * 2. Espera en bucle hasta la medianoche (00:00:01) para recargar automáticamente las alertas de cada nuevo día.
     */
    fun iniciarSubprocesoDiario(scope: CoroutineScope) {
        backgroundJob?.cancel()
        backgroundJob = scope.launch(Dispatchers.IO) {
            AppLogger.info("RecordatorioRepo", "🚀 Iniciando subproceso de carga diaria de recordatorios en RAM...")

            // 1. Sincronizar inmediatamente al arrancar la app
            sincronizarRecordatoriosDelDia()

            // 2. Bucle que aguarda a que comience cada nuevo día
            while (isActive) {
                val now = LocalDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay().plusSeconds(1)
                val millisUntilMidnight = Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1000L)

                val horas = millisUntilMidnight / 3600000
                val minutos = (millisUntilMidnight % 3600000) / 60000
                AppLogger.info("RecordatorioRepo", "⏳ Subproceso diario en espera hasta medianoche ($horas h $minutos min)")

                delay(millisUntilMidnight)

                // Amanece el nuevo día: recargar el buffer de RAM
                val nuevoDia = LocalDate.now()
                AppLogger.success("RecordatorioRepo", "☀️ ¡Nuevo día iniciado ($nuevoDia)! Tomando recordatorios de la BD hacia el buffer en RAM...")
                sincronizarRecordatoriosDelDia(nuevoDia)
            }
        }
    }

    /**
     * Elimina TODOS los recordatorios de la base de datos y vacía el buffer en RAM.
     * Requerimiento directo del usuario:
     * "eliminame todos los recordatorios que esten en las bases de datos porque los testeos los voy a hacer yo."
     */
    suspend fun limpiarTodosLosRecordatorios(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            for (reminder in ReminderMemoryBuffer.remindersFlow.value) {
                scheduler.cancel(reminder.id)
            }
            ReminderMemoryBuffer.clearAll()
            recordatorioDao.deleteAllRecordatorios()
            AppLogger.success("RecordatorioRepo", "🗑️ [BD & RAM] Todos los recordatorios eliminados de SQLite y del buffer en RAM para nuevos testeos.")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.error("RecordatorioRepo", "Error al limpiar recordatorios: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Purga inicial para testeos del usuario si no se ha realizado previamente en este dispositivo.
     */
    suspend fun purgarParaNuevosTesteosSiEsNecesario(prefs: SharedPreferences) = withContext(Dispatchers.IO) {
        if (!prefs.getBoolean(PREF_KEY_PURGED, false)) {
            AppLogger.warn("RecordatorioRepo", "🧹 Ejecutando purga inicial de recordatorios en BD solicitada por el usuario...")
            limpiarTodosLosRecordatorios()
            prefs.edit().putBoolean(PREF_KEY_PURGED, true).apply()
        }
    }

    suspend fun eliminarRecordatorio(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            scheduler.cancel(id)
            ReminderMemoryBuffer.removeReminder(id)
            recordatorioDao.deleteById(id)
            AppLogger.info("RecordatorioRepo", "Recordatorio ID=$id eliminado de BD y RAM")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAllRecordatoriosFlow(): Flow<List<RecordatorioEntity>> {
        return recordatorioDao.getAllRecordatorios()
    }

    private fun parseFechaHora(raw: String): LocalDateTime? {
        val formats = listOf(
            DATE_TIME_FORMATTER,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
        )
        for (f in formats) {
            try {
                return LocalDateTime.parse(raw.trim(), f)
            } catch (_: DateTimeParseException) {}
        }
        return null
    }

    private suspend fun asegurarPacienteBase(pacienteId: Long) {
        val existente = pacienteDao.getPacienteById(pacienteId)
        if (existente == null) {
            val pacienteDefecto = PacienteEntity(
                Id_paciente = pacienteId,
                Nombre = "Christian",
                ape_p = "Carriel",
                ape_m = "González",
                Fech_nac = "1948-05-14",
                edad = 78,
                genero = "Masculino"
            )
            pacienteDao.insertPaciente(pacienteDefecto)
            AppLogger.success("RecordatorioRepo", "Paciente base asegurado: Christian Carriel (ID: $pacienteId)")
        } else if (existente.Nombre.contains("Juan", ignoreCase = true) || existente.ape_p.contains("Pérez", ignoreCase = true) || existente.ape_p.contains("Perez", ignoreCase = true)) {
            val actualizado = existente.copy(Nombre = "Christian", ape_p = "Carriel")
            pacienteDao.updatePaciente(actualizado)
            AppLogger.info("RecordatorioRepo", "Paciente ID=$pacienteId actualizado a 'Christian Carriel' en SQLite")
        }
    }
}
