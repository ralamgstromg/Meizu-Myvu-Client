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
            val events = withContext(Dispatchers.IO) {
                CalendarService.getUpcomingEvents(context)
            }
            if (events.isNotBlank()) {
                SkillResult(true, events, events)
            } else {
                SkillResult(true, "No hay eventos ni reuniones agendadas próximas.", "No hay eventos próximos agendados.")
            }
        } catch (e: Exception) {
            LogBus.error("CalendarEventsHandler -> Error reading calendar", e)
            SkillResult(false, "Error al consultar el calendario: ${e.message}")
        }
    }
}
