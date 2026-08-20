package com.myvu.client.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.util.Base64

/** Answers via the Claude Messages API with multimodal vision support. */
class ClaudeClient(
    apiKey: String?,
    model: String?,
    systemPrompt: String?
) : AiHttpClient(AiProvider.CLAUDE, apiKey, model, systemPrompt) {

    override fun endpoint(): String = ENDPOINT

    override fun authorize(conn: HttpURLConnection) {
        conn.setRequestProperty("x-api-key", apiKey ?: "")
        conn.setRequestProperty("anthropic-version", API_VERSION)
    }

    @Throws(IOException::class)
    override fun askWithImage(question: String, imageBytes: ByteArray?, mimeType: String): String {
        if (imageBytes == null || imageBytes.isEmpty()) {
            return ask(question)
        }
        if (!isConfigured()) {
            throw IOException("${provider.displayName} is not fully configured")
        }
        val body = try {
            buildBodyWithImage(question, imageBytes, mimeType)
        } catch (e: JSONException) {
            throw IOException("could not build the multimodal request: ${e.message}", e)
        }
        return HttpRetry.execute(provider.displayName) {
            askOnce(body)
        }
    }

    @Throws(JSONException::class)
    override fun buildBody(question: String): String {
        return buildBodyWithImage(question, null, "image/jpeg")
    }

    @Throws(JSONException::class)
    fun buildBodyWithImage(question: String, imageBytes: ByteArray?, mimeType: String = "image/jpeg"): String {
        val userContent: Any = if (imageBytes != null && imageBytes.isNotEmpty()) {
            val b64 = Base64.getEncoder().encodeToString(imageBytes)
            JSONArray()
                .put(JSONObject()
                    .put("type", "image")
                    .put("source", JSONObject()
                        .put("type", "base64")
                        .put("media_type", mimeType)
                        .put("data", b64)))
                .put(JSONObject()
                    .put("type", "text")
                    .put("text", question))
        } else {
            question
        }

        return JSONObject()
            .put("model", model)
            .put("max_tokens", MAX_TOKENS)
            .put("system", systemPrompt)
            .put("messages", JSONArray().put(JSONObject()
                .put("role", "user")
                .put("content", userContent)))
            .toString()
    }

    @Throws(JSONException::class)
    override fun extractText(response: String): String {
        val content = JSONObject(response).getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if ("text" == block.optString("type")) {
                sb.append(block.optString("text"))
            }
        }
        return sb.toString()
    }

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
        private const val MAX_TOKENS = 1024
    }
}
