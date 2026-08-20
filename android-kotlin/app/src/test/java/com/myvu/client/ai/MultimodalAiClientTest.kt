package com.myvu.client.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultimodalAiClientTest {

    @Test
    fun testOpenAiMultimodalBodyConstruction() {
        val client = OpenAiClient("sk-test", "gpt-4o-mini", "System prompt")
        val imageDummy = byteArrayOf(1, 2, 3, 4, 5)
        val bodyStr = client.buildBodyWithImage("¿Qué ves en la imagen?", imageDummy, "image/jpeg")

        val root = JSONObject(bodyStr)
        assertEquals("gpt-4o-mini", root.getString("model"))
        val messages = root.getJSONArray("messages")
        assertEquals(2, messages.length())

        val userMessage = messages.getJSONObject(1)
        assertEquals("user", userMessage.getString("role"))
        val contentArray = userMessage.getJSONArray("content")
        assertEquals(2, contentArray.length())
        assertEquals("text", contentArray.getJSONObject(0).getString("type"))
        assertEquals("image_url", contentArray.getJSONObject(1).getString("type"))
    }

    @Test
    fun testClaudeMultimodalBodyConstruction() {
        val client = ClaudeClient("sk-test-claude", "claude-3-5-haiku-20241022", "System prompt")
        val imageDummy = byteArrayOf(10, 20, 30)
        val bodyStr = client.buildBodyWithImage("Describe esta foto", imageDummy, "image/png")

        val root = JSONObject(bodyStr)
        assertEquals("claude-3-5-haiku-20241022", root.getString("model"))
        assertEquals("System prompt", root.getString("system"))
        val messages = root.getJSONArray("messages")
        assertEquals(1, messages.length())

        val userMsg = messages.getJSONObject(0)
        val contentArray = userMsg.getJSONArray("content")
        assertEquals(2, contentArray.length())

        val imageBlock = contentArray.getJSONObject(0)
        assertEquals("image", imageBlock.getString("type"))
        val sourceObj = imageBlock.getJSONObject("source")
        assertEquals("base64", sourceObj.getString("type"))
        assertEquals("image/png", sourceObj.getString("media_type"))
        assertTrue(sourceObj.has("data"))
    }

    @Test
    fun testLocalAiMultimodalBodyConstruction() {
        val client = LocalAiClient("http://localhost:8080/v1/chat/completions", "key", "model", "System prompt")
        val imageDummy = byteArrayOf(9, 8, 7)
        val bodyStr = client.buildBodyWithImage("¿Qué es esto?", imageDummy, "image/jpeg")

        val root = JSONObject(bodyStr)
        assertEquals("model", root.getString("model"))
        val messages = root.getJSONArray("messages")
        val userMsg = messages.getJSONObject(1)
        val contentArray = userMsg.getJSONArray("content")
        assertEquals(2, contentArray.length())
    }
}
