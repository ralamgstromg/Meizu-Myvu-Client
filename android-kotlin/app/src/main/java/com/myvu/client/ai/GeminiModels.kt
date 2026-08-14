package com.myvu.client.ai

/** Backend capability state, without exposing platform-specific runtime types. */
data class GeminiAvailability(
    val state: State,
    val reason: String? = null
) {
    enum class State {
        AVAILABLE,
        UNAVAILABLE,
        MODEL_MISSING,
        TASK_UNSUPPORTED
    }

    companion object {
        @JvmField
        val UNAVAILABLE: GeminiAvailability = GeminiAvailability(State.UNAVAILABLE)
    }
}

data class GeminiRequest(
    val requestId: String,
    val prompt: String,
    val systemInstruction: String,
    val requireStructuredOutput: Boolean = true
)

data class GeminiResult(
    val requestId: String,
    val answer: String,
    val backendId: String,
    val actionCandidates: List<String> = emptyList()
)
