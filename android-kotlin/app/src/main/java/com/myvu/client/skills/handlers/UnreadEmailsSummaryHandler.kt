package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.service.MirrorNotificationListener
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class UnreadEmailsSummaryHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val summary = withContext(Dispatchers.IO) {
                MirrorNotificationListener.getUnreadSummary("email")
            }
            if (summary.isNotBlank()) {
                SkillResult(true, summary, summary)
            } else {
                SkillResult(true, "No tienes correos electrónicos pendientes por leer.", "No tienes correos pendientes.")
            }
        } catch (e: Exception) {
            LogBus.error("UnreadEmailsSummaryHandler -> Error reading emails summary", e)
            SkillResult(false, "Error al consultar los correos pendientes: ${e.message}")
        }
    }
}
