package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.ai.ExternalInfoService
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WikipediaSearchHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val topic = args.optString("topic", "").trim()
            if (topic.isEmpty()) {
                return SkillResult(false, "Falta especificar el tema de Wikipedia.")
            }

            val wikiResult = withContext(Dispatchers.IO) {
                ExternalInfoService.executeSearch("que es $topic")
            }

            if (wikiResult.isNotBlank() && !wikiResult.startsWith("No se encontraron")) {
                SkillResult(true, wikiResult, wikiResult)
            } else {
                SkillResult(false, "No se encontró información sobre '$topic'.")
            }
        } catch (e: Exception) {
            LogBus.error("WikipediaSearchHandler -> Exception during execution", e)
            SkillResult(false, "Error al consultar Wikipedia: ${e.message}")
        }
    }
}
