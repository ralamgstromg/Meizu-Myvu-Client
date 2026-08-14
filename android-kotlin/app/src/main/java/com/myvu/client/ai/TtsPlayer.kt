package com.myvu.client.ai

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Speaks the assistant's answer. */
class TtsPlayer(private val context: Context) {

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

    fun init() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) {
                LogBus.warn("text-to-speech unavailable (status $status)")
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
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId == activeUtteranceId) {
                        LogBus.log("TTS_PLAYBACK_STARTED generation=$activeCallbackGeneration")
                    }
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId != activeUtteranceId) return
                    LogBus.log("TTS_PLAYBACK_FINISHED generation=$activeCallbackGeneration success=true")
                    flushPending(true)
                }

                override fun onError(utteranceId: String?) {
                    if (utteranceId != activeUtteranceId) return
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
                        val audio = client.synthesize(text)
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

    private fun speakSystemTts(text: String) {
        if (!ready) {
            LogBus.warn("TTS requested before init completed")
            flushPending(false)
            return
        }
        val generation = activeCallbackGeneration
        val id = UUID.randomUUID().toString()
        activeUtteranceId = id
        LogBus.log("TTS_REQUEST_STARTED generation=$generation textLength=${text.length} provider=system")
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result != TextToSpeech.SUCCESS) {
            LogBus.warn("tts.speak returned $result")
            flushPending(false)
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
