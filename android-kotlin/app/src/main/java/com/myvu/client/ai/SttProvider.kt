package com.myvu.client.ai

enum class SttProvider(
    @JvmField val id: String,
    @JvmField val label: String,
    @JvmField val defaultEndpoint: String,
    @JvmField val defaultModel: String,
    @JvmField val apiKeyRequired: Boolean
) {
    GROQ(
        "groq",
        "Groq",
        "https://api.groq.com/openai/v1/audio/transcriptions",
        "whisper-large-v3-turbo",
        true
    ),
    LOCAL(
        "local",
        "Local STT",
        "http://10.0.0.2:1235/v1/audio/transcriptions",
        "whisper",
        false
    );

    val displayName: String get() = label

    companion object {
        @JvmStatic
        fun fromId(id: String?): SttProvider {
            for (provider in values()) {
                if (provider.id == id) return provider
            }
            return GROQ
        }
    }
}
