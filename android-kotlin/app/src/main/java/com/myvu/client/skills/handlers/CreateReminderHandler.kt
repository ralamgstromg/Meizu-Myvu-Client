package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.database.ReminderRepository
import com.myvu.client.reminder.ReminderScheduler
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

class CreateReminderHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val title = args.optString("title", "").trim()
        val minutesStr = args.optString("minutes_from_now", "30").trim()
        val body = args.optString("body", "").trim()

        if (title.isEmpty()) {
            return SkillResult(false, "Falta especificar el título o asunto del recordatorio.")
        }

        val minutes = minutesStr.toIntOrNull() ?: 30
        val triggerAt = System.currentTimeMillis() + (minutes * 60 * 1000L)

        val repository = ReminderRepository(context)
        val reminder = repository.addReminder(
            title = title,
            triggerAt = triggerAt,
            body = body
        )

        if (reminder != null && reminder.id > 0L) {
            ReminderScheduler.scheduleReminder(context, reminder)
            return SkillResult(true, "Recordatorio programado para dentro de $minutes minutos: '$title'")
        } else {
            return SkillResult(false, "No se pudo guardar el recordatorio en la base de datos.")
        }
    }
}
