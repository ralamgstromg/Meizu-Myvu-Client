package com.myvu.client.app.feature

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    const val ACTION_GEMINI: String = "launch_gemini"
    const val ACTION_PHONE_ASSISTANT: String = "phone_assistant"
    const val ACTION_APP: String = "launch_app"
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
        fun executeGeminiAssistant() { executePhoneAssistant() }
        fun executePhoneAssistant() { }
        fun executeLaunchApp(packageName: String) { }
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
    private const val GEMINI_SCO_CAPTURE_WINDOW_MS = 4500L
    private var lastTriggerTime = 0L

    private val audioHandler = Handler(Looper.getMainLooper())
    private var scoReleaseRunnable: Runnable? = null

    @JvmStatic
    fun releaseBluetoothSco(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            }
            if (am.isBluetoothScoOn) {
                am.stopBluetoothSco()
                am.isBluetoothScoOn = false
            }
            am.mode = AudioManager.MODE_NORMAL
            LogBus.log("Released Bluetooth SCO audio routing -- restored A2DP media channel for Gemini reply")
        } catch (e: Exception) {
            LogBus.warn("Error releasing Bluetooth SCO: ${e.message}")
        }
    }

    @JvmStatic
    fun resetDebounceForTesting() {
        lastTriggerTime = 0L
    }

    @JvmStatic
    fun getRawActionIdForGesture(context: Context?, gesture: GlassGesture): String {
        if (context == null) {
            return when (gesture) {
                GlassGesture.TAP -> GestureAction.NONE.id
                GlassGesture.DOUBLE_TAP -> GestureAction.MEDIA_PLAY_PAUSE.id
                GlassGesture.TRIPLE_TAP -> GestureAction.LAUNCH_GEMINI.id
                GlassGesture.SWIPE_FORWARD -> GestureAction.MEDIA_NEXT.id
                GlassGesture.SWIPE_BACKWARD -> GestureAction.MEDIA_PREV.id
                GlassGesture.LONG_PRESS -> GestureAction.LAUNCH_LOCAL_AI.id
                GlassGesture.UNKNOWN -> GestureAction.NONE.id
            }
        }
        return when (gesture) {
            GlassGesture.TAP -> Prefs.touchpadTapAction(context)
            GlassGesture.DOUBLE_TAP -> Prefs.touchpadDoubleTapAction(context)
            GlassGesture.TRIPLE_TAP -> Prefs.touchpadTripleTapAction(context)
            GlassGesture.SWIPE_FORWARD -> Prefs.touchpadSwipeForwardAction(context)
            GlassGesture.SWIPE_BACKWARD -> Prefs.touchpadSwipeBackwardAction(context)
            GlassGesture.LONG_PRESS -> Prefs.touchpadLongPressAction(context)
            GlassGesture.UNKNOWN -> GestureAction.NONE.id
        }
    }

    @JvmStatic
    fun getActionForGesture(context: Context?, gesture: GlassGesture): GestureAction {
        val actionId = getRawActionIdForGesture(context, gesture)
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
        if (executor == null || gesture == GlassGesture.UNKNOWN) return

        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < DEBOUNCE_MS) {
            LogBus.trace("Touchpad gesture ignored -- debounce (" + (now - lastTriggerTime) + "ms)")
            return
        }
        lastTriggerTime = now

        val rawActionId = getRawActionIdForGesture(context, gesture)
        if (GestureAction.isAppAction(rawActionId)) {
            val pkg = GestureAction.getAppPackage(rawActionId)
            if (!pkg.isNullOrEmpty()) {
                LogBus.log("Touchpad gesture ($gesture, code=$rawCode) -> Launch App: $pkg")
                executor.executeLaunchApp(pkg)
                return
            }
        }

        val action = GestureAction.fromId(rawActionId)
        LogBus.log("Touchpad gesture received ($gesture, code=$rawCode) -> Action: ${action.id} (${action.name})")

        when (action) {
            GestureAction.NONE -> executor.executeNone()
            GestureAction.LAUNCH_GEMINI -> executor.executeGeminiAssistant()
            GestureAction.LAUNCH_PHONE_ASSISTANT -> executor.executePhoneAssistant()
            GestureAction.LAUNCH_APP -> {
                val pkg = GestureAction.getAppPackage(rawActionId)
                if (!pkg.isNullOrEmpty()) {
                    executor.executeLaunchApp(pkg)
                } else {
                    executor.executeNone()
                }
            }
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

    /**
     * Wakes up the phone screen, unlocks keyguard, connects Bluetooth SCO microphone from glasses,
     * and triggers Gemini in active hands-free voice command mode delegating full screen control.
     */
    @JvmStatic
    fun launchGeminiAssistant(context: Context?) {
        if (context == null) return
        val appContext = context.applicationContext

        // 1. Wake up phone screen with bright wakelock
        try {
            com.myvu.client.core.LockScreenHelper.wakeUpScreen(
                appContext,
                "MYVU:GeminiVoiceAssistant",
                15000L
            )
        } catch (e: Exception) {
            LogBus.warn("LockScreen wakeUp error: ${e.message}")
        }

        // 2. Route Bluetooth SCO microphone so Gemini receives audio input directly from glasses
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am != null) {
            try {
                // Cancel previous release timer if user triggered again
                scoReleaseRunnable?.let { audioHandler.removeCallbacks(it) }

                am.mode = AudioManager.MODE_NORMAL
                am.startBluetoothSco()
                am.isBluetoothScoOn = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val btScoDevice = am.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    }
                    if (btScoDevice != null) {
                        am.setCommunicationDevice(btScoDevice)
                    }
                }
                LogBus.log("Enabled Bluetooth SCO audio routing to glasses microphone for Gemini")

                // Auto-release SCO after prompt capture window (4.5s) to immediately restore A2DP media channel for Gemini's voice reply
                val release = Runnable {
                    releaseBluetoothSco(appContext)
                }
                scoReleaseRunnable = release
                audioHandler.postDelayed(release, GEMINI_SCO_CAPTURE_WINDOW_MS)
            } catch (e: Exception) {
                LogBus.warn("Could not route Bluetooth SCO for Gemini: ${e.message}")
            }
        }

        // 3. Dispatch KEYCODE_VOICE_ASSIST to system AudioManager
        try {
            if (am != null) {
                val now = SystemClock.uptimeMillis()
                val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, 0)
                val up = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOICE_ASSIST, 0)
                am.dispatchMediaKeyEvent(down)
                am.dispatchMediaKeyEvent(up)
                LogBus.log("Dispatched KEYCODE_VOICE_ASSIST")
            }
        } catch (e: Exception) {
            LogBus.warn("Could not dispatch KEYCODE_VOICE_ASSIST: ${e.message}")
        }

        // 4. Launch Gemini Hands-Free Voice Activity over keyguard
        try {
            val voiceSearchIntent = Intent(android.speech.RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_SECURE, true)
                putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 2500L)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            com.myvu.client.ui.SendTrampolineActivity.launchWithKeyguardDismiss(appContext, voiceSearchIntent)
            LogBus.log("Launched Gemini hands-free search via SendTrampolineActivity")
            return
        } catch (e: Exception) {
            LogBus.warn("Could not launch ACTION_VOICE_SEARCH_HANDS_FREE: ${e.message}")
        }

        // 5. Fallback: Google QuickSearchBox Voice Command Intent
        try {
            val voiceIntent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                setPackage("com.google.android.googlequicksearchbox")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            com.myvu.client.ui.SendTrampolineActivity.launchWithKeyguardDismiss(appContext, voiceIntent)
            LogBus.log("Launched Google Assistant voice command via SendTrampolineActivity")
            return
        } catch (e: Exception) {
            LogBus.warn("Could not launch Google Assistant voice command: ${e.message}")
        }

        // 6. Generic ACTION_VOICE_COMMAND fallback
        try {
            val genericVoiceIntent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            com.myvu.client.ui.SendTrampolineActivity.launchWithKeyguardDismiss(appContext, genericVoiceIntent)
            LogBus.log("Launched generic ACTION_VOICE_COMMAND")
        } catch (e: Exception) {
            LogBus.warn("Could not launch generic ACTION_VOICE_COMMAND: ${e.message}")
        }
    }

    @JvmStatic
    fun launchPhoneAssistant(context: Context?) {
        launchGeminiAssistant(context)
    }

    @JvmStatic
    fun launchApp(context: Context?, packageName: String) {
        if (context == null || packageName.isBlank()) return
        val appContext = context.applicationContext
        try {
            com.myvu.client.core.LockScreenHelper.wakeUpScreen(
                appContext,
                "MYVU:LaunchApp:$packageName",
                10000L
            )
            val launchIntent = appContext.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                com.myvu.client.ui.SendTrampolineActivity.launchWithKeyguardDismiss(appContext, launchIntent)
                LogBus.log("Launched app via SendTrampolineActivity: $packageName")
            } else {
                LogBus.warn("No launch intent found for package: $packageName")
            }
        } catch (e: Exception) {
            LogBus.error("Failed to launch app $packageName", e)
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
