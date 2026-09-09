package com.myvu.client.ai

import org.json.JSONObject

/**
 * Specification for a function/tool that an LLM can invoke (OpenAI/LiteLLM/Gemini schema).
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JSONObject
) {
    fun toOpenAiJsonObject(): JSONObject {
        val functionObj = JSONObject()
            .put("name", name)
            .put("description", description)
            .put("parameters", parameters)

        return JSONObject()
            .put("type", "function")
            .put("function", functionObj)
    }
}

/**
 * Representation of a tool call emitted by the model.
 */
data class ToolCall(
    val id: String,
    val functionName: String,
    val argumentsJson: String
)

/**
 * Chat message representing a turn in the conversation (system, user, assistant, tool).
 */
data class ChatMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject().put("role", role)
        if (content != null) {
            obj.put("content", content)
        } else if (toolCalls == null) {
            obj.put("content", "")
        }

        if (!toolCalls.isNullOrEmpty()) {
            val arr = org.json.JSONArray()
            for (tc in toolCalls) {
                val fn = JSONObject()
                    .put("name", tc.functionName)
                    .put("arguments", tc.argumentsJson)
                val tcObj = JSONObject()
                    .put("id", tc.id)
                    .put("type", "function")
                    .put("function", fn)
                arr.put(tcObj)
            }
            obj.put("tool_calls", arr)
        }

        if (!toolCallId.isNullOrBlank()) {
            obj.put("tool_call_id", toolCallId)
        }

        return obj
    }

    companion object {
        fun system(content: String): ChatMessage = ChatMessage(role = "system", content = content)
        fun user(content: String): ChatMessage = ChatMessage(role = "user", content = content)
        fun assistant(content: String?, toolCalls: List<ToolCall>? = null): ChatMessage =
            ChatMessage(role = "assistant", content = content, toolCalls = toolCalls)
        fun tool(toolCallId: String, content: String): ChatMessage =
            ChatMessage(role = "tool", content = content, toolCallId = toolCallId)
    }
}

/**
 * Result returned from a chat completion request that may contain direct content or tool calls.
 */
data class ChatCompletionResult(
    val content: String?,
    val toolCalls: List<ToolCall> = emptyList(),
    val rawJson: String = ""
) {
    val hasToolCalls: Boolean get() = toolCalls.isNotEmpty()
}
