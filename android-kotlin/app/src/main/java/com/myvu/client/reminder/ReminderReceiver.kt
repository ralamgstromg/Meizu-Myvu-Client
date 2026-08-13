package com.myvu.client.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.myvu.client.core.LogBus
import com.myvu.client.database.ReminderRepository

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val reminderId = intent.getLongExtra("reminder_id", -1)
        if (reminderId == -1L) return

        LogBus.log("ReminderReceiver -> Alarm triggered for reminder #$reminderId")

        val repo = ReminderRepository(context)
        val reminder = repo.getReminder(reminderId)

        if (reminder != null && "PENDING" == reminder.state) {
            repo.updateReminderState(reminderId, "FIRED")
            ReminderNotifier.notifyReminder(context, reminder)
        }
    }
}
