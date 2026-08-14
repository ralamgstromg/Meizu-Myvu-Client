package com.myvu.client.ai

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiApiBackendCancellationTest {
    @Test
    fun cancellationRequestsTransportCancellationAndSuppressesCallback() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val transport = object : GeminiApiTransport {
            override fun post(requestId: String, url: String, apiKey: String, requestBody: String): GeminiHttpResponse {
                entered.countDown()
                release.await(2, TimeUnit.SECONDS)
                return GeminiHttpResponse(200, """{"candidates":[{"content":{"parts":[{"text":"late"}]}}]}""")
            }

            override fun cancel(requestId: String) {
                cancelled.countDown()
                release.countDown()
            }
        }
        val backend = GeminiApiBackend("key", "model", transport)
        var callbackCalled = false

        backend.ask(GeminiRequest("cancel-me", "hola", "sistema")) { callbackCalled = true }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        backend.cancel("cancel-me")

        assertTrue(cancelled.await(2, TimeUnit.SECONDS))
        Thread.sleep(50)
        assertTrue(!callbackCalled)
    }
}
