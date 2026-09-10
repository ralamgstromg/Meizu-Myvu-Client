package com.myvu.client.ai

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
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
        val isAsyncWeather: Boolean = false,
        val isAsyncExternalSearch: Boolean = false,
        val searchQuery: String = "",
        val targetCity: String? = null
    )

    fun tryRoute(rawQuery: String): RouteResult {
        if (rawQuery.isBlank()) return RouteResult(handled = false)

        val rawTrimmed = rawQuery.trim()
        // Strip leading filler interjections (e.g. "¿Qué?", "Oye,", "Por favor,", "Eh,") while preserving questions like "Qué es..."
        val cleanedTrimmed = rawTrimmed
            .replace(Regex("(?i)^[¿¡?\\s]*(qué|que)\\s*[?,!]\\s*|(?i)^[¿¡?\\s]*(oye|eh|ah|hola|por\\s+favor)\\s*[,.]?\\s*"), "")
            .trim()
        val trimmed = if (cleanedTrimmed.isNotBlank()) cleanedTrimmed else rawTrimmed
        val normalized = normalize(trimmed)

        // 0a. Daily Briefing Ejecutivo ("Buenos días", "Mi día", "Resumen del día", "Inicia mi día")
        if (normalized == "buenos dias" || normalized == "buen dia" ||
            normalized == "mi dia" || normalized == "resumen del dia" ||
            normalized == "resumen diario" || normalized == "inicia mi dia" ||
            normalized == "briefing" || normalized == "dame mi briefing" ||
            normalized == "como pinta el dia" || normalized == "plan de hoy"
        ) {
            LogBus.log("VoiceActionRouter -> Fast-Path Daily Briefing")
            val text = DailyBriefingService.generateBriefingText(context)
            try {
                val hudText = DailyBriefingService.generateHudBriefing(context)
                val conn = com.myvu.client.service.MyvuService.activeConnection()
                conn?.openTeleprompter(hudText, "Mi Día")
            } catch (ignored: Exception) {}
            return RouteResult(handled = true, responseText = text)
        }

        // 0b. Modos de Rutina y Automatizaciones Contextuales
        if (normalized.contains("modo reunion") || normalized.contains("modo junta")) {
            val enable = !normalized.contains("desactiva") && !normalized.contains("quitar") && !normalized.contains("off") && !normalized.contains("termina") && !normalized.contains("salir")
            val resp = RoutineManager.setMeetingMode(context, enable)
            return RouteResult(handled = true, responseText = resp)
        }
        if (normalized.contains("modo auto") || normalized.contains("modo carro") || normalized.contains("modo vehiculo") || normalized.contains("modo conduccion") || normalized.contains("modo manejar")) {
            val enable = !normalized.contains("desactiva") && !normalized.contains("quitar") && !normalized.contains("off") && !normalized.contains("apaga") && !normalized.contains("llegue")
            val resp = RoutineManager.setDriveMode(context, enable)
            return RouteResult(handled = true, responseText = resp)
        }
        if (normalized.contains("modo gym") || normalized.contains("modo gimnasio") || normalized.contains("modo entrenamiento") || normalized.contains("modo ejercicio")) {
            val enable = !normalized.contains("desactiva") && !normalized.contains("quitar") && !normalized.contains("off") && !normalized.contains("terminar") && !normalized.contains("fin")
            val resp = RoutineManager.setGymMode(context, enable)
            return RouteResult(handled = true, responseText = resp)
        }
        if (normalized.contains("modo noche") || normalized.contains("modo dormir") || normalized.contains("modo descanso")) {
            val enable = !normalized.contains("desactiva") && !normalized.contains("quitar") && !normalized.contains("off") && !normalized.contains("despertar")
            val resp = RoutineManager.setNightMode(context, enable)
            return RouteResult(handled = true, responseText = resp)
        }
        if (normalized == "que modo esta activo" || normalized == "modo activo" || normalized == "estado de rutina") {
            val resp = RoutineManager.getActiveMode(context)
            return RouteResult(handled = true, responseText = resp)
        }

        // 0c. Memoria Espacial y Estacionamiento (Parking)
        if (normalized.contains("estacione aqui") || normalized.contains("estacione aca") ||
            normalized.contains("deje el carro aqui") || normalized.contains("deje el auto aqui") ||
            normalized.contains("guardar estacionamiento") || normalized.contains("guardar parqueadero") ||
            normalized.contains("recuerda donde estacione")
        ) {
            val resp = SpatialMemoryManager.saveParkingLocation(context)
            return RouteResult(handled = true, responseText = resp)
        }
        if (normalized.contains("donde estacione") || normalized.contains("donde deje el carro") ||
            normalized.contains("donde deje el auto") || normalized.contains("donde deje mi carro") ||
            normalized.contains("donde deje mi auto") || normalized.contains("donde esta el carro") ||
            normalized.contains("donde esta mi auto") || normalized.contains("donde parquee") ||
            normalized.contains("donde esta el vehiculo") || normalized.contains("donde esta el parqueadero")
        ) {
            val resp = SpatialMemoryManager.getParkingLocation(context)
            return RouteResult(handled = true, responseText = resp)
        }
        if (normalized.contains("borra mi estacionamiento") || normalized.contains("borrar estacionamiento") ||
            normalized.contains("olvida donde estacione") || normalized.contains("quitar estacionamiento") ||
            normalized.contains("borrar parqueadero")
        ) {
            val resp = SpatialMemoryManager.clearParkingLocation(context)
            return RouteResult(handled = true, responseText = resp)
        }

        // 0d. Despacho Rápido de Ubicación GPS a Contacto ("mándale mi ubicación a Carlos")
        val sendLocMatch = Regex("^(mandale|manda|enviar?|envia|comparte|compartir?)\\s+(mi\\s+)?ubicacion(\\s+actual)?(\\s+por\\s+(whatsapp|telegram))?\\s+(a|con|para)\\s+(.+)$", RegexOption.IGNORE_CASE).find(normalized)
        if (sendLocMatch != null) {
            val app = if (normalized.contains("telegram")) "telegram" else "whatsapp"
            val lastGroup = sendLocMatch.groups[7]
            val rawTarget = if (lastGroup != null && lastGroup.range.first < trimmed.length) {
                trimmed.substring(lastGroup.range.first).trim()
            } else {
                sendLocMatch.groupValues[7]
            }
            val target = cleanTarget(rawTarget)
            if (target.isNotBlank()) {
                LogBus.log("VoiceActionRouter -> Fast-Path sendLocation to $target via $app")
                actionExecutor.sendLocationToContact(target, app)
                return RouteResult(handled = true, responseText = "Enviando tu ubicación actual a $target...")
            }
        }

        // 1. Agenda / Calendario (Reuniones próximas)
        if (normalized.contains("reunion") || normalized.contains("reunione") || normalized.contains("agenda") ||
            normalized.contains("calendario") || normalized.contains("evento") || normalized.contains("cita") ||
            normalized.contains("compromiso") ||
            normalized.matches(Regex(".*(que tengo|tengo algo|tengo alguna|hay algo|tengo citas?|tengo planes)\\s+(para\\s+|el\\s+|en\\s+)?(hoy|manana|la tarde|la manana|esta semana).*"))
        ) {
            LogBus.log("VoiceActionRouter -> Fast-Path calendar events")
            val response = CalendarService.getUpcomingEvents(context)
            return RouteResult(handled = true, responseText = response)
        }

        // 2. Resumen de Notificaciones Activas
        if (normalized.contains("resumir notificacion") || normalized.contains("resumen de notificacion") || normalized.contains("notificaciones pendientes") || normalized.contains("que notificaciones") || normalized.contains("que avisos tengo")) {
            LogBus.log("VoiceActionRouter -> Fast-Path notification summary")
            val response = MirrorNotificationListener.getUnreadSummary(null)
            return RouteResult(handled = true, responseText = response)
        }

        // 3. Correos Pendientes / Mail
        if (normalized.contains("correos pendientes") || normalized.contains("mails sin leer") || normalized.contains("revisar correo") || normalized.contains("tengo correos")) {
            LogBus.log("VoiceActionRouter -> Fast-Path email summary")
            val response = MirrorNotificationListener.getUnreadSummary("correo")
            return RouteResult(handled = true, responseText = response)
        }

        // 4. Estado de Batería y Dispositivos
        if (normalized.contains("bateria") || normalized.contains("cuanta bateria")) {
            LogBus.log("VoiceActionRouter -> Fast-Path battery query")
            val response = actionExecutor.queryBatteryStatus()
            return RouteResult(handled = true, responseText = response)
        }

        // 4a. Salud y Bienestar (Pasos, Estrés, Ritmo Cardíaco, Resumen)
        if (normalized.contains("pasos") || normalized.contains("podometro") || normalized.contains("cuanto he caminado") || normalized.contains("cuantos pasos")) {
            LogBus.log("VoiceActionRouter -> Fast-Path health steps query")
            val resp = com.myvu.client.health.HealthService.getInstance(context).getStepsSummary()
            return RouteResult(handled = true, responseText = resp)
        }
        if (normalized.contains("estres") || normalized.contains("estresado") || normalized.contains("nivel de tension")) {
            LogBus.log("VoiceActionRouter -> Fast-Path health stress query")
            val resp = com.myvu.client.health.HealthService.getInstance(context).getStressSummary()
            return RouteResult(handled = true, responseText = resp)
        }
        if (normalized.contains("ritmo cardiaco") || normalized.contains("frecuencia cardiaca") || normalized.contains("pulsaciones") || normalized.contains("mi pulso")) {
            LogBus.log("VoiceActionRouter -> Fast-Path health heart rate query")
            val resp = com.myvu.client.health.HealthService.getInstance(context).getHeartRateSummary()
            return RouteResult(handled = true, responseText = resp)
        }
        if (normalized.contains("resumen de salud") || normalized.contains("resumen de actividad") ||
            normalized.contains("como esta mi salud") || normalized.contains("mi salud hoy") ||
            normalized.contains("resumen fitness") || normalized.contains("actividad fisica")
        ) {
            LogBus.log("VoiceActionRouter -> Fast-Path health full summary")
            val resp = com.myvu.client.health.HealthService.getInstance(context).getFullHealthSummary()
            return RouteResult(handled = true, responseText = resp)
        }

        // 4b. Control de Linterna (Torch)
        if (normalized == "enciende la linterna" || normalized == "prende la linterna" || normalized == "activa la linterna" ||
            normalized == "linterna encendida" || normalized == "prender linterna" || normalized == "encender linterna" ||
            normalized == "activa linterna" || normalized == "linterna on" || normalized == "enciende linterna") {
            LogBus.log("VoiceActionRouter -> Fast-Path flashlight ON")
            val resp = actionExecutor.setFlashlight(true)
            return RouteResult(handled = true, responseText = resp)
        }
        if (normalized == "apaga la linterna" || normalized == "desactiva la linterna" || normalized == "quita la linterna" ||
            normalized == "apagar linterna" || normalized == "desactivar linterna" || normalized == "linterna off" ||
            normalized == "apaga linterna") {
            LogBus.log("VoiceActionRouter -> Fast-Path flashlight OFF")
            val resp = actionExecutor.setFlashlight(false)
            return RouteResult(handled = true, responseText = resp)
        }

        // 4c. Control Multimedia y Música
        // 4c. Control Multimedia y Música
        if (normalized == "pausa la musica" || normalized == "pausar musica" || normalized == "pausa" ||
            normalized == "silencia la musica" || normalized == "pausar") {
            LogBus.log("VoiceActionRouter -> Fast-Path media pause")
            actionExecutor.pauseMusic()
            return RouteResult(handled = true, responseText = "Música pausada.")
        }
        if (normalized == "reproduce musica" || normalized == "reproducir musica" || normalized == "play musica" ||
            normalized == "reanuda la musica" || normalized == "reanudar musica" || normalized == "continua la musica" ||
            normalized == "seguir reproduciendo" || normalized == "play" || normalized == "reanudar" ||
            normalized == "sigue reproduciendo" || normalized == "continua") {
            LogBus.log("VoiceActionRouter -> Fast-Path media play")
            actionExecutor.resumeMusic()
            return RouteResult(handled = true, responseText = "Reproduciendo música.")
        }
        if (normalized == "siguiente cancion" || normalized == "pasa la cancion" || normalized == "pasar cancion" ||
            normalized == "cambia de cancion" || normalized == "cambiar cancion" || normalized == "siguiente pista" ||
            normalized == "proxima cancion" || normalized == "siguiente" || normalized == "salta la cancion" ||
            normalized == "saltar cancion" || normalized == "salta cancion" || normalized == "pasa cancion" ||
            normalized == "pon la siguiente" || normalized == "avanza cancion") {
            LogBus.log("VoiceActionRouter -> Fast-Path media next")
            actionExecutor.nextTrack()
            return RouteResult(handled = true, responseText = "Siguiente canción.")
        }
        if (normalized == "cancion anterior" || normalized == "anterior cancion" || normalized == "pista anterior" ||
            normalized == "retrocede la cancion" || normalized == "repite la cancion" || normalized == "anterior" ||
            normalized == "vuelve a la cancion anterior" || normalized == "cancion previa" || normalized == "pista previa") {
            LogBus.log("VoiceActionRouter -> Fast-Path media previous")
            actionExecutor.previousTrack()
            return RouteResult(handled = true, responseText = "Canción anterior.")
        }
        if (normalized == "deten la musica" || normalized == "detener la musica" || normalized == "para la musica" ||
            normalized == "parar musica" || normalized == "stop musica" || normalized == "para musica" ||
            normalized == "stop" || normalized == "para" || normalized == "deten") {
            LogBus.log("VoiceActionRouter -> Fast-Path media stop")
            actionExecutor.stopMusic()
            return RouteResult(handled = true, responseText = "Música detenida.")
        }
        val cleanNorm = normalized.replace(Regex("[¿¡?!.,;:\"']"), "").trim()
        if (cleanNorm == "que cancion esta sonando" || cleanNorm == "que cancion suena" ||
            cleanNorm == "que esta sonando" || cleanNorm == "que cancion es esta" ||
            cleanNorm == "que musica suena" || cleanNorm == "que musica esta sonando" ||
            cleanNorm == "nombre de la cancion" || cleanNorm == "info de la cancion" ||
            cleanNorm == "informacion de la cancion" || cleanNorm == "que suena") {
            LogBus.log("VoiceActionRouter -> Fast-Path media now playing")
            val nowPlayingText = actionExecutor.queryNowPlaying()
            return RouteResult(handled = true, responseText = nowPlayingText)
        }

        // 4c.1 Búsqueda en Apps de Terceros (NewPipe, OpenTune, Spotify, YouTube, etc.)
        val searchAppMatch = Regex("^(busca|buscar?|buscame|búscame|encuentra|encontrar?)\\s+(.+?)\\s+(en|por)\\s+(newpipe|opentune|innertune|rimusic|vimusic|youtube\\s+music|yt\\s+music|spotify|youtube|deezer|apple\\s+music|vlc).*$", RegexOption.IGNORE_CASE).find(normalized)
        if (searchAppMatch != null) {
            val query = searchAppMatch.groupValues[2].replace(Regex("(?i)^(canciones\\s+de|musica\\s+de|videos?\\s+de|el\\s+video\\s+de|la\\s+cancion\\s+de|el\\s+tema\\s+de)\\s+"), "").trim()
            val app = searchAppMatch.groupValues[4].trim()
            val resp = actionExecutor.searchInThirdPartyApp("$app: $query")
            return RouteResult(handled = true, responseText = resp)
        }

        // 4c.2 Reproducción en Apps de Terceros (NewPipe, OpenTune, Spotify, YouTube, etc.)
        val playMatch = Regex("^(reproduce|reproducir?|pon|poner?|toca|tocar?|escuchar?)\\s+(.+?)\\s+(en|por)\\s+(newpipe|opentune|innertune|rimusic|vimusic|youtube\\s+music|yt\\s+music|spotify|youtube|deezer|apple\\s+music|vlc).*$", RegexOption.IGNORE_CASE).find(normalized)
        if (playMatch != null) {
            val song = playMatch.groupValues[2].replace(Regex("(?i)^(la\\s+cancion\\s+de|la\\s+cancion|el\\s+tema\\s+de|el\\s+tema|canciones\\s+de|musica\\s+de)\\s+"), "").trim()
            val app = playMatch.groupValues[4].trim()
            val resp = actionExecutor.playInThirdPartyApp("$app: $song")
            return RouteResult(handled = true, responseText = resp)
        }

        // 4d. Control de Volumen y Modos de Sonido
        if (normalized == "sube el volumen" || normalized == "aumenta el volumen" || normalized == "subir volumen" ||
            normalized == "mas volumen" || normalized == "subele") {
            LogBus.log("VoiceActionRouter -> Fast-Path volume UP")
            val resp = actionExecutor.adjustVolume(increase = true)
            return RouteResult(handled = true, responseText = resp)
        }
        if (normalized == "baja el volumen" || normalized == "disminuye el volumen" || normalized == "bajar volumen" ||
            normalized == "menos volumen" || normalized == "bajale") {
            LogBus.log("VoiceActionRouter -> Fast-Path volume DOWN")
            val resp = actionExecutor.adjustVolume(increase = false)
            return RouteResult(handled = true, responseText = resp)
        }
        if (normalized == "silencia el telefono" || normalized == "silencia el movil" || normalized == "modo silencio" ||
            normalized == "pon el telefono en silencio" || normalized == "silenciar celular" || normalized == "silencio") {
            LogBus.log("VoiceActionRouter -> Fast-Path ringer silent")
            val resp = actionExecutor.setRingerMode(AudioManager.RINGER_MODE_SILENT)
            return RouteResult(handled = true, responseText = resp)
        }
        if (normalized == "pon en vibracion" || normalized == "modo vibracion" || normalized == "pon el movil en vibracion" ||
            normalized == "solo vibrar" || normalized == "vibracion") {
            LogBus.log("VoiceActionRouter -> Fast-Path ringer vibrate")
            val resp = actionExecutor.setRingerMode(AudioManager.RINGER_MODE_VIBRATE)
            return RouteResult(handled = true, responseText = resp)
        }
        if (normalized == "activa el sonido" || normalized == "modo normal" || normalized == "activa el timbre" ||
            normalized == "con sonido" || normalized == "quitar silencio") {
            LogBus.log("VoiceActionRouter -> Fast-Path ringer normal")
            val resp = actionExecutor.setRingerMode(AudioManager.RINGER_MODE_NORMAL)
            return RouteResult(handled = true, responseText = resp)
        }

        // 5a. Llamadas VoIP por WhatsApp
        val hasCallIntent = normalized.contains("llama") || normalized.contains("llamar") ||
                normalized.contains("videollama") || normalized.contains("marca") ||
                normalized.startsWith("anomar") || normalized.contains(" anomar") ||
                normalized.startsWith("asomar") || normalized.contains(" asomar") ||
                normalized.startsWith("jamar") || normalized.contains(" jamar") ||
                normalized.startsWith("yamar") || normalized.contains(" yamar")

        if ((normalized.contains("whatsapp") || normalized.contains("wasap")) &&
            hasCallIntent &&
            !normalized.contains("mensaje") && !normalized.contains("escribe") && !normalized.contains("texto")
        ) {
            val rawTarget = trimmed
                .replace(Regex("(?i)^(llamar?|llama|llamo|llamó|marcar?|marca|anomar?|asomar?|jamar?|yamar?|videollamar?|videollama|haz\\s+(una\\s+)?llamada|iniciar?\\s+llamada)\\s+"), "")
                .replace(Regex("(?i)\\s+(por|en|de)\\s*(whatsapp|wasap)$"), "")
                .replace(Regex("(?i)^(por|en|de)\\s*(whatsapp|wasap)\\s*"), "")
                .replace(Regex("(?i)^(a|al|a\\s+mi|con|para)\\s+"), "")
                .trim()
            val target = cleanTarget(rawTarget)
            if (target.isNotBlank()) {
                LogBus.log("VoiceActionRouter -> Fast-Path WhatsApp VoIP Call: '$target'")
                actionExecutor.makeWhatsAppCall(target)
                return RouteResult(handled = true, responseText = "Llamando a $target por WhatsApp...")
            }
        }

        // 5b. Llamadas VoIP por Microsoft Teams
        if (normalized.contains("teams") &&
            (normalized.contains("llama") || normalized.contains("llamar") || normalized.contains("videollama") || normalized.contains("marca")) &&
            !normalized.contains("mensaje") && !normalized.contains("escribe")
        ) {
            val rawTarget = trimmed
                .replace(Regex("(?i)^(llamar?|llama|llamo|llamó|marcar?|marca|haz\\s+(una\\s+)?llamada|iniciar?\\s+llamada)\\s+"), "")
                .replace(Regex("(?i)\\s+(por|en|de)\\s*teams$"), "")
                .replace(Regex("(?i)^(por|en|de)\\s*teams\\s*"), "")
                .replace(Regex("(?i)^(a|al|a\\s+mi|con|para)\\s+"), "")
                .trim()
            val target = cleanTarget(rawTarget)
            if (target.isNotBlank()) {
                LogBus.log("VoiceActionRouter -> Fast-Path Teams Call: '$target'")
                actionExecutor.makeTeamsCall(target)
                return RouteResult(handled = true, responseText = "Iniciando llamada por Teams a $target...")
            }
        }

        // 5c. Llamadas VoIP por Google Chat / Google Meet
        if ((normalized.contains("google chat") || normalized.contains("chat de google") || normalized.contains("google meet") || (normalized.contains("meet") && (normalized.contains("llama") || normalized.contains("llamada")))) &&
            !normalized.contains("mensaje") && !normalized.contains("escribe")
        ) {
            val rawTarget = trimmed
                .replace(Regex("(?i)^(llamar?|llama|llamo|llamó|marcar?|marca|haz\\s+(una\\s+)?llamada|iniciar?\\s+llamada)\\s+"), "")
                .replace(Regex("(?i)\\s+(por|en|de)\\s*(google\\s+chat|chat\\s+de\\s+google|google\\s+meet|meet)$"), "")
                .replace(Regex("(?i)^(por|en|de)\\s*(google\\s+chat|chat\\s+de\\s+google|google\\s+meet|meet)\\s*"), "")
                .replace(Regex("(?i)^(a|al|a\\s+mi|con|para)\\s+"), "")
                .trim()
            val target = cleanTarget(rawTarget)
            if (target.isNotBlank()) {
                LogBus.log("VoiceActionRouter -> Fast-Path Google Chat/Meet Call: '$target'")
                actionExecutor.makeGoogleChatCall(target)
                return RouteResult(handled = true, responseText = "Iniciando llamada por Google Chat a $target...")
            }
        }

        // 5d. Llamadas telefónicas estándar (celular)
        val callMatch = Regex("^(llamar?|marcar?|marca|llama|llamo|llamó|llamas|llamame|marcale|marcarle|call|jamar?|yamar?)\\s+(a|al|a\\s+mi)?\\s*(.+)$", RegexOption.IGNORE_CASE).find(normalized)
        if (callMatch != null) {
            val rawTarget = trimmed.substring(callMatch.groups[1]!!.range.last + 1)
            val target = cleanTarget(rawTarget)
            if (target.isNotBlank()) {
                LogBus.log("VoiceActionRouter -> Fast-Path makeCall: '$target'")
                actionExecutor.makeCall(target)
                return RouteResult(handled = true, responseText = "Llamando a $target...")
            }
        }

        // 6. SMS y Mensajes de Texto
        val smsMatch = Regex("^(enviar?|envio|envió|envia|envía|envias|envías|manda|mandar?|mando|mandó|mandale|enviarle|mandarle|escribe|escribir?|escribirle)\\s+(un\\s+)?(sms|mensaje\\s+de\\s+texto|texto)\\s*(a|al|a\\s+mi|para)?\\s*(.+)$", RegexOption.IGNORE_CASE).find(normalized)
        if (smsMatch != null) {
            val payload = trimmed
                .replace(Regex("(?i)^(enviar?|envio|envió|envia|envía|envias|envías|manda|mandar?|mando|mandó|mandale|enviarle|mandarle|escribe|escribir?|escribirle)\\s+(un\\s+)?(sms|mensaje\\s+de\\s+texto|texto)\\s*(a|al|a\\s+mi|para)?\\s*"), "")
                .trim()
            if (payload.isNotBlank()) {
                LogBus.log("VoiceActionRouter -> Fast-Path SMS: '$payload'")
                actionExecutor.sendSms(payload)
                return RouteResult(handled = true, responseText = "Enviando mensaje de texto...")
            }
        }

        // 7. WhatsApp y Mensajes
        val waMatch = Regex("^(enviar?|envio|envió|envia|envía|envias|envías|manda|mandar?|mando|mandó|mandale|enviarle|mandarle|escribe|escribir?|escribirle|mensaje\\s+para|para)\\s+(un\\s+)?(mensaje\\s+de\\s+whatsapp|mensaje\\s+por\\s+whatsapp|whatsapp|mensaje)?\\s*(a|al|a\\s+mi|para)?\\s*(.+)$", RegexOption.IGNORE_CASE).find(normalized)
        if (waMatch != null && !normalized.startsWith("para las ") && !normalized.startsWith("para el ") && !normalized.contains("sms") && !normalized.contains("de texto")) {
            val payload = trimmed
                .replace(Regex("(?i)^(enviar?|envio|envió|envia|envía|envias|envías|manda|mandar?|mando|mandó|mandale|enviarle|mandarle|escribe|escribir?|escribirle)\\s+(un\\s+)?(mensaje\\s+de\\s+whatsapp|mensaje\\s+por\\s+whatsapp|whatsapp|mensaje)?\\s*(a|al|a\\s+mi|para)?\\s*"), "")
                .replace(Regex("(?i)^(un\\s+)?(mensaje\\s+de\\s+whatsapp|mensaje\\s+por\\s+whatsapp|whatsapp|mensaje)\\s*(a|al|a\\s+mi|para)?\\s*"), "")
                .trim()
            if (payload.isNotBlank()) {
                LogBus.log("VoiceActionRouter -> Fast-Path WhatsApp: '$payload'")
                actionExecutor.openWhatsApp(payload)
                val isAutoSendEnabled = com.myvu.client.service.AutoSendAccessibilityService.isAccessibilityServiceEnabled(context)
                val response = if (isAutoSendEnabled) {
                    "Enviando mensaje de WhatsApp..."
                } else {
                    "Abriendo WhatsApp. Para envío automático sin tocar la pantalla, activa el Asistente MYVU en Accesibilidad."
                }
                return RouteResult(handled = true, responseText = response)
            }
        }

        if (normalized.contains("whatsapp") && !hasCallIntent) {
            val waPayload = trimmed
                .replace(Regex("(?i)^(enviar?|envio|envió|envia|envía|manda|mandar?|mandó|escribe|escribir?)\\s+(un\\s+)?(mensaje\\s+de\\s+whatsapp|mensaje\\s+por\\s+whatsapp|whatsapp|mensaje)?\\s*(a|al|a\\s+mi|para)?\\s*"), "")
                .replace(Regex("(?i)^(un\\s+)?(mensaje\\s+de\\s+whatsapp|mensaje\\s+por\\s+whatsapp|whatsapp|mensaje)\\s*(a|al|a\\s+mi|para)?\\s*"), "")
                .trim()
            if (waPayload.isNotBlank() && waPayload.length > 3) {
                LogBus.log("VoiceActionRouter -> Fast-Path WhatsApp fallback: '$waPayload'")
                actionExecutor.openWhatsApp(waPayload)
                val isAutoSendEnabled = com.myvu.client.service.AutoSendAccessibilityService.isAccessibilityServiceEnabled(context)
                val response = if (isAutoSendEnabled) {
                    "Enviando mensaje de WhatsApp..."
                } else {
                    "Abriendo WhatsApp. Para envío automático sin tocar la pantalla, activa el Asistente MYVU en Accesibilidad."
                }
                return RouteResult(handled = true, responseText = response)
            }
        }

        // 7. Telegram
        val tgMatch = Regex("^(enviar?|envio|envió|envia|envía|manda|mandar?|mandó|mandale|escribe|escribir?)\\s+(un\\s+)?(telegram|mensaje\\s+de\\s+telegram)\\s+(a|al)?\\s*(.+)$", RegexOption.IGNORE_CASE).find(normalized)
        if (tgMatch != null) {
            val payload = trimmed
                .replace(Regex("(?i)^(enviar?|envio|envió|envia|envía|manda|mandar?|mandó|mandale|escribe|escribir?)\\s+(un\\s+)?(telegram|mensaje\\s+de\\s+telegram)\\s*(a|al|a\\s+mi)?\\s*"), "")
                .trim()
            if (payload.isNotBlank()) {
                LogBus.log("VoiceActionRouter -> Fast-Path Telegram: '$payload'")
                com.myvu.client.service.AutoSendAccessibilityService.triggerTelegramAutoSend()
                actionExecutor.openTelegram(payload)
                return RouteResult(handled = true, responseText = "Enviando mensaje de Telegram...")
            }
        }

        // 4. Resumen de Notificaciones
        val isNotificationQuery = (normalized.contains("notificacion") || normalized.contains("notificaciones")) &&
                (normalized.contains("tengo") || normalized.contains("pendientes") ||
                 normalized.contains("leer") || normalized.contains("lee") ||
                 normalized.contains("revisa") || normalized.contains("revisar") ||
                 normalized.contains("resumen") || normalized.contains("resume") ||
                 normalized.contains("cuales") || normalized.contains("que") ||
                 normalized.contains("hay") || normalized.contains("nuevas") ||
                 normalized.contains("recientes") || normalized.contains("por leer"))
        val isOtherUnreadQuery = normalized.matches(Regex(".*(resume|resumen|leer?|lee|revisa|revisar?|cuales|que)\\s+(mis\\s+|las\\s+)?(notificaciones|mensajes|chats|correos|emails).*")) ||
                normalized.contains("notificaciones pendientes") ||
                normalized.contains("mensajes pendientes") ||
                normalized.contains("mensajes por leer") ||
                normalized.contains("correos pendientes") ||
                normalized.contains("correos por leer") ||
                normalized.contains("tengo mensajes") ||
                normalized.contains("tengo notificaciones") ||
                normalized.contains("tengo correos")

        if (isNotificationQuery || isOtherUnreadQuery) {
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

        // 5. Clima, Divisas y Búsqueda Web Externa
        // 5a. Clima (con ciudad específica vs clima local)
        if (ExternalInfoService.isWeatherQuery(trimmed) ||
            normalized.matches(Regex("^(como\\s+esta\\s+el\\s+clima|clima|temperatura|tiempo\\s+hoy|pronostico).*")) ||
            normalized.contains("actualiza el clima") || normalized.contains("consultar clima")
        ) {
            val city = ExternalInfoService.extractCityFromWeatherQuery(trimmed)
            if (city != null) {
                LogBus.log("VoiceActionRouter -> Fast-Path weather query for city: '$city'")
                return RouteResult(
                    handled = true,
                    isAsyncExternalSearch = true,
                    searchQuery = trimmed,
                    targetCity = city
                )
            } else {
                LogBus.log("VoiceActionRouter -> Fast-Path local weather query")
                return RouteResult(handled = true, isAsyncWeather = true)
            }
        }

        // 5b. Divisas y Tasas de Cambio (TRM)
        if (ExternalInfoService.isCurrencyQuery(trimmed)) {
            LogBus.log("VoiceActionRouter -> Fast-Path currency query: '$trimmed'")
            return RouteResult(
                handled = true,
                isAsyncExternalSearch = true,
                searchQuery = trimmed
            )
        }

        // 5c. Acciones, Bolsa y Criptomonedas
        if (ExternalInfoService.isStockOrMarketQuery(trimmed)) {
            LogBus.log("VoiceActionRouter -> Fast-Path stock/market query: '$trimmed'")
            return RouteResult(
                handled = true,
                isAsyncExternalSearch = true,
                searchQuery = trimmed
            )
        }

        // 5d. Noticias en Vivo y Titulares de Actualidad
        if (ExternalInfoService.isNewsQuery(trimmed)) {
            LogBus.log("VoiceActionRouter -> Fast-Path news query: '$trimmed'")
            return RouteResult(
                handled = true,
                isAsyncExternalSearch = true,
                searchQuery = trimmed
            )
        }

        // 5e. Búsqueda Web / Información General
        if (ExternalInfoService.isGeneralSearchQuery(trimmed)) {
            LogBus.log("VoiceActionRouter -> Fast-Path web search query: '$trimmed'")
            return RouteResult(
                handled = true,
                isAsyncExternalSearch = true,
                searchQuery = trimmed
            )
        }

        // 6. Listas de Tareas (To-Do)
        // 6a.0 Lista de Compras Especializada
        val shoppingAddMatch = Regex("^(agrega|agregar?|anade|anadir?|pon|poner?)\\s+(.+?)\\s+(a\\s+la\\s+lista\\s+de\\s+compras|en\\s+la\\s+lista\\s+de\\s+compras|a\\s+las\\s+compras|en\\s+las\\s+compras|a\\s+compras|en\\s+compras)$", RegexOption.IGNORE_CASE).find(normalized)
            ?: Regex("^(agrega|agregar?|anade|anadir?|pon|poner?)\\s+(a\\s+la\\s+lista\\s+de\\s+compras|en\\s+la\\s+lista\\s+de\\s+compras|a\\s+las\\s+compras|en\\s+las\\s+compras|a\\s+compras|en\\s+compras)\\s+(.+)$", RegexOption.IGNORE_CASE).find(normalized)
            ?: Regex("^comprar\\s+(.+)$", RegexOption.IGNORE_CASE).find(normalized)

        if (shoppingAddMatch != null) {
            val itemRaw = when {
                normalized.startsWith("comprar") -> shoppingAddMatch.groupValues[1]
                shoppingAddMatch.groupValues[2].contains("compra") -> shoppingAddMatch.groupValues[3]
                else -> shoppingAddMatch.groupValues[2]
            }
            val item = cleanTarget(itemRaw)
            if (item.isNotBlank()) {
                val repo = TodoRepository(context)
                repo.createTodo(title = item, listName = "Compras")
                LogBus.log("VoiceActionRouter -> Fast-Path shopping item added: '$item'")
                return RouteResult(handled = true, responseText = "Agregado '$item' a tu lista de compras.")
            }
        }

        if (normalized == "lista de compras" || normalized == "que hay en la lista de compras" ||
            normalized == "que hay en las compras" || normalized == "que tengo que comprar" ||
            normalized == "que debo comprar" || normalized == "ver compras" ||
            normalized == "consultar compras" || normalized == "que hay de compras"
        ) {
            val repo = TodoRepository(context)
            val pending = repo.getPendingTodos("Compras")
            val resp = if (pending.isEmpty()) {
                "Tu lista de compras está vacía."
            } else {
                "En tu lista de compras tienes: " + pending.joinToString(", ") { it.title } + "."
            }
            return RouteResult(handled = true, responseText = resp)
        }

        val shoppingDoneMatch = Regex("^(tacha|tachar?|compre|comprado|marca\\s+como\\s+comprado|elimina\\s+de\\s+compras)\\s+(.+?)\\s*(de\\s+las\\s+compras|de\\s+la\\s+lista\\s+de\\s+compras|de\\s+compras)?$", RegexOption.IGNORE_CASE).find(normalized)
        if (shoppingDoneMatch != null) {
            val item = cleanTarget(shoppingDoneMatch.groupValues[2])
            if (item.isNotBlank() && item != "la lista" && item != "compras") {
                val repo = TodoRepository(context)
                repo.markCompletedByTitle(item, true)
                LogBus.log("VoiceActionRouter -> Fast-Path shopping item bought: '$item'")
                return RouteResult(handled = true, responseText = "Marcado '$item' como comprado en tu lista de compras.")
            }
        }

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

        // 7. Notas y Recordatorios
        // 7a. Consultar Notas: ej: "mis notas", "consultar notas", "qué notas tengo"
        if (normalized.contains("mis notas") || normalized.contains("consultar notas") || normalized.contains("que notas tengo") || normalized.startsWith("buscar nota")) {
            val query = if (normalized.contains("sobre ") || normalized.contains("de ")) {
                trimmed.substringAfter("de ").substringAfter("sobre ").trim()
            } else ""
            val summary = if (query.isNotBlank()) actionExecutor.searchNotesSummary(query) else actionExecutor.searchNotesSummary(" ")
            return RouteResult(handled = true, responseText = summary)
        }

        // 7b. Consultar Recordatorios: ej: "mis recordatorios", "consultar recordatorios"
        if (normalized.contains("mis recordatorios") || normalized.contains("consultar recordatorios") || normalized.contains("que recordatorios tengo")) {
            val summary = actionExecutor.listRemindersSummary()
            return RouteResult(handled = true, responseText = summary)
        }

        // 7c. Consultar Grabaciones de Voz / Reuniones: ej: "mis grabaciones", "que grabé", "reunión grabada", "audio de voz"
        if (normalized.contains("grabacion") || normalized.contains("grabaciones") || normalized.contains("que grabe") || normalized.contains("audios grabados") || normalized.contains("audio de voz") || normalized.contains("ultima reunion")) {
            val query = if (normalized.contains("sobre ") || normalized.contains("de ")) {
                trimmed.substringAfter("de ").substringAfter("sobre ").trim()
            } else ""
            val summary = actionExecutor.searchVoiceRecordingsSummary(if (query.isNotBlank()) query else null)
            return RouteResult(handled = true, responseText = summary)
        }

        // 7c. Crear Nota: ej: "toma nota que la cita es a las 4"
        val noteMatch = Regex("^(toma\\s+nota|anota\\s+que|anota|nueva\\s+nota|apunta\\s+que|apunta)\\s+(.+)$", RegexOption.IGNORE_CASE).find(normalized)
        if (noteMatch != null) {
            val noteBody = trimmed.substring(noteMatch.groups[2]!!.range.first).trim()
            val repo = NoteRepository(context)
            val noteId = repo.createNote(title = "", body = noteBody)
            if (noteId > 0) {
                com.myvu.client.ai.NoteAiProcessor(context).processNote(noteId) { _ -> }
            }
            LogBus.log("VoiceActionRouter -> Fast-Path note created: '$noteBody'")
            return RouteResult(handled = true, responseText = "Nota guardada.")
        }

        // 7d. Eliminar Nota: ej: "elimina la nota de reunión"
        val noteDelMatch = Regex("^(elimina|borra|borrar?|eliminar?)\\s+(la\\s+nota\\s+(de\\s+)?|nota\\s+)?(.+)$", RegexOption.IGNORE_CASE).find(normalized)
        if (noteDelMatch != null && normalized.contains("nota")) {
            val noteTitle = cleanTarget(noteDelMatch.groupValues[4])
            val repo = NoteRepository(context)
            repo.deleteByTitle(noteTitle)
            LogBus.log("VoiceActionRouter -> Fast-Path note deleted: '$noteTitle'")
            return RouteResult(handled = true, responseText = "Nota eliminada.")
        }

        // 7e. Eliminar Recordatorio: ej: "eliminar recordatorio de médico"
        val remDelMatch = Regex("^(elimina|borra|borrar?|eliminar?)\\s+(el\\s+recordatorio\\s+(de\\s+)?|recordatorio\\s+)?(.+)$", RegexOption.IGNORE_CASE).find(normalized)
        if (remDelMatch != null && normalized.contains("recordatorio")) {
            val remTitle = cleanTarget(remDelMatch.groupValues[4])
            actionExecutor.deleteReminderAction(remTitle)
            LogBus.log("VoiceActionRouter -> Fast-Path reminder deleted: '$remTitle'")
            return RouteResult(handled = true, responseText = "Recordatorio eliminado.")
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


        // 10. Abrir Apps: ej: "abre la calculadora", "abrir instagram"
        val openAppMatch = Regex("^(abre|abrir?|lanzar?|lanza|iniciar?|inicia|ejecutar?|ejecuta)\\s+(la\\s+app\\s+de\\s+|la\\s+aplicacion\\s+de\\s+|el\\s+|la\\s+)?([a-zA-Z0-9_ ]+)$", RegexOption.IGNORE_CASE).find(trimmed)
        if (openAppMatch != null && !normalized.contains("nota") && !normalized.contains("lista") && !normalized.contains("teleprompter") && !normalized.contains("linterna") && !normalized.contains("musica")) {
            val appName = cleanTarget(openAppMatch.groupValues[3])
            val resp = actionExecutor.openAppByName(appName)
            return RouteResult(handled = true, responseText = resp)
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
        // Remover puntuación común producida por Whisper STT (. , ? ! : ;)
        clean = clean.replace(Regex("^[\\s.,?!:;\"'¿¡]+|[\\s.,?!:;\"'¿¡]+$"), "").trim()
        clean = clean.replace(Regex("(?i)^(a|al|a\\s+mi|el|la|las|los)\\s+"), "").trim()
        clean = clean.replace(Regex("^[\\s.,?!:;\"'¿¡]+|[\\s.,?!:;\"'¿¡]+$"), "").trim()
        // Manejar errores de STT donde la preposición 'a' se une al nombre (ej: "amatías" -> "matías")
        val lowerNoAccents = Normalizer.normalize(clean, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase()
        if (lowerNoAccents.matches(Regex("^a[a-z]{3,}.*")) && !lowerNoAccents.startsWith("ana") && !lowerNoAccents.startsWith("antonio") && !lowerNoAccents.startsWith("andres") && !lowerNoAccents.startsWith("alejandro") && !lowerNoAccents.startsWith("alvaro") && !lowerNoAccents.startsWith("arturo") && !lowerNoAccents.startsWith("alberto")) {
            clean = clean.substring(1).trim()
        }
        return clean.replace(Regex("^[\\s.,?!:;\"'¿¡]+|[\\s.,?!:;\"'¿¡]+$"), "").trim()
    }
}
