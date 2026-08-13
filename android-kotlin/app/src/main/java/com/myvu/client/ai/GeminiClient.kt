package com.myvu.client.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection

/** Answers via the Gemini generateContent API. */
class GeminiClient(
    apiKey: String?,
    model: String?,
    systemPrompt: String?
) : AiHttpClient(AiProvider.GEMINI, apiKey, model, systemPrompt) {

    override fun endpoint(): String = BASE + model + ":generateContent"

    override fun authorize(conn: HttpURLConnection) {
        conn.setRequestProperty("x-goog-api-key", apiKey ?: "")
    }

    @Throws(JSONException::class)
    override fun buildBody(question: String): String {
        return JSONObject()
            .put("system_instruction", JSONObject()
                .put("parts", JSONArray().put(JSONObject()
                    .put("text", systemPrompt))))
            .put("contents", JSONArray().put(JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject()
                    .put("text", question)))))
            .put("tools", JSONArray().put(JSONObject()
                .put("google_search", JSONObject())))
            .put("generationConfig", JSONObject()
                .put("maxOutputTokens", MAX_TOKENS))
            .toString()
    }

    @Throws(JSONException::class)
    override fun extractText(response: String): String {
        val candidate = JSONObject(response)
            .getJSONArray("candidates").getJSONObject(0)
        val content = candidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.getJSONObject(i).optString("text"))
        }
        return sb.toString()
    }

    companion object {
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"
        private const val MAX_TOKENS = 4096
    }
}
