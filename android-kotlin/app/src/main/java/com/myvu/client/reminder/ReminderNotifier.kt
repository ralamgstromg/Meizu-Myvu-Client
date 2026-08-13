package com.myvu.client.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.myvu.client.app.feature.Notifications
import com.myvu.client.core.LogBus
import com.myvu.client.database.Reminder
import com.myvu.client.service.MyvuService

object ReminderNotifier {

    const val CHANNEL_ID = "myvu_reminders"

    @JvmStatic
    fun notifyReminder(context: Context, reminder: Reminder?) {
        if (reminder == null) return

        createNotificationChannel(context)

        val notificationId = reminder.id.toInt()

        val completeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_COMPLETE
            putExtra("reminder_id", reminder.id)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        val pComplete = PendingIntent.getBroadcast(context, notificationId * 10 + 1, completeIntent, flags)

        val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE
            putExtra("reminder_id", reminder.id)
        }
        val pSnooze = PendingIntent.getBroadcast(context, notificationId * 10 + 2, snoozeIntent, flags)

        val title = if (reminder.title.isNotBlank()) reminder.title else "Recordatorio MYVU"
        val body = if (reminder.body.isNotBlank()) reminder.body else reminder.title

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Completar", pComplete)
            .addAction(android.R.drawable.ic_menu_recent_history, "Posponer 10m", pSnooze)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        nm?.notify(notificationId, builder.build())

        sendToGlasses(context, reminder)
    }

    @JvmStatic
    fun dismissGlassesNotification(context: Context, reminderId: Long) {
        try {
            val conn = MyvuService.activeConnection()
            if (conn != null) {
                val hudId = Notifications.notificationId("com.myvu.client", reminderId.toInt())
                conn.sendAction(Notifications.buildDismiss(hudId))
            }
        } catch (e: Exception) {
            LogBus.warn("ReminderNotifier -> Could not dismiss HUD notification: " + e.message)
        }
    }

    private fun sendToGlasses(context: Context, reminder: Reminder) {
        try {
            val conn = MyvuService.activeConnection()
            if (conn != null) {
                val title = if (reminder.title.isNotBlank()) reminder.title else "Recordatorio"
                val body = if (reminder.body.isNotBlank()) reminder.body else reminder.title
                val now = System.currentTimeMillis()

                val entry = Notifications.entry(
                    "com.myvu.client",
                    reminder.id.toInt(),
                    title,
                    body,
                    "Recordatorio",
                    now,
                    false
                )
                val actionJson = Notifications.buildShow(entry)
                conn.sendAction(actionJson)
                LogBus.log("ReminderNotifier -> Pushed reminder #${reminder.id} to HUD glasses")
            }
        } catch (e: Exception) {
            LogBus.error("ReminderNotifier -> Could not send reminder to glasses", e)
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recordatorios MYVU",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal de notificaciones de recordatorios locales"
                enableVibration(true)
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }
}

