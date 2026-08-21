package com.myvu.client.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.myvu.client.core.LogBus
import com.myvu.client.core.ServiceKeepAliveHelper

/**
 * Periodic Watchdog Receiver to verify that MyvuService foreground service remains active.
 * If the service was stopped or killed by OS Low Memory Manager, this alarm revives it.
 */
class ServiceWatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        LogBus.log("ServiceWatchdogReceiver -> Executing periodic keep-alive check...")
        try {
            ServiceKeepAliveHelper.ensureServiceRunning(context)
        } catch (e: Exception) {
            LogBus.error("ServiceWatchdogReceiver -> Failed to ensure service running", e)
        }
        // Reschedule next watchdog interval
        scheduleWatchdog(context)
    }

    companion object {
        private const val WATCHDOG_REQUEST_CODE = 9991
        private const val WATCHDOG_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

        @JvmStatic
        fun scheduleWatchdog(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, ServiceWatchdogReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    WATCHDOG_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val triggerAt = SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
                LogBus.log("ServiceWatchdogReceiver -> Scheduled periodic watchdog in 15 minutes")
            } catch (e: Exception) {
                LogBus.error("ServiceWatchdogReceiver -> Failed to schedule watchdog", e)
            }
        }
    }
}
