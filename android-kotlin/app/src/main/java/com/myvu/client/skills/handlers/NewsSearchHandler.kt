package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.ai.ExternalInfoService
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class NewsSearchHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val topic = args.optString("topic", "").ifBlank { args.optString("query", "noticias destacadas") }.trim()
            val news = withContext(Dispatchers.IO) {
                ExternalInfoService.fetchNewsSearch(topic) ?: ExternalInfoService.executeSearch("noticias $topic")
            }
            if (!news.isNullOrBlank() && !news.startsWith("No se encontraron")) {
                SkillResult(true, news, news)
            } else {
                SkillResult(false, "No se encontraron noticias recientes sobre '$topic'.")
            }
        } catch (e: Exception) {
            LogBus.error("NewsSearchHandler -> Error searching news", e)
            SkillResult(false, "Error al buscar noticias: ${e.message}")
        }
    }
}
