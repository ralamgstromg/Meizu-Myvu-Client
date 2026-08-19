package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.LogBus
import java.io.File
import java.io.IOException

import com.google.mediapipe.tasks.genai.llminference.LlmInference

data class GemmaModelOption(
    val id: String,
    val name: String,
    val downloadUrl: String,
    val fileName: String,
    val sizeBytes: Long
)

class GemmaLocalClient(
    private val context: Context,
    private val modelOption: GemmaModelOption = DEFAULT_OPTION
) : AiClient {

    companion object {
        val GEMMA_2B_IT_GPU = GemmaModelOption(
            id = "gemma-2b-it-gpu-int4",
            name = "Gemma 2B IT (Google AI Edge GPU ~1.35GB)",
            downloadUrl = "https://huggingface.co/google/gemma-2b-it-tflite/resolve/main/gemma-2b-it-gpu-int4.bin",
            fileName = "gemma-2b-it-gpu-int4.bin",
            sizeBytes = 1_350_000_000L
        )

        val GEMMA_2B_IT_CPU = GemmaModelOption(
            id = "gemma-2b-it-cpu-int4",
            name = "Gemma 2B IT (Google AI Edge CPU ~1.35GB)",
            downloadUrl = "https://huggingface.co/google/gemma-2b-it-tflite/resolve/main/gemma-2b-it-cpu-int4.bin",
            fileName = "gemma-2b-it-cpu-int4.bin",
            sizeBytes = 1_350_000_000L
        )

        val GEMMA_2_2B_IT_GPU = GemmaModelOption(
            id = "gemma-2-2b-it-gpu-int4",
            name = "Gemma 2 2B IT (Google AI Edge GPU ~1.48GB)",
            downloadUrl = "https://huggingface.co/google/gemma-2-2b-it-tflite/resolve/main/gemma-2-2b-it-gpu-int4.bin",
            fileName = "gemma-2-2b-it-gpu-int4.bin",
            sizeBytes = 1_480_000_000L
        )

        val GEMMA_1_1_2B_IT_GPU = GemmaModelOption(
            id = "gemma-1.1-2b-it-gpu-int4",
            name = "Gemma 1.1 2B IT (Google AI Edge GPU ~1.35GB)",
            downloadUrl = "https://huggingface.co/google/gemma-1.1-2b-it-tflite/resolve/main/gemma-1.1-2b-it-gpu-int4.bin",
            fileName = "gemma-1.1-2b-it-gpu-int4.bin",
            sizeBytes = 1_350_000_000L
        )

        val OPTIONS = listOf(
            GEMMA_2B_IT_GPU,
            GEMMA_2B_IT_CPU,
            GEMMA_2_2B_IT_GPU,
            GEMMA_1_1_2B_IT_GPU
        )

        val DEFAULT_OPTION = GEMMA_2B_IT_GPU

        private var cachedEngine: LlmInference? = null
        private var cachedModelPath: String? = null

        fun findOption(id: String?): GemmaModelOption {
            return OPTIONS.firstOrNull { it.id == id } ?: DEFAULT_OPTION
        }

        fun getModelFile(context: Context, fileName: String): File {
            val dir = File(context.filesDir, "models/gemma")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, fileName)
        }
    }

    private val modelFile: File get() = getModelFile(context, modelOption.fileName)

    override fun isConfigured(): Boolean {
        return modelFile.exists() && modelFile.length() > 50_000_000L
    }

    @Synchronized
    private fun getOrInitEngine(): LlmInference {
        val path = modelFile.absolutePath
        if (cachedEngine != null && cachedModelPath == path) {
            return cachedEngine!!
        }

        LogBus.log("AI_GEMMA_NATIVE_INIT path=$path model=${modelOption.id}")
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(512)
            .setMaxTopK(40)
            .build()

        val engine = LlmInference.createFromOptions(context, options)
        cachedEngine = engine
        cachedModelPath = path
        return engine
    }

    @Throws(IOException::class)
    override fun ask(question: String): String {
        if (!isConfigured()) {
            throw IOException("Modelo local Gemma de Google AI Edge no descargado o incompleto (${modelOption.fileName})")
        }

        LogBus.log("AI_GEMMA_LOCAL_START questionLength=${question.length} model=${modelOption.fileName}")
        try {
            val engine = getOrInitEngine()
            val response = engine.generateResponse(question)
            if (response.isNullOrBlank()) {
                throw IOException("MediaPipe LlmInference retornó una respuesta vacía")
            }
            return response.trim()
        } catch (e: Throwable) {
            LogBus.error("AI_GEMMA_NATIVE_ERROR: ${e.message}", e)
            throw IOException("Inferencia nativa Gemma falló (${e.message}). Activando fallback.", e)
        }
    }
}
