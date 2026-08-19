package com.myvu.client.ai

import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract
import com.myvu.client.core.LogBus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Service to query upcoming calendar meetings and events from Android CalendarContract.
 */
object CalendarService {

    /**
     * Retrieves upcoming calendar events for the next [hours] hours.
     */
    fun getUpcomingEvents(context: Context, hours: Int = 24): String {
        return try {
            val contentResolver: ContentResolver = context.contentResolver
            val startMillis = System.currentTimeMillis()
            val endMillis = startMillis + (hours * 3600 * 1000L)

            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.ALL_DAY
            )

            val selection = "(${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTSTART} <= ?) AND (${CalendarContract.Events.DELETED} = 0)"
            val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
            val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

            val cursor = contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            if (cursor == null || cursor.count == 0) {
                cursor?.close()
                return "No tienes reuniones ni eventos agendados para las próximas $hours horas."
            }

            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEEE d 'de' MMMM", Locale.getDefault())
            val sb = StringBuilder("Tienes ${cursor.count} evento(s) agendado(s):\n")

            var count = 1
            while (cursor.moveToNext() && count <= 5) {
                val title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)) ?: "Sin título"
                val dtStart = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                val isAllDay = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1
                val location = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION))

                val dateStr = dateFormat.format(Date(dtStart))
                val timeStr = if (isAllDay) "Todo el día" else timeFormat.format(Date(dtStart))
                val locStr = if (!location.isNullOrBlank()) " ($location)" else ""

                sb.append("$count. $title - $dateStr a las $timeStr$locStr\n")
                count++
            }

            cursor.close()
            sb.toString().trim()
        } catch (e: SecurityException) {
            LogBus.error("CalendarService -> Missing READ_CALENDAR permission", e)
            "No tengo permiso para acceder a tu calendario. Por favor concede el permiso en los ajustes del teléfono."
        } catch (e: Exception) {
            LogBus.error("CalendarService -> Failed to query calendar", e)
            "No se pudo consultar el calendario en este momento."
        }
    }
}
