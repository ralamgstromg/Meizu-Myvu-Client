package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.database.TodoRepository
import com.myvu.client.service.MirrorNotificationListener
import com.myvu.client.service.MyvuService
import com.myvu.client.weather.WeatherSync
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Servicio de Daily Briefing Ejecutivo ("Mi Día" / "Buenos Días").
 * Sintetiza de forma compacta el estado completo de la jornada para audio TTS y visualización HUD.
 */
object DailyBriefingService {

    fun generateBriefingText(context: Context): String {
        val sb = StringBuilder()

        // 1. Saludo y Hora
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11 -> "Buenos días"
            in 12..18 -> "Buenas tardes"
            else -> "Buenas noches"
        }
        val timeStr = SimpleDateFormat("h:mm a", Locale("es", "CO")).format(Date())
        sb.append("$greeting. Son las $timeStr. ")

        // 2. Clima
        val weather = WeatherSync.lastSummary
        if (!weather.isNullOrBlank()) {
            sb.append("El clima: $weather. ")
        }

        // 3. Calendario / Agenda de Hoy
        try {
            val eventsStr = CalendarService.getEvents(context, "hoy")
            if (eventsStr.isNotBlank() && !eventsStr.contains("No tienes eventos") && !eventsStr.contains("permiso")) {
                sb.append("En tu agenda: $eventsStr. ")
            } else {
                sb.append("No tienes reuniones en agenda hoy. ")
            }
        } catch (e: Exception) {
            LogBus.warn("DailyBriefingService -> Error leyendo eventos de calendario: ${e.message}")
        }

        // 4. Tareas Pendientes (ToDo)
        try {
            val todoRepo = TodoRepository(context)
            val pendingTodos = todoRepo.getPendingTodos()
            if (pendingTodos.isNotEmpty()) {
                val count = pendingTodos.size
                val firstItems = pendingTodos.take(2).joinToString(", ") { it.title }
                sb.append("Tienes $count ${if (count == 1) "tarea pendiente" else "tareas pendientes"}: $firstItems. ")
            }
        } catch (e: Exception) {
            LogBus.warn("DailyBriefingService -> Error leyendo tareas: ${e.message}")
        }

        // 5. Notificaciones VIP sin leer
        try {
            val notifs = MirrorNotificationListener.getUnreadSummary(null)
            if (notifs.isNotBlank() && !notifs.contains("No tienes notificaciones")) {
                sb.append("Avisos pendientes: $notifs. ")
            }
        } catch (e: Exception) {
            LogBus.warn("DailyBriefingService -> Error leyendo notificaciones: ${e.message}")
        }

        // 6. Batería de Gafas
        try {
            val conn = MyvuService.activeConnection()
            val battery = conn?.glassesInfo()?.battery
            if (battery != null && battery in 0..100) {
                sb.append("Gafas al $battery% de batería.")
            }
        } catch (ignored: Exception) {
        }

        return sb.toString().trim()
    }

    fun generateHudBriefing(context: Context): String {
        val weatherPart = WeatherSync.lastSummary?.take(25) ?: "Clima OK"
        val batteryPart = MyvuService.activeConnection()?.glassesInfo()?.battery?.let { "Gafas:$it%" } ?: ""

        val todoCount = try {
            TodoRepository(context).getPendingTodos().size
        } catch (e: Exception) {
            0
        }

        return buildString {
            append(weatherPart)
            if (batteryPart.isNotBlank()) append(" | ").append(batteryPart)
            append("\n")
            if (todoCount > 0) append("$todoCount tareas pendientes") else append("Sin tareas pendientes")
        }.take(120)
    }
}
