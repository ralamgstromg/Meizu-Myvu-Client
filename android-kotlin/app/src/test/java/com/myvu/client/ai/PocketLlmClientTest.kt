package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PocketLlmClientTest {

    @Test
    fun isConfiguredReturnsTrueForDefaultEndpoint() {
        val client = PocketLlmClient(
            endpoint = null,
            apiKey = null,
            model = null,
            systemPrompt = "You are a helpful assistant."
        )
        assertTrue(client.isConfigured())
    }

    @Test
    fun askFailsWithDescriptiveErrorMessageWhenServerIsNotRunning() {
        val client = PocketLlmClient(
            endpoint = "http://127.0.0.1:59999/v1/chat/completions",
            apiKey = "",
            model = "gemma-4-e2b-it",
            systemPrompt = "System test"
        )
        try {
            client.ask("Hello")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Pocket LLM Server Android no está activo") == true)
        }
    }
}
