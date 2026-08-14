package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiConversationGeminiTest {

    @Test
    fun geminiHybridClientHonorsConfiguredPolicy() {
        val nanoBackend = object : GeminiBackend {
            override fun availability(): GeminiAvailability = GeminiAvailability.UNAVAILABLE
            override fun ask(request: GeminiRequest, callback: (Result<GeminiResult>) -> Unit) {
                callback(Result.failure(GeminiNanoException(GeminiAvailability.State.UNAVAILABLE, "Unavailable")))
            }
            override fun cancel(requestId: String) {}
        }

        var apiCalled = false
        val apiBackend = object : GeminiBackend {
            override fun availability(): GeminiAvailability = GeminiAvailability(GeminiAvailability.State.AVAILABLE)
            override fun ask(request: GeminiRequest, callback: (Result<GeminiResult>) -> Unit) {
                apiCalled = true
                callback(Result.success(GeminiResult(request.requestId, "API answer", "GEMINI_API")))
            }
            override fun cancel(requestId: String) {}
        }

        val hybrid = GeminiHybridClient(nanoBackend, apiBackend, GeminiFallbackPolicy.NANO_ONLY)

        try {
            hybrid.ask("Hello")
        } catch (_: Exception) {
            // Expected failure when Nano fails under NANO_ONLY
        }
        assertEquals(false, apiCalled)
    }

    @Test
    fun geminiHybridClientFallsBackToApiWhenAllowed() {
        val nanoBackend = object : GeminiBackend {
            override fun availability(): GeminiAvailability = GeminiAvailability(GeminiAvailability.State.MODEL_MISSING)
            override fun ask(request: GeminiRequest, callback: (Result<GeminiResult>) -> Unit) {
                callback(Result.failure(GeminiNanoException(GeminiAvailability.State.MODEL_MISSING, "Model missing")))
            }
            override fun cancel(requestId: String) {}
        }

        var apiCalled = false
        val apiBackend = object : GeminiBackend {
            override fun availability(): GeminiAvailability = GeminiAvailability(GeminiAvailability.State.AVAILABLE)
            override fun ask(request: GeminiRequest, callback: (Result<GeminiResult>) -> Unit) {
                apiCalled = true
                callback(Result.success(GeminiResult(request.requestId, "API answer", "GEMINI_API")))
            }
            override fun cancel(requestId: String) {}
        }

        val hybrid = GeminiHybridClient(nanoBackend, apiBackend, GeminiFallbackPolicy.NANO_THEN_API)

        val result = hybrid.ask("Hello")
        assertEquals("API answer", result)
        assertTrue(apiCalled)
    }
}
