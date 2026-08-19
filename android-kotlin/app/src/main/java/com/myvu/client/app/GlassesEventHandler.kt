package com.myvu.client.app

import android.content.Context
import android.view.KeyEvent
import com.myvu.client.app.feature.Notifications
import com.myvu.client.app.feature.SystemSettings
import com.myvu.client.app.feature.Teleprompter
import com.myvu.client.app.feature.TouchGestureManager
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import org.json.JSONObject

/**
 * Handles incoming events registered with InboundRouter (AI triggers, touch gestures,
 * weather requests, and battery updates).
 */
class GlassesEventHandler(
    context: Context?,
    private val inbound: InboundRouter,
    private val delegate: Delegate
) {

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
        inbound.setAiTriggerListener { code: Int, payload: JSONObject? ->
            if (payload != null && payload.optInt("control", 1) == 0) {
                delegate.pageClosed()
                return@setAiTriggerListener
            }
            delegate.wakeRelay()
            TouchGestureManager.handleTrigger(this.context, code, createActionExecutor())
        }

        inbound.setTouchGestureListener { gestureType, rawCode, _ ->
            TouchGestureManager.handleGesture(this.context, gestureType, rawCode, createActionExecutor())
        }

        inbound.setWeatherRequestListener {
            delegate.refreshWeather()
        }

        inbound.setBatteryUpdateListener { battery, isCharging ->
            delegate.updateBattery(battery, isCharging)
        }
    }

    private fun createActionExecutor(): TouchGestureManager.ActionExecutor {
        return object : TouchGestureManager.ActionExecutor {
            override fun executeAiAssistant(triggerCode: Int) {
                delegate.triggerAi(triggerCode)
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
