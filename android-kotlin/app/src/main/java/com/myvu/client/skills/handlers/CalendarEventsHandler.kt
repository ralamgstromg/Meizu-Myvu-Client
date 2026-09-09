package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.ai.CalendarService
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Calendar Events Handler:
 * Queries scheduled meetings and events with day filter (hoy, mañana), keyword search,
 * or pre-fills new event creation via system intent.
 */
class CalendarEventsHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val action = args.optString("action", "query").lowercase().trim()
            val date = args.optString("date", "").trim()
            val query = args.optString("query", "").trim()
            val title = args.optString("title", "").trim()
            val location = args.optString("location", "").trim()

            if (action == "create" || (action == "query" && title.isNotBlank() && query.isBlank())) {
                val eventTitle = title.ifBlank { query }
                val launched = CalendarService.launchCreateEventIntent(
                    context = context,
                    title = eventTitle,
                    location = location
                )
                return if (launched) {
                    SkillResult(true, "📅 **Abriendo calendario** para agendar: **$eventTitle**.")
                } else {
                    SkillResult(false, "No se pudo abrir el calendario para crear el evento.")
                }
            }

            val events = withContext(Dispatchers.IO) {
                CalendarService.getEvents(context, dateExpr = date, queryFilter = query)
            }

            SkillResult(true, events, events)
        } catch (e: Exception) {
            LogBus.error("CalendarEventsHandler -> Error handling calendar", e)
            SkillResult(false, "Error al interactuar con el calendario: ${e.message}")
        }
    }
}
