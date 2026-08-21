package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.ai.ExternalInfoService
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GoogleSearchHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val query = args.optString("query", "").trim()
        if (query.isEmpty()) {
            return SkillResult(false, "Falta la consulta de búsqueda.")
        }

        val searchResult = withContext(Dispatchers.IO) {
            ExternalInfoService.executeSearch(query)
        }

        return if (searchResult.isNotBlank()) {
            SkillResult(true, searchResult, searchResult)
        } else {
            SkillResult(false, "No se encontraron resultados en la búsqueda de Google.")
        }
    }
}
