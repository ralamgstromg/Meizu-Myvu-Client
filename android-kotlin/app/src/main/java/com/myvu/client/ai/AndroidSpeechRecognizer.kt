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
    private var recognizer: SpeechRecognizer? = null
    private var terminal = false
    private var pendingStart = false
    private var destroyed = false

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
        pendingStart = true
        main.post {
            pendingStart = false
            if (destroyed || recognizer != null) return@post
            terminal = false
            val engine = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = engine
            engine.setRecognitionListener(listener(onPartial, onResult, onError))
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, onPartial != null)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag ?: Locale.getDefault().toLanguageTag())
            }
            try {
                engine.startListening(intent)
            } catch (e: Exception) {
                terminalError(SpeechRecognizer.ERROR_CLIENT, e.message ?: "Could not start Android speech recognition", onError)
            }
        }
        return true
    }

    private fun listener(
        onPartial: ((String) -> Unit)?,
        onResult: (String) -> Unit,
        onError: (Int, String) -> Unit
    ): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
        override fun onPartialResults(results: Bundle?) {
            results?.firstText()?.takeIf { it.isNotBlank() }?.let { onPartial?.invoke(it) }
        }
        override fun onResults(results: Bundle?) {
            val text = results?.firstText().orEmpty().trim()
            if (text.isEmpty()) {
                terminalError(SpeechRecognizer.ERROR_NO_MATCH, "Android speech returned no text", onError)
            } else {
                terminalResult(text, onResult)
            }
        }
        override fun onError(error: Int) {
            terminalError(error, "Android speech recognition error $error", onError)
        }
    }

    private fun Bundle.firstText(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun terminalResult(text: String, callback: (String) -> Unit) {
        main.post {
            if (terminal || destroyed) return@post
            terminal = true
            callback(text)
            releaseRecognizer()
        }
    }

    private fun terminalError(code: Int, message: String, callback: (Int, String) -> Unit) {
        main.post {
            if (terminal || destroyed) return@post
            terminal = true
            LogBus.warn(message)
            callback(code, message)
            releaseRecognizer()
        }
    }

    override fun stop() {
        main.post {
            try { recognizer?.stopListening() } catch (_: Exception) {}
        }
    }

    override fun cancel() {
        main.post {
            terminal = true
            try { recognizer?.cancel() } catch (_: Exception) {}
            releaseRecognizer()
        }
    }

    override fun destroy() {
        main.post {
            if (destroyed) return@post
            destroyed = true
            terminal = true
            releaseRecognizer()
        }
    }

    private fun releaseRecognizer() {
        recognizer?.setRecognitionListener(null)
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }
}
