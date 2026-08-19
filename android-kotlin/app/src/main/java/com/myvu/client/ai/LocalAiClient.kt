package com.myvu.client.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection

/** Answers through a user-configured OpenAI-compatible Chat Completions API. */
class LocalAiClient @JvmOverloads constructor(
    endpoint: String?,
    apiKey: String?,
    model: String?,
    systemPrompt: String?,
    ignoreSsl: Boolean = false
) : AiHttpClient(AiProvider.LOCAL, apiKey, model, systemPrompt, ignoreSsl) {

    private val configuredEndpoint: String = endpoint?.trim() ?: ""

    override fun isConfigured(): Boolean {
        return configuredEndpoint.isNotEmpty() && model.isNotEmpty()
    }

    override fun endpoint(): String = configuredEndpoint

    override fun authorize(conn: HttpURLConnection) {
        if (!apiKey.isNullOrBlank()) {
            val key = apiKey.trim()
            conn.setRequestProperty("authorization", "Bearer $key")
            conn.setRequestProperty("api-key", key)
            conn.setRequestProperty("x-api-key", key)
        }
    }

    @Throws(java.io.IOException::class)
    override fun askWithImage(question: String, imageBytes: ByteArray?, mimeType: String): String {
        if (imageBytes == null || imageBytes.isEmpty()) {
            return ask(question)
        }
        if (!isConfigured()) {
            throw java.io.IOException("${provider.displayName} is not fully configured")
        }
        val body = try {
            buildBodyWithImage(question, imageBytes, mimeType)
        } catch (e: JSONException) {
            throw java.io.IOException("could not build the multimodal request: ${e.message}", e)
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
        val userContentObj: Any = if (imageBytes != null && imageBytes.isNotEmpty()) {
            val b64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
            val dataUrl = "data:$mimeType;base64,$b64"
            JSONArray()
                .put(JSONObject().put("type", "text").put("text", question))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUrl)))
        } else {
            question
        }

        return JSONObject()
            .put("model", model)
            .put("stream", false)
            .put("max_tokens", MAX_TOKENS)
            .put("messages", JSONArray()
                .put(JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt))
                .put(JSONObject()
                    .put("role", "user")
                    .put("content", userContentObj)))
            .toString()
    }

    @Throws(JSONException::class)
    override fun extractText(response: String): String {
        val clean = response?.trim() ?: ""
        if (clean.startsWith("data:")) {
            val sb = StringBuilder()
            for (rawLine in clean.split("\n")) {
                val line = rawLine.trim()
                if (line.startsWith("data:")) {
                    val jsonStr = line.substring(5).trim()
                    if ("[DONE]".equalsIgnoreCase(jsonStr)) continue
                    try {
                        val json = JSONObject(jsonStr)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val choice = choices.getJSONObject(0)
                            val delta = choice.optJSONObject("delta")
                            if (delta != null && delta.has("content")) {
                                sb.append(delta.optString("content", ""))
                            } else {
                                val msg = choice.optJSONObject("message")
                                if (msg != null && msg.has("content")) {
                                    sb.append(msg.optString("content", ""))
                                }
                            }
                        }
                    } catch (ignored: JSONException) {
                    }
                }
            }
            if (sb.isNotEmpty()) return sb.toString()
        }

        val root = JSONObject(clean)
        val choices = root.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val choiceObj = choices.get(0)
            if (choiceObj is JSONObject) {
                val msgObj = choiceObj.opt("message")
                if (msgObj is JSONObject) {
                    return if (msgObj.isNull("content")) "" else msgObj.optString("content")
                } else if (msgObj is String) {
                    return msgObj
                }
                val delta = choiceObj.optJSONObject("delta")
                if (delta != null && delta.has("content")) {
                    return delta.optString("content")
                }
                if (choiceObj.has("text")) {
                    return choiceObj.optString("text")
                }
            } else if (choiceObj is String) {
                return choiceObj
            }
        }

        if (root.has("response")) {
            return root.optString("response")
        }
        if (root.has("data")) {
            val dataObj = root.get("data")
            if (dataObj is String) {
                return dataObj
            } else if (dataObj is JSONObject) {
                if (dataObj.has("content")) return dataObj.optString("content")
            }
        }

        throw JSONException("Unrecognized response format: $clean")
    }

    companion object {
        private const val MAX_TOKENS = 1024
        private fun String.equalsIgnoreCase(other: String): Boolean = this.equals(other, ignoreCase = true)
    }
}
