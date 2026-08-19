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
    private val systemPrompt: String? = null,
    private val customEngine: OnDeviceLlmEngine? = null,
    private val fallbackEngineFactory: ((GemmaModelOption) -> OnDeviceLlmEngine)? = null
) : AiClient {

    companion object {
        val QWEN_2_5_1_5B_LITERT = GemmaModelOption(
            id = "qwen-2.5-1.5b-it-litert",
            name = "⭐ Qwen 2.5 1.5B Instruct (LiteRT ~1.15GB) - Recomendado Entidades/Tareas",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            sizeBytes = 1_150_000_000L
        )

        val DEEPSEEK_R1_1_5B_LITERT = GemmaModelOption(
            id = "deepseek-r1-1.5b-it-litert",
            name = "🧠 DeepSeek R1 Distill Qwen 1.5B (LiteRT ~1.15GB) - Razonamiento",
            downloadUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
            fileName = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
            sizeBytes = 1_150_000_000L
        )

        val GEMMA_4_E2B_LITERT = GemmaModelOption(
            id = "gemma-4-e2b-it-litert-lm",
            name = "⚡ Gemma 4 E2B IT (Google AI Edge Gallery ~1.12GB) - Ultrarrápido",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            fileName = "gemma-4-E2B-it.litertlm",
            sizeBytes = 1_120_000_000L
        )

        val GEMMA_4_E4B_LITERT = GemmaModelOption(
            id = "gemma-4-e4b-it-litert-lm",
            name = "🔬 Gemma 4 E4B IT (Google AI Edge Gallery ~2.35GB) - Razonamiento Avanzado",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            fileName = "gemma-4-E4B-it.litertlm",
            sizeBytes = 2_350_000_000L
        )

        val TINYLLAMA_1_1B_TASK = GemmaModelOption(
            id = "tinyllama-1.1b-chat-task",
            name = "📱 TinyLlama 1.1B Chat (LiteRT Task ~1.10GB)",
            downloadUrl = "https://huggingface.co/litert-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
            fileName = "TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
            sizeBytes = 1_100_000_000L
        )

        val GEMMA_2_2B_IT_GPU = GemmaModelOption(
            id = "gemma-2-2b-it-gpu-int4",
            name = "💎 Gemma 2 2B IT (Google AI Edge GPU ~1.48GB) - Requiere HF Token",
            downloadUrl = "https://huggingface.co/google/gemma-2-2b-it-tflite/resolve/main/gemma-2-2b-it-gpu-int4.bin",
            fileName = "gemma-2-2b-it-gpu-int4.bin",
            sizeBytes = 1_480_000_000L
        )

        val GEMMA_2B_IT_GPU = GemmaModelOption(
            id = "gemma-2b-it-gpu-int4",
            name = "Gemma 2B IT (Google AI Edge GPU ~1.35GB) - Requiere HF Token",
            downloadUrl = "https://huggingface.co/google/gemma-2b-it-tflite/resolve/main/gemma-2b-it-gpu-int4.bin",
            fileName = "gemma-2b-it-gpu-int4.bin",
            sizeBytes = 1_350_000_000L
        )

        val GEMMA_2B_IT_CPU = GemmaModelOption(
            id = "gemma-2b-it-cpu-int4",
            name = "🖥️ Gemma 2B IT CPU (MediaPipe XNNPACK ~1.35GB) - 100% CPU (Sin OpenCL/GPU)",
            downloadUrl = "https://huggingface.co/google/gemma-2b-it-tflite/resolve/main/gemma-2b-it-cpu-int4.bin",
            fileName = "gemma-2b-it-cpu-int4.bin",
            sizeBytes = 1_350_000_000L
        )

        val GEMMA_1_1_2B_IT_GPU = GemmaModelOption(
            id = "gemma-1.1-2b-it-gpu-int4",
            name = "Gemma 1.1 2B IT (Google AI Edge GPU ~1.35GB) - Requiere HF Token",
            downloadUrl = "https://huggingface.co/google/gemma-1.1-2b-it-tflite/resolve/main/gemma-1.1-2b-it-gpu-int4.bin",
            fileName = "gemma-1.1-2b-it-gpu-int4.bin",
            sizeBytes = 1_350_000_000L
        )

        val OPTIONS = listOf(
            QWEN_2_5_1_5B_LITERT,
            DEEPSEEK_R1_1_5B_LITERT,
            GEMMA_4_E2B_LITERT,
            GEMMA_4_E4B_LITERT,
            TINYLLAMA_1_1B_TASK,
            GEMMA_2_2B_IT_GPU,
            GEMMA_2B_IT_GPU,
            GEMMA_2B_IT_CPU,
            GEMMA_1_1_2B_IT_GPU
        )

        val DEFAULT_OPTION = QWEN_2_5_1_5B_LITERT

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

        fun formatPrompt(systemPrompt: String?, userQuery: String): String {
            val sys = if (!systemPrompt.isNullOrBlank()) "$systemPrompt\n\n" else ""
            return "<start_of_turn>user\n${sys}${userQuery.trim()}<end_of_turn>\n<start_of_turn>model\n"
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
            TINYLLAMA_1_1B_TASK,
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

        val prompt = if (question.contains("<start_of_turn>")) {
            question
        } else {
            formatPrompt(systemPrompt, question)
        }

        LogBus.log("AI_GEMMA_LOCAL_START questionLength=${prompt.length} model=${modelOption.fileName} engine=${modelOption.engineType}")
        try {
            val engine = getOrInitEngine()
            val response = engine.generate(prompt)
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
                    val response = fallbackEngine.generate(prompt)
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
