package com.myvu.client.ai.engine

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Interfaz unificada para motores de inferencia LLM on-device (MediaPipe Tasks GenAI, LiteRT-LM, etc.).
 */
interface OnDeviceLlmEngine {
    @Throws(IOException::class)
    fun initialize(context: Context, modelFile: File, maxTokens: Int = 512)

    @Throws(IOException::class)
    fun generate(prompt: String): String

    fun isReady(): Boolean

    fun close()
}
