package com.myvu.client.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.myvu.client.core.LogBus
import com.myvu.client.core.ServiceKeepAliveHelper
import com.myvu.client.database.ReminderRepository

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action
        LogBus.log("BootReceiver -> Received broadcast action: $action")

        if (Intent.ACTION_BOOT_COMPLETED == action ||
            Intent.ACTION_MY_PACKAGE_REPLACED == action ||
            Intent.ACTION_POWER_CONNECTED == action ||
            Intent.ACTION_USER_PRESENT == action ||
            Intent.ACTION_TIME_CHANGED == action ||
            Intent.ACTION_TIMEZONE_CHANGED == action
        ) {
            try {
                ServiceKeepAliveHelper.ensureServiceRunning(context)
                LogBus.log("BootReceiver -> Ensured MyvuService running on $action")
            } catch (e: Exception) {
                LogBus.error("BootReceiver -> Failed to ensure MyvuService running", e)
            }

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
}
