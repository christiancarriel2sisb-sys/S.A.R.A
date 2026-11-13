package com.sara.robot.reminders

import com.sara.robot.core.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class ReminderStatus {
    PROGRAMADA,
    DISPARADA,
    CANCELADA
}

data class InMemoryReminder(
    val id: Long,
    val title: String,
    val targetTimeMillis: Long,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val status: ReminderStatus = ReminderStatus.PROGRAMADA
) {
    val targetLocalDateTime: LocalDateTime
        get() = Instant.ofEpochMilli(targetTimeMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()

    val formattedTargetTime: String
        get() = targetLocalDateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.getDefault()))

    val formattedDate: String
        get() = targetLocalDateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault()))

    val formattedTime: String
        get() = targetLocalDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault()))

    val remainingSeconds: Long
        get() = ((targetTimeMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
}

/**
 * Buffer en memoria RAM para registro y testeo de recordatorios (Fase 1).
 * Almacena las alarmas temporalmente en RAM sin persistir en base de datos.
 */
object ReminderMemoryBuffer {
    private val idCounter = AtomicLong(1000L)
    private val memoryMap = ConcurrentHashMap<Long, InMemoryReminder>()

    private val _remindersFlow = MutableStateFlow<List<InMemoryReminder>>(emptyList())
    val remindersFlow: StateFlow<List<InMemoryReminder>> = _remindersFlow.asStateFlow()

    fun addReminder(title: String, targetTimeMillis: Long): InMemoryReminder {
        val id = idCounter.incrementAndGet()
        val reminder = InMemoryReminder(
            id = id,
            title = title,
            targetTimeMillis = targetTimeMillis,
            status = ReminderStatus.PROGRAMADA
        )
        memoryMap[id] = reminder
        updateFlow()
        AppLogger.info(
            "ReminderBuffer",
            "[RAM] Recordatorio registrado con LocalDateTime: ID=$id | Titulo='$title' | Programado para: ${reminder.formattedTargetTime} (Epoch: ${targetTimeMillis}ms)"
        )
        return reminder
    }

    fun markTriggered(id: Long) {
        val existing = memoryMap[id]
        if (existing != null) {
            val updated = existing.copy(status = ReminderStatus.DISPARADA)
            memoryMap[id] = updated
            updateFlow()
            AppLogger.success(
                "ReminderBuffer",
                "[RAM] Recordatorio DISPARADO: ID=$id | Titulo='${existing.title}'"
            )
        }
    }

    fun cancelReminder(id: Long): Boolean {
        val existing = memoryMap[id]
        return if (existing != null) {
            val updated = existing.copy(status = ReminderStatus.CANCELADA)
            memoryMap[id] = updated
            updateFlow()
            AppLogger.warn("ReminderBuffer", "[RAM] Recordatorio cancelado: ID=$id")
            true
        } else false
    }

    fun removeReminder(id: Long) {
        if (memoryMap.remove(id) != null) {
            updateFlow()
            AppLogger.info("ReminderBuffer", "[RAM] Recordatorio eliminado del buffer: ID=$id")
        }
    }

    fun clearAll() {
        memoryMap.clear()
        updateFlow()
        AppLogger.info("ReminderBuffer", "[RAM] Buffer de recordatorios vaciado completamente.")
    }

    private fun updateFlow() {
        _remindersFlow.value = memoryMap.values.sortedByDescending { it.createdAtMillis }
    }
}
