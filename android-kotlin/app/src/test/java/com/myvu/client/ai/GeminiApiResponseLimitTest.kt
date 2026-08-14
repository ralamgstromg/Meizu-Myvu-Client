package com.myvu.client.ai

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiApiResponseLimitTest {
    @Test
    fun oversizedResponseFailsBeforeJsonParsing() {
        val oversized = ByteArray(512 * 1024 + 1) { 'x'.code.toByte() }
        val input = ByteArrayInputStream(oversized)
        val transport = object : GeminiApiTransport {
            override fun post(requestId: String, url: String, apiKey: String, requestBody: String): GeminiHttpResponse {
                val text = input.readBytes().toString(StandardCharsets.UTF_8)
                return GeminiHttpResponse(200, text)
            }
        }
        val backend = GeminiApiBackend("key", "model", transport)
        var result: Result<GeminiResult>? = null
        val done = java.util.concurrent.CountDownLatch(1)

        backend.ask(GeminiRequest("large", "hola", "sistema")) {
            result = it
            done.countDown()
        }
        assertTrue(done.await(2, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue(result!!.isFailure)
        assertEquals(GeminiApiException.Kind.MALFORMED_RESPONSE, (result!!.exceptionOrNull() as GeminiApiException).kind)
    }
}
