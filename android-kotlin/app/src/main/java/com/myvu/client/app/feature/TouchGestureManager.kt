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
    const val ACTION_GEMINI_LIVE: String = "gemini_live"
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
    const val ACTION_HUD_DASHBOARD: String = "hud_dashboard"
    const val ACTION_VOICE_AGENT_AURA: String = "voice_agent_aura"

    fun interface ActionExecutor {
        fun executeAiAssistant(code: Int)
        fun executeGeminiAssistant() { executePhoneAssistant() }
        fun executeGeminiLive() { executeGeminiAssistant() }
        fun executePhoneAssistant() { }
        fun executeLaunchApp(packageName: String) { }
        fun executeWeatherSync() { }
        fun executeToggleMirror() { }
        fun executeMediaPlayPause() { }
        fun executeMediaNext() { }
        fun executeMediaPrevious() { }
        fun executeOpenTeleprompter() { }
        fun executeZenMode() { }
        fun executeHudDashboard() { executeNone() }
        fun executeVoiceAgentAura() { executeAiAssistant(3) }
        fun executeNone() { }
    }

    private const val DEBOUNCE_MS = 200L
    private const val DOUBLE_TAP_MIN_INTERVAL_MS = 30L
    private const val DOUBLE_TAP_MAX_INTERVAL_MS = 1100L
    private const val TRIPLE_TAP_MAX_INTERVAL_MS = 1350L
    private const val GEMINI_SCO_CAPTURE_WINDOW_MS = 8500L
    private const val GEMINI_LIVE_MAX_SCO_DURATION_MS = 5 * 60 * 1000L // 5 minutes max safety window
    const val PHYSICAL_BUTTON_SUPPRESSION_MS = 1200L

    @JvmStatic
    var lastPhysicalButtonTime: Long = 0L
        internal set

    @JvmStatic
    var lastPhysicalKeyEventTime: Long = 0L
        internal set

    private var lastTriggerTime = 0L
    private var lastTapTime = 0L
    private var lastTapEventTime = -1L
    private var accumulatedTapCount = 0

    @JvmField
    internal var timeProvider: () -> Long = { System.currentTimeMillis() }

    private val audioHandler = Handler(Looper.getMainLooper())
    private var scoReleaseRunnable: Runnable? = null

    @JvmStatic
    fun notifyPhysicalButtonPressed(context: Context?) {
        val now = timeProvider()
        lastPhysicalButtonTime = now
        lastTriggerTime = now
        accumulatedTapCount = 0
        lastTapTime = 0L
        lastTapEventTime = -1L
        LogBus.log("TouchGestureManager: Physical button press registered. Suppressing temple touch gestures for ${PHYSICAL_BUTTON_SUPPRESSION_MS}ms")
    }

    @JvmStatic
    fun releaseBluetoothSco(context: Context) {
        audioHandler.removeCallbacksAndMessages(null)
        scoReleaseRunnable = null
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
                if (am.mode != AudioManager.MODE_NORMAL) {
                    am.mode = AudioManager.MODE_NORMAL
                }
            } else {
                @Suppress("DEPRECATION")
                if (am.isBluetoothScoOn) {
                    am.stopBluetoothSco()
                    am.isBluetoothScoOn = false
                }
                if (am.mode != AudioManager.MODE_NORMAL) {
                    am.mode = AudioManager.MODE_NORMAL
                }
            }
            LogBus.log("Released Bluetooth SCO audio routing -- restored A2DP media channel for Gemini reply")
        } catch (e: Exception) {
            LogBus.warn("Error releasing Bluetooth SCO: ${e.message}")
        }
    }

    @JvmStatic
    fun resetDebounceForTesting() {
        lastTriggerTime = 0L
        lastTapTime = 0L
        lastTapEventTime = -1L
        lastPhysicalButtonTime = 0L
        lastPhysicalKeyEventTime = 0L
        accumulatedTapCount = 0
        timeProvider = { System.currentTimeMillis() }
    }

    @JvmStatic
    fun getRawActionIdForGesture(context: Context?, gesture: GlassGesture): String {
        if (context == null) {
            return when (gesture) {
                GlassGesture.TAP -> GestureAction.NONE.id
                GlassGesture.DOUBLE_TAP -> GestureAction.LAUNCH_GEMINI.id
                GlassGesture.TRIPLE_TAP -> GestureAction.LAUNCH_GEMINI.id
                GlassGesture.SWIPE_FORWARD -> GestureAction.MEDIA_NEXT.id
                GlassGesture.SWIPE_BACKWARD -> GestureAction.MEDIA_PREV.id
                GlassGesture.LONG_PRESS -> GestureAction.LAUNCH_LOCAL_AI.id
                GlassGesture.ACTION_BUTTON -> GestureAction.HUD_DASHBOARD.id
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
            GlassGesture.ACTION_BUTTON -> GestureAction.HUD_DASHBOARD.id
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
        executor: ActionExecutor?,
        eventTime: Long = -1L
    ) {
        if (executor == null || gesture == GlassGesture.UNKNOWN) return

        val now = timeProvider()

        val isPhysicalButton = gesture == GlassGesture.ACTION_BUTTON || rawCode == 230 || rawCode == 231 || rawCode == 202

        // 1. If physical button was recently pressed, suppress concurrent temple gestures
        if (!isPhysicalButton && lastPhysicalButtonTime > 0L && (now - lastPhysicalButtonTime) in 0 until PHYSICAL_BUTTON_SUPPRESSION_MS) {
            LogBus.log("Touchpad gesture ignored ($gesture, code=$rawCode) -- suppressed by recent physical button press (${now - lastPhysicalButtonTime}ms ago)")
            return
        }

        // 2. Physical Action Button direct handling (codes 202, 230, 231, or ACTION_BUTTON)
        if (isPhysicalButton) {
            accumulatedTapCount = 0
            lastTapTime = 0L
            lastTapEventTime = -1L
            lastPhysicalButtonTime = now
            lastPhysicalKeyEventTime = now
            lastTriggerTime = now

            if (rawCode == 230 || rawCode == 202 || (gesture == GlassGesture.ACTION_BUTTON && rawCode != 231)) {
                LogBus.log("Physical action button short press (code=$rawCode) -> HUD Dashboard")
                executor.executeHudDashboard()
            } else {
                // rawCode 231 or Action Button Long Press
                LogBus.log("Physical action button long press (code=$rawCode) -> executing action button mapping")
                val actionBtnMapping = if (context != null) Prefs.glassesActionButtonAction(context) else "VOICE_AI_FIXED"
                when (actionBtnMapping) {
                    "LAUNCH_GEMINI" -> executor.executeGeminiAssistant()
                    "LAUNCH_GEMINI_LIVE" -> executor.executeGeminiLive()
                    "LAUNCH_PHONE_ASSISTANT" -> executor.executePhoneAssistant()
                    else -> executor.executeVoiceAgentAura()
                }
            }
            return
        }

        // Software double-tap and triple-tap detection:
        // Flyme XR glasses frequently send consecutive TAP events (code 210 / 200)
        // across separate packets instead of hardware synthesizing DOUBLE_TAP (211) or TRIPLE_TAP.
        if (gesture == GlassGesture.TAP) {
            val dtFromLastTap = if (lastTapEventTime > 0L && eventTime > 0L) {
                Math.abs(eventTime - lastTapEventTime)
            } else if (lastTapTime != 0L) {
                Math.abs(now - lastTapTime)
            } else {
                Long.MAX_VALUE
            }

            if (lastTapTime != 0L && dtFromLastTap < DOUBLE_TAP_MIN_INTERVAL_MS) {
                // Electrical contact bounce: ignore without updating lastTapTime
                LogBus.trace("Contact bounce tap ignored (${dtFromLastTap}ms < ${DOUBLE_TAP_MIN_INTERVAL_MS}ms)")
                return
            }

            if (accumulatedTapCount == 2 && dtFromLastTap <= TRIPLE_TAP_MAX_INTERVAL_MS) {
                val tripleTapActionId = getRawActionIdForGesture(context, GlassGesture.TRIPLE_TAP)
                val tripleTapAction = GestureAction.fromId(tripleTapActionId)
                accumulatedTapCount = 0
                lastTapTime = 0L
                lastTapEventTime = -1L
                if (tripleTapAction != GestureAction.NONE) {
                    LogBus.log("Software synthesized TRIPLE_TAP from 3 consecutive TAPs (${dtFromLastTap}ms apart)")
                    dispatchAction(context, GlassGesture.TRIPLE_TAP, rawCode, tripleTapActionId, tripleTapAction, executor, now)
                    return
                }
            } else if (accumulatedTapCount == 1 && dtFromLastTap <= DOUBLE_TAP_MAX_INTERVAL_MS) {
                val doubleTapActionId = getRawActionIdForGesture(context, GlassGesture.DOUBLE_TAP)
                val doubleTapAction = GestureAction.fromId(doubleTapActionId)
                accumulatedTapCount = 2
                lastTapTime = now
                lastTapEventTime = eventTime
                if (doubleTapAction != GestureAction.NONE) {
                    LogBus.log("Software synthesized DOUBLE_TAP from 2 consecutive TAPs (${dtFromLastTap}ms apart)")
                    dispatchAction(context, GlassGesture.DOUBLE_TAP, rawCode, doubleTapActionId, doubleTapAction, executor, now)
                    return
                }
            } else {
                accumulatedTapCount = 1
                lastTapTime = now
                lastTapEventTime = eventTime
            }
        } else {
            // Any non-TAP gesture resets software multi-tap accumulator
            accumulatedTapCount = 0
            lastTapTime = 0L
            lastTapEventTime = -1L
        }

        val rawActionId = getRawActionIdForGesture(context, gesture)
        val action = GestureAction.fromId(rawActionId)

        // If action is NONE or HUD_DASHBOARD, do not trigger debounce so legitimate subsequent gestures are not swallowed
        if (action == GestureAction.NONE || action == GestureAction.HUD_DASHBOARD) {
            LogBus.log("Touchpad gesture received ($gesture, code=$rawCode) -> Action: ${action.id}")
            if (action == GestureAction.HUD_DASHBOARD) {
                executor.executeHudDashboard()
            } else {
                executor.executeNone()
            }
            return
        }

        // Apply debounce only for non-NONE actions
        if (now - lastTriggerTime < DEBOUNCE_MS) {
            LogBus.trace("Touchpad gesture ignored -- debounce (" + (now - lastTriggerTime) + "ms)")
            return
        }

        dispatchAction(context, gesture, rawCode, rawActionId, action, executor, now)
    }

    private fun dispatchAction(
        context: Context?,
        gesture: GlassGesture,
        rawCode: Int,
        rawActionId: String,
        action: GestureAction,
        executor: ActionExecutor,
        now: Long
    ) {
        lastTriggerTime = now

        if (GestureAction.isAppAction(rawActionId)) {
            val pkg = GestureAction.getAppPackage(rawActionId)
            if (!pkg.isNullOrEmpty()) {
                LogBus.log("Touchpad gesture ($gesture, code=$rawCode) -> Launch App: $pkg")
                executor.executeLaunchApp(pkg)
                return
            }
        }

        LogBus.log("Touchpad gesture received ($gesture, code=$rawCode) -> Action: ${action.id} (${action.name})")

        when (action) {
            GestureAction.NONE -> executor.executeNone()
            GestureAction.HUD_DASHBOARD -> executor.executeHudDashboard()
            GestureAction.VOICE_AGENT_AURA -> executor.executeVoiceAgentAura()
            GestureAction.LAUNCH_GEMINI -> executor.executeGeminiAssistant()
            GestureAction.LAUNCH_GEMINI_LIVE -> executor.executeGeminiLive()
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
    @JvmOverloads
    fun handleTrigger(context: Context?, code: Int, executor: ActionExecutor?, eventTime: Long = -1L) {
        val gesture = GlassGesture.fromCode(code)
        handleGesture(context, gesture, code, executor, eventTime)
    }

    /**
     * Wakes up the phone screen, unlocks keyguard, connects Bluetooth SCO microphone from glasses,
     * and triggers Gemini in active hands-free voice command mode or Gemini Live continuous conversation.
     */
    @JvmStatic
    @JvmOverloads
    fun launchGeminiAssistant(context: Context?, isLive: Boolean = false) {
        if (context == null) return
        val appContext = context.applicationContext
        val isLocked = com.myvu.client.core.LockScreenHelper.isDeviceLocked(appContext)

        // 1. Wake up phone screen with bright wakelock
        try {
            com.myvu.client.core.LockScreenHelper.wakeUpScreen(
                appContext,
                "MYVU:GeminiVoiceAssistant",
                if (isLive) 60000L else 15000L
            )
        } catch (e: Exception) {
            LogBus.warn("LockScreen wakeUp error: ${e.message}")
        }

        // 2. Trigger Accessibility Service for Gemini Live or Gemini Voice Dictation/Mic auto-click
        if (isLive) {
            com.myvu.client.service.AutoSendAccessibilityService.triggerGeminiLiveAutoStart(
                isDeviceLocked = isLocked
            )
        } else {
            com.myvu.client.service.AutoSendAccessibilityService.triggerGeminiVoiceAutoStart(
                isDeviceLocked = isLocked
            )
        }

        // 3. Route Bluetooth SCO microphone only if explicitly enabled in preferences
        val forceSco = com.myvu.client.core.Prefs.isGeminiForceScoEnabled(appContext)
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (forceSco && am != null) {
            try {
                // Cancel previous release timer if user triggered again
                scoReleaseRunnable?.let { audioHandler.removeCallbacks(it) }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val btScoDevice = am.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    }
                    if (btScoDevice != null) {
                        am.mode = AudioManager.MODE_IN_COMMUNICATION
                        am.setCommunicationDevice(btScoDevice)
                        LogBus.log("Set communication device to Bluetooth SCO (${btScoDevice.productName}) for Gemini (isLive=$isLive)")
                    } else {
                        LogBus.log("No Bluetooth SCO communication device available for Gemini; keeping system default")
                    }
                } else {
                    @Suppress("DEPRECATION")
                    if (!am.isBluetoothScoOn) {
                        if (am.mode != AudioManager.MODE_NORMAL) {
                            am.mode = AudioManager.MODE_NORMAL
                        }
                        am.startBluetoothSco()
                        am.isBluetoothScoOn = true
                        LogBus.log("Started legacy Bluetooth SCO for Gemini (isLive=$isLive)")
                    }
                }

                // If isLive is true: Keep communication device active continuously so entire conversation uses glasses mic!
                // If isLive is false: Give user 8.5s capture window to ask question, then restore A2DP media channel for Gemini's voice reply.
                if (!isLive) {
                    val release = Runnable {
                        releaseBluetoothSco(appContext)
                    }
                    scoReleaseRunnable = release
                    audioHandler.postDelayed(release, GEMINI_SCO_CAPTURE_WINDOW_MS)
                } else {
                    LogBus.log("Gemini Live active: Bluetooth SCO communication channel active (safety watchdog 5m)")
                    val safetyRelease = Runnable {
                        LogBus.log("Gemini Live: Safety timeout reached (5m); releasing Bluetooth SCO channel to preserve battery")
                        releaseBluetoothSco(appContext)
                    }
                    scoReleaseRunnable = safetyRelease
                    audioHandler.postDelayed(safetyRelease, GEMINI_LIVE_MAX_SCO_DURATION_MS)
                }
            } catch (e: Exception) {
                LogBus.warn("Could not route Bluetooth SCO for Gemini: ${e.message}")
            }
        } else {
            LogBus.log("Using native system Bluetooth routing for Gemini (SCO force disabled, preserving SPP stability)")
        }

        // 4. If isLive: First invoke Phone Assistant via media key & voice command (proven to open overlay & mic instantly)
        if (isLive) {
            try {
                if (am != null) {
                    val now = SystemClock.uptimeMillis()
                    val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, 0)
                    val up = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOICE_ASSIST, 0)
                    am.dispatchMediaKeyEvent(down)
                    am.dispatchMediaKeyEvent(up)
                    LogBus.log("Dispatched KEYCODE_VOICE_ASSIST for Gemini Live (Phone Assistant overlay trigger)")
                }
            } catch (e: Exception) {
                LogBus.warn("Could not dispatch KEYCODE_VOICE_ASSIST for Gemini Live: ${e.message}")
            }

            try {
                val voiceIntent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                if (voiceIntent.resolveActivity(appContext.packageManager) != null) {
                    com.myvu.client.ui.SendTrampolineActivity.launchWithKeyguardDismiss(appContext, voiceIntent)
                    LogBus.log("Launched ACTION_VOICE_COMMAND for Gemini Live")
                    return
                }
            } catch (e: Exception) {
                LogBus.warn("Could not launch ACTION_VOICE_COMMAND for Gemini Live: ${e.message}")
            }
        }

        // 5. Primary: Launch Gemini App (com.google.android.apps.bard) directly over keyguard
        try {
            val bardLaunchIntent = appContext.packageManager.getLaunchIntentForPackage("com.google.android.apps.bard")
            if (bardLaunchIntent != null) {
                bardLaunchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
                com.myvu.client.ui.SendTrampolineActivity.launchWithKeyguardDismiss(appContext, bardLaunchIntent)
                LogBus.log("Launched Gemini app (com.google.android.apps.bard) via SendTrampolineActivity (isLive=$isLive)")
                return
            }
        } catch (e: Exception) {
            LogBus.warn("Could not launch com.google.android.apps.bard: ${e.message}")
        }

        // 6. Fallback: System Assist intent (triggers Gemini assistant overlay if set as default assistant)
        try {
            val assistIntent = Intent(Intent.ACTION_ASSIST).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (assistIntent.resolveActivity(appContext.packageManager) != null) {
                com.myvu.client.ui.SendTrampolineActivity.launchWithKeyguardDismiss(appContext, assistIntent)
                LogBus.log("Launched assistant via ACTION_ASSIST (fallback)")
                return
            }
        } catch (e: Exception) {
            LogBus.warn("Could not launch ACTION_ASSIST: ${e.message}")
        }

        // 6. Fallback: Hands-Free Voice Search over keyguard
        try {
            val voiceSearchIntent = Intent(android.speech.RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_SECURE, true)
                putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 2500L)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (voiceSearchIntent.resolveActivity(appContext.packageManager) != null) {
                com.myvu.client.ui.SendTrampolineActivity.launchWithKeyguardDismiss(appContext, voiceSearchIntent)
                LogBus.log("Launched hands-free search via SendTrampolineActivity (fallback)")
                return
            }
        } catch (e: Exception) {
            LogBus.warn("Could not launch ACTION_VOICE_SEARCH_HANDS_FREE: ${e.message}")
        }

        // 7. Generic ACTION_VOICE_COMMAND fallback only if Gemini is not installed
        try {
            val genericVoiceIntent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (genericVoiceIntent.resolveActivity(appContext.packageManager) != null) {
                com.myvu.client.ui.SendTrampolineActivity.launchWithKeyguardDismiss(appContext, genericVoiceIntent)
                LogBus.log("Launched generic ACTION_VOICE_COMMAND (fallback)")
            }
        } catch (e: Exception) {
            LogBus.warn("Could not launch generic ACTION_VOICE_COMMAND: ${e.message}")
        }
    }

    @JvmStatic
    fun launchPhoneAssistant(context: Context?) {
        if (context == null) return
        val appContext = context.applicationContext
        try {
            com.myvu.client.core.LockScreenHelper.wakeUpScreen(
                appContext,
                "MYVU:PhoneVoiceAssistant",
                15000L
            )
        } catch (e: Exception) {
            LogBus.warn("Could not wake up screen for phone assistant: ${e.message}")
        }

        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            if (am != null) {
                val now = SystemClock.uptimeMillis()
                val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, 0)
                val up = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOICE_ASSIST, 0)
                am.dispatchMediaKeyEvent(down)
                am.dispatchMediaKeyEvent(up)
                LogBus.log("Dispatched KEYCODE_VOICE_ASSIST for Phone Assistant")
            }
        } catch (e: Exception) {
            LogBus.warn("Could not dispatch KEYCODE_VOICE_ASSIST: ${e.message}")
        }

        try {
            val voiceIntent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (voiceIntent.resolveActivity(appContext.packageManager) != null) {
                com.myvu.client.ui.SendTrampolineActivity.launchWithKeyguardDismiss(appContext, voiceIntent)
                LogBus.log("Launched Phone Assistant via ACTION_VOICE_COMMAND")
                return
            }
        } catch (e: Exception) {
            LogBus.warn("Could not launch ACTION_VOICE_COMMAND for Phone Assistant: ${e.message}")
        }

        try {
            val assistIntent = Intent(Intent.ACTION_ASSIST).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (assistIntent.resolveActivity(appContext.packageManager) != null) {
                com.myvu.client.ui.SendTrampolineActivity.launchWithKeyguardDismiss(appContext, assistIntent)
                LogBus.log("Launched Phone Assistant via ACTION_ASSIST")
            }
        } catch (e: Exception) {
            LogBus.warn("Could not launch ACTION_ASSIST for Phone Assistant: ${e.message}")
        }
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
