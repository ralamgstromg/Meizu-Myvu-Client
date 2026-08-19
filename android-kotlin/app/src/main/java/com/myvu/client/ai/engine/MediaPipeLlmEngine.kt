package com.myvu.client.ai.engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.myvu.client.core.LogBus
import java.io.File
import java.io.IOException

/**
 * Motor on-device para modelos empaquetados en formato MediaPipe (.bin, .task, .litertlm),
 * utilizando Google AI Edge / MediaPipe Tasks GenAI con soporte de GPU/NPU y precarga VNDK.
 */
class MediaPipeLlmEngine(
    private val inferenceFactory: ((Context, LlmInference.LlmInferenceOptions) -> LlmInference)? = null
) : OnDeviceLlmEngine {

    companion object {
        @Volatile
        private var librariesPreloaded = false

        fun tryPreloadNativeLibraries() {
            if (librariesPreloaded) return
            librariesPreloaded = true

            val systemVendorLibs = listOf(
                "/system/lib64/libvndksupport.so",
                "/vendor/lib64/libvndksupport.so",
                "/system/lib/libvndksupport.so",
                "/vendor/lib/libvndksupport.so",
                "/vendor/lib64/libOpenCL.so",
                "/vendor/lib64/egl/libGLES_mali.so",
                "/system/vendor/lib64/egl/libGLES_mali.so",
                "/vendor/lib64/libgal.so"
            )

            for (path in systemVendorLibs) {
                try {
                    val file = File(path)
                    if (file.exists()) {
                        System.load(path)
                        LogBus.log("AI_MEDIAPIPE_LOADED_SYS_LIB: $path")
                    }
                } catch (t: Throwable) {
                    LogBus.trace("AI_MEDIAPIPE_LOAD_SYS_LIB_SKIP ($path): ${t.message}")
                }
            }

            try { System.loadLibrary("vndksupport") } catch (_: Throwable) {}
            try { System.loadLibrary("OpenCL") } catch (_: Throwable) {}
            try { System.loadLibrary("GLES_mali") } catch (_: Throwable) {}
        }
    }

    @Volatile
    private var engine: LlmInference? = null
    private var loadedModelPath: String? = null

    @Synchronized
    @Throws(IOException::class)
    override fun initialize(context: Context, modelFile: File, maxTokens: Int) {
        if (!modelFile.exists() || !modelFile.canRead()) {
            throw IOException("El archivo del modelo no existe o no se puede leer: ${modelFile.absolutePath}")
        }

        if (engine != null && loadedModelPath == modelFile.absolutePath) {
            return
        }

        close()

        tryPreloadNativeLibraries()

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
