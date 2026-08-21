package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.ai.ExternalInfoService
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class DuckDuckGoSearchHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val query = args.optString("query", "").ifBlank { args.optString("topic", "") }.trim()
            val category = args.optString("category", "").trim()

            if (query.isEmpty()) {
                return SkillResult(false, "Falta la consulta para la búsqueda en DuckDuckGo.")
            }

            val fullQuery = if (category.isNotEmpty()) "$query $category" else query

            val searchResult = withContext(Dispatchers.IO) {
                ExternalInfoService.executeSearch(fullQuery)
            }

            if (!searchResult.isNullOrBlank() && !searchResult.startsWith("No se encontraron")) {
                SkillResult(true, searchResult, searchResult)
            } else {
                SkillResult(false, "No se encontraron resultados en DuckDuckGo para '$fullQuery'.")
            }
        } catch (e: Exception) {
            LogBus.error("DuckDuckGoSearchHandler -> Error searching DuckDuckGo", e)
            SkillResult(false, "Error al realizar la búsqueda en DuckDuckGo: ${e.message}")
        }
    }
}
