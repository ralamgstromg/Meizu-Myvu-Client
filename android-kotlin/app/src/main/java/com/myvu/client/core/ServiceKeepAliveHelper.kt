package com.myvu.client.core

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.myvu.client.service.MyvuService

object ServiceKeepAliveHelper {

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            } catch (e: Exception) {
                LogBus.warn("ServiceKeepAliveHelper: Battery check failed: ${e.message}")
                true
            }
        } else {
            true
        }
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimization(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (!isBatteryOptimizationIgnored(activity)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivity(intent)
                    LogBus.log("ServiceKeepAliveHelper: Requested battery optimization exemption")
                }
            } catch (e: Exception) {
                LogBus.error("ServiceKeepAliveHelper: Failed to request battery exemption", e)
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    activity.startActivity(intent)
                } catch (ignored: Exception) {}
            }
        }
    }

    fun ensureServiceRunning(context: Context, macAddress: String? = null) {
        try {
            val targetMac = macAddress?.ifBlank { null } ?: Prefs.targetMac(context).ifBlank { null }
            val intent = Intent(context, MyvuService::class.java).apply {
                action = MyvuService.ACTION_START
                targetMac?.let { putExtra(MyvuService.EXTRA_MAC, it) }
            }
            ContextCompat.startForegroundService(context, intent)
            LogBus.log("ServiceKeepAliveHelper: Ensured MyvuService foreground service is active")
        } catch (e: Exception) {
            LogBus.error("ServiceKeepAliveHelper: Failed to start foreground service", e)
        }
    }
}
