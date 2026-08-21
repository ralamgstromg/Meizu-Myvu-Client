package com.myvu.client.core

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager

object LockScreenHelper {

    /**
     * Configures the activity to display over keyguard/lockscreen and turn screen on.
     */
    fun setupShowWhenLocked(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                activity.window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            }
            LogBus.log("LockScreenHelper: Configured ${activity.javaClass.simpleName} to show over lock screen")
        } catch (e: Exception) {
            LogBus.warn("LockScreenHelper: Could not setup showWhenLocked: ${e.message}")
        }
    }

    /**
     * Attempts to unlock or request dismissal of keyguard.
     */
    fun unlockKeyguard(activity: Activity, onDismissed: (() -> Unit)? = null) {
        val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager?.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    super.onDismissSucceeded()
                    LogBus.log("LockScreenHelper: Keyguard dismiss succeeded")
                    onDismissed?.invoke()
                }

                override fun onDismissCancelled() {
                    super.onDismissCancelled()
                    LogBus.log("LockScreenHelper: Keyguard dismiss cancelled by user")
                }

                override fun onDismissError() {
                    super.onDismissError()
                    LogBus.warn("LockScreenHelper: Keyguard dismiss error")
                }
            })
        } else {
            onDismissed?.invoke()
        }
    }

    /**
     * Wakes up device screen using PowerManager WakeLock.
     */
    fun wakeUpScreen(context: Context, tag: String = "MYVU:LockScreenWake", durationMs: Long = 5000L) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            @Suppress("DEPRECATION")
            val wakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                tag
            )
            wakeLock.acquire(durationMs)
            LogBus.log("LockScreenHelper: Screen awakened for agent interaction ($tag)")
        } catch (e: Exception) {
            LogBus.warn("LockScreenHelper: Could not acquire WakeLock: ${e.message}")
        }
    }

    /**
     * Returns true if device is currently locked.
     */
    fun isDeviceLocked(context: Context): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return km?.isDeviceLocked == true || km?.isKeyguardLocked == true
    }

    /**
     * Checks if overlay permission (SYSTEM_ALERT_WINDOW) is granted.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Requests SYSTEM_ALERT_WINDOW permission.
     */
    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                LogBus.log("LockScreenHelper: Requested overlay permission for lock screen interaction")
            } catch (e: Exception) {
                LogBus.error("LockScreenHelper: Failed to open overlay permission settings", e)
            }
        }
    }
}
