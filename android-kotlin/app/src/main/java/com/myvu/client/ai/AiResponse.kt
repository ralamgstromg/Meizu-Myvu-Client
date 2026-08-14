package com.myvu.client.ai

data class AiResponse(
    val sessionId: String,
    val text: String,
    val shouldSpeak: Boolean,
    val baseStatus: Int = 1,
    val source: Source = Source.AI
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(text.isNotBlank()) { "response text must not be blank" }
    }

    val normalizedText: String
        get() = text.trim()

    enum class Source {
        AI,
        WEB_SEARCH,
        TIME,
        WEATHER,
        CURRENCY,
        CALCULATION,
        NAVIGATION,
        PHONE_ACTION,
        ERROR
    }
}
