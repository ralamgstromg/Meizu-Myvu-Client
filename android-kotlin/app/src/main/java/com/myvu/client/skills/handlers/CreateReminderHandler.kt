package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.database.ReminderRepository
import com.myvu.client.reminder.ReminderScheduler
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

class CreateReminderHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val title = args.optString("title", "").trim()
            val minutesStr = args.optString("minutes_from_now", "30").trim()
            val body = args.optString("body", "").trim()

            if (title.isEmpty()) {
                return SkillResult(false, "Falta especificar el título o asunto del recordatorio.")
            }

            val minutes = minutesStr.toIntOrNull() ?: 30
            val triggerAt = System.currentTimeMillis() + (minutes * 60 * 1000L)

            val repository = ReminderRepository(context)
            val reminder = repository.createReminder(
                title = title,
                body = body,
                triggerAt = triggerAt
            )

            if (reminder != null && reminder.id > 0L) {
                val scheduled = ReminderScheduler.scheduleReminder(
                    context,
                    reminder.id,
                    triggerAt,
                    reminder.alarmRequestCode
                )
                if (!scheduled) {
                    repository.updateReminderState(reminder.id, "FAILED")
                }
                SkillResult(true, "Recordatorio programado para dentro de $minutes minutos: '$title'")
            } else {
                SkillResult(false, "No se pudo guardar el recordatorio en la base de datos.")
            }
        } catch (e: Exception) {
            LogBus.error("CreateReminderHandler -> Exception during execution", e)
            SkillResult(false, "Error al programar el recordatorio: ${e.message}")
        }
    }
}
