package com.myvu.client.ai

import org.json.JSONArray
import org.json.JSONObject

/** Structured schema representation for Gemini actions. */
data class GeminiAction(
    val type: String,
    val arguments: Map<String, String> = emptyMap()
)

data class GeminiParsedResponse(
    val answer: String,
    val actions: List<GeminiAction> = emptyList()
)
