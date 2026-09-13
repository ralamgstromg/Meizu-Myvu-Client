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
    private var isInitialized = false
    private val pendingUtterances = mutableListOf<String>()

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("es", "CO"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("es", "ES"))
            }
            tts?.setSpeechRate(1.05f)
            tts?.setPitch(1.0f)
            isInitialized = true
            LogBus.log("TextToSpeechHelper -> TTS Engine initialized successfully")

            synchronized(pendingUtterances) {
                for (text in pendingUtterances) {
                    speak(text)
                }
                pendingUtterances.clear()
            }
        } else {
            LogBus.warn("TextToSpeechHelper -> Failed to initialize TTS engine (status: $status)")
        }
    }

    /**
     * Speaks text aloud through the current active audio device (e.g. Bluetooth headphones).
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (text.isBlank()) return

        if (!isInitialized || tts == null) {
            synchronized(pendingUtterances) {
                pendingUtterances.add(text)
            }
            return
        }

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
        }
        val utteranceId = "tts_${System.currentTimeMillis()}"
        tts?.speak(text, queueMode, params, utteranceId)
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
