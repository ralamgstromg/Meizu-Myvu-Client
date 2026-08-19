package com.myvu.client.ai

import android.content.Context
import com.myvu.client.ai.engine.LiteRtLmEngine
import com.myvu.client.ai.engine.MediaPipeLlmEngine
import com.myvu.client.ai.engine.OnDeviceLlmEngine
import com.myvu.client.core.LogBus
import java.io.File
import java.io.IOException

enum class GemmaEngineType {
    LITERT_LM,
    MEDIAPIPE
}

data class GemmaModelOption(
    val id: String,
    val name: String,
    val downloadUrl: String,
    val fileName: String,
    val sizeBytes: Long,
    val engineType: GemmaEngineType = if (fileName.endsWith(".litertlm", ignoreCase = true)) GemmaEngineType.LITERT_LM else GemmaEngineType.MEDIAPIPE
)

class GemmaLocalClient(
    private val context: Context,
    val modelOption: GemmaModelOption = DEFAULT_OPTION,
    private val customEngine: OnDeviceLlmEngine? = null,
    private val fallbackEngineFactory: ((GemmaModelOption) -> OnDeviceLlmEngine)? = null
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
            GEMMA_4_E2B_LITERT,
            GEMMA_2B_IT_GPU,
            GEMMA_2_2B_IT_GPU,
            GEMMA_2B_IT_CPU,
            GEMMA_1_1_2B_IT_GPU
        )

        val DEFAULT_OPTION = GEMMA_2B_IT_GPU

        @Volatile
        private var cachedEngine: OnDeviceLlmEngine? = null
        @Volatile
        private var cachedModelPath: String? = null

        fun findOption(id: String?): GemmaModelOption {
            return OPTIONS.firstOrNull { it.id == id } ?: DEFAULT_OPTION
        }

        fun createEngine(option: GemmaModelOption): OnDeviceLlmEngine {
            return when (option.engineType) {
                GemmaEngineType.LITERT_LM -> LiteRtLmEngine()
                GemmaEngineType.MEDIAPIPE -> MediaPipeLlmEngine()
            }
        }

        @Synchronized
        fun clearCache() {
            cachedEngine?.close()
            cachedEngine = null
            cachedModelPath = null
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
    private fun getOrInitEngine(): OnDeviceLlmEngine {
        val file = modelFile
        val path = file.absolutePath

        val engine = customEngine ?: run {
            if (cachedEngine != null && cachedModelPath == path && cachedEngine!!.isReady()) {
                return cachedEngine!!
            }
            cachedEngine?.close()
            val newEngine = createEngine(modelOption)
            cachedEngine = newEngine
            cachedModelPath = path
            newEngine
        }

        if (!engine.isReady()) {
            LogBus.log("AI_GEMMA_LOCAL_INIT path=$path model=${modelOption.id} engine=${engine.javaClass.simpleName}")
            engine.initialize(context, file, maxTokens = 512)
        }

        return engine
    }

    fun findAvailableMediaPipeFallback(): GemmaModelOption? {
        val fallbackCandidates = listOf(
            GEMMA_2B_IT_GPU,
            GEMMA_2_2B_IT_GPU,
            GEMMA_2B_IT_CPU,
            GEMMA_1_1_2B_IT_GPU
        )

        return fallbackCandidates.firstOrNull { candidate ->
            candidate.id != modelOption.id && run {
                val candidateFile = getModelFile(context, candidate.fileName)
                candidateFile.exists() && candidateFile.length() > 50_000_000L
            }
        }
    }

    @Throws(IOException::class)
    override fun ask(question: String): String {
        if (!isConfigured()) {
            throw IOException("Modelo local Gemma no descargado o incompleto (${modelOption.fileName})")
        }

        LogBus.log("AI_GEMMA_LOCAL_START questionLength=${question.length} model=${modelOption.fileName} engine=${modelOption.engineType}")
        try {
            val engine = getOrInitEngine()
            val response = engine.generate(question)
            if (response.isBlank()) {
                throw IOException("El motor ${engine.javaClass.simpleName} retornó una respuesta vacía")
            }
            return response.trim()
        } catch (e: Throwable) {
            LogBus.error("AI_GEMMA_LOCAL_ERROR: ${e.message}", e)

            val fallbackOption = findAvailableMediaPipeFallback()
            if (fallbackOption != null) {
                val fallbackFile = getModelFile(context, fallbackOption.fileName)
                LogBus.log(
                    "AI_GEMMA_AUTO_SWITCH from=${modelOption.fileName} " +
                    "to=${fallbackOption.fileName} reason=${e.message}"
                )
                try {
                    val fallbackEngine = fallbackEngineFactory?.invoke(fallbackOption)
                        ?: createEngine(fallbackOption)
                    fallbackEngine.initialize(context, fallbackFile, maxTokens = 512)
                    val response = fallbackEngine.generate(question)
                    if (response.isBlank()) {
                        throw IOException("El motor MediaPipe (${fallbackOption.fileName}) retornó una respuesta vacía")
                    }
                    return response.trim()
                } catch (fallbackError: Throwable) {
                    LogBus.error("AI_GEMMA_AUTO_SWITCH_ERROR: ${fallbackError.message}", fallbackError)
                    throw IOException(
                        "Inferencia local Gemma falló tras auto-conmutación a ${fallbackOption.fileName} (${fallbackError.message}). Activando fallback.",
                        fallbackError
                    )
                }
            }

            throw IOException("Inferencia local Gemma falló (${e.message}). Activando fallback.", e)
        }
    }
}
