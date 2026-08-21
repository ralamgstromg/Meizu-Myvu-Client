package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.service.MirrorNotificationListener
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class UnreadNotificationsHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val category: String? = if (args.has("category")) args.optString("category").ifBlank { null } else null
            val summary = withContext(Dispatchers.IO) {
                MirrorNotificationListener.getUnreadSummary(category)
            }
            if (summary.isNotBlank()) {
                SkillResult(true, summary, summary)
            } else {
                SkillResult(true, "No hay notificaciones ni avisos pendientes.", "No hay notificaciones pendientes.")
            }
        } catch (e: Exception) {
            LogBus.error("UnreadNotificationsHandler -> Error reading notifications", e)
            SkillResult(false, "Error al consultar las notificaciones: ${e.message}")
        }
    }
}
