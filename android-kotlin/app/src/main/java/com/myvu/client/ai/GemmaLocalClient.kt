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
        val GEMMA_4_E2B_LITERT = GemmaModelOption(
            id = "gemma-4-e2b-it-litert-lm",
            name = "Gemma 4 E2B IT (LiteRT-LM ~1.12GB)",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            fileName = "gemma-4-E2B-it.litertlm",
            sizeBytes = 1_120_000_000L
        )

        val GEMMA_2B_IT_GPU = GemmaModelOption(
            id = "gemma-2b-it-gpu-int4",
            name = "Gemma 2B IT (MediaPipe GPU ~1.35GB)",
            downloadUrl = "https://huggingface.co/google/gemma-2b-it-tflite/resolve/main/gemma-2b-it-gpu-int4.bin",
            fileName = "gemma-2b-it-gpu-int4.bin",
            sizeBytes = 1_350_000_000L
        )

        val GEMMA_2B_IT_CPU = GemmaModelOption(
            id = "gemma-2b-it-cpu-int4",
            name = "Gemma 2B IT (MediaPipe CPU ~1.35GB)",
            downloadUrl = "https://huggingface.co/google/gemma-2b-it-tflite/resolve/main/gemma-2b-it-cpu-int4.bin",
            fileName = "gemma-2b-it-cpu-int4.bin",
            sizeBytes = 1_350_000_000L
        )

        val OPTIONS = listOf(GEMMA_4_E2B_LITERT, GEMMA_2B_IT_GPU, GEMMA_2B_IT_CPU)
        val DEFAULT_OPTION = GEMMA_4_E2B_LITERT

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
        return modelFile.exists() && modelFile.length() > 0
    }

    @Synchronized
    private fun getOrInitEngine(): LlmInference {
        val path = modelFile.absolutePath
        if (cachedEngine != null && cachedModelPath == path) {
            return cachedEngine!!
        }

        LogBus.log("AI_GEMMA_NATIVE_INIT path=$path")
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(512)
            .build()

        val engine = LlmInference.createFromOptions(context, options)
        cachedEngine = engine
        cachedModelPath = path
        return engine
    }

    @Throws(IOException::class)
    override fun ask(question: String): String {
        if (!isConfigured()) {
            throw IOException("Modelo local Gemma no descargado (${modelOption.fileName})")
        }

        LogBus.log("AI_GEMMA_LOCAL_START questionLength=${question.length} model=${modelOption.fileName}")
        try {
            val engine = getOrInitEngine()
            val response = engine.generateResponse(question)
            if (response.isNullOrBlank()) {
                throw IOException("MediaPipe LlmInference returned blank response")
            }
            return response.trim()
        } catch (e: Throwable) {
            LogBus.error("AI_GEMMA_NATIVE_ERROR: ${e.message}", e)
            throw IOException("Inferencia nativa Gemma falló (${e.message}). Activando fallback.", e)
        }
    }
}
