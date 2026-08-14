package com.myvu.client.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.myvu.client.core.LogBus
import java.util.Locale

class AndroidSpeechRecognizer(context: Context) : AndroidSpeechEngine {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var recognizer: SpeechRecognizer? = null
    private var terminal = false
    private var pendingStart = false
    private var destroyed = false
    private var attempt = 0L
    private var activeAttempt = 0L
    private var activeLanguage: String? = null
    private var candidateLanguages: List<String> = emptyList()
    private var fallbackIndex = 0
    private var startCallbacks: Callbacks? = null

    private data class Callbacks(
        val onPartial: ((String) -> Unit)?,
        val onResult: (String) -> Unit,
        val onError: (Int, String) -> Unit
    )

    override fun start(
        languageTag: String?,
        onPartial: ((String) -> Unit)?,
        onResult: (String) -> Unit,
        onError: (Int, String) -> Unit
    ): Boolean {
        if (destroyed || recognizer != null || pendingStart) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, "Microphone permission is required")
            return false
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError(SpeechRecognizer.ERROR_SERVER, "Android speech service unavailable")
            return false
        }
        val requested = languageTag ?: Locale.getDefault().toLanguageTag()
        startCallbacks = Callbacks(onPartial, onResult, onError)
        candidateLanguages = AndroidSpeechLanguagePolicy.candidates(requested)
        fallbackIndex = 0
        activeLanguage = candidateLanguages.firstOrNull()
        pendingStart = true
        main.post { startAttempt(preferOffline = true) }
        return true
    }

    private fun startAttempt(preferOffline: Boolean) {
        pendingStart = false
        if (destroyed || recognizer != null) return
        val callbacks = startCallbacks ?: return
        val language = activeLanguage ?: Locale.getDefault().toLanguageTag()
        terminal = false
        attempt++
        activeAttempt = attempt
        acquireWakeLock()
        LogBus.log("STT_ANDROID_START attempt=$activeAttempt language=$language preferOffline=$preferOffline")
        val engine = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = engine
        engine.setRecognitionListener(listener(activeAttempt, callbacks))
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, callbacks.onPartial != null)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        try {
            engine.startListening(intent)
        } catch (e: Exception) {
            terminalError(activeAttempt, SpeechRecognizer.ERROR_CLIENT, e.message ?: "Could not start Android speech recognition", callbacks.onError)
        }
    }

    private fun retryLanguage(code: Int): Boolean {
        if (!AndroidSpeechErrorPolicy.isLanguageFallbackError(code)) return false
        if (fallbackIndex + 1 >= candidateLanguages.size) return false
        fallbackIndex++
        activeLanguage = candidateLanguages[fallbackIndex]
        releaseRecognizer()
        LogBus.warn("STT_ANDROID_RETRY language=$activeLanguage preferOffline=false")
        main.postDelayed({ startAttempt(preferOffline = false) }, 150)
        return true
    }

    private fun describeError(code: Int): String = when (code) {
        11 -> "Android speech language unavailable"
        12 -> "Android speech language not supported"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required"
        SpeechRecognizer.ERROR_NETWORK -> "Android speech network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Android speech network timeout"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Android speech recognizer is busy"
        SpeechRecognizer.ERROR_NO_MATCH -> "Android speech returned no text"
        else -> "Android speech recognition error $code"
    }

    private fun logReady() {
        LogBus.log("STT_ANDROID_READY attempt=$activeAttempt language=$activeLanguage")
    }

    private fun logBeginning() {
        LogBus.log("STT_ANDROID_BEGIN attempt=$activeAttempt")
    }

    private fun logEnd() {
        LogBus.log("STT_ANDROID_END attempt=$activeAttempt")
    }

    private fun logError(code: Int) {
        LogBus.warn("STT_ANDROID_ERROR attempt=$activeAttempt code=$code message=${describeError(code)}")
    }

    private fun callbacksFor(attemptId: Long): Callbacks? =
        startCallbacks.takeIf { attemptId == activeAttempt }

    private fun listener(attemptId: Long, callbacks: Callbacks): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = logReady()
        override fun onBeginningOfSpeech() = logBeginning()
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = logEnd()
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
        override fun onPartialResults(results: Bundle?) {
            if (attemptId != activeAttempt) return
            results?.firstText()?.takeIf { it.isNotBlank() }?.let {
                LogBus.log("STT_ANDROID_PARTIAL attempt=$attemptId textLength=${it.length}")
                callbacks.onPartial?.invoke(it)
            }
        }
        override fun onResults(results: Bundle?) {
            val text = results?.firstText().orEmpty().trim()
            if (text.isEmpty()) {
                terminalError(attemptId, SpeechRecognizer.ERROR_NO_MATCH, describeError(SpeechRecognizer.ERROR_NO_MATCH), callbacks.onError)
            } else {
                terminalResult(attemptId, text, callbacks.onResult)
            }
        }
        override fun onError(error: Int) {
            if (attemptId != activeAttempt || terminal) return
            logError(error)
            if (!retryLanguage(error)) {
                terminalError(attemptId, error, describeError(error), callbacks.onError)
            }
        }
    }

    private fun terminalResult(attemptId: Long, text: String, callback: (String) -> Unit) {
        main.post {
            if (attemptId != activeAttempt || terminal || destroyed) return@post
            terminal = true
            LogBus.log("STT_ANDROID_RESULT attempt=$attemptId textLength=${text.length}")
            callback(text)
            releaseRecognizer()
        }
    }

    private fun terminalError(attemptId: Long, code: Int, message: String, callback: (Int, String) -> Unit) {
        main.post {
            if (attemptId != activeAttempt || terminal || destroyed) return@post
            terminal = true
            LogBus.warn(message)
            callback(code, message)
            releaseRecognizer()
        }
    }

    private fun Bundle.firstText(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "myvu:stt_wakelock")
        }
        try {
            if (wakeLock?.isHeld == false) wakeLock?.acquire(30_000L)
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun releaseRecognizer() {
        releaseWakeLock()
        recognizer?.setRecognitionListener(null)
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    override fun stop() {
        main.post {
            try { recognizer?.stopListening() } catch (_: Exception) {}
        }
    }

    override fun cancel() {
        main.post {
            terminal = true
            startCallbacks = null
            try { recognizer?.cancel() } catch (_: Exception) {}
            releaseRecognizer()
        }
    }

    override fun destroy() {
        main.post {
            if (destroyed) return@post
            destroyed = true
            terminal = true
            startCallbacks = null
            releaseRecognizer()
        }
    }
}
