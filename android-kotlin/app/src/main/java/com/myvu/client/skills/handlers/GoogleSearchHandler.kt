package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.ai.ExternalInfoService
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GoogleSearchHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val query = args.optString("query", "").trim()
            if (query.isEmpty()) {
                return SkillResult(false, "Falta la consulta de búsqueda.")
            }

            val searchResult = withContext(Dispatchers.IO) {
                ExternalInfoService.executeSearch(query)
            }

            if (searchResult.isNotBlank() && !searchResult.startsWith("No se encontraron")) {
                SkillResult(true, searchResult, searchResult)
            } else {
                SkillResult(false, "No se encontraron resultados en la búsqueda.")
            }
        } catch (e: Exception) {
            LogBus.error("GoogleSearchHandler -> Exception during execution", e)
            SkillResult(false, "Error al ejecutar la búsqueda en Google: ${e.message}")
        }
    }
}
