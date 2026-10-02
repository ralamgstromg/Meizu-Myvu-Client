package com.myvu.client.ai

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.core.locale.SpeechNormalizer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Speaks the assistant's answer. */
class TtsPlayer(private val context: Context) {

    private companion object {
        /** Max chars per queued utterance: about one or two spoken sentences. */
        const val SPEECH_CHUNK_CHARS = 220
    }


    fun interface Callback {
        fun onSpoken(success: Boolean)
    }

    private val main = Handler(Looper.getMainLooper())
    private val network: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "tts-network").apply {
            isDaemon = true
            setUncaughtExceptionHandler { t, e ->
                LogBus.error("Uncaught exception on thread ${t.name}", e)
            }
        }
    }
    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var mediaFile: File? = null
    private var ready = false
    private var pending: Callback? = null
    private var pendingText: String? = null
    private var requestGeneration = 0
    private var callbackGeneration = 0
    private var activeCallbackGeneration = 0
    private var activeUtteranceId: String? = null
    private var activeFirstUtteranceId: String? = null
    private var activeChunkIds: Set<String> = emptySet()

    fun init() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) {
                LogBus.warn("text-to-speech unavailable (status $status) -- will retry on next request")
                // Drop the failed engine so the next speak() binds a fresh one instead of
                // waiting forever on an instance that will never become ready.
                runCatching { tts?.shutdown() }
                tts = null
                pendingText = null
                flushPending(false)
                return@TextToSpeech
            }
            var result = tts?.setLanguage(Locale("es", "CO")) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = tts?.setLanguage(Locale("es")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
            }
            tts?.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            tts?.setSpeechRate(Prefs.ttsSpeechRate(context))
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId == activeFirstUtteranceId) {
                        LogBus.log("TTS_PLAYBACK_STARTED generation=$activeCallbackGeneration")
                    }
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId != activeUtteranceId) return
                    LogBus.log("TTS_PLAYBACK_FINISHED generation=$activeCallbackGeneration success=true")
                    flushPending(true)
                }

                override fun onError(utteranceId: String?) {
                    if (utteranceId == null || utteranceId !in activeChunkIds) return
                    LogBus.warn("text-to-speech failed for $utteranceId")
                    LogBus.log("TTS_PLAYBACK_FINISHED generation=$activeCallbackGeneration success=false")
                    flushPending(false)
                }
            })
            if (pendingText != null) {
                val text = pendingText!!
                val callback = pending
                pendingText = null
                pending = null
                speak(text, callback)
            }
        }
    }

    /** [text] is raw answer text; it is normalized for es-CO speech exactly once, right before synthesis. */
    fun speak(text: String, cb: Callback?) {
        if (tts == null) {
            pendingText = text
            pending = cb
            init()
            return
        }

        stop(notify = false)
        pending = cb
        requestGeneration++
        callbackGeneration++
        val gen = requestGeneration
        val callbackGen = callbackGeneration
        activeCallbackGeneration = callbackGen

        val provider = Prefs.ttsProvider(context)
        if (provider != "system") {
            val endpoint = Prefs.ttsEndpoint(context)
            val apiKey = Prefs.ttsApiKey(context)
            val model = Prefs.ttsModel(context)
            val voice = Prefs.ttsVoice(context)
            if (endpoint.isNotEmpty()) {
                LogBus.log("synthesizing speech via HTTP TTS ($provider)...")
                val client = HttpTtsClient(endpoint, apiKey, model, voice)
                network.execute {
                    try {
                        val audio = client.synthesize(SpeechNormalizer.normalize(text))
                        main.post {
                            if (gen == requestGeneration) playWavBytes(audio)
                        }
                    } catch (e: Exception) {
                        LogBus.warn("HTTP TTS failed (${e.message}) -- falling back to system TTS")
                        main.post {
                            if (gen == requestGeneration) speakSystemTts(text)
                        }
                    }
                }
                return
            }
        }

        speakSystemTts(text)
    }

    private fun speakSystemTts(rawText: String) {
        if (!ready) {
            if (tts != null) {
                // Engine still binding (1-3 s on first use): keep the request; init() replays it.
                LogBus.log("TTS engine not ready yet -- queued until initialization completes")
                pendingText = rawText
                return
            }
            LogBus.warn("TTS requested with no engine")
            flushPending(false)
            return
        }
        val text = SpeechNormalizer.normalize(rawText)
        val generation = activeCallbackGeneration
        // Queue sentence-sized chunks: the engine starts speaking the first one
        // without synthesizing the whole answer. The callback fires on the last.
        val chunks = HudSummary.paginate(text, SPEECH_CHUNK_CHARS).ifEmpty { listOf(text) }
        val ids = chunks.map { UUID.randomUUID().toString() }
        activeFirstUtteranceId = ids.first()
        activeUtteranceId = ids.last()
        activeChunkIds = ids.toSet()
        LogBus.log("TTS_REQUEST_STARTED generation=$generation textLength=${text.length} chunks=${chunks.size} provider=system")
        val params = android.os.Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
        }
        chunks.forEachIndexed { i, chunk ->
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val result = tts?.speak(chunk, mode, params, ids[i])
            if (result != TextToSpeech.SUCCESS) {
                LogBus.warn("tts.speak returned $result for chunk ${i + 1}/${chunks.size}")
                flushPending(false)
                return
            }
        }
    }

    private fun playWavBytes(wav: ByteArray) {
        val generation = activeCallbackGeneration
        LogBus.log("TTS_REQUEST_STARTED generation=$generation textLength=${wav.size} provider=http")
        try {
            cleanupMediaPlayer()
            val temp = File.createTempFile("tts_", ".wav", context.cacheDir)
            mediaFile = temp
            FileOutputStream(temp).use { out -> out.write(wav) }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(temp.absolutePath)
                setOnCompletionListener {
                    cleanupMediaPlayer()
                    LogBus.log("TTS_PLAYBACK_FINISHED generation=$generation success=true")
                    flushPending(true)
                }
                setOnErrorListener { _, what, extra ->
                    LogBus.warn("MediaPlayer error ($what, $extra)")
                    cleanupMediaPlayer()
                    LogBus.log("TTS_PLAYBACK_FINISHED generation=$generation success=false")
                    flushPending(false)
                    true
                }
                prepare()
                start()
                LogBus.log("TTS_PLAYBACK_STARTED generation=$generation")
            }
        } catch (e: IOException) {
            LogBus.warn("could not play HTTP TTS audio: ${e.message}")
            cleanupMediaPlayer()
            flushPending(false)
        }
    }

    private fun cleanupMediaPlayer() {
        mediaPlayer?.run {
            try {
                if (isPlaying) stop()
                reset()
                release()
            } catch (ignored: Exception) {
            }
        }
        mediaPlayer = null
        mediaFile?.run {
            try {
                delete()
            } catch (ignored: Exception) {
            }
        }
        mediaFile = null
    }

    fun stop(notify: Boolean = true) {
        requestGeneration++
        activeUtteranceId = null
        cleanupMediaPlayer()
        if (tts != null && ready) {
            try {
                tts?.stop()
            } catch (ignored: Exception) {
            }
        }
        if (notify) flushPending(false)
    }

    fun shutdown() {
        stop()
        if (tts != null) {
            try {
                tts?.shutdown()
            } catch (ignored: Exception) {
            }
            tts = null
            ready = false
        }
        network.shutdown()
    }

    private fun flushPending(success: Boolean) {
        main.post {
            val cb = pending
            pending = null
            cb?.onSpoken(success)
        }
    }
}
