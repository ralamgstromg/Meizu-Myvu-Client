package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.routines.RoutineAction
import com.myvu.client.routines.RoutineChannel
import com.myvu.client.routines.RoutineScheduler
import com.myvu.client.routines.RoutineStore
import com.myvu.client.routines.ScheduledRoutine
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject
import java.text.Normalizer
import java.util.Calendar
import java.util.UUID

/** Creates a [ScheduledRoutine] from a spoken/typed request. */
class ScheduleRoutineHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val name = args.optString("name").trim().ifBlank { "Rutina" }
        val time = parseTime(args.optString("time"))
            ?: return SkillResult(false, "No entendí la hora de la rutina. Usa el formato 07:00.")
        val actions = parseActions(args.optString("actions"))
        if (actions.isEmpty()) {
            return SkillResult(false, "Dime qué incluir: agenda, recordatorios, tareas, cumpleaños, correos, TRM o clima.")
        }
        val routine = ScheduledRoutine(
            id = UUID.randomUUID().toString(),
            name = name,
            hour = time.first,
            minute = time.second,
            days = parseDays(args.optString("days")),
            actions = actions,
            channels = parseChannels(args.optString("channels"))
        )
        RoutineStore.save(context, routine)
        RoutineScheduler.schedule(context, routine)
        val what = RoutineAction.values().filter { it in actions }.joinToString(", ") { it.label.lowercase() }
        return SkillResult(true, "Listo. Rutina \"$name\" programada a las ${"%d:%02d".format(time.first, time.second)} con $what.")
    }

    companion object {
        internal fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase().trim()

        internal fun parseTime(raw: String): Pair<Int, Int>? {
            val m = Regex("(\\d{1,2})(?::(\\d{2}))?").find(raw) ?: return null
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            return if (h in 0..23 && min in 0..59) h to min else null
        }

        internal fun parseDays(raw: String): Set<Int> {
            val s = norm(raw)
            if (s.isEmpty() || "todos" in s || "diario" in s || "cada dia" in s) return ScheduledRoutine.EVERY_DAY
            if ("lunes a viernes" in s || "entre semana" in s || "laborales" in s) return ScheduledRoutine.WEEKDAYS
            if ("fin de semana" in s || "fines de semana" in s) return setOf(Calendar.SATURDAY, Calendar.SUNDAY)
            val map = mapOf(
                "lunes" to Calendar.MONDAY, "martes" to Calendar.TUESDAY, "miercoles" to Calendar.WEDNESDAY,
                "jueves" to Calendar.THURSDAY, "viernes" to Calendar.FRIDAY, "sabado" to Calendar.SATURDAY,
                "domingo" to Calendar.SUNDAY
            )
            return map.filterKeys { it in s }.values.toSet().ifEmpty { ScheduledRoutine.EVERY_DAY }
        }

        internal fun parseActions(raw: String): Set<RoutineAction> {
            val s = norm(raw)
            val out = mutableSetOf<RoutineAction>()
            if ("agenda" in s || "evento" in s || "reunion" in s || "calendario" in s) out += RoutineAction.AGENDA
            if ("recordatorio" in s) out += RoutineAction.REMINDERS
            if ("tarea" in s || "pendiente" in s) out += RoutineAction.TASKS
            if ("cumple" in s) out += RoutineAction.BIRTHDAYS
            if ("correo" in s || "email" in s || "mail" in s) out += RoutineAction.EMAILS
            if ("trm" in s || "dolar" in s || "divisa" in s) out += RoutineAction.TRM
            if ("clima" in s || "tiempo" in s) out += RoutineAction.WEATHER
            return out
        }

        internal fun parseChannels(raw: String): Set<RoutineChannel> {
            val s = norm(raw)
            val out = mutableSetOf<RoutineChannel>()
            if ("notific" in s) out += RoutineChannel.NOTIFICATION
            if ("voz" in s || "hablad" in s || "audio" in s) out += RoutineChannel.VOICE
            if ("gafa" in s || "lente" in s || "hud" in s) out += RoutineChannel.HUD
            return out.ifEmpty { setOf(RoutineChannel.NOTIFICATION) }
        }
    }
}
