package com.myvu.client.ai

import com.myvu.client.core.LogBus
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection

/**
 * Answers through an OpenAI-compatible Chat Completions API (such as LiteLLM proxying to Gemini).
 * Supports native Tool Calling (Function Calling), Structured Outputs (json_object), and Multimodal queries.
 */
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

    override fun supportsToolCalling(): Boolean = true

    override fun authorize(conn: HttpURLConnection) {
        if (!apiKey.isNullOrBlank()) {
            val key = apiKey.trim()
            conn.setRequestProperty("authorization", "Bearer $key")
            conn.setRequestProperty("api-key", key)
            conn.setRequestProperty("x-api-key", key)
        }
    }

    @Throws(java.io.IOException::class)
    override fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?,
        jsonMode: Boolean,
        customReadTimeoutMs: Int?
    ): ChatCompletionResult {
        if (!isConfigured()) {
            throw java.io.IOException("${provider.displayName} is not fully configured")
        }
        val body = try {
            buildChatBody(messages, tools, jsonMode)
        } catch (e: JSONException) {
            throw java.io.IOException("Could not build chat request: ${e.message}", e)
        }
        val rawResponse = try {
            HttpRetry.execute(provider.displayName) {
                postRaw(body, customReadTimeoutMs)
            }
        } catch (e: java.io.IOException) {
            // Fallback: Si jsonMode falló o dió timeout, reintentar sin response_format
            if (jsonMode) {
                LogBus.warn("${provider.displayName} chat with jsonMode failed (${e.message}), retrying without jsonMode...")
                val fallbackBody = try {
                    buildChatBody(messages, tools, jsonMode = false)
                } catch (je: JSONException) {
                    throw e
                }
                HttpRetry.execute(provider.displayName) {
                    postRaw(fallbackBody, customReadTimeoutMs)
                }
            } else {
                throw e
            }
        }
        return parseChatCompletion(rawResponse)
    }

    @Throws(JSONException::class)
    fun buildChatBody(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?,
        jsonMode: Boolean = false
    ): String {
        val root = JSONObject()
        root.put("model", model)
        root.put("stream", false)
        root.put("max_tokens", MAX_TOKENS)

        val messagesArray = JSONArray()

        // If no explicit system message is provided in the list and a systemPrompt exists, prepend it
        val hasSystemMsg = messages.any { it.role.equals("system", ignoreCase = true) }
        if (!hasSystemMsg && !systemPrompt.isNullOrBlank()) {
            messagesArray.put(ChatMessage.system(systemPrompt).toJsonObject())
        }

        for (msg in messages) {
            messagesArray.put(msg.toJsonObject())
        }
        root.put("messages", messagesArray)

        if (!tools.isNullOrEmpty()) {
            val toolsArray = JSONArray()
            for (tool in tools) {
                toolsArray.put(tool.toOpenAiJsonObject())
            }
            root.put("tools", toolsArray)
            root.put("tool_choice", "auto")
        }

        if (jsonMode) {
            root.put("response_format", JSONObject().put("type", "json_object"))
        }

        return root.toString()
    }

    @Throws(JSONException::class)
    fun parseChatCompletion(response: String): ChatCompletionResult {
        val clean = response.trim()
        val root = JSONObject(clean)
        val choices = root.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val firstChoice = choices.getJSONObject(0)
            val msgObj = firstChoice.optJSONObject("message")
            if (msgObj != null) {
                val content = if (msgObj.isNull("content")) null else msgObj.optString("content")

                val toolCallsList = mutableListOf<ToolCall>()
                val toolCallsArray = msgObj.optJSONArray("tool_calls")
                if (toolCallsArray != null) {
                    for (i in 0 until toolCallsArray.length()) {
                        val tcObj = toolCallsArray.optJSONObject(i) ?: continue
                        val id = tcObj.optString("id", "call_$i")
                        val fnObj = tcObj.optJSONObject("function")
                        val fnName = fnObj?.optString("name", "") ?: ""
                        val fnArgs = fnObj?.optString("arguments", "{}") ?: "{}"
                        if (fnName.isNotBlank()) {
                            toolCallsList.add(ToolCall(id = id, functionName = fnName, argumentsJson = fnArgs))
                        }
                    }
                }

                return ChatCompletionResult(
                    content = content,
                    toolCalls = toolCallsList,
                    rawJson = clean
                )
            }
        }

        // Fallback to text extraction if format is non-standard
        val fallbackText = extractText(clean)
        return ChatCompletionResult(content = fallbackText, rawJson = clean)
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
        val raw = HttpRetry.execute(provider.displayName) {
            askOnce(body)
        }
        return extractText(raw)
    }

    @Throws(JSONException::class)
    override fun buildBody(question: String): String {
        return buildBodyWithImage(question, null, "image/jpeg")
    }

    @Throws(JSONException::class)
    fun buildBodyWithImage(question: String, imageBytes: ByteArray?, mimeType: String = "image/jpeg"): String {
        val userContentObj: Any = if (imageBytes != null && imageBytes.isNotEmpty()) {
            val b64 = java.util.Base64.getEncoder().encodeToString(imageBytes)
            val dataUrl = "data:$mimeType;base64,$b64"
            JSONArray()
                .put(JSONObject().put("type", "text").put("text", question))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUrl)))
        } else {
            question
        }

        val messages = JSONArray()
        if (!systemPrompt.isNullOrBlank()) {
            if (userContentObj is String) {
                val combinedText = "$systemPrompt\n\n=== PREGUNTA DEL USUARIO ===\n$userContentObj"
                messages.put(JSONObject().put("role", "user").put("content", combinedText))
            } else if (userContentObj is JSONArray) {
                val newArr = JSONArray()
                newArr.put(JSONObject().put("type", "text").put("text", "$systemPrompt\n\n=== PREGUNTA DEL USUARIO ==="))
                for (i in 0 until userContentObj.length()) {
                    newArr.put(userContentObj.get(i))
                }
                messages.put(JSONObject().put("role", "user").put("content", newArr))
            } else {
                messages.put(JSONObject().put("role", "user").put("content", userContentObj))
            }
        } else {
            messages.put(JSONObject().put("role", "user").put("content", userContentObj))
        }

        return JSONObject()
            .put("model", model)
            .put("stream", false)
            .put("max_tokens", MAX_TOKENS)
            .put("messages", messages)
            .toString()
    }

    @Throws(JSONException::class)
    override fun extractText(response: String): String {
        val clean = response.trim()
        if (clean.startsWith("data:")) {
            val sb = StringBuilder()
            for (rawLine in clean.split("\n")) {
                val line = rawLine.trim()
                if (line.startsWith("data:")) {
                    val jsonStr = line.substring(5).trim()
                    if ("[DONE]".equals(jsonStr, ignoreCase = true)) continue
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
    }
}
