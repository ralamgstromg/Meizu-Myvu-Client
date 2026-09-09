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
                    json.has("answer") -> json.optString("answer")
                    json.has("message") -> json.optString("message")
                    json.has("response") -> json.optString("response")
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
        return formatCleanPlainTextForGlasses(rawInput)
    }

    /**
     * Complete sanitizer for text sent to the AR glasses HUD and spoken by TTS:
     * 1. Strips all Markdown syntax (headers, bold, italics, code fences, bullets, blockquotes).
     * 2. Strips HTML tags and unescapes entities.
     * 3. Strips decorative emojis and pictographs that render as glitches/boxes on the HUD.
     * 4. Normalizes numbers: removes thousands separators (commas/dots), preserves decimals ONLY with '.',
     *    and strips special currency symbols (e.g. '$', '€' -> 'EUR', '£' -> 'GBP').
     * 5. Removes system prompt metadata brackets and action tags.
     */
    fun formatCleanPlainTextForGlasses(rawInput: String?): String {
        if (rawInput.isNullOrBlank()) return ""
        var s = sanitizeToMarkdown(rawInput)

        // 1. Remove system context and Gemma headers
        s = s.replace(Regex("\\[Contexto del Sistema:[^\\]]*\\]", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("^Respuesta local[^\n:]*:\\s*", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("\\[SKILL:\\s*[^\\]]+\\]", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("\\[Acci[oó]n Ejecutada:\\s*([^\\]]+)\\]", RegexOption.IGNORE_CASE), "$1")
        s = s.replace(Regex("\\[Error[^\\]]*:\\s*([^\\]]+)\\]", RegexOption.IGNORE_CASE), "Error: $1")

        // 2. Strip HTML tags and entities
        s = s.replace(Regex("<[^>]*>"), " ")
        s = s.replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#x27;", "'")

        // 3. Strip Markdown syntax
        s = s.replace(Regex("(?m)^#{1,6}\\s+"), "")            // Headers (#, ##)
        s = s.replace("#", "")                                  // Strip any remaining hashes or hashtags
        s = s.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")       // Bold **text**
        s = s.replace(Regex("\\*([^*]+)\\*"), "$1")             // Italic *text*
        s = s.replace(Regex("__([^_]+)__"), "$1")               // Bold __text__
        s = s.replace(Regex("(?<=\\s|^)_([^_]+)_(?=\\s|$)"), "$1") // Italic _text_
        s = s.replace(Regex("~~([^~]+)~~"), "$1")               // Strikethrough ~~text~~
        s = s.replace(Regex("`{1,3}([^`]+)`{1,3}"), "$1")       // Inline code `code`
        s = s.replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1") // Links [text](url)
        s = s.replace(Regex("(?m)^[\\s]*[-*+•]\\s+"), "")       // Bullet lists
        s = s.replace(Regex("(?m)^[\\s]*\\d+\\.\\s+"), "")     // Numbered lists
        s = s.replace(Regex("(?m)^>\\s*"), "")                 // Blockquotes
        s = s.replace("•", " ")                                // Stray bullet symbols
        s = s.replace(Regex("(?m)^\\|.*?\\|$"), "")            // Tables

        // 4. Strip decorative Emojis and miscellaneous pictographs
        // Preserve standard Latin letters, numbers, punctuation, and math (+, -, =, %)
        s = s.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\uFE00-\\uFE0F\\u2300-\\u23FF\\u2B50\\u2B55\\u200D\\u200C]"), "")

        // 5. Clean Currencies & Numbers
        // Convert currency symbols to plain currency codes or strip
        s = s.replace(Regex("COP\\s*\\$|\\$\\s*COP"), "COP")
        s = s.replace("€", " EUR ")
        s = s.replace("£", " GBP ")
        s = s.replace("¥", " JPY ")
        s = s.replace("$", "") // Remove dollar signs

        // Remove Swiss apostrophe thousands separator: e.g. 1'000.00 -> 1000.00
        s = s.replace(Regex("(?<=\\d)'(?=\\d{3})"), "")

        // European thousands dots with multiple groups: 1.000.000 -> 1000000
        while (s.contains(Regex("(?<=\\d)\\.(?=\\d{3}\\.\\d{3})"))) {
            s = s.replace(Regex("(?<=\\d)\\.(?=\\d{3}\\.\\d{3})"), "")
        }
        while (s.contains(Regex("(?<=\\d)\\.(?=\\d{3}\\.)"))) {
            s = s.replace(Regex("(?<=\\d)\\.(?=\\d{3}\\.)"), "")
        }

        // Remove dot thousand separator when followed by decimal comma: e.g. 1.250,50 -> 1250,50
        s = s.replace(Regex("(?<=\\d)\\.(?=\\d{3},)"), "")

        // Remove dot thousand separator before units / currencies: e.g. 10.000 pasos -> 10000 pasos, 50.000 COP -> 50000 COP
        s = s.replace(Regex("(?<=\\d)\\.(?=\\d{3}(?:\\s*(?:COP|USD|EUR|GBP|pesos|d[oó]lares|pasos|km|kcal|bpm|mil|millones)\\b))", RegexOption.IGNORE_CASE), "")

        // Remove US comma thousands separators: e.g. 1,000,000 -> 1000000, 1,250.50 -> 1250.50
        while (s.contains(Regex("(?<=\\d),(?=\\d{3}(?:[.,\\s\\D]|$))"))) {
            s = s.replace(Regex("(?<=\\d),(?=\\d{3}(?:[.,\\s\\D]|$))"), "")
        }

        // Replace any remaining decimal commas between digits with dot: e.g. 12,50 -> 12.50, 0,5 -> 0.5
        s = s.replace(Regex("(?<=\\d),(?=\\d+)"), ".")

        // 6. Clean whitespace and punctuation
        s = s.replace(Regex("\\s+([.,;:!?])"), "$1")
        s = s.replace(Regex("([.,;:!?])([a-zA-ZáéíóúÁÉÍÓÚñÑ])"), "$1 $2")
        s = s.replace(Regex("[ \\t]+"), " ")
        s = s.replace(Regex("\\n{3,}"), "\n\n")

        return s.trim()
    }
}
