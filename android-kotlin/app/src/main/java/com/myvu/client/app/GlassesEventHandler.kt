package com.myvu.client.app

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import com.myvu.client.app.feature.Notifications
import com.myvu.client.app.feature.TouchGestureManager
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import org.json.JSONObject

/**
 * Handles incoming events registered with InboundRouter (AI triggers, weather requests,
 * and battery updates).
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

            TouchGestureManager.handleTrigger(this.context, code, object : TouchGestureManager.ActionExecutor {
                override fun executeAiAssistant(triggerCode: Int) {
                    delegate.triggerAi(triggerCode)
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
                        try {
                            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                            if (am != null) {
                                val now = SystemClock.uptimeMillis()
                                am.dispatchMediaKeyEvent(
                                    KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
                                )
                                am.dispatchMediaKeyEvent(
                                    KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
                                )
                            }
                        } catch (e: Exception) {
                            LogBus.error("could not send media play/pause key event", e)
                        }
                    }
                    try {
                        delegate.sendAction(Notifications.buildShow("MYVU", "Música: Play / Pausa"))
                    } catch (ignored: Exception) {
                    }
                }
            })
        }

        inbound.setWeatherRequestListener {
            delegate.refreshWeather()
        }

        inbound.setBatteryUpdateListener { battery, isCharging ->
            delegate.updateBattery(battery, isCharging)
        }
    }
}
