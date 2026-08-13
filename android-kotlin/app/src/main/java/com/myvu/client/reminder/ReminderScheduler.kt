package com.myvu.client.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.myvu.client.core.LogBus
import com.myvu.client.database.Reminder

object ReminderScheduler {

    @JvmStatic
    fun scheduleReminder(context: Context, reminder: Reminder): Boolean {
        return scheduleReminder(context, reminder.id, reminder.triggerAt, reminder.alarmRequestCode)
    }

    @JvmStatic
    fun scheduleReminder(context: Context, reminderId: Long, triggerAtMillis: Long, requestCode: Int): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        if (alarmManager == null) {
            LogBus.error("ReminderScheduler -> AlarmManager service null", null)
            return false
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_id", reminderId)
        }

        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }

        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    try {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    } catch (se: SecurityException) {
                        LogBus.warn("ReminderScheduler -> Exact alarm permission missing, falling back: ${se.message}")
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    }
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            LogBus.log("ReminderScheduler -> scheduled reminder #$reminderId at $triggerAtMillis")
            return true
        } catch (e: Exception) {
            LogBus.error("ReminderScheduler -> failed to schedule reminder #$reminderId", e)
            return false
        }
    }

    @JvmStatic
    fun cancelReminder(context: Context, reminder: Reminder) {
        cancelReminder(context, reminder.alarmRequestCode)
    }

    @JvmStatic
    fun cancelReminder(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager? ?: return

        val intent = Intent(context, ReminderReceiver::class.java)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }

        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        LogBus.log("ReminderScheduler -> cancelled reminder requestCode #$requestCode")
    }
}

