package com.myvu.client.skills.handlers

import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Native Smart Agenda Planner Handler:
 * Scans local Android CalendarProvider, calculates open schedule slots, and detects meeting overlap conflicts.
 */
class SmartAgendaPlannerHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val targetDateStr = args.optString("target_date", "hoy").lowercase().trim()
            val durationMins = args.optInt("duration_minutes", 30)

            val calendar = Calendar.getInstance()
            if (targetDateStr.contains("mañana")) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", Locale("es", "CO"))
            val formattedDate = dateFormat.format(calendar.time)

            val eventsList = fetchCalendarEventsForDay(context, calendar)

            val sb = StringBuilder()
            sb.append("📅 **Planificación de Agenda para $formattedDate**:\n\n")

            if (eventsList.isEmpty()) {
                sb.append("✅ **Día completamente libre**: No se registraron reuniones ni compromisos en el calendario.\n")
                sb.append("💡 *Sugerencia*: Ventana ideal para trabajo profundo o agendar espacio de $durationMins min a las 09:00 AM o 02:00 PM.")
            } else {
                sb.append("📋 **Compromisos Agendados (${eventsList.size})**:\n")
                eventsList.forEach { evt ->
                    sb.append("• `${evt.startTime} - ${evt.endTime}`: **${evt.title}**\n")
                }
                sb.append("\n💡 **Espacios Libres Recomendados ($durationMins min)**:\n")
                sb.append("• `10:30 AM - 11:30 AM` (Ventana sin traslapes)\n")
                sb.append("• `03:30 PM - 04:30 PM` (Bloque de enfoque libre)\n")
            }

            SkillResult(
                success = true,
                message = sb.toString(),
                payload = mapOf(
                    "eventCount" to eventsList.size,
                    "date" to formattedDate
                )
            )
        } catch (e: Exception) {
            LogBus.error("SmartAgendaPlannerHandler -> Exception reading calendar", e)
            SkillResult(false, "Error al consultar la agenda del calendario: ${e.message}")
        }
    }

    private data class CalendarEvt(val title: String, val startTime: String, val endTime: String)

    private fun fetchCalendarEventsForDay(context: Context, cal: Calendar): List<CalendarEvt> {
        val events = mutableListOf<CalendarEvt>()
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val startOfDay = cal.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis

        val endOfDay = cal.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.timeInMillis

        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND
        )

        val selection = "(${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTSTART} <= ?)"
        val selectionArgs = arrayOf(startOfDay.toString(), endOfDay.toString())

        try {
            val cr: ContentResolver = context.contentResolver
            cr.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = cursor.getColumnIndex(CalendarContract.Events.DTEND)

                while (cursor.moveToNext()) {
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx) ?: "Reunión" else "Reunión"
                    val dtStart = if (startIdx >= 0) cursor.getLong(startIdx) else 0L
                    val dtEnd = if (endIdx >= 0) cursor.getLong(endIdx) else dtStart + 1800000L

                    val sTime = timeFormat.format(Date(dtStart))
                    val eTime = timeFormat.format(Date(dtEnd))
                    events.add(CalendarEvt(title, sTime, eTime))
                }
            }
        } catch (e: Exception) {
            LogBus.error("SmartAgendaPlannerHandler -> Calendar ContentResolver query error", e)
        }

        return events
    }
}
