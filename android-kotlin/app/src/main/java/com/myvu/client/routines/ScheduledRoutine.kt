package com.myvu.client.routines

import android.content.Context
import android.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** What a routine reports. Order here is the order of the composed briefing. */
enum class RoutineAction(val label: String) {
    AGENDA("Agenda de hoy"),
    REMINDERS("Recordatorios"),
    TASKS("Tareas pendientes"),
    BIRTHDAYS("Cumpleaños"),
    EMAILS("Correos sin leer"),
    TRM("TRM (dólar)"),
    WEATHER("Clima");
}

/** Where a routine's result is delivered. */
enum class RoutineChannel(val label: String) {
    NOTIFICATION("Notificación"),
    VOICE("Voz"),
    HUD("Gafas (HUD)");
}

/**
 * A briefing that runs at [hour]:[minute] on [days] (Calendar.DAY_OF_WEEK values).
 */
data class ScheduledRoutine(
    val id: String,
    val name: String,
    val hour: Int,
    val minute: Int,
    val days: Set<Int>,
    val actions: Set<RoutineAction>,
    val channels: Set<RoutineChannel>,
    val enabled: Boolean = true
) {
    /** Next trigger strictly after [fromMillis], or null when no day is selected. */
    fun nextTrigger(fromMillis: Long, calendar: Calendar = Calendar.getInstance()): Long? {
        if (days.isEmpty()) return null
        val cal = (calendar.clone() as Calendar).apply {
            timeInMillis = fromMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(8) {
            if (cal.get(Calendar.DAY_OF_WEEK) in days && cal.timeInMillis > fromMillis) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return null
    }

    /** Stable request code for this routine's PendingIntent. */
    val requestCode: Int get() = 0x52000000 or (id.hashCode() and 0x00FFFFFF)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("hour", hour)
        .put("minute", minute)
        .put("days", JSONArray(days.sorted()))
        .put("actions", JSONArray(actions.map { it.name }))
        .put("channels", JSONArray(channels.map { it.name }))
        .put("enabled", enabled)

    companion object {
        val WEEKDAYS = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
        val EVERY_DAY = WEEKDAYS + setOf(Calendar.SATURDAY, Calendar.SUNDAY)

        fun fromJson(o: JSONObject): ScheduledRoutine = ScheduledRoutine(
            id = o.getString("id"),
            name = o.getString("name"),
            hour = o.getInt("hour"),
            minute = o.getInt("minute"),
            days = o.getJSONArray("days").let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() },
            actions = o.getJSONArray("actions").let { a ->
                (0 until a.length()).mapNotNull { runCatching { RoutineAction.valueOf(a.getString(it)) }.getOrNull() }.toSet()
            },
            channels = o.getJSONArray("channels").let { a ->
                (0 until a.length()).mapNotNull { runCatching { RoutineChannel.valueOf(a.getString(it)) }.getOrNull() }.toSet()
            },
            enabled = o.optBoolean("enabled", true)
        )

        /** Templates offered in the UI. */
        fun templates(): List<ScheduledRoutine> = listOf(
            ScheduledRoutine(
                "tpl-morning", "Buenos días", 7, 0, WEEKDAYS,
                setOf(RoutineAction.AGENDA, RoutineAction.REMINDERS, RoutineAction.BIRTHDAYS, RoutineAction.TRM, RoutineAction.WEATHER),
                setOf(RoutineChannel.NOTIFICATION, RoutineChannel.VOICE)
            ),
            ScheduledRoutine(
                "tpl-evening", "Cierre del día", 18, 0, WEEKDAYS,
                setOf(RoutineAction.TASKS, RoutineAction.REMINDERS, RoutineAction.EMAILS),
                setOf(RoutineChannel.NOTIFICATION)
            ),
            ScheduledRoutine(
                "tpl-birthdays", "Cumpleaños de mañana", 20, 0, EVERY_DAY,
                setOf(RoutineAction.BIRTHDAYS),
                setOf(RoutineChannel.NOTIFICATION)
            )
        )
    }
}

/** Persists routines as JSON in SharedPreferences (included in backups). */
object RoutineStore {

    private const val KEY = "scheduled_routines"

    @Suppress("DEPRECATION")
    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    @Synchronized
    fun all(context: Context): List<ScheduledRoutine> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { ScheduledRoutine.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun get(context: Context, id: String): ScheduledRoutine? = all(context).firstOrNull { it.id == id }

    @Synchronized
    fun save(context: Context, routine: ScheduledRoutine) {
        val list = all(context).filterNot { it.id == routine.id } + routine
        write(context, list)
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        write(context, all(context).filterNot { it.id == id })
    }

    private fun write(context: Context, list: List<ScheduledRoutine>) {
        prefs(context).edit().putString(KEY, JSONArray(list.map { it.toJson() }).toString()).apply()
    }
}
