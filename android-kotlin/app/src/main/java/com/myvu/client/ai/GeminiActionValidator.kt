package com.myvu.client.ai

import org.json.JSONArray
import org.json.JSONObject

object GeminiActionValidator {

    private const val MAX_ARG_LENGTH = 1000

    private val ALLOWLISTED_TYPES = setOf(
        "weather_query",
        "open_whatsapp",
        "open_telegram",
        "make_call",
        "web_search",
        "set_alarm",
        "set_timer",
        "volume_control",
        "media_control"
    )

    @JvmStatic
    fun parse(text: String?): GeminiParsedResponse {
        if (text.isNullOrBlank()) {
            return GeminiParsedResponse(answer = "")
        }

        val trimmed = text.trim()
        if (!trimmed.startsWith("{")) {
            return GeminiParsedResponse(answer = trimmed)
        }

        return try {
            val root = JSONObject(trimmed)
            val answer = root.optString("answer", "")
            val actionsArray = root.optJSONArray("actions")

            val validActions = mutableListOf<GeminiAction>()
            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    val obj = actionsArray.optJSONObject(i) ?: continue
                    val type = obj.optString("type", "")

                    if (type.isBlank() || !ALLOWLISTED_TYPES.contains(type)) {
                        continue
                    }

                    val argsObj = obj.optJSONObject("arguments")
                    val argsMap = mutableMapOf<String, String>()
                    var invalidArg = false

                    if (argsObj != null) {
                        val keys = argsObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = argsObj.optString(key, "")
                            if (value.length > MAX_ARG_LENGTH) {
                                invalidArg = true
                                break
                            }
                            argsMap[key] = value
                        }
                    }

                    if (!invalidArg) {
                        validActions.add(GeminiAction(type, argsMap))
                    }
                }
            }

            GeminiParsedResponse(answer = answer, actions = validActions)
        } catch (e: Exception) {
            GeminiParsedResponse(answer = trimmed)
        }
    }
}
