package com.myvu.client.ai.engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.myvu.client.core.LogBus
import java.io.File
import java.io.IOException

/**
 * Motor on-device para modelos empaquetados en formato MediaPipe (.bin),
 * utilizando Google AI Edge / MediaPipe Tasks GenAI.
 */
class MediaPipeLlmEngine(
    private val inferenceFactory: ((Context, LlmInference.LlmInferenceOptions) -> LlmInference)? = null
) : OnDeviceLlmEngine {

    @Volatile
    private var engine: LlmInference? = null
    private var loadedModelPath: String? = null

    @Synchronized
    @Throws(IOException::class)
    override fun initialize(context: Context, modelFile: File, maxTokens: Int) {
        if (!modelFile.exists() || !modelFile.canRead()) {
            throw IOException("El archivo del modelo MediaPipe no existe o no se puede leer: ${modelFile.absolutePath}")
        }

        if (engine != null && loadedModelPath == modelFile.absolutePath) {
            return
        }

        close()

        LogBus.log("AI_MEDIAPIPE_ENGINE_INIT path=${modelFile.absolutePath} maxTokens=$maxTokens")
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(maxTokens)
                .setMaxTopK(40)
                .build()

            engine = if (inferenceFactory != null) {
                inferenceFactory.invoke(context, options)
            } else {
                LlmInference.createFromOptions(context, options)
            }
            loadedModelPath = modelFile.absolutePath
        } catch (e: Throwable) {
            LogBus.error("AI_MEDIAPIPE_ENGINE_INIT_ERROR: ${e.message}", e)
            throw IOException("Error inicializando MediaPipe LlmInference: ${e.message}", e)
        }
    }

    @Synchronized
    @Throws(IOException::class)
    override fun generate(prompt: String): String {
        val activeEngine = engine ?: throw IOException("MediaPipeLlmEngine no está inicializado. Llame a initialize() primero.")
        if (prompt.isBlank()) {
            return ""
        }

        try {
            val response = activeEngine.generateResponse(prompt)
            if (response.isNullOrBlank()) {
                throw IOException("MediaPipe LlmInference retornó una respuesta vacía")
            }
            return response.trim()
        } catch (e: Throwable) {
            LogBus.error("AI_MEDIAPIPE_ENGINE_GENERATE_ERROR: ${e.message}", e)
            throw IOException("Inferencia MediaPipe falló: ${e.message}", e)
        }
    }

    override fun isReady(): Boolean {
        return engine != null
    }

    @Synchronized
    override fun close() {
        engine?.let {
            try {
                it.close()
            } catch (_: Throwable) {
            }
        }
        engine = null
        loadedModelPath = null
    }
}
