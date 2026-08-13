package com.myvu.client.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.myvu.client.core.LogBus
import com.myvu.client.database.ReminderRepository

class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null || intent.action == null) return

        val reminderId = intent.getLongExtra("reminder_id", -1)
        if (reminderId == -1L) return

        val action = intent.action
        val repo = ReminderRepository(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        nm?.cancel(reminderId.toInt())

        ReminderNotifier.dismissGlassesNotification(context, reminderId)

        if (ACTION_COMPLETE == action) {
            LogBus.log("ReminderActionReceiver -> Marking reminder #$reminderId as COMPLETED")
            repo.updateReminderState(reminderId, "COMPLETED")
        } else if (ACTION_SNOOZE == action) {
            val newTriggerAt = System.currentTimeMillis() + (10 * 60 * 1000)
            LogBus.log("ReminderActionReceiver -> Snoozing reminder #$reminderId +10m")

            val r = repo.getReminder(reminderId)
            if (r != null && repo.snoozeReminder(reminderId, newTriggerAt)) {
                ReminderScheduler.scheduleReminder(context, reminderId, newTriggerAt, r.alarmRequestCode)
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.myvu.client.reminder.ACTION_COMPLETE"
        const val ACTION_SNOOZE = "com.myvu.client.reminder.ACTION_SNOOZE"
    }
}
