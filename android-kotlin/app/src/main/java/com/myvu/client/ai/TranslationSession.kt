package com.myvu.client.ai

import android.content.Context
import com.myvu.client.app.feature.HudMessageQueue
import com.myvu.client.core.LogBus

/** Manages live real-time voice translation pipeline for the MYVU AR HUD. */
class TranslationSession(context: Context?) {

    fun interface TranslationListener {
        fun onTranslationFrame(translatedText: String)
    }

    private val context: Context? = context?.applicationContext
    private val hudQueue = HudMessageQueue()
    private var active = false
    private var targetLanguage = "es"
    private var listener: TranslationListener? = null

    fun setListener(listener: TranslationListener?) {
        this.listener = listener
    }

    fun isActive(): Boolean = active

    fun start(targetLang: String?) {
        this.targetLanguage = targetLang ?: "es"
        this.active = true
        LogBus.log("TranslationSession started (targetLang=$targetLanguage)")
    }

    fun processAudioChunk(pcmData: ByteArray?, recognizedText: String?) {
        if (!active || recognizedText.isNullOrBlank()) return

        hudQueue.enqueue(recognizedText)
        while (hudQueue.hasNext()) {
            val textFrame = hudQueue.pollNext()
            if (textFrame != null) {
                listener?.onTranslationFrame(textFrame)
            }
        }
    }

    fun stop() {
        if (!active) return
        active = false
        hudQueue.clear()
        LogBus.log("TranslationSession stopped")
    }
}
