package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.ai.ExternalInfoService
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WikipediaSearchHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val topic = args.optString("topic", "").trim()
        if (topic.isEmpty()) {
            return SkillResult(false, "Falta especificar el tema de Wikipedia.")
        }

        val wikiResult = withContext(Dispatchers.IO) {
            ExternalInfoService.fetchWikipedia(topic)
        }

        return if (!wikiResult.isNullOrBlank()) {
            SkillResult(true, wikiResult, wikiResult)
        } else {
            SkillResult(false, "No se encontró información en Wikipedia sobre '$topic'.")
        }
    }
}
