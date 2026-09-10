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
        if (!com.myvu.client.core.Prefs.autoReconnectEnabled(context)) {
            LogBus.log("ServiceWatchdogReceiver -> Auto-reconnect disabled by user; cancelling watchdog")
            cancelWatchdog(context)
            return
        }
        LogBus.log("ServiceWatchdogReceiver -> Executing periodic keep-alive check...")
        try {
            ServiceKeepAliveHelper.ensureServiceRunning(context)
        } catch (e: Exception) {
            LogBus.error("ServiceWatchdogReceiver -> Failed to ensure service running", e)
        }
        // Reschedule next watchdog interval only if auto-reconnect is still enabled
        if (com.myvu.client.core.Prefs.autoReconnectEnabled(context)) {
            scheduleWatchdog(context)
        }
    }

    companion object {
        private const val WATCHDOG_REQUEST_CODE = 9991
        private const val WATCHDOG_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

        @JvmStatic
        fun cancelWatchdog(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, ServiceWatchdogReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    WATCHDOG_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
                LogBus.log("ServiceWatchdogReceiver -> Cancelled periodic watchdog alarm")
            } catch (e: Exception) {
                LogBus.error("ServiceWatchdogReceiver -> Failed to cancel watchdog", e)
            }
        }

        @JvmStatic
        fun scheduleWatchdog(context: Context) {
            if (!com.myvu.client.core.Prefs.autoReconnectEnabled(context)) {
                LogBus.log("ServiceWatchdogReceiver -> Auto-reconnect disabled; skipping watchdog schedule")
                cancelWatchdog(context)
                return
            }
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

                // Use ELAPSED_REALTIME (non-wakeup) to avoid breaking Android Doze Mode.
                // Foreground services remain active in memory; watchdog checks when the device is awake.
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME,
                    triggerAt,
                    pendingIntent
                )
                LogBus.log("ServiceWatchdogReceiver -> Scheduled periodic watchdog in 15 minutes (non-wakeup)")
            } catch (e: Exception) {
                LogBus.error("ServiceWatchdogReceiver -> Failed to schedule watchdog", e)
            }
        }
    }
}
