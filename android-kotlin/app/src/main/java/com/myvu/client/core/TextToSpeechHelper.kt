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
    private val lock = Any()
    private val pendingUtterances = mutableListOf<PendingUtterance>()
    private var utteranceSeq = 0L

    /** Utterances that hold a [SpeechAudioSession] reference. Removal is the only way to release, so each ends once. */
    private val duckedIds = mutableSetOf<String>()

    private class PendingUtterance(val id: String, val text: String)

    /** Engine callbacks for every utterance; the last one of a ducked group restores the audio session. */
    internal val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = finish(utteranceId)
        override fun onError(utteranceId: String?) = finish(utteranceId)
        override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
    }

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
            tts?.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            appContext?.let { tts?.setSpeechRate(Prefs.ttsSpeechRate(it)) }
            tts?.setPitch(1.0f)
            tts?.setOnUtteranceProgressListener(progressListener)
            isInitialized = true
            LogBus.log("TextToSpeechHelper -> TTS Engine initialized successfully")

            // Replay queued utterances in order: the first flushes, the rest queue behind it.
            val queued = synchronized(lock) {
                pendingUtterances.toList().also { pendingUtterances.clear() }
            }
            queued.forEachIndexed { i, p ->
                playNow(p.id, p.text, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD)
            }
        } else {
            LogBus.warn("TextToSpeechHelper -> Failed to initialize TTS engine (status: $status) -- will retry on next request")
            // Release the failed engine so the next speak() binds a new one instead of queueing forever.
            runCatching { tts?.shutdown() }
            tts = null
            isInitialized = false
            val dropped = synchronized(lock) {
                pendingUtterances.toList().also { pendingUtterances.clear() }
            }
            dropped.forEach { finish(it.id) }
        }
    }

    /**
     * Speaks text aloud through the current active audio device (e.g. Bluetooth headphones).
     * If context is provided and TTS is not yet instantiated, auto-initializes the engine.
     * [duck] lowers other audio and raises the music level for the duration of this utterance.
     */
    fun speak(
        text: String,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        context: Context? = null,
        duck: Boolean = false,
    ) {
        if (text.isBlank()) return
        RecentSpeech.remember(text)

        if (tts == null) {
            val ctx = context ?: appContext
            if (ctx == null) {
                LogBus.warn("TextToSpeechHelper -> speak() before attach()/init(): dropped")
                return
            }
            init(ctx)
        }

        val id = synchronized(lock) { "tts_${++utteranceSeq}" }
        if (duck) acquireDucking(id, text)

        if (!isInitialized || tts == null) {
            synchronized(lock) {
                // Raw text: normalized once when actually spoken.
                pendingUtterances.add(PendingUtterance(id, text))
            }
            return
        }

        playNow(id, text, queueMode)
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            LogBus.warn("TextToSpeechHelper: stop failed: ${e.message}")
        }
        // Engine callbacks may not arrive for queued utterances after stop(); release the ones still playing.
        val stopped = synchronized(lock) {
            val pendingIds = pendingUtterances.map { it.id }.toSet()
            val ids = duckedIds.filter { it !in pendingIds }
            duckedIds.removeAll(ids.toSet())
            ids
        }
        repeat(stopped.size) { SpeechAudioSession.release() }
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
        val ids = synchronized(lock) {
            pendingUtterances.clear()
            duckedIds.toList().also { duckedIds.clear() }
        }
        repeat(ids.size) { SpeechAudioSession.release() }
    }

    private fun acquireDucking(id: String, text: String) {
        val app = appContext ?: return
        synchronized(lock) { duckedIds.add(id) }
        val words = text.trim().split(Regex("\\s+")).size
        SpeechAudioSession.acquire(app, holdMs = words * WORD_MS + HOLD_MARGIN_MS)
    }

    private fun playNow(id: String, text: String, queueMode: Int) {
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
        }
        val result = tts?.speak(com.myvu.client.core.locale.SpeechNormalizer.normalize(text), queueMode, params, id)
        if (result != TextToSpeech.SUCCESS) {
            LogBus.warn("TextToSpeechHelper -> speak returned $result")
            finish(id)
        }
    }

    /** Releases the audio session for [id] once; repeated engine callbacks for the same id are no-ops. */
    private fun finish(id: String?) {
        if (id == null) return
        val wasDucked = synchronized(lock) { duckedIds.remove(id) }
        if (wasDucked) SpeechAudioSession.release()
    }

    private const val WORD_MS = 450L
    private const val HOLD_MARGIN_MS = 2000L
}
