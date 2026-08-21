package com.myvu.client.skills.handlers

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

/**
 * Native Quick Alarm and Timer Handler:
 * Launches system Intent to create alarms and countdown timers via voice or chat.
 */
class QuickAlarmTimerHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val action = args.optString("action", "set_timer").lowercase().trim()
            val timeOrDuration = args.optString("time_or_duration", "10m").trim()
            val label = args.optString("label", "Recordatorio").trim()

            if (action == "set_alarm") {
                val timeParts = timeOrDuration.split(":")
                val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 7
                val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    LogBus.error("QuickAlarmTimerHandler -> Alarm Intent failed", e)
                }

                val formattedTime = String.format("%02d:%02d", hour, minute)
                SkillResult(true, "⏰ **Alarma Programada**: Configurada para las $formattedTime (\"$label\").")
            } else {
                val seconds = parseDurationSeconds(timeOrDuration)
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    LogBus.error("QuickAlarmTimerHandler -> Timer Intent failed", e)
                }

                val mins = seconds / 60
                SkillResult(true, "⏱️ **Temporizador Configurado**: $mins minuto(s) para \"$label\".")
            }
        } catch (e: Exception) {
            LogBus.error("QuickAlarmTimerHandler -> Error handling alarm/timer", e)
            SkillResult(false, "Error al configurar alarma o temporizador: ${e.message}")
        }
    }

    private fun parseDurationSeconds(durationStr: String): Int {
        val clean = durationStr.lowercase().trim()
        val digits = clean.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 10
        return when {
            clean.endsWith("s") -> digits
            clean.endsWith("h") -> digits * 3600
            else -> digits * 60 // Default to minutes
        }
    }
}
