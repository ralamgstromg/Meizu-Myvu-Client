package com.myvu.client.ai

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiApiBackendTest {
    @Test
    fun apiBackendMapsSuccessfulJsonToGeminiResult() {
        val transport = FakeTransport(200, """
            {"candidates":[{"content":{"parts":[{"text":"Hola desde API"}]}}]}
        """.trimIndent())
        val backend = GeminiApiBackend("secret-key", "gemini-2.0-flash", transport)
        var result: Result<GeminiResult>? = null
        val done = CountDownLatch(1)

        backend.ask(GeminiRequest("r1", "¿Qué hora es?", "Responde breve")) {
            result = it
            done.countDown()
        }
        assertTrue(done.await(2, TimeUnit.SECONDS))

        assertEquals("Hola desde API", result!!.getOrThrow().answer)
        assertEquals("gemini_api", result!!.getOrThrow().backendId)
        assertEquals("r1", result!!.getOrThrow().requestId)
        assertTrue(transport.body.contains("¿Qué hora es?"))
        assertTrue(transport.body.contains("Responde breve"))
        assertTrue(JSONObject(transport.body).has("systemInstruction"))
        assertFalse(JSONObject(transport.body).has("system_instruction"))
    }

    @Test
    fun apiBackendMapsUnauthorizedResponseToConfigurationError() {
        val backend = GeminiApiBackend("bad-key", "gemini-2.0-flash", FakeTransport(401, "{" +
            "\"error\":{\"message\":\"bad key\"}}"))
        var result: Result<GeminiResult>? = null
        val done = CountDownLatch(1)

        backend.ask(GeminiRequest("r2", "hola", "sistema")) {
            result = it
            done.countDown()
        }
        assertTrue(done.await(2, TimeUnit.SECONDS))

        assertTrue(result!!.isFailure)
        assertTrue(result!!.exceptionOrNull() is GeminiApiException)
        assertEquals(GeminiApiException.Kind.CONFIGURATION, (result!!.exceptionOrNull() as GeminiApiException).kind)
    }

    @Test
    fun apiBackendNeverLogsApiKeyOrPromptBody() {
        val transport = FakeTransport(200, """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""")
        val backend = GeminiApiBackend("secret-key", "gemini-2.0-flash", transport)
        val prompt = "prompt-never-log-9f2a"
        val done = CountDownLatch(1)
        backend.ask(GeminiRequest("r3", prompt, "system-never-log")) { done.countDown() }
        assertTrue(done.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun apiBackendRejectsMalformedResponse() {
        val backend = GeminiApiBackend("key", "model", FakeTransport(200, "not-json"))
        var result: Result<GeminiResult>? = null
        val done = CountDownLatch(1)

        backend.ask(GeminiRequest("r4", "hola", "sistema")) {
            result = it
            done.countDown()
        }
        assertTrue(done.await(2, TimeUnit.SECONDS))

        assertTrue(result!!.isFailure)
        assertEquals(GeminiApiException.Kind.MALFORMED_RESPONSE, (result!!.exceptionOrNull() as GeminiApiException).kind)
    }

    private class FakeTransport(
        private val status: Int,
        private val response: String
    ) : GeminiApiTransport {
        var body: String = ""
        override fun post(requestId: String, url: String, apiKey: String, requestBody: String): GeminiHttpResponse {
            body = requestBody
            return GeminiHttpResponse(status, response)
        }
    }
}
