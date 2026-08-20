package com.myvu.client.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.util.Base64

/** Answers via the OpenAI Chat Completions API with multimodal vision support. */
class OpenAiClient(
    apiKey: String?,
    model: String?,
    systemPrompt: String?
) : AiHttpClient(AiProvider.OPENAI, apiKey, model, systemPrompt) {

    override fun endpoint(): String = ENDPOINT

    override fun authorize(conn: HttpURLConnection) {
        conn.setRequestProperty("authorization", "Bearer " + (apiKey ?: ""))
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
            val dataUrl = "data:$mimeType;base64,$b64"
            JSONArray()
                .put(JSONObject().put("type", "text").put("text", question))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUrl)))
        } else {
            question
        }

        return JSONObject()
            .put("model", model)
            .put("max_completion_tokens", MAX_TOKENS)
            .put("messages", JSONArray()
                .put(JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt))
                .put(JSONObject()
                    .put("role", "user")
                    .put("content", userContent)))
            .toString()
    }

    @Throws(JSONException::class)
    override fun extractText(response: String): String {
        val message = JSONObject(response)
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message")
        return if (message.isNull("content")) "" else message.optString("content")
    }

    companion object {
        private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
        private const val MAX_TOKENS = 1024
    }
}
