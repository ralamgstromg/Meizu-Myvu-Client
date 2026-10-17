package com.myvu.client.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Lowers other audio and raises the music stream while notifications are spoken, then restores both.
 *
 * Reference-counted: the first [acquire] starts the session and the last [release] ends it.
 * The raised level is persisted before it is set, so [restoreAfterCrash] can undo it after a process kill.
 * Skipped entirely while the audio mode is not MODE_NORMAL (SCO / Gemini capture, calls).
 */
object SpeechAudioSession {

    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var refs = 0
    private var deadlineUptime = 0L
    private var focusRequest: AudioFocusRequest? = null
    /** Music level before the boost; -1 when this session did not raise it. */
    private var savedLevel = -1
    /** Level read back after we set it; compared on release to detect a user change. */
    private var raisedLevel = -1

    private val watchdog = Runnable {
        synchronized(lock) {
            if (refs == 0) return@synchronized
            LogBus.warn("SpeechAudioSession: hold expired without end callbacks, forcing restore")
            refs = 0
            endLocked()
        }
    }

    /** Starts (or extends) the session. [holdMs] bounds how long to wait for the matching [release]. */
    fun acquire(context: Context, holdMs: Long) {
        synchronized(lock) {
            val app = context.applicationContext
            appContext = app
            refs++
            if (refs == 1) beginLocked(app)
            val due = SystemClock.uptimeMillis() + holdMs
            if (due > deadlineUptime) {
                deadlineUptime = due
                handler.removeCallbacks(watchdog)
                handler.postAtTime(watchdog, due)
            }
        }
    }

    /** Ends one reference; the last one restores the music level and abandons focus. */
    fun release() {
        synchronized(lock) {
            if (refs == 0) return
            refs--
            if (refs == 0) endLocked()
        }
    }

    /** Restores a music level left raised by a process that died mid-speech. Call at app start. */
    fun restoreAfterCrash(context: Context) {
        val app = context.applicationContext
        val saved = Prefs.speechVolumeSavedLevel(app)
        if (saved < 0) return
        val am = app.getSystemService(AudioManager::class.java)
        if (am != null && am.getStreamVolume(AudioManager.STREAM_MUSIC) == Prefs.speechVolumeRaisedLevel(app)) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0)
            LogBus.log("SpeechAudioSession: restored music volume to $saved after an interrupted read")
        }
        Prefs.clearSpeechVolumeRecovery(app)
    }

    private fun beginLocked(app: Context) {
        val am = app.getSystemService(AudioManager::class.java) ?: return
        if (am.mode != AudioManager.MODE_NORMAL) return

        if (Prefs.notifDuckEnabled(app)) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener({ }, handler)
                .build()
            if (am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                focusRequest = request
            } else {
                // Denied (e.g. a call holds focus): speak without boosting.
                LogBus.log("SpeechAudioSession: audio focus denied, notification spoken without boost")
                return
            }
        }

        if (Prefs.notifBoostEnabled(app)) raiseLocked(app, am)
    }

    private fun raiseLocked(app: Context, am: AudioManager) {
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val target = (max * Prefs.notifBoostPercent(app) / 100).coerceIn(0, max)
        if (target <= current) return

        // Persist first: if the process dies after this line, restoreAfterCrash still has the old level.
        Prefs.setSpeechVolumeRecovery(app, current, target)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        savedLevel = current
        raisedLevel = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    private fun endLocked() {
        handler.removeCallbacks(watchdog)
        deadlineUptime = 0L
        val app = appContext ?: return
        val am = app.getSystemService(AudioManager::class.java)
        if (am != null) {
            if (savedLevel >= 0 && am.getStreamVolume(AudioManager.STREAM_MUSIC) == raisedLevel) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, savedLevel, 0)
            }
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        }
        focusRequest = null
        if (savedLevel >= 0) Prefs.clearSpeechVolumeRecovery(app)
        savedLevel = -1
        raisedLevel = -1
    }
}
