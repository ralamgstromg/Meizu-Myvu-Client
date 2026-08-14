package com.myvu.client.ai

/** Platform-neutral contract implemented by Nano and Gemini API adapters. */
interface GeminiBackend {
    fun availability(): GeminiAvailability

    fun ask(request: GeminiRequest, callback: (Result<GeminiResult>) -> Unit)

    fun cancel(requestId: String)
}
