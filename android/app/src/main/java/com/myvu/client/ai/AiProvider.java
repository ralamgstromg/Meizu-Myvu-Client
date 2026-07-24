package com.myvu.client.ai;

/**
 * The selectable answer backends.
 *
 * Everything the rest of the app needs to know about a provider lives on its
 * constant -- pref id, UI label, key console, shipped model -- so adding a
 * provider is one constant here plus one {@link AiHttpClient} subclass.
 */
public enum AiProvider {

    CLAUDE("claude", "Claude (Anthropic)", "console.anthropic.com", "claude-3-5-haiku-20241022"),
    OPENAI("openai", "ChatGPT (OpenAI)", "platform.openai.com", "gpt-4o-mini"),
    GEMINI("gemini", "Gemini (Google)", "aistudio.google.com", "gemini-2.0-flash"),
    GROQ("groq", "Groq (Ultra-Fast)", "console.groq.com", "llama-3.3-70b-versatile"),
    NVIDIA("nvidia", "NVIDIA NIM (Free Credits)", "build.nvidia.com", "meta/llama-3.3-70b-instruct"),
    ASSISTANT("assistant", "Asistente de Android (Google/Gemini)", "", ""),
    LOCAL("local", "Custom / Local AI", "", "");

    /** Stable id used in SharedPreferences names -- never rename a value. */
    public final String id;
    /** What the user sees in Settings and the log. */
    public final String label;
    /** Where an API key comes from, shown as Settings helper text. */
    public final String console;
    /** Used when the model field in Settings is left blank. */
    public final String defaultModel;

    AiProvider(String id, String label, String console, String defaultModel) {
        this.id = id;
        this.label = label;
        this.console = console;
        this.defaultModel = defaultModel;
    }

    /** Blank model or system prompt fall back to the shipped defaults. */
    public AiClient newClient(String apiKey, String model, String endpoint,
                              String systemPrompt) {
        return newClient(null, apiKey, model, endpoint, systemPrompt);
    }

    public AiClient newClient(android.content.Context context, String apiKey, String model, String endpoint,
                              String systemPrompt) {
        boolean ignoreSsl = context != null && com.myvu.client.core.Prefs.ignoreSsl(context);
        switch (this) {
            case OPENAI:    return new OpenAiClient(apiKey, model, systemPrompt);
            case GEMINI:    return new GeminiClient(apiKey, model, systemPrompt);
            case GROQ:      return new LocalAiClient("https://api.groq.com/openai/v1/chat/completions", apiKey, model, systemPrompt, false);
            case NVIDIA:    return new LocalAiClient("https://integrate.api.nvidia.com/v1/chat/completions", apiKey, model, systemPrompt, false);
            case ASSISTANT: return new AndroidAssistantClient(context);
            case LOCAL:     return new LocalAiClient(endpoint, apiKey, model, systemPrompt, ignoreSsl);
            default:        return new ClaudeClient(apiKey, model, systemPrompt);
        }
    }

    /** Unknown ids fall back to Claude rather than crashing on a stale pref. */
    public static AiProvider fromId(String id) {
        for (AiProvider p : values()) {
            if (p.id.equals(id)) return p;
        }
        return CLAUDE;
    }
}
