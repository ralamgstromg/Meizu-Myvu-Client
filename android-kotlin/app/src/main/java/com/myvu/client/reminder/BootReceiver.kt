package com.myvu.client.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.myvu.client.core.LogBus
import com.myvu.client.database.ReminderRepository

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action
        if (Intent.ACTION_BOOT_COMPLETED == action ||
            Intent.ACTION_TIME_CHANGED == action ||
            Intent.ACTION_TIMEZONE_CHANGED == action
        ) {
            LogBus.log("BootReceiver -> Rescheduling pending reminders after $action")

            val repo = ReminderRepository(context)
            val pending = repo.getPendingReminders()

            val now = System.currentTimeMillis()
            for (r in pending) {
                if (r.triggerAt > now) {
                    ReminderScheduler.scheduleReminder(context, r.id, r.triggerAt, r.alarmRequestCode)
                } else {
                    repo.updateReminderState(r.id, "FAILED")
                }
            }
        }
    }
}
