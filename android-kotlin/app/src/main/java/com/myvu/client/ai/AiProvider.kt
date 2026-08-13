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
        val ignoreSsl = context != null && Prefs.ignoreSsl(context)
        return when (this) {
            OPENAI -> OpenAiClient(apiKey, model, systemPrompt)
            GEMINI -> GeminiClient(apiKey, model, systemPrompt)
            GROQ -> LocalAiClient("https://api.groq.com/openai/v1/chat/completions", apiKey, model, systemPrompt, false)
            NVIDIA -> LocalAiClient("https://integrate.api.nvidia.com/v1/chat/completions", apiKey, model, systemPrompt, false)
            ASSISTANT -> AndroidAssistantClient(context!!)
            LOCAL -> LocalAiClient(endpoint, apiKey, model, systemPrompt, ignoreSsl)
            else -> ClaudeClient(apiKey, model, systemPrompt)
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
