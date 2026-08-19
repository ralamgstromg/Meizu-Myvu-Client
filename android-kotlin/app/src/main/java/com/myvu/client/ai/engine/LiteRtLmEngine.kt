package com.myvu.client.ai.engine

import android.content.Context
import android.os.Build
import com.myvu.client.core.LogBus
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Motor de inferencia on-device nativo para contenedores LiteRT-LM (.litertlm),
 * optimizado para modelos como Gemma 4 E2B IT con aceleración de hardware SoC (GPU/NPU)
 * y delegación automática a MediaPipe GenAI.
 */
class LiteRtLmEngine(
    private val inferenceRunner: LiteRtInferenceRunner? = null
) : OnDeviceLlmEngine {

    fun interface LiteRtInferenceRunner {
        @Throws(Exception::class)
        fun executeInference(modelFile: File, prompt: String, maxTokens: Int): String
    }

    private val fallbackMediaPipeEngine: MediaPipeLlmEngine by lazy { MediaPipeLlmEngine() }

    @Volatile
    private var modelFile: File? = null
    private var maxTokens: Int = 512
    private var isInitialized: Boolean = false
    private var containerMetadata: LiteRtContainerMetadata? = null

    data class LiteRtContainerMetadata(
        val fileName: String,
        val fileSizeBytes: Long,
        val rootTableOffset: Long,
        val identifier: String,
        val isAccelerated: Boolean
    )

    @Synchronized
    @Throws(IOException::class)
    override fun initialize(context: Context, modelFile: File, maxTokens: Int) {
        if (!modelFile.exists() || !modelFile.canRead()) {
            throw IOException("El archivo del modelo LiteRT-LM no existe o no se puede leer: ${modelFile.absolutePath}")
        }

        if (modelFile.length() < MIN_CONTAINER_SIZE_BYTES) {
            throw IOException("El archivo del modelo .litertlm es demasiado pequeño o corrupto (${modelFile.length()} bytes)")
        }

        val metadata = validateAndParseContainer(modelFile)
        this.containerMetadata = metadata
        this.modelFile = modelFile
        this.maxTokens = maxTokens
        this.isInitialized = true

        LogBus.log(
            "AI_LITERT_LM_ENGINE_INIT path=${modelFile.absolutePath} " +
            "size=${modelFile.length()} id=${metadata.identifier} " +
            "accel=${metadata.isAccelerated} maxTokens=$maxTokens"
        )

        if (inferenceRunner == null) {
            try {
                LogBus.log("AI_LITERT_LM_DELEGATE_MEDIAPIPE delegando a MediaPipeLlmEngine para .litertlm")
                fallbackMediaPipeEngine.initialize(context, modelFile, maxTokens)
            } catch (t: Throwable) {
                LogBus.trace("AI_LITERT_LM_DELEGATE_NOTICE: ${t.message}")
            }
        }
    }

    @Synchronized
    @Throws(IOException::class)
    override fun generate(prompt: String): String {
        val file = modelFile
        if (!isReady() || file == null) {
            throw IOException("LiteRtLmEngine no está inicializado. Llame a initialize() primero.")
        }

        if (prompt.isBlank()) {
            return ""
        }

        LogBus.log("AI_LITERT_LM_GENERATE promptLength=${prompt.length} model=${file.name}")

        try {
            if (inferenceRunner != null) {
                val result = inferenceRunner.executeInference(file, prompt, maxTokens)
                if (result.isBlank()) {
                    throw IOException("LiteRT-LM retornó una respuesta vacía")
                }
                return result.trim()
            }

            return fallbackMediaPipeEngine.generate(prompt)
        } catch (e: Throwable) {
            val soc = Build.HARDWARE ?: "unknown"
            val message = "Error en ejecución de inferencia LiteRT-LM en SoC ($soc): ${e.message}"
            LogBus.error("AI_LITERT_LM_GENERATE_ERROR: $message", e)
            throw IOException(message, e)
        }
    }

    override fun isReady(): Boolean {
        return (isInitialized && modelFile?.exists() == true) || (isInitialized && fallbackMediaPipeEngine.isReady())
    }

    @Synchronized
    override fun close() {
        try {
            if (isInitialized) {
                fallbackMediaPipeEngine.close()
            }
        } catch (_: Throwable) {
        }
        isInitialized = false
        modelFile = null
        containerMetadata = null
        LogBus.log("AI_LITERT_LM_ENGINE_CLOSED")
    }

    fun isNativeRunnerAvailable(): Boolean = inferenceRunner != null

    fun getContainerMetadata(): LiteRtContainerMetadata? = containerMetadata

    @Throws(IOException::class)
    private fun validateAndParseContainer(file: File): LiteRtContainerMetadata {
        val header = ByteArray(32)
        try {
            FileInputStream(file).use { stream ->
                val bytesRead = stream.read(header)
                if (bytesRead < 16) {
                    throw IOException("No se pudo leer el encabezado completo del contenedor .litertlm")
                }
            }
        } catch (e: IOException) {
            throw IOException("Falla al leer el encabezado del archivo .litertlm: ${e.message}", e)
        }

        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val rootOffset = buffer.int.toLong() and 0xFFFFFFFFL

        if (rootOffset <= 0 || rootOffset > file.length()) {
            throw IOException("Encabezado FlatBuffer inválido en .litertlm (root table offset: $rootOffset, tamaño: ${file.length()})")
        }

        val idBytes = ByteArray(4)
        buffer.get(idBytes)
        val rawId = String(idBytes, Charsets.US_ASCII).filter { it.isLetterOrDigit() }
        val identifier = if (rawId.isNotBlank()) rawId else "LTLM"

        val isAccelerated = isSocAccelerationSupported()

        return LiteRtContainerMetadata(
            fileName = file.name,
            fileSizeBytes = file.length(),
            rootTableOffset = rootOffset,
            identifier = identifier,
            isAccelerated = isAccelerated
        )
    }

    private fun isSocAccelerationSupported(): Boolean {
        val hardware = Build.HARDWARE?.lowercase() ?: ""
        return hardware.contains("qcom") ||
               hardware.contains("snapdragon") ||
               hardware.contains("mt") ||
               hardware.contains("mali") ||
               hardware.contains("exynos") ||
               hardware.contains("tensor") ||
               hardware.contains("kirin")
    }

    companion object {
        const val MIN_CONTAINER_SIZE_BYTES = 16L
    }
}
