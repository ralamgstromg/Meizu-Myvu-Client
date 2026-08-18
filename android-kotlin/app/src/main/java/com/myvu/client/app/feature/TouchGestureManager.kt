package com.myvu.client.app.feature

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.plugin.tasker.event.TaskerEventBroadcaster

/**
 * Handles mapping and dispatching of single touchpad / hardware button (code: 3)
 * gesture actions received from the Meizu MYVU glasses.
 */
object TouchGestureManager {
    const val ACTION_NONE: String = "none"
    const val ACTION_AI_ASSISTANT: String = "ai_assistant"
    const val ACTION_WEATHER_SYNC: String = "weather_sync"
    const val ACTION_TOGGLE_MIRROR: String = "toggle_mirror"
    const val ACTION_MEDIA_PLAY_PAUSE: String = "media_play_pause"
    const val ACTION_TASKER_EVENT: String = "tasker_event"

    fun interface ActionExecutor {
        fun executeAiAssistant(code: Int)
        fun executeWeatherSync() { }
        fun executeToggleMirror() { }
        fun executeMediaPlayPause() { }
    }

    private const val DEBOUNCE_MS = 400L
    private var lastTriggerTime = 0L

    @JvmStatic
    fun handleTrigger(context: Context?, code: Int, executor: ActionExecutor?) {
        if (executor == null) return

        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < DEBOUNCE_MS) {
            LogBus.trace("Touchpad / Button trigger ignored -- debounce (" + (now - lastTriggerTime) + "ms)")
            return
        }
        lastTriggerTime = now

        if (context != null) {
            val gestureName = when (code) {
                7 -> "Voz / Wake Word"
                3 -> "Botón / Panel Táctil"
                else -> "Gesto $code"
            }
            TaskerEventBroadcaster.sendGestureEvent(context, code, gestureName)
            if (code == 3 || code == 7) {
                TaskerEventBroadcaster.sendAiButtonEvent(context, code)
            }
        }

        val action = if (context != null) Prefs.touchpadLongPressAction(context) else ACTION_AI_ASSISTANT
        LogBus.log("Touchpad / Button trigger received (code=$code) -> Action: $action")

        when (action) {
            ACTION_WEATHER_SYNC -> executor.executeWeatherSync()
            ACTION_TOGGLE_MIRROR -> executor.executeToggleMirror()
            ACTION_MEDIA_PLAY_PAUSE -> executor.executeMediaPlayPause()
            ACTION_TASKER_EVENT -> {
                LogBus.log("Touchpad action set to tasker_event -- dispatched to Tasker")
            }
            ACTION_NONE -> { }
            ACTION_AI_ASSISTANT -> executor.executeAiAssistant(code)
            else -> executor.executeAiAssistant(code)
        }
    }
}
