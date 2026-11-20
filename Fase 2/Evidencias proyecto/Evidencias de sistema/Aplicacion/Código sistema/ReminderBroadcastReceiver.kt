package com.sara.robot.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.sara.robot.MainActivity
import com.sara.robot.core.AppLogger

/**
 * Receptor de difusión que se activa en el momento exacto en que AlarmManager dispara la alarma.
 * Despierta el dispositivo, marca el buffer de RAM y abre la aplicación para que Sara le hable al usuario.
 */
class ReminderBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER_REMINDER = "com.sara.robot.ACTION_TRIGGER_REMINDER"
        const val EXTRA_REMINDER_ID = "EXTRA_REMINDER_ID"
        const val EXTRA_REMINDER_TITLE = "EXTRA_REMINDER_TITLE"
        const val CHANNEL_ID = "sara_reminders_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val title = intent.getStringExtra(EXTRA_REMINDER_TITLE) ?: "Recordatorio Clínico"

        AppLogger.success(
            "ReminderReceiver",
            "🔔 [ALARMA DISPARADA] Señal de AlarmManager recibida | ID=$reminderId | '$title'"
        )

        // 1. Actualizar estado en el buffer temporal en memoria RAM
        if (reminderId != -1L) {
            ReminderMemoryBuffer.markTriggered(reminderId)
        }

        // 2. WakeLock para iluminar la pantalla y despertar el CPU
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "SaraRobot:ReminderWakeLock"
        )
        wakeLock?.acquire(8000L) // Mantener pantalla despierta por 8 segundos

        // 3. Emitir notificación heads-up de alta prioridad
        showNotification(context, reminderId, title)

        // 4. Iniciar MainActivity para que Sara hable al usuario de inmediato
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("TRIGGER_REMINDER_ID", reminderId)
            putExtra("TRIGGER_REMINDER_MESSAGE", title)
        }

        try {
            context.startActivity(launchIntent)
            AppLogger.info("ReminderReceiver", "Levantando a Sara en pantalla principal para anunciar el recordatorio...")
        } catch (e: Exception) {
            AppLogger.error("ReminderReceiver", "No se pudo iniciar MainActivity: ${e.message}")
        }
    }

    private fun showNotification(context: Context, id: Long, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recordatorios de Sara Robot",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de recordatorios y medicamentos para el paciente"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("TRIGGER_REMINDER_ID", id)
            putExtra("TRIGGER_REMINDER_MESSAGE", message)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            id.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🔔 Recordatorio de Sara")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(id.toInt(), notification)
    }
}
