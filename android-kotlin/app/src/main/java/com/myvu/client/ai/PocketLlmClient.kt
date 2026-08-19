package com.myvu.client.ai

import com.myvu.client.core.LogBus
import java.io.IOException

/**
 * Cliente dedicado para Pocket LLM Server Android (https://github.com/knooob/pocket-llm-server-android),
 * el cual sirve modelos de Google AI Edge Gallery / LiteRT / llama.cpp como una API REST HTTP compatible con OpenAI.
 */
class PocketLlmClient @JvmOverloads constructor(
    endpoint: String?,
    apiKey: String?,
    model: String?,
    systemPrompt: String?,
    ignoreSsl: Boolean = true
) : AiClient {

    private val primaryEndpoint: String = endpoint?.trim()?.ifEmpty { DEFAULT_ENDPOINT } ?: DEFAULT_ENDPOINT
    private val effectiveModel: String = model?.trim()?.ifEmpty { DEFAULT_MODEL } ?: DEFAULT_MODEL
    private val effectiveApiKey: String = apiKey?.trim() ?: ""

    private val httpClient = LocalAiClient(
        endpoint = primaryEndpoint,
        apiKey = effectiveApiKey,
        model = effectiveModel,
        systemPrompt = systemPrompt,
        ignoreSsl = ignoreSsl
    )

    private val fallbackPort11434Client by lazy {
        LocalAiClient(
            endpoint = "http://127.0.0.1:11434/v1/chat/completions",
            apiKey = effectiveApiKey,
            model = effectiveModel,
            systemPrompt = systemPrompt,
            ignoreSsl = true
        )
    }

    override fun isConfigured(): Boolean {
        return primaryEndpoint.isNotBlank()
    }

    @Throws(IOException::class)
    override fun ask(question: String): String {
        LogBus.log("AI_POCKET_LLM_ASK: Enviando petición HTTP a Pocket LLM Server ($primaryEndpoint, model=$effectiveModel)...")
        try {
            return httpClient.ask(question)
        } catch (e: Exception) {
            LogBus.warn("AI_POCKET_LLM_PRIMARY_FAIL: Falló $primaryEndpoint (${e.message}). Intentando puerto 11434 (Ollama/Pocket)...")
            try {
                return fallbackPort11434Client.ask(question)
            } catch (fallbackErr: Exception) {
                LogBus.error("AI_POCKET_LLM_ERROR: Pocket LLM Server no respondió en 127.0.0.1:8080 ni 11434: ${fallbackErr.message}", fallbackErr)
                throw IOException("Pocket LLM Server Android no está activo en 127.0.0.1:8080 ni 11434. Inicie el servidor en su celular (${fallbackErr.message})", fallbackErr)
            }
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:8080/v1/chat/completions"
        const val DEFAULT_MODEL = "gemma-4-e2b-it"
    }
}
