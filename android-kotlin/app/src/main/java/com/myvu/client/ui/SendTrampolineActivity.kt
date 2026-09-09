package com.myvu.client.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.myvu.client.core.LockScreenHelper
import com.myvu.client.core.LogBus

/**
 * Completely isolated, transparent trampoline activity used to request keyguard dismissal
 * and launch messaging or phone control apps over the lock screen without pulling the MYVU UI.
 */
class SendTrampolineActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var hasLaunched = false
    private var dismissRequested = false
    private var cachedTargetIntent: Intent? = null

    companion object {
        const val EXTRA_TARGET_INTENT = "target_intent"

        fun launchWithKeyguardDismiss(context: Context, targetIntent: Intent) {
            val trampolineIntent = Intent(context, SendTrampolineActivity::class.java).apply {
                putExtra(EXTRA_TARGET_INTENT, targetIntent)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }
            context.startActivity(trampolineIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LockScreenHelper.setupShowWhenLocked(this)

        @Suppress("DEPRECATION")
        cachedTargetIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_TARGET_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_TARGET_INTENT)
        }

        if (cachedTargetIntent == null) {
            LogBus.warn("SendTrampolineActivity: targetIntent is null, finishing")
            finish()
            return
        }

        // Safety timeout to avoid leaving activity hanging
        handler.postDelayed({
            if (!isFinishing && !hasLaunched) {
                LogBus.log("SendTrampolineActivity: Safety timeout (2.5s) reached, launching target intent directly")
                cachedTargetIntent?.let { dispatchTarget(it) }
            }
        }, 2500L)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val target = cachedTargetIntent ?: return
        if (hasFocus && !dismissRequested && !hasLaunched) {
            dismissRequested = true
            LogBus.log("SendTrampolineActivity: Window focused, requesting keyguard dismissal")
            LockScreenHelper.unlockKeyguard(
                activity = this,
                onDismissed = {
                    dispatchTarget(target)
                },
                onCancelledOrFailed = {
                    // Do NOT wait 12s on cancel/fail! Launch immediately so target app is ready
                    LogBus.log("SendTrampolineActivity: Keyguard dismissal cancelled/failed, launching target intent immediately")
                    dispatchTarget(target)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // If window focus did not trigger after 600ms, ensure keyguard dismissal is attempted
        handler.postDelayed({
            if (!dismissRequested && !hasLaunched && !isFinishing) {
                cachedTargetIntent?.let { target ->
                    dismissRequested = true
                    LogBus.log("SendTrampolineActivity: Fallback onResume dismissal trigger")
                    LockScreenHelper.unlockKeyguard(
                        activity = this,
                        onDismissed = { dispatchTarget(target) },
                        onCancelledOrFailed = { dispatchTarget(target) }
                    )
                }
            }
        }, 600L)
    }

    private fun dispatchTarget(targetIntent: Intent) {
        if (hasLaunched) return
        hasLaunched = true
        try {
            targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(targetIntent)
            LogBus.log("SendTrampolineActivity: Target intent launched successfully")
        } catch (e: Exception) {
            LogBus.error("SendTrampolineActivity: Failed to launch target intent", e)
        } finally {
            finish()
            overridePendingTransition(0, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
