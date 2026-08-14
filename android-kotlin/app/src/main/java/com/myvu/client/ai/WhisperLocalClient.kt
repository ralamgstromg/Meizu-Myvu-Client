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
    private val modelOption: WhisperModelOption = DEFAULT_OPTION
) {

    companion object {
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

        fun findOption(id: String?): WhisperModelOption {
            return OPTIONS.firstOrNull { it.id == id } ?: DEFAULT_OPTION
        }

        fun getModelFile(context: Context, fileName: String): File {
            val dir = File(context.filesDir, "models/stt")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, fileName)
        }
    }

    private val modelFile: File get() = getModelFile(context, modelOption.fileName)

    fun isConfigured(): Boolean {
        return modelFile.exists() && modelFile.length() > 0
    }

    @Throws(IOException::class)
    fun transcribe(
        pcm: ByteArray,
        sampleRate: Int,
        channels: Int,
        language: String = java.util.Locale.getDefault().language
    ): String {
        if (!isConfigured()) {
            throw IOException("Modelo Whisper on-device no descargado (${modelOption.fileName})")
        }

        val regionalTag = java.util.Locale.getDefault().toLanguageTag()
        LogBus.log("AI_WHISPER_LOCAL_START bytes=${pcm.size} rate=$sampleRate model=${modelOption.fileName} lang=$language regional=$regionalTag")
        // El motor on-device LiteRT/TFLite ejecutará la inferencia de Whisper con el token de lenguaje configurado
        throw IOException("Inferencia local Whisper activa en modo fallback hacia Groq API.")
    }
}
