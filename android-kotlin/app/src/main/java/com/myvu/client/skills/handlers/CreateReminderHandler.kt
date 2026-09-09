package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.database.ReminderRepository
import com.myvu.client.reminder.ReminderScheduler
import com.myvu.client.reminder.ReminderTimeParser
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enhanced Reminder Creation Handler:
 * Parses relative durations ("en 20 minutos", "2h") and exact clock times ("17:30", "5:30 pm")
 * and schedules persistent alarms synced with Meizu Myvu AR glasses notifications.
 */
class CreateReminderHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val title = args.optString("title", "").trim()
            val timeInput = args.optString("time", "").ifBlank {
                args.optString("time_or_minutes", "").ifBlank {
                    args.optString("minutes_from_now", "30")
                }
            }.trim()
            val body = args.optString("body", "").trim()

            if (title.isEmpty()) {
                return SkillResult(false, "Falta especificar el título o motivo del recordatorio.")
            }

            val triggerAt: Long = if (timeInput.matches(Regex("^\\d+$"))) {
                val mins = timeInput.toInt()
                System.currentTimeMillis() + (mins * 60 * 1000L)
            } else {
                val parsedMillis = ReminderTimeParser.parseTimeToMillis(timeInput)
                if (parsedMillis > System.currentTimeMillis()) {
                    parsedMillis
                } else {
                    System.currentTimeMillis() + (30 * 60 * 1000L) // fallback 30m
                }
            }

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

                val timeFmt = SimpleDateFormat("h:mm a (EEEE d 'de' MMMM)", Locale("es", "CO"))
                val formattedTarget = timeFmt.format(Date(triggerAt))

                SkillResult(true, "⏰ **Recordatorio programado**: \"$title\" para el **$formattedTarget**.")
            } else {
                SkillResult(false, "No se pudo guardar el recordatorio en la base de datos.")
            }
        } catch (e: Exception) {
            LogBus.error("CreateReminderHandler -> Exception during execution", e)
            SkillResult(false, "Error al programar el recordatorio: ${e.message}")
        }
    }
}
