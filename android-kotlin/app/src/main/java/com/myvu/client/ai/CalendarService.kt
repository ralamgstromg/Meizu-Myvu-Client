package com.myvu.client.ai

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.myvu.client.core.LogBus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Service to query and create calendar meetings and events using Android CalendarContract.
 */
object CalendarService {

    /**
     * Retrieves calendar events for a specific target day expression ("hoy", "mañana", or default hours).
     */
    fun getEvents(context: Context, dateExpr: String = "", queryFilter: String = ""): String {
        return try {
            val contentResolver: ContentResolver = context.contentResolver
            val cal = Calendar.getInstance()

            val normDate = dateExpr.lowercase().trim()
            val (startMillis, endMillis, label) = when {
                normDate.contains("mañana") -> {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    val start = cal.timeInMillis
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    Triple(start, cal.timeInMillis, "mañana")
                }
                normDate.contains("hoy") -> {
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    val start = cal.timeInMillis
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    Triple(start, cal.timeInMillis, "hoy")
                }
                else -> {
                    val start = System.currentTimeMillis()
                    val end = start + (24 * 3600 * 1000L)
                    Triple(start, end, "las próximas 24 horas")
                }
            }

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
                return "No tienes reuniones ni eventos agendados para $label."
            }

            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEEE d 'de' MMMM", Locale.getDefault())
            val sb = StringBuilder("📅 **Eventos para $label** (${cursor.count}):\n\n")

            var count = 0
            while (cursor.moveToNext()) {
                val title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)) ?: "Sin título"
                if (queryFilter.isNotBlank() && !title.contains(queryFilter, ignoreCase = true)) {
                    continue
                }

                count++
                val dtStart = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                val isAllDay = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1
                val location = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION))

                val dateStr = dateFormat.format(Date(dtStart))
                val timeStr = if (isAllDay) "Todo el día" else timeFormat.format(Date(dtStart))
                val locStr = if (!location.isNullOrBlank()) " 📍 $location" else ""

                sb.append("$count. **$title**\n   ⏰ $dateStr a las $timeStr$locStr\n")
            }

            cursor.close()
            if (count == 0 && queryFilter.isNotBlank()) {
                return "No se encontraron eventos que coincidan con '$queryFilter' para $label."
            }
            sb.toString().trim()
        } catch (e: SecurityException) {
            LogBus.error("CalendarService -> Missing READ_CALENDAR permission", e)
            "No tengo permiso para acceder a tu calendario. Por favor concede el permiso en los ajustes del teléfono."
        } catch (e: Exception) {
            LogBus.error("CalendarService -> Failed to query calendar", e)
            "No se pudo consultar el calendario en este momento: ${e.message}"
        }
    }

    /**
     * Backward-compatible helper for 24h query.
     */
    fun getUpcomingEvents(context: Context, hours: Int = 24): String {
        return getEvents(context, "")
    }

    /**
     * Launches the system calendar event insertion dialog prefilled.
     */
    fun launchCreateEventIntent(
        context: Context,
        title: String,
        description: String = "",
        location: String = "",
        startMillis: Long = System.currentTimeMillis() + 3600000L,
        durationMinutes: Int = 60
    ): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                if (description.isNotBlank()) putExtra(CalendarContract.Events.DESCRIPTION, description)
                if (location.isNotBlank()) putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + (durationMinutes * 60000L))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            LogBus.error("CalendarService -> Error launching insert event intent", e)
            false
        }
    }
}
