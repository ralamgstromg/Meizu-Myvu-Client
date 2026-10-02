package com.myvu.client.core

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Text-to-Speech Engine for spoken responses and notifications via connected Bluetooth headphones or speaker.
 */
object TextToSpeechHelper : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var appContext: Context? = null
    private var isInitialized = false
    private val pendingUtterances = mutableListOf<String>()

    fun isReady(): Boolean = isInitialized && tts != null

    /** True while an utterance is playing or queued. */
    fun isSpeaking(): Boolean = try {
        tts?.isSpeaking == true
    } catch (e: Exception) {
        false
    }

    /**
     * Remembers the application context without binding the engine, so later
     * speak() calls without a context (chat, headphone gestures) can start it.
     * Called from MyApp.onCreate().
     */
    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun init(context: Context, onReady: (() -> Unit)? = null) {
        appContext = context.applicationContext
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        } else if (isInitialized) {
            onReady?.invoke()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("es", "CO"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("es", "ES"))
            }
            appContext?.let { tts?.setSpeechRate(Prefs.ttsSpeechRate(it)) }
            tts?.setPitch(1.0f)
            isInitialized = true
            LogBus.log("TextToSpeechHelper -> TTS Engine initialized successfully")

            // Replay queued utterances in order: the first flushes, the rest queue behind it.
            val queued = synchronized(pendingUtterances) {
                pendingUtterances.toList().also { pendingUtterances.clear() }
            }
            queued.forEachIndexed { i, text ->
                speak(text, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD)
            }
        } else {
            LogBus.warn("TextToSpeechHelper -> Failed to initialize TTS engine (status: $status) -- will retry on next request")
            // Release the failed engine so the next speak() binds a new one instead of queueing forever.
            runCatching { tts?.shutdown() }
            tts = null
            isInitialized = false
            synchronized(pendingUtterances) { pendingUtterances.clear() }
        }
    }

    /**
     * Speaks text aloud through the current active audio device (e.g. Bluetooth headphones).
     * If context is provided and TTS is not yet instantiated, auto-initializes the engine.
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH, context: Context? = null) {
        if (text.isBlank()) return

        if (tts == null) {
            val ctx = context ?: appContext
            if (ctx == null) {
                LogBus.warn("TextToSpeechHelper -> speak() before attach()/init(): dropped")
                return
            }
            init(ctx)
        }

        if (!isInitialized || tts == null) {
            synchronized(pendingUtterances) {
                // Raw text: normalized once when actually spoken.
                pendingUtterances.add(text)
            }
            return
        }

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
        }
        val utteranceId = "tts_${System.currentTimeMillis()}"
        val result = tts?.speak(com.myvu.client.core.locale.SpeechNormalizer.normalize(text), queueMode, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) LogBus.warn("TextToSpeechHelper -> speak returned $result")
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            LogBus.warn("TextToSpeechHelper: stop failed: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            LogBus.warn("TextToSpeechHelper: shutdown failed: ${e.message}")
        }
    }
}
