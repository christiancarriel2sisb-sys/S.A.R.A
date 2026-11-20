package com.sara.robot.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sara.robot.core.AppLogger

/**
 * Gestor del AlarmManager nativo de Android para programar alarmas exactas.
 * Soporta modo Doze mediante setExactAndAllowWhileIdle.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(reminder: InMemoryReminder) {
        if (alarmManager == null) {
            AppLogger.error("AlarmScheduler", "AlarmManager no disponible en el sistema.")
            return
        }

        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_TRIGGER_REMINDER
            putExtra(ReminderBroadcastReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(ReminderBroadcastReceiver.EXTRA_REMINDER_TITLE, reminder.title)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.targetTimeMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    reminder.targetTimeMillis,
                    pendingIntent
                )
            }
            val secondsUntil = ((reminder.targetTimeMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            AppLogger.success(
                "AlarmScheduler",
                "[AlarmManager] Alarma programada: ID=${reminder.id} | Disparo en ~$secondsUntil seg (${reminder.formattedTargetTime})"
            )
        } catch (e: SecurityException) {
            AppLogger.warn("AlarmScheduler", "Permiso de alarma exacta no otorgado: ${e.message}. Programando alarma estándar...")
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                reminder.targetTimeMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            AppLogger.error("AlarmScheduler", "Error al programar en AlarmManager: ${e.message}")
        }
    }

    fun cancel(reminderId: Long) {
        if (alarmManager == null) return

        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        AppLogger.info("AlarmScheduler", "[AlarmManager] Alarma cancelada en el sistema para ID=$reminderId")
    }
}
