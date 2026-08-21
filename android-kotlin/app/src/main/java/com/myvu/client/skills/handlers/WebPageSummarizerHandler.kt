package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Web Page Summarizer Handler:
 * Fetches HTML from a target URL using HttpURLConnection, strips HTML tags, and extracts clean text content.
 */
class WebPageSummarizerHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult = withContext(Dispatchers.IO) {
        try {
            val url = args.optString("url", "").trim()
            val maxBullets = args.optInt("max_bullet_points", 5)

            if (url.isEmpty()) {
                return@withContext SkillResult(false, "Falta especificar la dirección URL ('url').")
            }

            val validUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else url

            val connection = (URL(validUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0")
                instanceFollowRedirects = true
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                connection.disconnect()
                return@withContext SkillResult(false, "No se pudo acceder a la página web (HTTP $responseCode).")
            }

            val rawHtml = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            connection.disconnect()

            if (rawHtml.isBlank()) {
                return@withContext SkillResult(false, "La página web retornó un contenido vacío.")
            }

            // Strip HTML tags and clean whitespace
            val cleanText = rawHtml
                .replace(Regex("(?s)<script.*?>.*?</script>"), "")
                .replace(Regex("(?s)<style.*?>.*?</style>"), "")
                .replace(Regex("<[^>]*>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            val truncatedText = if (cleanText.length > 3500) {
                cleanText.substring(0, 3500) + "..."
            } else {
                cleanText
            }

            val summaryResult = StringBuilder()
            summaryResult.append("🌐 **Contenido extraído de $validUrl**:\n\n")
            summaryResult.append(truncatedText)

            SkillResult(
                success = true,
                message = summaryResult.toString(),
                payload = mapOf(
                    "url" to validUrl,
                    "characterCount" to cleanText.length,
                    "maxBullets" to maxBullets
                )
            )
        } catch (e: Exception) {
            LogBus.error("WebPageSummarizerHandler -> Error fetching URL", e)
            SkillResult(false, "Error al descargar o resumir la página web: ${e.message}")
        }
    }
}

