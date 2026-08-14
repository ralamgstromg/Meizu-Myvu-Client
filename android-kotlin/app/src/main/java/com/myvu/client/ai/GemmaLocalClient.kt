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
        val GEMMA_E2B = GemmaModelOption(
            id = "gemma-4-e2b-it-int4",
            name = "Gemma 4 E2B IT (INT4 - Mobile GPU/CPU)",
            downloadUrl = "https://huggingface.co/google/gemma-2b-it-gpu-int4/resolve/main/gemma-2b-it-gpu-int4.bin",
            fileName = "gemma-4-e2b-it-gpu-int4.task",
            sizeBytes = 1_350_000_000L
        )

        val GEMMA_E4B = GemmaModelOption(
            id = "gemma-4-e4b-it-int4",
            name = "Gemma 4 E4B IT (INT4 - Mobile GPU)",
            downloadUrl = "https://huggingface.co/google/gemma-4b-it-gpu-int4/resolve/main/gemma-4b-it-gpu-int4.bin",
            fileName = "gemma-4-e4b-it-gpu-int4.task",
            sizeBytes = 2_450_000_000L
        )

        val OPTIONS = listOf(GEMMA_E2B, GEMMA_E4B)
        val DEFAULT_OPTION = GEMMA_E2B

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
