package com.myvu.client.ai

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiApiCancellationRaceTest {
    @Test
    fun cancellationBeforeWorkerStartsSuppressesTransportCallAndCleansRequest() {
        val postCalled = CountDownLatch(1)
        val transport = object : GeminiApiTransport {
            override fun post(requestId: String, url: String, apiKey: String, requestBody: String): GeminiHttpResponse {
                postCalled.countDown()
                return GeminiHttpResponse(200, "")
            }
        }
        val backend = GeminiApiBackend("key", "model", transport)
        backend.ask(GeminiRequest("race", "hola", "sistema")) {}
        backend.cancel("race")

        Thread.sleep(100)
        assertTrue(!postCalled.await(100, TimeUnit.MILLISECONDS))
        assertTrue(backend.activeRequestCountForTest() == 0)
        assertTrue(backend.cancelledRequestCountForTest() == 0)
    }
}
