package com.myvu.client.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection

/** Answers via the Claude Messages API. */
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

    @Throws(JSONException::class)
    override fun buildBody(question: String): String {
        return JSONObject()
            .put("model", model)
            .put("max_tokens", MAX_TOKENS)
            .put("system", systemPrompt)
            .put("messages", JSONArray().put(JSONObject()
                .put("role", "user")
                .put("content", question)))
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
