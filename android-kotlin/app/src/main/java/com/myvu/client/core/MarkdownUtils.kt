package com.myvu.client.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object MarkdownUtils {

    /**
     * Ensures any summary text from LLMs or database is returned as clean, human-readable Markdown.
     * Removes JSON wrappers, code fences, or unescapes JSON fields.
     */
    fun sanitizeToMarkdown(rawInput: String?): String {
        if (rawInput.isNullOrBlank()) return ""

        var cleaned = rawInput.trim()

        // 1. Remove ```markdown or ```json code fences
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replace(Regex("^```[a-zA-Z]*\\n?"), "")
                .replace(Regex("\\n?```$"), "")
                .trim()
        }

        // 2. If it's a JSON object string, extract summary field or unwrap object to Markdown
        if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
            try {
                val json = JSONObject(cleaned)
                val extractedSummary = when {
                    json.has("summary") -> json.optString("summary")
                    json.has("resumen") -> json.optString("resumen")
                    json.has("content") -> json.optString("content")
                    json.has("text") -> json.optString("text")
                    json.has("markdown") -> json.optString("markdown")
                    else -> null
                }

                if (!extractedSummary.isNullOrBlank() && !extractedSummary.startsWith("{")) {
                    return sanitizeToMarkdown(extractedSummary)
                }

                // If JSON object with multiple fields, convert to Markdown sections
                val sb = StringBuilder()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key == "action_items" || key == "mindmap_mermaid" || key == "mindmap") continue
                    val valObj = json.get(key)
                    val header = key.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                    sb.append("### 📌 ").append(header).append("\n")
                    when (valObj) {
                        is JSONObject -> sb.append(valObj.toString(2)).append("\n\n")
                        is JSONArray -> sb.append(valObj.toString(2)).append("\n\n")
                        else -> sb.append(valObj.toString()).append("\n\n")
                    }
                }
                val result = sb.toString().trim()
                if (result.isNotEmpty()) return result
            } catch (_: JSONException) {
                // Not valid JSON, continue with cleaned text
            }
        }

        return cleaned
    }
}
