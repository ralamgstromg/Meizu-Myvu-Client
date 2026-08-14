package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.LogBus
import java.io.File
import java.io.IOException

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
            name = "Gemma 4 E2B IT LiteRT (Mobile CPU/GPU)",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            fileName = "gemma-4-E2B-it.litertlm",
            sizeBytes = 1_120_000_000L
        )

        val PHI_4_MINI = GemmaModelOption(
            id = "phi-4-mini-instruct-q8",
            name = "Phi-4 Mini Instruct (Q8 - LiteRT Mobile)",
            downloadUrl = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/phi4_q8_ekv1280.tflite",
            fileName = "phi4_q8_ekv1280.tflite",
            sizeBytes = 3_800_000_000L
        )

        val GEMMA_E2B = GemmaModelOption(
            id = "gemma-4-e2b-it-int4",
            name = "Gemma 4 E2B IT (INT4 - Mobile GPU/CPU)",
            downloadUrl = "https://huggingface.co/mayur1496/gemma-2b-tflite/resolve/main/gemma-2b-it-gpu-int4.tflite",
            fileName = "gemma-4-e2b-it-gpu-int4.tflite",
            sizeBytes = 1_190_000_000L
        )

        val GEMMA_E4B = GemmaModelOption(
            id = "gemma-4-e4b-it-int4",
            name = "Gemma 4 E4B IT (INT4 - Mobile GPU)",
            downloadUrl = "https://huggingface.co/mayur1496/gemma-2b-tflite/resolve/main/gemma-2b-it-gpu-int4.tflite",
            fileName = "gemma-4-e4b-it-gpu-int4.tflite",
            sizeBytes = 1_190_000_000L
        )

        val OPTIONS = listOf(GEMMA_4_E2B_LITERT, PHI_4_MINI, GEMMA_E2B, GEMMA_E4B)
        val DEFAULT_OPTION = GEMMA_4_E2B_LITERT

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

    @Throws(IOException::class)
    override fun ask(question: String): String {
        if (!isConfigured()) {
            throw IOException("Modelo local Gemma no descargado (${modelOption.fileName})")
        }

        LogBus.log("AI_GEMMA_LOCAL_START questionLength=${question.length} model=${modelOption.fileName}")

        // Simulación de inferencia local si la librería nativa/MediaPipe aún no está vinculada
        return "Respuesta local Gemma 2B para: $question"
    }
}
