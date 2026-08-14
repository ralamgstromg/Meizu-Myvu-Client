package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiConversationGeminiTest {

    @Test
    fun newClientCreatesGeminiHybridClientForGeminiAndroidProvider() {
        val context = RuntimeEnvironment.getApplication()
        Prefs.setAiProvider(context, AiProvider.GEMINI_ANDROID.id)
        Prefs.setGeminiFallbackPolicy(context, GeminiFallbackPolicy.NANO_ONLY.id)

        val client = AiProvider.GEMINI_ANDROID.newClient(
            context = context,
            apiKey = "",
            model = "",
            endpoint = "",
            systemPrompt = "System prompt"
        )

        assertTrue(client is GeminiHybridClient)
    }

    @Test
    fun geminiHybridClientHonorsConfiguredPolicy() {
        val context = RuntimeEnvironment.getApplication()
        Prefs.setGeminiFallbackPolicy(context, GeminiFallbackPolicy.NANO_ONLY.id)

        val nanoBackend = object : GeminiBackend {
            override fun availability(): GeminiAvailability = GeminiAvailability.UNAVAILABLE
            override fun ask(request: GeminiRequest, callback: (Result<GeminiResult>) -> Unit) {
                callback(Result.failure(GeminiNanoException(GeminiAvailability.UNAVAILABLE)))
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

        val result = hybrid.ask("Hello")
        assertNotNull(result)
        assertEquals(false, apiCalled)
    }

    @Test
    fun geminiHybridClientFallsBackToApiWhenAllowed() {
        val nanoBackend = object : GeminiBackend {
            override fun availability(): GeminiAvailability = GeminiAvailability(GeminiAvailability.State.MODEL_MISSING)
            override fun ask(request: GeminiRequest, callback: (Result<GeminiResult>) -> Unit) {
                callback(Result.failure(GeminiNanoException(GeminiAvailability(GeminiAvailability.State.MODEL_MISSING))))
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
