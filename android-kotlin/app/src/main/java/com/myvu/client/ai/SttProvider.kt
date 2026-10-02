package com.myvu.client.ai

enum class SttProvider(
    @JvmField val id: String,
    @JvmField val label: String,
    @JvmField val defaultEndpoint: String,
    @JvmField val defaultModel: String,
    @JvmField val apiKeyRequired: Boolean
) {
    ANDROID(
        "android",
        "Android Nativo (Google / On-Device)",
        "",
        "",
        false
    ),
    GROQ(
        "groq",
        "Groq Whisper",
        "https://api.groq.com/openai/v1/audio/transcriptions",
        "whisper-large-v3-turbo",
        true
    ),
    LOCAL(
        "local",
        "Local STT (Port 8181)",
        "http://127.0.0.1:8181/v1/audio/transcriptions",
        "whisper",
        false
    ),
    WHISPER_CPP(
        "whisper_cpp",
        "Whisper.cpp (Local Server)",
        "http://127.0.0.1:8282/v1/audio/transcriptions",
        "whisper",
        false
    );

    val isNative: Boolean get() = this == ANDROID
    val requiresEndpoint: Boolean get() = this != ANDROID
    val requiresModel: Boolean get() = this != ANDROID
    val requiresApiKey: Boolean get() = apiKeyRequired

    val displayName: String get() = label

    companion object {
        @JvmStatic
        fun fromId(id: String?): SttProvider {
            for (provider in values()) {
                if (provider.id == id) return provider
            }
            return LOCAL
        }
    }
}
