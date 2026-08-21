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

    /**
     * Strips all Markdown syntax elements (#, *, _, `, links, list bullets) to produce
     * pure clean plain text suitable for display on MicroLED AR HUD glasses.
     */
    fun stripMarkdownToCleanText(rawInput: String?): String {
        if (rawInput.isNullOrBlank()) return ""
        val markdownCleaned = sanitizeToMarkdown(rawInput)
        return markdownCleaned
            .replace(Regex("^#{1,6}\\s+"), "")               // Remove Headers (#, ##)
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")        // Remove Bold **text**
            .replace(Regex("\\*(.*?)\\*"), "$1")            // Remove Italic *text*
            .replace(Regex("__(.*?)__"), "$1")              // Remove Bold __text__
            .replace(Regex("_(.*?)_"), "$1")                // Remove Italic _text_
            .replace(Regex("`{1,3}(.*?)`{1,3}"), "$1")       // Remove Inline code `code`
            .replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1") // Remove Links [text](url)
            .replace(Regex("^[\\s]*[-*+]\\s+"), "")          // Remove Bullet points
            .replace(Regex("^[\\s]*\\d+\\.\\s+"), "")        // Remove Numbered list items
            .replace(Regex("\\n{3,}"), "\n\n")              // Normalize multiple newlines
            .trim()
    }
}
