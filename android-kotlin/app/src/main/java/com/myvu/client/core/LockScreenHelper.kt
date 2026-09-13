package com.myvu.client.core

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    fun unlockKeyguard(
        activity: Activity,
        onDismissed: (() -> Unit)? = null,
        onCancelledOrFailed: (() -> Unit)? = null
    ) {
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
                    onCancelledOrFailed?.invoke()
                }

                override fun onDismissError() {
                    super.onDismissError()
                    LogBus.log("LockScreenHelper: Keyguard requires PIN/biometrics or dismiss rejected; proceeding over lock screen")
                    onCancelledOrFailed?.invoke()
                }
            })
        } else {
            onDismissed?.invoke()
        }
    }

    private val reLockHandler = Handler(Looper.getMainLooper())
    private var reLockRunnable: Runnable? = null

    /**
     * Wakes up device screen using PowerManager WakeLock and schedules automatic re-lock
     * after the user-configured timeout (Prefs.screenReLockTimeoutSeconds, default 60s).
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

            // Schedule automatic re-lock to protect device and conserve battery
            scheduleReLock(context)
        } catch (e: Exception) {
            LogBus.warn("LockScreenHelper: Could not acquire WakeLock: ${e.message}")
        }
    }

    /**
     * Schedules turning off and re-locking the screen after the configured timeout in Prefs.
     */
    fun scheduleReLock(context: Context, timeoutSeconds: Int = 0) {
        cancelScheduledReLock()
        val appContext = context.applicationContext
        val sec = if (timeoutSeconds > 0) timeoutSeconds else Prefs.screenReLockTimeoutSeconds(appContext)
        val delayMs = sec * 1000L

        val runnable = Runnable {
            LogBus.log("LockScreenHelper: Re-lock timer expired (${sec}s) -> Re-locking screen")
            lockDevice(appContext)
        }
        reLockRunnable = runnable
        reLockHandler.postDelayed(runnable, delayMs)
        LogBus.log("LockScreenHelper: Scheduled screen re-lock in ${sec}s")
    }

    /**
     * Cancels any pending re-lock timer (e.g. when user manually uses device or dismisses).
     */
    fun cancelScheduledReLock() {
        reLockRunnable?.let {
            reLockHandler.removeCallbacks(it)
            reLockRunnable = null
        }
    }

    /**
     * Immediately locks the screen using Accessibility Service GLOBAL_ACTION_LOCK_SCREEN.
     */
    fun lockDevice(context: Context): Boolean {
        cancelScheduledReLock()
        com.myvu.client.app.feature.TouchGestureManager.releaseBluetoothSco(context)
        val accessibilityService = com.myvu.client.service.AutoSendAccessibilityService.activeInstance
        if (accessibilityService != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val locked = accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            if (locked) {
                LogBus.log("LockScreenHelper: Screen locked successfully via AccessibilityService GLOBAL_ACTION_LOCK_SCREEN")
                return true
            }
        }
        LogBus.warn("LockScreenHelper: Unable to lock screen directly (Accessibility service not active or Android < P)")
        return false
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
