package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.Prefs

enum class AiProvider(
    @JvmField val id: String,
    @JvmField val label: String,
    @JvmField val console: String,
    @JvmField val defaultModel: String
) {
    CLAUDE("claude", "Claude (Anthropic)", "console.anthropic.com", "claude-3-5-haiku-20241022"),
    OPENAI("openai", "ChatGPT (OpenAI)", "platform.openai.com", "gpt-4o-mini"),
    GEMINI("gemini", "Gemini (Google)", "aistudio.google.com", "gemini-2.0-flash"),
    GEMINI_ANDROID("gemini_android", "Gemini Android (Nano/API)", "ai.google.dev", "gemini-2.0-flash"),
    GROQ("groq", "Groq (Ultra-Fast)", "console.groq.com", "llama-3.3-70b-versatile"),
    NVIDIA("nvidia", "NVIDIA NIM (Free Credits)", "build.nvidia.com", "meta/llama-3.3-70b-instruct"),
    GEMMA_LOCAL("gemma_local", "Gemma Local (On-Device GPU)", "huggingface.co/google", "gemma-2b-it-gpu-int4"),
    ASSISTANT("assistant", "Asistente de Android (Google/Gemini)", "", ""),
    LOCAL("local", "Custom / Local AI", "", "");

    val displayName: String get() = label

    fun newClient(
        apiKey: String,
        model: String,
        endpoint: String,
        systemPrompt: String
    ): AiClient {
        return newClient(null, apiKey, model, endpoint, systemPrompt)
    }

    fun newClient(
        context: Context?,
        apiKey: String,
        model: String,
        endpoint: String,
        systemPrompt: String
    ): AiClient {
        val effectivePrompt = if (systemPrompt.isNotBlank()) {
            systemPrompt
        } else {
            context?.let { Prefs.systemPrompt(it) } ?: AiClient.DEFAULT_SYSTEM_PROMPT
        }
        val ignoreSsl = context != null && Prefs.ignoreSsl(context)
        val baseClient = when (this) {
            OPENAI -> OpenAiClient(apiKey, model, effectivePrompt)
            GEMINI -> GeminiClient(apiKey, model, effectivePrompt)
            GEMINI_ANDROID -> {
                val policyId = context?.let { Prefs.geminiFallbackPolicy(it) } ?: GeminiFallbackPolicy.NANO_THEN_API.id
                val policy = GeminiFallbackPolicy.fromId(policyId)
                val nanoDetector = object : GeminiCapabilityDetector {
                    override fun detect(): GeminiAvailability = GeminiAvailability(GeminiAvailability.State.UNAVAILABLE, "not_supported")
                }
                val nanoBackend = GeminiNanoBackend(nanoDetector)
                val apiApiKey = context?.let { Prefs.aiApiKey(it, GEMINI_ANDROID.id) } ?: apiKey
                val apiModel = context?.let { Prefs.aiModel(it, GEMINI_ANDROID.id) }.orEmpty().ifBlank { model.ifBlank { "gemini-2.0-flash" } }
                val apiBackend = GeminiApiBackend(apiApiKey, apiModel)
                GeminiHybridClient(nanoBackend, apiBackend, policy)
            }
            GEMMA_LOCAL -> context?.let { 
                val opt = GemmaLocalClient.findOption(Prefs.gemmaModelId(it))
                GemmaLocalClient(it, opt) 
            } ?: GemmaLocalClient(context = context ?: error("Context required for GEMMA_LOCAL"))
            GROQ -> LocalAiClient("https://api.groq.com/openai/v1/chat/completions", apiKey, model, effectivePrompt, false)
            NVIDIA -> LocalAiClient("https://integrate.api.nvidia.com/v1/chat/completions", apiKey, model, effectivePrompt, false)
            ASSISTANT -> AndroidAssistantClient(context!!)
            LOCAL -> LocalAiClient(endpoint, apiKey, model, effectivePrompt, ignoreSsl)
            else -> ClaudeClient(apiKey, model, effectivePrompt)
        }

        if (context != null) {
            val isLocalGemmaActive = Prefs.useLocalGemmaIfAvailable(context) || this == GEMMA_LOCAL
            val optionId = Prefs.gemmaModelId(context)
            val option = GemmaLocalClient.findOption(optionId)
            val gemmaClient = GemmaLocalClient(context, option)

            // Construir cliente de rescate en la nube con cualquier API Key disponible (o groq de STT)
            val rescueProviderId = listOf("groq", "gemini", "openai", "claude", "nvidia")
                .firstOrNull { 
                    Prefs.aiApiKey(context, it).isNotBlank() || (it == "groq" && Prefs.sttApiKey(context, "groq").isNotBlank())
                } ?: "groq"
            val rescueProvider = fromId(rescueProviderId)
            val rescueApiKey = Prefs.aiApiKey(context, rescueProviderId).ifBlank {
                if (rescueProviderId == "groq") Prefs.sttApiKey(context, "groq") else ""
            }
            val rescueModel = Prefs.aiModel(context, rescueProviderId).ifBlank { rescueProvider.defaultModel }
            val rescueEndpoint = Prefs.aiEndpoint(context, rescueProviderId)
            val rescueClient = rescueProvider.newClient(
                null,
                rescueApiKey,
                rescueModel,
                rescueEndpoint,
                effectivePrompt
            )

            if (isLocalGemmaActive && gemmaClient.isConfigured()) {
                return LocalFallbackAiClient(
                    localClient = gemmaClient,
                    primaryClient = baseClient,
                    rescueClient = rescueClient
                )
            } else if (this == LOCAL) {
                // Si el usuario eligió Servidor HTTP Local, envolverlo con rescate a Cloud API si el servidor local se cae
                return LocalFallbackAiClient(
                    localClient = null,
                    primaryClient = baseClient,
                    rescueClient = rescueClient
                )
            }
        }

        return baseClient
    }

    class LocalFallbackAiClient(
        private val localClient: AiClient?,
        private val primaryClient: AiClient,
        private val rescueClient: AiClient?
    ) : AiClient {
        override fun isConfigured(): Boolean =
            (localClient?.isConfigured() == true) || primaryClient.isConfigured() || (rescueClient?.isConfigured() == true)

        @Throws(java.io.IOException::class)
        override fun ask(question: String): String {
            if (localClient?.isConfigured() == true) {
                try {
                    com.myvu.client.core.LogBus.log("AI_LOCAL_ATTEMPT: Ejecutando en modelo local on-device...")
                    return localClient.ask(question)
                } catch (e: Exception) {
                    com.myvu.client.core.LogBus.warn("AI_LOCAL_FAILED: Falló modelo local on-device (${e.message}). Intentando proveedor principal/remoto...")
                }
            }

            if (primaryClient.isConfigured() && primaryClient != localClient) {
                try {
                    return primaryClient.ask(question)
                } catch (e: Exception) {
                    com.myvu.client.core.LogBus.warn("AI_PRIMARY_FAILED: Proveedor principal falló (${e.message}). Conmutando automáticamente a API de rescate en la nube...")
                }
            }

            if (rescueClient?.isConfigured() == true && rescueClient != primaryClient) {
                com.myvu.client.core.LogBus.log("AI_RESCUE_ATTEMPT: Ejecutando con API de rescate en la nube...")
                return rescueClient.ask(question)
            }

            throw java.io.IOException("Ningún proveedor de IA (On-Device, Servidor Local o Cloud API) respondió.")
        }
    }

    companion object {
        @JvmStatic
        fun fromId(id: String?): AiProvider {
            for (p in values()) {
                if (p.id == id) return p
            }
            return CLAUDE
        }
    }
}
