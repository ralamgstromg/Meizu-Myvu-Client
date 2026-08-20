package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.LogBus
import java.io.File
import java.io.IOException

data class WhisperModelOption(
    val id: String,
    val name: String,
    val downloadUrl: String,
    val fileName: String,
    val sizeBytes: Long
)

class WhisperLocalClient(
    private val context: Context,
    val modelOption: WhisperModelOption = DEFAULT_OPTION,
    private val inferenceRunner: WhisperInferenceRunner? = null
) {

    fun interface WhisperInferenceRunner {
        @Throws(Exception::class)
        fun transcribe(
            modelFile: File,
            pcm: ByteArray,
            sampleRate: Int,
            channels: Int,
            language: String
        ): String
    }

    @Throws(IOException::class)
    fun transcribe(
        pcm: ByteArray,
        sampleRate: Int,
        channels: Int,
        language: String = java.util.Locale.getDefault().language
    ): String {
        if (!modelFile.exists() || modelFile.length() == 0L) {
            if (inferenceRunner == null) {
                throw IOException("Modelo Whisper on-device no descargado (${modelOption.fileName})")
            }
        }

        if (modelFile.exists() && modelFile.length() < MIN_MODEL_SIZE_BYTES) {
            if (inferenceRunner == null) {
                throw IOException("Archivo de modelo Whisper incompleto o corrupto (${modelFile.length()} bytes, esperado ~${modelOption.sizeBytes} bytes)")
            }
        }

        if (pcm.isEmpty()) {
            return ""
        }

        val regionalTag = java.util.Locale.getDefault().toLanguageTag()
        LogBus.log("AI_WHISPER_LOCAL_START bytes=${pcm.size} rate=$sampleRate channels=$channels model=${modelOption.fileName} lang=$language regional=$regionalTag")

        try {
            if (inferenceRunner != null) {
                val text = inferenceRunner.transcribe(modelFile, pcm, sampleRate, channels, language)
                if (text.isBlank()) {
                    throw IOException("Whisper retornó una transcripción vacía")
                }
                return text.trim()
            }

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

    companion object {
        const val DEFAULT_LOCAL_STT_ENDPOINT = "http://127.0.0.1:8181/v1/audio/transcriptions"
        val WHISPER_LARGE_V3_TURBO_I4 = WhisperModelOption(
            id = "whisper-large-v3-turbo-i4",
            name = "Whisper Large v3 Turbo (INT4 LiteRT ~721MB)",
            downloadUrl = "https://huggingface.co/litert-community/whisper-large-v3-turbo/resolve/main/whisper_large_v3_turbo_30s_i4.tflite",
            fileName = "whisper_large_v3_turbo_30s_i4.tflite",
            sizeBytes = 721_000_000L
        )

        val WHISPER_TINY_ACFT = WhisperModelOption(
            id = "whisper-tiny-acft",
            name = "Whisper Tiny ACFT (LiteRT ~75MB)",
            downloadUrl = "https://huggingface.co/litert-community/whisper-acft/resolve/main/whisper-tiny-acft.tflite",
            fileName = "whisper-tiny-acft.tflite",
            sizeBytes = 75_000_000L
        )

        val OPTIONS = listOf(WHISPER_LARGE_V3_TURBO_I4, WHISPER_TINY_ACFT)
        val DEFAULT_OPTION = WHISPER_LARGE_V3_TURBO_I4

        const val MIN_MODEL_SIZE_BYTES = 10_000_000L

        fun findOption(id: String?): WhisperModelOption {
            return OPTIONS.firstOrNull { it.id == id } ?: DEFAULT_OPTION
        }

        fun getModelFile(context: Context, fileName: String): File {
            val dir = File(context.filesDir, "models/stt")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, fileName)
        }
    }

    val modelFile: File get() = getModelFile(context, modelOption.fileName)

    fun isConfigured(): Boolean {
        return (modelFile.exists() && modelFile.length() >= MIN_MODEL_SIZE_BYTES) || inferenceRunner != null
    }

    fun isRunnerAvailable(): Boolean = inferenceRunner != null
}
