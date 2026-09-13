package com.myvu.client.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import com.myvu.client.app.feature.TouchGestureManager
import com.myvu.client.core.LockScreenHelper
import com.myvu.client.core.LogBus
import com.myvu.client.core.TextToSpeechHelper
import com.myvu.client.data.BluetoothDeviceEntity
import com.myvu.client.data.BluetoothDeviceType
import com.myvu.client.database.AppDatabase
import com.myvu.client.media.MediaPlaybackHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles button gestures (1 Tap, 2 Taps, 3 Taps, Long Press) from connected Bluetooth Headphones/Earbuds.
 */
class HeadphoneGestureManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db = AppDatabase.getInstance(context)
    private val dao = db.bluetoothDeviceDao()

    private var lastTapTime = 0L
    private var tapCount = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val tapEvaluatorRunnable = Runnable {
        val count = tapCount
        tapCount = 0
        scope.launch {
            dispatchTapGesture(count)
        }
    }

    /**
     * Called when a media button (e.g. KEYCODE_HEADSETHOOK or KEYCODE_MEDIA_PLAY_PAUSE) is pressed on headphones.
     */
    fun onHeadsetButtonEvent(keyCode: Int, action: Int): Boolean {
        if (action != KeyEvent.ACTION_DOWN) return false

        when (keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                val now = SystemClock.uptimeMillis()
                handler.removeCallbacks(tapEvaluatorRunnable)

                if (now - lastTapTime < 500L) {
                    tapCount++
                } else {
                    tapCount = 1
                }
                lastTapTime = now

                // Wait 400ms to see if subsequent taps arrive
                handler.postDelayed(tapEvaluatorRunnable, 400L)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                scope.launch { executeAction("MEDIA_NEXT", "Siguiente pista") }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                scope.launch { executeAction("MEDIA_PREV", "Pista anterior") }
                return true
            }
            KeyEvent.KEYCODE_VOICE_ASSIST,
            KeyEvent.KEYCODE_ASSIST -> {
                scope.launch { dispatchLongPressGesture() }
                return true
            }
        }
        return false
    }

    private suspend fun getActiveHeadphone(): BluetoothDeviceEntity? {
        return dao.getConnectedDeviceByType(BluetoothDeviceType.HEADPHONES.name)
            ?: dao.getAllDevices().find { it.deviceType == BluetoothDeviceType.HEADPHONES.name && it.isConnected }
            ?: dao.getAllDevices().find { it.deviceType == BluetoothDeviceType.HEADPHONES.name }
            ?: dao.getAllDevices().find { it.deviceType == BluetoothDeviceType.GENERIC.name }
    }

    private suspend fun dispatchTapGesture(count: Int) {
        val activeHeadphone = getActiveHeadphone()

        val actionName = when (count) {
            1 -> activeHeadphone?.tap1Action ?: "MEDIA_PLAY_PAUSE"
            2 -> activeHeadphone?.tap2Action ?: "LAUNCH_GEMINI"
            3 -> activeHeadphone?.tap3Action ?: "LAUNCH_PHONE_ASSISTANT"
            else -> activeHeadphone?.tap3Action ?: "LAUNCH_PHONE_ASSISTANT"
        }

        LogBus.log("HeadphoneGestureManager -> Detected $count tap(s) -> Executing: $actionName")
        executeAction(actionName, "Gesto $count toque(s)")
    }

    private suspend fun dispatchLongPressGesture() {
        val activeHeadphone = getActiveHeadphone()
        val actionName = activeHeadphone?.longPressAction ?: "CREATE_AI_NOTE"
        LogBus.log("HeadphoneGestureManager -> Detected Long Press -> Executing: $actionName")
        executeAction(actionName, "Pulsación larga")
    }

    suspend fun executeAction(actionName: String, sourceDesc: String) {
        val activeHeadphone = getActiveHeadphone()
        val ttsEnabled = activeHeadphone?.ttsEnabled ?: true

        when (actionName) {
            "VOICE_AGENT_AURA" -> {
                if (ttsEnabled) TextToSpeechHelper.speak("Te escucho")
                val intent = Intent(context, com.myvu.client.ui.chat.ChatActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(com.myvu.client.ui.chat.ChatActivity.EXTRA_AUTO_START_STT, true)
                }
                context.startActivity(intent)
            }
            "HUD_DASHBOARD" -> {
                // No HUD on headphones alone
            }
            "LAUNCH_GEMINI" -> {
                if (ttsEnabled) TextToSpeechHelper.speak("Abriendo Gemini")
                TouchGestureManager.launchGeminiAssistant(context, isLive = false)
            }
            "LAUNCH_GEMINI_LIVE" -> {
                if (ttsEnabled) TextToSpeechHelper.speak("Iniciando Gemini Live")
                TouchGestureManager.launchGeminiAssistant(context, isLive = true)
            }
            "LAUNCH_PHONE_ASSISTANT" -> {
                if (ttsEnabled) TextToSpeechHelper.speak("Iniciando asistente")
                TouchGestureManager.launchPhoneAssistant(context)
            }
            "CREATE_AI_NOTE" -> {
                if (ttsEnabled) TextToSpeechHelper.speak("Crear nota con IA")
                val intent = Intent(context, com.myvu.client.ui.VoiceRecorderActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("AUTO_START_RECORDING", true)
                }
                context.startActivity(intent)
            }
            "READ_UNREAD_NOTIFICATIONS" -> {
                val summary = MirrorNotificationListener.getUnreadSummary(null)
                if (summary.isBlank() || summary.contains("No tienes notificaciones")) {
                    TextToSpeechHelper.speak("No tienes notificaciones pendientes.")
                } else {
                    TextToSpeechHelper.speak(summary)
                }
            }
            "DAILY_BRIEFING" -> {
                TextToSpeechHelper.speak("Generando tu resumen del día...")
                val briefing = com.myvu.client.ai.DailyBriefingService.generateBriefingText(context)
                TextToSpeechHelper.speak(briefing)
            }
            "MEDIA_PLAY_PAUSE" -> {
                MediaPlaybackHelper.sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
            "MEDIA_NEXT" -> {
                MediaPlaybackHelper.sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            "MEDIA_PREV" -> {
                MediaPlaybackHelper.sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            "NONE" -> {
                // No action
            }
            else -> {
                if (actionName.startsWith("app:")) {
                    val pkg = actionName.removePrefix("app:")
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                    }
                }
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: HeadphoneGestureManager? = null

        fun getInstance(context: Context): HeadphoneGestureManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HeadphoneGestureManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
