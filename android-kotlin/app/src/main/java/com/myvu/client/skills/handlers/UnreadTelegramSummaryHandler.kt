package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.service.MirrorNotificationListener
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class UnreadTelegramSummaryHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val summary = withContext(Dispatchers.IO) {
                MirrorNotificationListener.getUnreadSummary("telegram")
            }
            if (summary.isNotBlank()) {
                SkillResult(true, summary, summary)
            } else {
                SkillResult(true, "No tienes mensajes de Telegram pendientes por leer.", "No tienes mensajes de Telegram pendientes.")
            }
        } catch (e: Exception) {
            LogBus.error("UnreadTelegramSummaryHandler -> Error reading Telegram summary", e)
            SkillResult(false, "Error al consultar las notificaciones de Telegram: ${e.message}")
        }
    }
}
