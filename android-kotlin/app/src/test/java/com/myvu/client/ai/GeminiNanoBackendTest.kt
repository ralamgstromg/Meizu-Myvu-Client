package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch

class GeminiNanoBackendTest {

    @Test
    fun unavailableNanoDoesNotInvokeRuntime() {
        var runtimeInvoked = false
        val detector = object : GeminiCapabilityDetector {
            override fun detect(): GeminiAvailability = GeminiAvailability.UNAVAILABLE
        }
        val runtime = object : GeminiNanoRuntime {
            override fun generate(request: GeminiRequest, callback: (Result<GeminiResult>) -> Unit) {
                runtimeInvoked = true
            }
            override fun cancel(requestId: String) {}
        }

        val backend = GeminiNanoBackend(detector, runtime)
        assertEquals(GeminiAvailability.State.UNAVAILABLE, backend.availability().state)

        var resultReceived = false
        backend.ask(GeminiRequest("req-1", "test prompt", "system")) { result ->
            resultReceived = true
            assertTrue(result.isFailure)
        }

        assertFalse(runtimeInvoked)
        assertTrue(resultReceived)
    }

    @Test
    fun supportedNanoMapsRuntimeTextToResult() {
        val detector = object : GeminiCapabilityDetector {
            override fun detect(): GeminiAvailability = GeminiAvailability(GeminiAvailability.State.AVAILABLE)
        }
        val runtime = object : GeminiNanoRuntime {
            override fun generate(request: GeminiRequest, callback: (Result<GeminiResult>) -> Unit) {
                callback(Result.success(GeminiResult(request.requestId, "Nano answer", "NANO")))
            }
            override fun cancel(requestId: String) {}
        }

        val backend = GeminiNanoBackend(detector, runtime)
        var result: GeminiResult? = null
        val latch = CountDownLatch(1)
        backend.ask(GeminiRequest("req-2", "hello", "sys")) { res ->
            result = res.getOrNull()
            latch.countDown()
        }

        latch.await()
        assertEquals("req-2", result?.requestId)
        assertEquals("Nano answer", result?.answer)
        assertEquals("NANO", result?.backendId)
    }

    @Test
    fun modelMissingIsMarkedEligibleForApiFallback() {
        val detector = object : GeminiCapabilityDetector {
            override fun detect(): GeminiAvailability = GeminiAvailability(GeminiAvailability.State.MODEL_MISSING, "Model not downloaded")
        }
        val runtime = object : GeminiNanoRuntime {
            override fun generate(request: GeminiRequest, callback: (Result<GeminiResult>) -> Unit) {
                callback(Result.failure(GeminiNanoException(GeminiAvailability.State.MODEL_MISSING, "Model missing")))
            }
            override fun cancel(requestId: String) {}
        }

        val backend = GeminiNanoBackend(detector, runtime)
        val availability = backend.availability()
        assertEquals(GeminiAvailability.State.MODEL_MISSING, availability.state)

        var exception: GeminiNanoException? = null
        backend.ask(GeminiRequest("req-3", "hello", "sys")) { res ->
            exception = res.exceptionOrNull() as? GeminiNanoException
        }

        assertEquals(GeminiAvailability.State.MODEL_MISSING, exception?.state)
    }
}
