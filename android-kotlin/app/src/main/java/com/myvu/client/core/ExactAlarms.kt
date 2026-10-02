package com.myvu.client.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build

/** Schedules a wake-up alarm, exact when the app is allowed to, inexact otherwise. */
object ExactAlarms {

    fun set(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() ->
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (se: SecurityException) {
                LogBus.warn("ExactAlarms -> exact alarm not permitted, using inexact: ${se.message}")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            else -> alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }
}
