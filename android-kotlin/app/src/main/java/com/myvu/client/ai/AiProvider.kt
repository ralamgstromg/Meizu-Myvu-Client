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
    GROQ("groq", "Groq (Ultra-Fast)", "console.groq.com", "llama-3.3-70b-versatile"),
    NVIDIA("nvidia", "NVIDIA NIM (Free Credits)", "build.nvidia.com", "meta/llama-3.3-70b-instruct"),
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
        return when (this) {
            OPENAI -> OpenAiClient(apiKey, model, effectivePrompt)
            GEMINI -> GeminiClient(apiKey, model, effectivePrompt)
            GROQ -> LocalAiClient("https://api.groq.com/openai/v1/chat/completions", apiKey, model, effectivePrompt, false)
            NVIDIA -> LocalAiClient("https://integrate.api.nvidia.com/v1/chat/completions", apiKey, model, effectivePrompt, false)
            ASSISTANT -> AndroidAssistantClient(context!!)
            LOCAL -> LocalAiClient(endpoint, apiKey, model, effectivePrompt, ignoreSsl)
            else -> ClaudeClient(apiKey, model, effectivePrompt)
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
