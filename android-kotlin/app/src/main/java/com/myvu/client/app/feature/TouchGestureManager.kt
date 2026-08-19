package com.myvu.client.app.feature

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs

/**
 * Handles mapping, debounce, and dispatching of touch gestures and hardware triggers
 * received from the Meizu MYVU glasses to customizable actions.
 */
object TouchGestureManager {
    const val ACTION_NONE: String = "none"
    const val ACTION_PHONE_ASSISTANT: String = "phone_assistant"
    const val ACTION_AI_ASSISTANT: String = "ai_assistant"
    const val ACTION_LOCAL_AI: String = "ai_assistant"
    const val ACTION_MEDIA_PLAY_PAUSE: String = "media_play_pause"
    const val ACTION_MEDIA_NEXT: String = "media_next"
    const val ACTION_MEDIA_PREV: String = "media_prev"
    const val ACTION_WEATHER_SYNC: String = "weather_sync"
    const val ACTION_TOGGLE_MIRROR: String = "toggle_mirror"
    const val ACTION_OPEN_TELEPROMPTER: String = "open_teleprompter"
    const val ACTION_ZEN_MODE: String = "zen_mode"

    fun interface ActionExecutor {
        fun executeAiAssistant(code: Int)
        fun executePhoneAssistant() { }
        fun executeWeatherSync() { }
        fun executeToggleMirror() { }
        fun executeMediaPlayPause() { }
        fun executeMediaNext() { }
        fun executeMediaPrevious() { }
        fun executeOpenTeleprompter() { }
        fun executeZenMode() { }
        fun executeNone() { }
    }

    private const val DEBOUNCE_MS = 350L
    private var lastTriggerTime = 0L

    @JvmStatic
    fun resetDebounceForTesting() {
        lastTriggerTime = 0L
    }

    @JvmStatic
    fun getActionForGesture(context: Context?, gesture: GlassGesture): GestureAction {
        if (context == null) {
            return when (gesture) {
                GlassGesture.TAP -> GestureAction.NONE
                GlassGesture.DOUBLE_TAP -> GestureAction.MEDIA_PLAY_PAUSE
                GlassGesture.TRIPLE_TAP -> GestureAction.LAUNCH_PHONE_ASSISTANT
                GlassGesture.SWIPE_FORWARD -> GestureAction.MEDIA_NEXT
                GlassGesture.SWIPE_BACKWARD -> GestureAction.MEDIA_PREV
                GlassGesture.LONG_PRESS -> GestureAction.LAUNCH_LOCAL_AI
                GlassGesture.UNKNOWN -> GestureAction.LAUNCH_LOCAL_AI
            }
        }
        val actionId = when (gesture) {
            GlassGesture.TAP -> Prefs.touchpadTapAction(context)
            GlassGesture.DOUBLE_TAP -> Prefs.touchpadDoubleTapAction(context)
            GlassGesture.TRIPLE_TAP -> Prefs.touchpadTripleTapAction(context)
            GlassGesture.SWIPE_FORWARD -> Prefs.touchpadSwipeForwardAction(context)
            GlassGesture.SWIPE_BACKWARD -> Prefs.touchpadSwipeBackwardAction(context)
            GlassGesture.LONG_PRESS -> Prefs.touchpadLongPressAction(context)
            GlassGesture.UNKNOWN -> Prefs.touchpadLongPressAction(context)
        }
        return GestureAction.fromId(actionId)
    }

    @JvmStatic
    @JvmOverloads
    fun handleGesture(
        context: Context?,
        gesture: GlassGesture,
        rawCode: Int = gesture.code,
        executor: ActionExecutor?
    ) {
        if (executor == null) return

        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < DEBOUNCE_MS) {
            LogBus.trace("Touchpad gesture ignored -- debounce (" + (now - lastTriggerTime) + "ms)")
            return
        }
        lastTriggerTime = now

        val action = getActionForGesture(context, gesture)
        LogBus.log("Touchpad gesture received ($gesture, code=$rawCode) -> Action: ${action.id} (${action.name})")

        when (action) {
            GestureAction.NONE -> executor.executeNone()
            GestureAction.LAUNCH_PHONE_ASSISTANT -> executor.executePhoneAssistant()
            GestureAction.LAUNCH_LOCAL_AI -> executor.executeAiAssistant(rawCode)
            GestureAction.MEDIA_PLAY_PAUSE -> executor.executeMediaPlayPause()
            GestureAction.MEDIA_NEXT -> executor.executeMediaNext()
            GestureAction.MEDIA_PREV -> executor.executeMediaPrevious()
            GestureAction.WEATHER_SYNC -> executor.executeWeatherSync()
            GestureAction.TOGGLE_MIRROR -> executor.executeToggleMirror()
            GestureAction.OPEN_TELEPROMPTER -> executor.executeOpenTeleprompter()
            GestureAction.ZEN_MODE -> executor.executeZenMode()
        }
    }

    @JvmStatic
    fun handleTrigger(context: Context?, code: Int, executor: ActionExecutor?) {
        val gesture = GlassGesture.fromCode(code)
        handleGesture(context, gesture, code, executor)
    }

    @JvmStatic
    fun launchPhoneAssistant(context: Context?) {
        if (context == null) return
        val appContext = context.applicationContext

        // 1. Dispatch KEYCODE_VOICE_ASSIST to system AudioManager
        try {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null) {
                val now = SystemClock.uptimeMillis()
                val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, 0)
                val up = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOICE_ASSIST, 0)
                am.dispatchMediaKeyEvent(down)
                am.dispatchMediaKeyEvent(up)
                LogBus.log("Dispatched KEYCODE_VOICE_ASSIST for Voice Assistant (Google/Gemini)")
            }
        } catch (e: Exception) {
            LogBus.warn("Could not dispatch KEYCODE_VOICE_ASSIST: ${e.message}")
        }

        // 2. Launch Google Assistant voice command Intent as fallback
        try {
            val voiceIntent = Intent(Intent.ACTION_VOICE_COMMAND)
            voiceIntent.setPackage("com.google.android.googlequicksearchbox")
            voiceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(voiceIntent)
            LogBus.log("Launched Google Assistant voice command")
            return
        } catch (e: Exception) {
            LogBus.warn("Could not launch Google Assistant voice command: ${e.message}")
        }

        // 3. Fallback to generic ACTION_VOICE_COMMAND
        try {
            val voiceIntent = Intent(Intent.ACTION_VOICE_COMMAND)
            voiceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(voiceIntent)
            LogBus.log("Launched generic ACTION_VOICE_COMMAND")
        } catch (e: Exception) {
            LogBus.warn("Could not launch generic ACTION_VOICE_COMMAND: ${e.message}")
        }
    }

    @JvmStatic
    fun sendMediaKey(context: Context?, keyCode: Int) {
        if (context == null) return
        try {
            val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null) {
                val now = SystemClock.uptimeMillis()
                val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
                val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
                am.dispatchMediaKeyEvent(down)
                am.dispatchMediaKeyEvent(up)
            }
        } catch (e: Exception) {
            LogBus.error("Could not send media key $keyCode", e)
        }
    }
}
