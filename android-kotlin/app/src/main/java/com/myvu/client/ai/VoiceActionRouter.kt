package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.database.NoteRepository
import com.myvu.client.database.ReminderRepository
import com.myvu.client.database.TodoRepository
import com.myvu.client.service.MirrorNotificationListener
import java.text.Normalizer

/**
 * Deterministic fast-path action router based on keywords and grammar patterns.
 * Intercepts explicit voice commands in under 5ms, avoiding LLM roundtrips and hallucinations.
 */
class VoiceActionRouter(
    private val context: Context,
    private val actionExecutor: PhoneActionExecutor
) {

    data class RouteResult(
        val handled: Boolean,
        val responseText: String = "",
        val source: AiResponse.Source = AiResponse.Source.AI,
        val isAsyncWeather: Boolean = false
    )

    fun tryRoute(rawQuery: String): RouteResult {
        if (rawQuery.isBlank()) return RouteResult(handled = false)

        val trimmed = rawQuery.trim()
        val normalized = normalize(trimmed)

        // 1. Llamadas telefónicas (incluyendo variaciones fonéticas comunes de STT: jamar, yamar, llama, marcar)
        val callMatch = Regex("^(llamar?|marcar?|marca|llama|call|jamar?|yamar?|llamas|llamame)\\s+(a|al|a\\s+mi)?\\s*(.+)$", RegexOption.IGNORE_CASE).find(normalized)
        if (callMatch != null) {
            val rawTarget = trimmed.substring(callMatch.groups[1]!!.range.last + 1)
            val target = cleanTarget(rawTarget)
            if (target.isNotBlank()) {
                LogBus.log("VoiceActionRouter -> Fast-Path makeCall: '$target'")
                actionExecutor.makeCall(target)
                return RouteResult(handled = true, responseText = "Llamando a $target...")
            }
        }

        // 2. WhatsApp y Mensajes (incluyendo frases como "enviar mensaje de whatsapp a Matías Castro, hola cómo vas")
        val waMatch = Regex("^(enviar?|manda|mandar?|escribir?|mensaje\\s+para|para)\\s+(un\\s+)?(mensaje\\s+de\\s+whatsapp|whatsapp|mensaje)?\\s*(a|al|a\\s+mi|para)?\\s*(.+)$", RegexOption.IGNORE_CASE).find(normalized)
        if (waMatch != null && !normalized.startsWith("para las ") && !normalized.startsWith("para el ")) {
            val payload = trimmed.substring(waMatch.groups[1]!!.range.last + 1)
                .replace(Regex("(?i)^(un\\s+)?(mensaje\\s+de\\s+whatsapp|whatsapp|mensaje)\\s*(a|al|a\\s+mi|para)?\\s*"), "")
                .trim()
            if (payload.isNotBlank()) {
                LogBus.log("VoiceActionRouter -> Fast-Path WhatsApp: '$payload'")
                actionExecutor.openWhatsApp(payload)
                return RouteResult(handled = true, responseText = "Preparando mensaje de WhatsApp...")
            }
        }

        // 3. Telegram
        val tgMatch = Regex("^(enviar?|manda|mandar?|escribir?)\\s+(un\\s+)?(telegram)\\s+(a|al)?\\s*(.+)$", RegexOption.IGNORE_CASE).find(normalized)
        if (tgMatch != null) {
            val payload = trimmed.substring(tgMatch.groups[1]!!.range.last + 1)
                .replace(Regex("(?i)^(un\\s+)?(telegram)\\s+(a|al|a\\s+mi)?\\s*"), "")
                .trim()
            if (payload.isNotBlank()) {
                LogBus.log("VoiceActionRouter -> Fast-Path Telegram: '$payload'")
                actionExecutor.openTelegram(payload)
                return RouteResult(handled = true, responseText = "Preparando mensaje de Telegram...")
            }
        }

        // 4. Resumen de Notificaciones
        if (normalized.matches(Regex(".*(resume|resumen|leer?|lee|revisa|revisar?|cuales|que)\\s+(mis\\s+|las\\s+)?(notificaciones|mensajes|chats|correos|emails).*")) ||
            normalized.contains("notificaciones pendientes") ||
            normalized.contains("mensajes pendientes")
        ) {
            LogBus.log("VoiceActionRouter -> Fast-Path unread notification summary")
            val type = when {
                normalized.contains("whatsapp") -> "whatsapp"
                normalized.contains("telegram") -> "telegram"
                normalized.contains("correo") || normalized.contains("email") || normalized.contains("gmail") -> "email"
                else -> "all"
            }
            val summary = MirrorNotificationListener.getUnreadSummary(type)
            return RouteResult(handled = true, responseText = summary)
        }

        // 5. Clima / Tiempo
        if (normalized.matches(Regex("^(como\\s+esta\\s+el\\s+clima|clima|temperatura|tiempo\\s+hoy|pronostico).*")) ||
            normalized.contains("actualiza el clima") || normalized.contains("consultar clima")
        ) {
            LogBus.log("VoiceActionRouter -> Fast-Path weather query")
            return RouteResult(handled = true, isAsyncWeather = true)
        }

        // 6. Listas de Tareas (To-Do)
        // 6a. Añadir Tarea: ej: "agrega a la lista compras comprar manzanas"
        val todoAddMatch = Regex("^(agrega|agregar?|anota|anotar?|pon|poner?|nueva\\s+tarea)\\s+(a\\s+la\\s+lista\\s+de\\s+|a\\s+la\\s+lista\\s+|en\\s+la\\s+lista\\s+de\\s+|en\\s+la\\s+lista\\s+|a\\s+|en\\s+)?([^:]+?)(:|\\s+que\\s+|\\s+de\\s+|\\s+tarea\\s+)?\\s+(.+)$", RegexOption.IGNORE_CASE).find(normalized)
        if (todoAddMatch != null && (normalized.contains("lista") || normalized.contains("tarea"))) {
            val list = cleanTarget(todoAddMatch.groupValues[3])
            val task = trimmed.substring(todoAddMatch.groups[5]!!.range.first).trim()
            val repo = TodoRepository(context)
            repo.createTodo(title = task, listName = list)
            LogBus.log("VoiceActionRouter -> Fast-Path todo add: [$list] '$task'")
            return RouteResult(handled = true, responseText = "Tarea agregada a la lista $list.")
        }

        // 6b. Marcar Tarea Realizada: ej: "marca como hecha la tarea comprar manzanas"
        val todoDoneMatch = Regex("^(marca|marcar?|completa|completar?)\\s+(como\\s+)?(hecha|completada|realizada|lista)\\s+(la\\s+tarea\\s+)?(.+)$", RegexOption.IGNORE_CASE).find(normalized)
        if (todoDoneMatch != null) {
            val taskPattern = cleanTarget(todoDoneMatch.groupValues[5])
            val repo = TodoRepository(context)
            repo.markCompletedByTitle(taskPattern, true)
            LogBus.log("VoiceActionRouter -> Fast-Path todo done: '$taskPattern'")
            return RouteResult(handled = true, responseText = "Tarea '$taskPattern' marcada como realizada.")
        }

        // 6c. Consultar Tareas: ej: "¿cuáles son mis tareas?", "tareas pendientes de compras"
        if (normalized.contains("tareas pendientes") || normalized.contains("mis tareas") || normalized.contains("que tareas tengo")) {
            val repo = TodoRepository(context)
            val list = if (normalized.contains(" de ")) normalized.substringAfter(" de ").trim() else null
            val summary = actionExecutor.listTodosSummary(list)
            return RouteResult(handled = true, responseText = summary)
        }

        // 7. Notas
        // 7a. Crear Nota: ej: "toma nota que la cita es a las 4"
        val noteMatch = Regex("^(toma\\s+nota|anota\\s+que|anota|nueva\\s+nota|apunta\\s+que|apunta)\\s+(.+)$", RegexOption.IGNORE_CASE).find(normalized)
        if (noteMatch != null) {
            val noteBody = trimmed.substring(noteMatch.groups[2]!!.range.first).trim()
            val repo = NoteRepository(context)
            repo.createNote(title = "", body = noteBody)
            LogBus.log("VoiceActionRouter -> Fast-Path note created: '$noteBody'")
            return RouteResult(handled = true, responseText = "Nota guardada.")
        }

        // 7b. Eliminar Nota: ej: "elimina la nota de reunión"
        val noteDelMatch = Regex("^(elimina|borra|borrar?|eliminar?)\\s+(la\\s+nota\\s+(de\\s+)?|nota\\s+)?(.+)$", RegexOption.IGNORE_CASE).find(normalized)
        if (noteDelMatch != null && normalized.contains("nota")) {
            val noteTitle = cleanTarget(noteDelMatch.groupValues[4])
            val repo = NoteRepository(context)
            repo.deleteByTitle(noteTitle)
            LogBus.log("VoiceActionRouter -> Fast-Path note deleted: '$noteTitle'")
            return RouteResult(handled = true, responseText = "Nota eliminada.")
        }

        // 8. Alarmas y Temporizadores
        val alarmMatch = Regex("^(pon|poner?|crear?|configura|configurar?|despiertame|alarma)\\s+(una\\s+)?(alarma\\s+(a\\s+las\\s+|para\\s+las\\s+)?|a\\s+las\\s+)?([0-9]{1,2}(:[0-9]{2})?.*)$", RegexOption.IGNORE_CASE).find(normalized)
        if (alarmMatch != null) {
            val timeStr = alarmMatch.groupValues[4]
            actionExecutor.setAlarm(timeStr)
            return RouteResult(handled = true, responseText = "Alarma configurada.")
        }

        val timerMatch = Regex("^(pon|poner?|crear?|temporizador)\\s+(un\\s+)?(temporizador\\s+(de\\s+)?)([0-9]+)\\s*(segundos?|minutos?|horas?).*$", RegexOption.IGNORE_CASE).find(normalized)
        if (timerMatch != null) {
            val count = timerMatch.groupValues[5].toIntOrNull() ?: 60
            val unit = timerMatch.groupValues[6]
            val totalSeconds = when {
                unit.startsWith("min") -> count * 60
                unit.startsWith("hor") -> count * 3600
                else -> count
            }
            actionExecutor.setTimer(totalSeconds.toString())
            return RouteResult(handled = true, responseText = "Temporizador iniciado para $count $unit.")
        }

        // 9. Reproducción en Apps de Terceros (YouTube Music, Spotify, YouTube)
        val playMatch = Regex("^(reproduce|reproducir?|pon|poner?|toca|tocar?|escuchar?)\\s+(.+?)\\s+(en|por)\\s+(youtube\\s+music|spotify|youtube|deezer|apple\\s+music|opentune).*$", RegexOption.IGNORE_CASE).find(normalized)
        if (playMatch != null) {
            val song = cleanTarget(playMatch.groupValues[2])
            val app = playMatch.groupValues[4].trim()
            actionExecutor.playInThirdPartyApp("$app: $song")
            return RouteResult(handled = true, responseText = "Abriendo $app y reproduciendo $song...")
        }

        // 10. Abrir Apps: ej: "abre la calculadora", "abrir instagram"
        val openAppMatch = Regex("^(abre|abrir?|lanzar?|lanza)\\s+(la\\s+app\\s+de\\s+|la\\s+aplicacion\\s+de\\s+|el\\s+|la\\s+)?([a-zA-Z0-9_ ]+)$", RegexOption.IGNORE_CASE).find(normalized)
        if (openAppMatch != null && !normalized.contains("nota") && !normalized.contains("lista") && !normalized.contains("teleprompter")) {
            val appName = cleanTarget(openAppMatch.groupValues[3])
            actionExecutor.openAppByName(appName)
            return RouteResult(handled = true, responseText = "Abriendo $appName...")
        }

        // 11. Control de Navegación HUD en Gafas
        if (normalized.startsWith("navega a ") || normalized.startsWith("navegar a ") || normalized.startsWith("inicia navegacion a ")) {
            val dest = trimmed.replace(Regex("(?i)^(navega\\s+a|navegar\\s+a|inicia\\s+navegacion\\s+a)\\s+"), "").trim()
            actionExecutor.startNavigation(dest)
            return RouteResult(handled = true, responseText = "Iniciando navegación hacia $dest en tus gafas...")
        }

        if (normalized == "deten navegacion" || normalized == "parar navegacion" || normalized == "cancelar navegacion" || normalized == "stop navigation") {
            actionExecutor.stopNavigation()
            return RouteResult(handled = true, responseText = "Navegación detenida.")
        }

        // 12. Teleprompter en Gafas: ej: "abre teleprompter con mi discurso"
        if (normalized.startsWith("abre teleprompter") || normalized.startsWith("proyecta ")) {
            val text = trimmed.replace(Regex("(?i)^(abre\\s+teleprompter(\\s+con)?|proyecta)\\s+"), "").trim()
            actionExecutor.openTeleprompter(text)
            return RouteResult(handled = true, responseText = "Proyectando texto en el teleprompter de las gafas...")
        }

        return RouteResult(handled = false)
    }

    private fun normalize(text: String): String {
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase().trim()
    }

    private fun cleanTarget(raw: String): String {
        var clean = raw.trim()
        clean = clean.replace(Regex("(?i)^(a|al|a\\s+mi|el|la|las|los)\\s+"), "").trim()
        // Manejar errores de STT donde la preposición 'a' se une al nombre (ej: "amatías" -> "matías")
        val lowerNoAccents = Normalizer.normalize(clean, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase()
        if (lowerNoAccents.matches(Regex("^a[a-z]{3,}.*")) && !lowerNoAccents.startsWith("ana") && !lowerNoAccents.startsWith("antonio") && !lowerNoAccents.startsWith("andres") && !lowerNoAccents.startsWith("alejandro") && !lowerNoAccents.startsWith("alvaro") && !lowerNoAccents.startsWith("arturo") && !lowerNoAccents.startsWith("alberto")) {
            clean = clean.substring(1).trim()
        }
        return clean
    }
}
