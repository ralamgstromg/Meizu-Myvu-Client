package com.myvu.client.ai

/** Detector interface for checking on-device Gemini Nano capability. */
interface GeminiCapabilityDetector {
    fun detect(): GeminiAvailability
}

/** Exception representing typed local Nano failures eligible for fallback. */
class GeminiNanoException(
    val state: GeminiAvailability.State,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
