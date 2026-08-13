package com.myvu.client.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection

/** Answers via the OpenAI Chat Completions API. */
class OpenAiClient(
    apiKey: String?,
    model: String?,
    systemPrompt: String?
) : AiHttpClient(AiProvider.OPENAI, apiKey, model, systemPrompt) {

    override fun endpoint(): String = ENDPOINT

    override fun authorize(conn: HttpURLConnection) {
        conn.setRequestProperty("authorization", "Bearer " + (apiKey ?: ""))
    }

    @Throws(JSONException::class)
    override fun buildBody(question: String): String {
        return JSONObject()
            .put("model", model)
            .put("max_completion_tokens", MAX_TOKENS)
            .put("messages", JSONArray()
                .put(JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt))
                .put(JSONObject()
                    .put("role", "user")
                    .put("content", question)))
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
