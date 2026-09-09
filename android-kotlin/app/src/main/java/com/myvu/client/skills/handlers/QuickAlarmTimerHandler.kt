package com.myvu.client.skills.handlers

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject
import java.util.Calendar

/**
 * Enhanced Quick Alarm & Timer Handler:
 * Supports setting alarms, timers, showing active alarms/timers, and natural language duration parsing.
 */
class QuickAlarmTimerHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val action = args.optString("action", "set_timer").lowercase().trim()
            val timeOrDuration = args.optString("time_or_duration", "10m").trim()
            val label = args.optString("label", "Alarma MYVU").trim()

            when (action) {
                "show_alarms", "view_alarms" -> {
                    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return SkillResult(true, "⏰ **Mostrando alarmas configuradas**.")
                }
                "show_timers", "view_timers" -> {
                    val intent = Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return SkillResult(true, "⏱️ **Mostrando temporizadores activos**.")
                }
                "dismiss_alarm", "cancel_alarm" -> {
                    val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_NEXT)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(intent)
                        return SkillResult(true, "🛑 **Alarma desactivada / silenciada**.")
                    } catch (e: Exception) {
                        return SkillResult(false, "No se pudo desactivar la alarma: ${e.message}")
                    }
                }
                "set_alarm" -> {
                    val (hour, minute) = parseAlarmTime(timeOrDuration)
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, label)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    val formatted = String.format("%02d:%02d", hour, minute)
                    return SkillResult(true, "⏰ **Alarma programada** para las **$formatted** (\"$label\").")
                }
                else -> { // set_timer
                    val seconds = parseDurationSeconds(timeOrDuration)
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, label)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    val desc = when {
                        seconds >= 3600 -> "${seconds / 3600} hora(s) y ${(seconds % 3600) / 60} minuto(s)"
                        seconds >= 60 -> "${seconds / 60} minuto(s)"
                        else -> "$seconds segundo(s)"
                    }
                    return SkillResult(true, "⏱️ **Temporizador iniciado**: **$desc** para \"$label\".")
                }
            }
        } catch (e: Exception) {
            LogBus.error("QuickAlarmTimerHandler -> Error handling alarm/timer", e)
            SkillResult(false, "Error al configurar alarma o temporizador: ${e.message}")
        }
    }

    private fun parseAlarmTime(timeStr: String): Pair<Int, Int> {
        val clean = timeStr.lowercase().trim()
        val isPm = clean.contains("pm") || clean.contains("p.m.")
        val isAm = clean.contains("am") || clean.contains("a.m.")

        val digitsOnly = clean.replace(Regex("[^0-9:]"), "")
        val parts = digitsOnly.split(":")
        var hour = parts.getOrNull(0)?.toIntOrNull() ?: 7
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        if (isPm && hour < 12) hour += 12
        if (isAm && hour == 12) hour = 0

        return Pair(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    private fun parseDurationSeconds(durationStr: String): Int {
        val clean = durationStr.lowercase().trim()

        if (clean.contains("hora y media") || clean.contains("1.5h")) return 5400
        if (clean.contains("media hora")) return 1800

        var totalSeconds = 0

        // Horas
        val hourMatch = Regex("(\\d+)\\s*(?:h|hora|horas)").find(clean)
        if (hourMatch != null) {
            totalSeconds += (hourMatch.groupValues[1].toIntOrNull() ?: 0) * 3600
        }

        // Minutos
        val minMatch = Regex("(\\d+)\\s*(?:m|min|minuto|minutos)").find(clean)
        if (minMatch != null) {
            totalSeconds += (minMatch.groupValues[1].toIntOrNull() ?: 0) * 60
        }

        // Segundos
        val secMatch = Regex("(\\d+)\\s*(?:s|seg|segundo|segundos)").find(clean)
        if (secMatch != null) {
            totalSeconds += (secMatch.groupValues[1].toIntOrNull() ?: 0)
        }

        if (totalSeconds > 0) return totalSeconds

        // Fallback numérico simple
        val rawNum = clean.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 10
        return if (clean.endsWith("s")) rawNum else rawNum * 60
    }
}
