package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class GeminiHybridClientTest {

    private class FakeBackend(
        override val backendId: String,
        private var avail: GeminiAvailability = GeminiAvailability(GeminiAvailability.State.AVAILABLE)
    ) : GeminiBackend {
        var askCount = 0
        var cancelCount = 0
        var shouldSucceed = true
        var errorToThrow: Throwable? = null
        var answerToReturn = "Answer from $backendId"

        override fun availability(): GeminiAvailability = avail

        override fun ask(request: GeminiRequest, callback: (Result<GeminiResult>) -> Unit) {
            askCount++
            val err = errorToThrow
            if (err != null) {
                callback(Result.failure(err))
            } else if (shouldSucceed) {
                callback(Result.success(GeminiResult(request.requestId, answerToReturn, backendId)))
            } else {
                callback(Result.failure(IOException("Backend $backendId failed")))
            }
        }

        override fun cancel(requestId: String) {
            cancelCount++
        }
    }

    @Test
    fun nanoSuccessDoesNotCallApi() {
        val nano = FakeBackend("nano")
        val api = FakeBackend("api")
        val client = GeminiHybridClient(nano, api, GeminiFallbackPolicy.NANO_THEN_API)

        val result = client.ask("Hola")
        assertEquals("Answer from nano", result)
        assertEquals(1, nano.askCount)
        assertEquals(0, api.askCount)
    }

    @Test
    fun eligibleNanoFailureFallsBackToApi() {
        val nano = FakeBackend("nano").apply {
            errorToThrow = GeminiNanoException(GeminiAvailability.State.MODEL_MISSING, "Model missing")
        }
        val api = FakeBackend("api")
        val client = GeminiHybridClient(nano, api, GeminiFallbackPolicy.NANO_THEN_API)

        val result = client.ask("Hola")
        assertEquals("Answer from api", result)
        assertEquals(1, nano.askCount)
        assertEquals(1, api.askCount)
    }

    @Test
    fun nanoOnlyNeverCallsApi() {
        val nano = FakeBackend("nano").apply {
            errorToThrow = GeminiNanoException(GeminiAvailability.State.MODEL_MISSING, "Model missing")
        }
        val api = FakeBackend("api")
        val client = GeminiHybridClient(nano, api, GeminiFallbackPolicy.NANO_ONLY)

        try {
            client.ask("Hola")
            fail("Expected IOException when NANO_ONLY fails")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Nano failed") == true)
        }
        assertEquals(1, nano.askCount)
        assertEquals(0, api.askCount)
    }

    @Test
    fun apiOnlySkipsNano() {
        val nano = FakeBackend("nano")
        val api = FakeBackend("api")
        val client = GeminiHybridClient(nano, api, GeminiFallbackPolicy.API_ONLY)

        val result = client.ask("Hola")
        assertEquals("Answer from api", result)
        assertEquals(0, nano.askCount)
        assertEquals(1, api.askCount)
    }

    @Test
    fun isConfiguredReturnsTrueWhenPolicyValid() {
        val nano = FakeBackend("nano")
        val api = FakeBackend("api")
        val client = GeminiHybridClient(nano, api, GeminiFallbackPolicy.NANO_THEN_API)
        assertTrue(client.isConfigured())
    }
}
