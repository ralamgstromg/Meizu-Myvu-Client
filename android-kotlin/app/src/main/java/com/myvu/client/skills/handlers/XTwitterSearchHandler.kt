package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.ai.ExternalInfoService
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class XTwitterSearchHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val query = args.optString("query", "").ifBlank { args.optString("topic", "") }.trim()
            val topic = args.optString("topic", "").trim()
            val author = args.optString("author", "").trim()

            if (query.isEmpty() && author.isEmpty()) {
                return SkillResult(false, "Falta el término de búsqueda o usuario para X / Twitter.")
            }

            val queryBuilder = StringBuilder()
            if (author.isNotEmpty()) {
                val formattedAuthor = if (author.startsWith("@")) author else "@$author"
                queryBuilder.append("from:").append(formattedAuthor.replace("@", "")).append(" ")
            }
            if (query.isNotEmpty()) {
                queryBuilder.append(query)
            } else if (topic.isNotEmpty()) {
                queryBuilder.append(topic)
            }

            val targetSearch = "${queryBuilder.toString().trim()} site:x.com OR site:twitter.com"
            val searchResult = withContext(Dispatchers.IO) {
                ExternalInfoService.executeSearch(targetSearch)
            }

            if (!searchResult.isNullOrBlank() && !searchResult.startsWith("No se encontraron")) {
                SkillResult(true, searchResult, searchResult)
            } else {
                val fallbackSearch = withContext(Dispatchers.IO) {
                    ExternalInfoService.executeSearch("X Twitter ${queryBuilder.toString().trim()}")
                }
                if (!fallbackSearch.isNullOrBlank() && !fallbackSearch.startsWith("No se encontraron")) {
                    SkillResult(true, fallbackSearch, fallbackSearch)
                } else {
                    SkillResult(false, "No se encontraron publicaciones recientes en X / Twitter sobre '${queryBuilder.toString().trim()}'.")
                }
            }
        } catch (e: Exception) {
            LogBus.error("XTwitterSearchHandler -> Error searching X / Twitter", e)
            SkillResult(false, "Error al consultar información en X / Twitter: ${e.message}")
        }
    }
}
