package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.LogBus
import java.io.IOException

/**
 * Cliente para inferencia de voz a texto (STT) usando el servidor local en puerto 8181.
 */
class WhisperLocalClient(
    private val context: Context
) {

    @Throws(IOException::class)
    fun transcribe(
        pcm: ByteArray,
        sampleRate: Int,
        channels: Int,
        language: String = java.util.Locale.getDefault().language
    ): String {
        if (pcm.isEmpty()) {
            return ""
        }

        val regionalTag = java.util.Locale.getDefault().toLanguageTag()
        LogBus.log("AI_WHISPER_LOCAL_START bytes=${pcm.size} rate=$sampleRate channels=$channels lang=$language regional=$regionalTag")

        try {
            return executeHttpLocalInference(pcm, sampleRate, channels, language)
        } catch (e: Throwable) {
            LogBus.error("AI_WHISPER_LOCAL_ERROR: ${e.message}", e)
            throw IOException("Error en inferencia local Whisper (${e.message}). Activando fallback.", e)
        }
    }

    @Throws(Exception::class)
    private fun executeHttpLocalInference(
        pcm: ByteArray,
        sampleRate: Int,
        channels: Int,
        language: String
    ): String {
        val endpoint = com.myvu.client.core.Prefs.sttEndpoint(context, "local").ifBlank { DEFAULT_LOCAL_STT_ENDPOINT }
        val apiKey = com.myvu.client.core.Prefs.sttApiKey(context, "local")
        val model = com.myvu.client.core.Prefs.sttModel(context, "local").ifBlank { "whisper" }

        val client = OpenAiTranscriptionClient(
            endpoint = endpoint,
            model = model,
            apiKey = apiKey,
            serviceLabel = "Whisper Local STT (Port 8181)",
            ignoreSsl = true,
            customLanguage = language
        )

        LogBus.log("AI_WHISPER_LOCAL_HTTP_ATTEMPT: Transcribiendo audio en servidor local Whisper ($endpoint)...")
        return client.transcribe(pcm, sampleRate, channels)
    }

    fun isConfigured(): Boolean = true

    companion object {
        const val DEFAULT_LOCAL_STT_ENDPOINT = "http://127.0.0.1:8181/v1/audio/transcriptions"
    }
}
