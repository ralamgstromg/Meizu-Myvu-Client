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
    private val network: ExecutorService = Executors.newSingleThreadExecutor()
    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var mediaFile: File? = null
    private var ready = false
    private var pending: Callback? = null
    private var pendingText: String? = null
    private var requestGeneration = 0

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
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    flushPending(true)
                }

                override fun onError(utteranceId: String?) {
                    LogBus.warn("text-to-speech failed for $utteranceId")
                    flushPending(false)
                }
            })
            if (pendingText != null) {
                val text = pendingText!!
                pendingText = null
                speak(text, pending)
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

        stop()
        pending = cb
        requestGeneration++
        val gen = requestGeneration

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
        val id = UUID.randomUUID().toString()
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result != TextToSpeech.SUCCESS) {
            LogBus.warn("tts.speak returned $result")
            flushPending(false)
        }
    }

    private fun playWavBytes(wav: ByteArray) {
        try {
            cleanupMediaPlayer()
            val temp = File.createTempFile("tts_", ".wav", context.cacheDir)
            mediaFile = temp
            FileOutputStream(temp).use { out -> out.write(wav) }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(temp.absolutePath)
                setOnCompletionListener {
                    cleanupMediaPlayer()
                    flushPending(true)
                }
                setOnErrorListener { _, what, extra ->
                    LogBus.warn("MediaPlayer error ($what, $extra)")
                    cleanupMediaPlayer()
                    flushPending(false)
                    true
                }
                prepare()
                start()
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

    fun stop() {
        requestGeneration++
        cleanupMediaPlayer()
        if (tts != null && ready) {
            try {
                tts?.stop()
            } catch (ignored: Exception) {
            }
        }
        flushPending(false)
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
