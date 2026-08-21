package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.ai.CalendarService
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CalendarEventsHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val date = args.optString("date", "").trim()
            val query = args.optString("query", "").trim()

            val events = withContext(Dispatchers.IO) {
                CalendarService.getUpcomingEvents(context)
            }

            if (events.isNotBlank()) {
                var filtered = events
                if (date.isNotEmpty()) {
                    filtered = "[Eventos agendados para $date]\n" + filtered
                }
                if (query.isNotEmpty()) {
                    filtered = "[Filtrado por: '$query']\n" + filtered
                }
                SkillResult(true, filtered, filtered)
            } else {
                val msg = if (date.isNotEmpty()) "No hay eventos agendados para $date." else "No hay eventos ni reuniones agendadas próximas."
                SkillResult(true, msg, msg)
            }
        } catch (e: Exception) {
            LogBus.error("CalendarEventsHandler -> Error reading calendar", e)
            SkillResult(false, "Error al consultar el calendario: ${e.message}")
        }
    }
}
