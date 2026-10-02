package com.myvu.client.app

import android.content.Context
import android.view.KeyEvent
import com.myvu.client.ai.HudAnswerPager
import com.myvu.client.ai.SensitiveActionGate
import com.myvu.client.app.feature.GlassGesture
import com.myvu.client.app.feature.Notifications
import com.myvu.client.app.feature.SystemSettings
import com.myvu.client.app.feature.Teleprompter
import com.myvu.client.app.feature.TouchGestureManager
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Handles incoming events registered with InboundRouter (AI triggers, touch gestures,
 * weather requests, and battery updates).
 */
class GlassesEventHandler(
    context: Context?,
    private val inbound: InboundRouter,
    private val delegate: Delegate,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /** Runs actions confirmed from the temple (they may send messages or call). */
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    interface Delegate {
        fun wakeRelay()
        fun triggerAi(triggerCode: Int)
        fun pageClosed()
        fun refreshWeather()
        fun updateBattery(battery: Int, isCharging: Boolean)
        fun sendAction(actionJson: String)
    }

    private val context: Context? = context?.applicationContext

    init {
        installListeners()
    }

    private fun installListeners() {
        // The glasses' AI button (code:3) and wake word (code:7) both land here.
        inbound.setAiTriggerListener { code: Int, payload: JSONObject? ->
            // control:0 is the button RELEASE / page close. It must NOT
            // abort a turn already in flight -- the release arrives moments
            // after the press -- so it only marks the conversation to end
            // at the next turn boundary.
            if (payload != null && payload.optInt("control", 1) == 0) {
                delegate.pageClosed()
                return@setAiTriggerListener
            }

            val ctx = this@GlassesEventHandler.context
            if (code == 3) {
                val elapsedSinceKey = System.currentTimeMillis() - TouchGestureManager.lastPhysicalKeyEventTime
                if (TouchGestureManager.lastPhysicalKeyEventTime > 0L && elapsedSinceKey in 0..500L) {
                    LogBus.log("Suppressing duplicate AI trigger (code: 3) within ${elapsedSinceKey}ms of physical button key event")
                    return@setAiTriggerListener
                }
                TouchGestureManager.notifyPhysicalButtonPressed(ctx)
            }

            // The glasses' mic audio only flows over the app relay. With
            // the relay down (its retry budget spent), a press listened to
            // nothing and timed out with "0 packets in" -- so treat the
            // press like the glasses asking for the relay back.
            delegate.wakeRelay()
            val actionBtnMapping = if (ctx != null) Prefs.glassesActionButtonAction(ctx) else "VOICE_AI_FIXED"
            if (code == 3 && actionBtnMapping == "LAUNCH_GEMINI") {
                if (ctx != null) TouchGestureManager.launchGeminiAssistant(ctx, isLive = false)
                return@setAiTriggerListener
            } else if (code == 3 && actionBtnMapping == "LAUNCH_GEMINI_LIVE") {
                if (ctx != null) TouchGestureManager.launchGeminiAssistant(ctx, isLive = true)
                return@setAiTriggerListener
            } else if (code == 3 && actionBtnMapping == "LAUNCH_PHONE_ASSISTANT") {
                if (ctx != null) TouchGestureManager.launchPhoneAssistant(ctx)
                return@setAiTriggerListener
            }

            delegate.triggerAi(code)
        }

        inbound.setTouchGestureListener { gestureType, rawCode, _, eventTime ->
            TouchGestureManager.handleGesture(this.context, gestureType, rawCode, createActionExecutor(), eventTime)
        }

        inbound.setWeatherRequestListener {
            delegate.refreshWeather()
        }

        inbound.setBatteryUpdateListener { battery, isCharging ->
            delegate.updateBattery(battery, isCharging)
        }
    }

    /** Runs a gesture that did not arrive through [InboundRouter] (e.g. phone key events). */
    fun handleGesture(gesture: GlassGesture, rawCode: Int = gesture.code, eventTime: Long = -1L) {
        TouchGestureManager.handleGesture(context, gesture, rawCode, createActionExecutor(), eventTime)
    }

    private fun runConfirmed(action: suspend () -> String) {
        scope.launch {
            val message = try {
                action()
            } catch (e: Exception) {
                LogBus.error("GlassesEventHandler: confirmed action failed", e)
                "No pude ejecutar la acción confirmada."
            }
            showOnHud(message)
        }
    }

    /** Swipes page through a condensed answer while it is open (see HudAnswerPager). */
    private fun pageAnswer(gesture: GlassGesture): Boolean {
        if (!HudAnswerPager.isOpen()) return false
        return when (gesture) {
            GlassGesture.SWIPE_FORWARD -> {
                showOnHud(HudAnswerPager.next() ?: "Fin de la respuesta")
                true
            }
            GlassGesture.SWIPE_BACKWARD -> {
                HudAnswerPager.previous()?.let(::showOnHud)
                true
            }
            else -> false
        }
    }

    private fun showOnHud(message: String) {
        try {
            delegate.sendAction(Notifications.buildShow("MYVU", message))
        } catch (e: Exception) {
            LogBus.warn("GlassesEventHandler: could not show '$message' on HUD: ${e.message}")
        }
    }

    private fun createActionExecutor(): TouchGestureManager.ActionExecutor {
        return object : TouchGestureManager.ActionExecutor {
            // While the AI waits for confirmation of a sensitive action, a double tap
            // confirms it and a backward swipe discards it.
            override fun interceptGesture(gesture: GlassGesture): Boolean {
                if (!SensitiveActionGate.hasPending()) return pageAnswer(gesture)
                return when (gesture) {
                    GlassGesture.DOUBLE_TAP -> {
                        SensitiveActionGate.confirmPending()?.let(::runConfirmed)
                        true
                    }
                    GlassGesture.SWIPE_BACKWARD -> {
                        if (SensitiveActionGate.cancelPending()) showOnHud(SensitiveActionGate.CANCELLED_MESSAGE)
                        true
                    }
                    else -> false
                }
            }

            override fun executeAiAssistant(code: Int) {
                delegate.triggerAi(code)
            }

            override fun executeHudDashboard() {
                LogBus.log("HUD Dashboard action: letting glasses display native HUD dashboard")
            }

            override fun executeVoiceAgentAura() {
                delegate.triggerAi(3)
            }

            override fun executeGeminiAssistant() {
                val ctx = this@GlassesEventHandler.context
                if (ctx != null) {
                    TouchGestureManager.launchGeminiAssistant(ctx, isLive = false)
                }
            }

            override fun executeGeminiLive() {
                val ctx = this@GlassesEventHandler.context
                if (ctx != null) {
                    TouchGestureManager.launchGeminiAssistant(ctx, isLive = true)
                }
            }

            override fun executePhoneAssistant() {
                val ctx = this@GlassesEventHandler.context
                if (ctx != null) {
                    TouchGestureManager.launchPhoneAssistant(ctx)
                }
                try {
                    delegate.sendAction(Notifications.buildShow("MYVU", "Asistente activado"))
                } catch (ignored: Exception) {
                }
            }

            override fun executeLaunchApp(packageName: String) {
                val ctx = this@GlassesEventHandler.context
                if (ctx != null) {
                    TouchGestureManager.launchApp(ctx, packageName)
                }
                try {
                    val appName = if (ctx != null) {
                        try {
                            val pm = ctx.packageManager
                            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                        } catch (e: Exception) {
                            packageName
                        }
                    } else packageName
                    delegate.sendAction(Notifications.buildShow("MYVU", "Abriendo $appName..."))
                } catch (ignored: Exception) {
                }
            }

            override fun executeWeatherSync() {
                delegate.refreshWeather()
                try {
                    delegate.sendAction(Notifications.buildShow("MYVU", "Actualizando clima..."))
                } catch (ignored: Exception) {
                }
            }

            override fun executeToggleMirror() {
                val ctx = this@GlassesEventHandler.context ?: return
                val enabled = !Prefs.mirrorEnabled(ctx)
                Prefs.setMirrorEnabled(ctx, enabled)
                LogBus.log("Touchpad gesture -> Notification mirroring " + if (enabled) "ON" else "OFF")
                try {
                    delegate.sendAction(
                        Notifications.buildShow(
                            "MYVU",
                            "Espejo notificaciones: " + if (enabled) "Activado" else "Desactivado"
                        )
                    )
                } catch (ignored: Exception) {
                }
            }

            override fun executeMediaPlayPause() {
                LogBus.log("Touchpad gesture -> Media Play/Pause")
                val ctx = this@GlassesEventHandler.context
                if (ctx != null) {
                    TouchGestureManager.sendMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                }
                try {
                    delegate.sendAction(Notifications.buildShow("MYVU", "Música: Play / Pausa"))
                } catch (ignored: Exception) {
                }
            }

            override fun executeMediaNext() {
                LogBus.log("Touchpad gesture -> Media Next")
                val ctx = this@GlassesEventHandler.context
                if (ctx != null) {
                    TouchGestureManager.sendMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT)
                }
                try {
                    delegate.sendAction(Notifications.buildShow("MYVU", "Música: Siguiente"))
                } catch (ignored: Exception) {
                }
            }

            override fun executeMediaPrevious() {
                LogBus.log("Touchpad gesture -> Media Previous")
                val ctx = this@GlassesEventHandler.context
                if (ctx != null) {
                    TouchGestureManager.sendMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }
                try {
                    delegate.sendAction(Notifications.buildShow("MYVU", "Música: Anterior"))
                } catch (ignored: Exception) {
                }
            }

            override fun executeOpenTeleprompter() {
                LogBus.log("Touchpad gesture -> Open Teleprompter")
                try {
                    delegate.sendAction(Teleprompter.buildOpen("", "MYVU"))
                } catch (ignored: Exception) {
                }
            }

            override fun executeZenMode() {
                val ctx = this@GlassesEventHandler.context ?: return
                val enabled = !Prefs.zenModeEnabled(ctx)
                Prefs.setZenModeEnabled(ctx, enabled)
                LogBus.log("Touchpad gesture -> Zen mode " + if (enabled) "ON" else "OFF")
                try {
                    delegate.sendAction(SystemSettings.setZenMode(enabled))
                    delegate.sendAction(
                        Notifications.buildShow(
                            "MYVU",
                            "Modo Zen: " + if (enabled) "Activado" else "Desactivado"
                        )
                    )
                } catch (ignored: Exception) {
                }
            }

            override fun executeNone() {
                LogBus.log("Touchpad gesture -> None")
            }
        }
    }
}
