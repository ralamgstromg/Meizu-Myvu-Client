package com.myvu.client.ai

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Abstract runtime interface for Gemini Nano on-device inference. */
interface GeminiNanoRuntime {
    fun generate(request: GeminiRequest, callback: (Result<GeminiResult>) -> Unit)
    fun cancel(requestId: String)
}

/** Implementation of GeminiBackend for on-device Gemini Nano. */
class GeminiNanoBackend(
    private val detector: GeminiCapabilityDetector,
    private val runtime: GeminiNanoRuntime? = null
) : GeminiBackend {

    private val activeRequests = ConcurrentHashMap.newKeySet<String>()

    override fun availability(): GeminiAvailability {
        return detector.detect()
    }

    override fun ask(request: GeminiRequest, callback: (Result<GeminiResult>) -> Unit) {
        val currentAvailability = availability()
        if (currentAvailability.state != GeminiAvailability.State.AVAILABLE) {
            callback(Result.failure(GeminiNanoException(
                currentAvailability.state,
                currentAvailability.reason ?: "Gemini Nano is unavailable (${currentAvailability.state})"
            )))
            return
        }

        val activeRuntime = runtime
        if (activeRuntime == null) {
            callback(Result.failure(GeminiNanoException(
                GeminiAvailability.State.UNAVAILABLE,
                "Gemini Nano runtime not initialized"
            )))
            return
        }

        activeRequests.add(request.requestId)
        activeRuntime.generate(request) { result ->
            activeRequests.remove(request.requestId)
            callback(result)
        }
    }

    override fun cancel(requestId: String) {
        if (activeRequests.remove(requestId)) {
            runtime?.cancel(requestId)
        }
    }
}
