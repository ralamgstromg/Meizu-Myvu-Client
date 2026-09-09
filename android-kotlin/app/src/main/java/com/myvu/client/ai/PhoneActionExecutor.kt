package com.myvu.client.ai

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import com.myvu.client.core.ContactHelper
import com.myvu.client.core.LockScreenHelper
import com.myvu.client.core.LogBus
import com.myvu.client.database.NoteRepository
import com.myvu.client.database.ReminderRepository
import com.myvu.client.database.TodoRepository
import com.myvu.client.database.TodoItem
import com.myvu.client.reminder.ReminderScheduler
import com.myvu.client.reminder.ReminderTimeParser
import com.myvu.client.service.MirrorNotificationListener
import com.myvu.client.service.MyvuService
import com.myvu.client.service.AutoSendAccessibilityService
import com.myvu.client.ui.SendTrampolineActivity
import com.myvu.client.app.feature.Weather
import java.net.URLEncoder

/**
 * Executes system & phone actions requested by voice via Gemini / AI.
 * Supports volume adjustments, media control, WhatsApp, Telegram, calls, and SMS.
 */
@android.annotation.SuppressLint("MissingPermission")
class PhoneActionExecutor(context: Context) {

    private val context: Context = context.applicationContext
    private val audioManager: AudioManager? = this.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun executeAction(action: GeminiAction) {
        try {
            when (action.type) {
                "weather_query" -> {
                    refreshWeather()
                }
                "open_whatsapp" -> {
                    val text = action.arguments["text"] ?: action.arguments["message"]
                    openWhatsApp(text)
                }
                "open_telegram" -> {
                    val text = action.arguments["text"] ?: action.arguments["message"]
                    openTelegram(text)
                }
                "send_sms" -> {
                    val text = action.arguments["text"] ?: action.arguments["message"] ?: action.arguments["payload"]
                    sendSms(text)
                }
                "make_call" -> {
                    val target = action.arguments["target"] ?: action.arguments["number"]
                    makeCall(target)
                }
                "call_whatsapp" -> {
                    val target = action.arguments["target"] ?: action.arguments["contact"] ?: action.arguments["number"]
                    makeWhatsAppCall(target)
                }
                "call_teams" -> {
                    val target = action.arguments["target"] ?: action.arguments["contact"]
                    makeTeamsCall(target)
                }
                "call_google_chat", "call_meet" -> {
                    val target = action.arguments["target"] ?: action.arguments["contact"]
                    makeGoogleChatCall(target)
                }
                "web_search" -> {
                    val query = action.arguments["query"]
                    openWebSearch(query)
                }
                "set_alarm" -> {
                    val time = action.arguments["time"] ?: action.arguments["alarm"]
                    setAlarm(time)
                }
                "set_timer" -> {
                    val duration = action.arguments["duration"] ?: action.arguments["timer"]
                    setTimer(duration)
                }
                "volume_control" -> {
                    val levelStr = action.arguments["level"] ?: action.arguments["volume"]
                    levelStr?.toIntOrNull()?.let { setVolume(it) }
                }
                "media_control" -> {
                    val command = action.arguments["command"] ?: action.arguments["action"]
                    when (command?.lowercase()) {
                        "pause" -> pauseMusic()
                        "resume", "play" -> resumeMusic()
                        "next" -> nextTrack()
                        "prev", "previous" -> previousTrack()
                        "stop" -> stopMusic()
                        else -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    }
                }
                "media_play", "play_music" -> {
                    val query = action.arguments["query"] ?: action.arguments["song"] ?: ""
                    val app = action.arguments["app"] ?: action.arguments["target_app"] ?: ""
                    playInThirdPartyApp(if (app.isNotBlank()) "$app: $query" else query)
                }
                "media_search" -> {
                    val query = action.arguments["query"] ?: ""
                    val app = action.arguments["app"] ?: action.arguments["target_app"] ?: ""
                    searchInThirdPartyApp(if (app.isNotBlank()) "$app: $query" else query)
                }
            }
        } catch (t: Throwable) {
            LogBus.error("PhoneActionExecutor: Failed to execute action ${action.type}", t)
        }
    }

    fun processAndExecute(aiText: String?): String {
        if (aiText.isNullOrEmpty()) return aiText ?: ""

        val lower = aiText.lowercase()

        // 1. Volume control
        if (lower.contains("action:volume=")) {
            try {
                val valStr = extractValue(aiText, "ACTION:VOLUME=")
                val vol = valStr.toInt()
                setVolume(vol)
            } catch (ignored: Exception) {
            }
        }

        // 2. Media control & OpenTune Integration
        if (lower.contains("action:app_play=")) {
            val appPlayVal = extractValue(aiText, "ACTION:APP_PLAY=")
            playInThirdPartyApp(appPlayVal)
        }
        if (lower.contains("action:app_open=")) {
            val appName = extractValue(aiText, "ACTION:APP_OPEN=")
            openAppByName(appName)
        }
        if (lower.contains("action:media_play=")) {
            val payload = extractValue(aiText, "ACTION:MEDIA_PLAY=")
            playInThirdPartyApp(payload)
        } else if (lower.contains("action:opentune_play=") || lower.contains("action:opentune_search=")) {
            val query = extractValue(aiText, "ACTION:OPENTUNE_PLAY=").ifBlank { extractValue(aiText, "ACTION:OPENTUNE_SEARCH=") }
            playInThirdPartyApp("opentune: $query")
        }

        if (lower.contains("action:media_search=")) {
            val payload = extractValue(aiText, "ACTION:MEDIA_SEARCH=")
            searchInThirdPartyApp(payload)
        }

        if (lower.contains("action:media_now_playing") || lower.contains("action:media_info") || lower.contains("action:now_playing")) {
            val nowPlaying = queryNowPlaying()
            return stripActionTags(aiText) + "\n\n" + nowPlaying
        }

        if (lower.contains("action:opentune_pause") || lower.contains("action:media_pause")) {
            pauseMusic()
        }
        if (lower.contains("action:opentune_resume") || lower.contains("action:media_resume")) {
            resumeMusic()
        }
        if (lower.contains("action:opentune_next") || lower.contains("action:media_next")) {
            nextTrack()
        }
        if (lower.contains("action:opentune_prev") || lower.contains("action:media_prev")) {
            previousTrack()
        }
        if (lower.contains("action:media_stop")) {
            stopMusic()
        }

        // 3. WhatsApp
        if (lower.contains("action:whatsapp=")) {
            val text = extractValue(aiText, "ACTION:WHATSAPP=")
            openWhatsApp(text)
        }

        // 4. Telegram
        if (lower.contains("action:telegram=")) {
            val text = extractValue(aiText, "ACTION:TELEGRAM=")
            openTelegram(text)
        }

        // 5. Calls / Dialing (Cellular, WhatsApp, Teams, Google Chat)
        if (lower.contains("action:call_whatsapp=")) {
            val target = extractValue(aiText, "ACTION:CALL_WHATSAPP=")
            makeWhatsAppCall(target)
        } else if (lower.contains("action:call_teams=")) {
            val target = extractValue(aiText, "ACTION:CALL_TEAMS=")
            makeTeamsCall(target)
        } else if (lower.contains("action:call_google=") || lower.contains("action:call_meet=") || lower.contains("action:call_chat=")) {
            val target = extractValue(aiText, "ACTION:CALL_GOOGLE=").ifBlank {
                extractValue(aiText, "ACTION:CALL_MEET=").ifBlank {
                    extractValue(aiText, "ACTION:CALL_CHAT=")
                }
            }
            makeGoogleChatCall(target)
        } else if (lower.contains("action:call=") || lower.contains("action:call:") || lower.contains("action:call ")) {
            val target = extractValue(aiText, "ACTION:CALL=").ifBlank {
                extractValue(aiText, "ACTION:CALL:").ifBlank {
                    extractValue(aiText, "ACTION:CALL ")
                }
            }
            makeCall(target)
        }

        // 6. Web Search
        if (lower.contains("action:search=")) {
            val query = extractValue(aiText, "ACTION:SEARCH=")
            openWebSearch(query)
        }

        // 7. Alarms
        if (lower.contains("action:alarm=")) {
            val alarmVal = extractValue(aiText, "ACTION:ALARM=")
            setAlarm(alarmVal)
        }

        // 8. Timers & Reminders
        if (lower.contains("action:timer=")) {
            val timerVal = extractValue(aiText, "ACTION:TIMER=")
            setTimer(timerVal)
        } else if (lower.contains("action:reminder_delete=")) {
            val remTarget = extractValue(aiText, "ACTION:REMINDER_DELETE=")
            deleteReminderAction(remTarget)
        } else if (lower.contains("action:reminder=")) {
            val remVal = extractValue(aiText, "ACTION:REMINDER=")
            createReminderAction(remVal)
        }

        // 9. To-Do Lists (Tareas)
        if (lower.contains("action:todo_add=")) {
            val todoVal = extractValue(aiText, "ACTION:TODO_ADD=")
            addTodoAction(todoVal)
        } else if (lower.contains("action:todo_done=")) {
            val todoVal = extractValue(aiText, "ACTION:TODO_DONE=")
            markTodoDoneAction(todoVal)
        } else if (lower.contains("action:todo_delete=")) {
            val todoVal = extractValue(aiText, "ACTION:TODO_DELETE=")
            deleteTodoAction(todoVal)
        } else if (lower.contains("action:todo_list=")) {
            val listVal = extractValue(aiText, "ACTION:TODO_LIST=")
            val todoSummary = listTodosSummary(listVal)
            return stripActionTags(aiText) + "\n\n" + todoSummary
        }

        // 10. GPS Navigation & HUD
        if (lower.contains("action:navigate_stop") || lower.contains("action:nav_stop")) {
            stopNavigation()
        } else if (lower.contains("action:navigate=")) {
            val dest = extractValue(aiText, "ACTION:NAVIGATE=")
            startNavigation(dest)
        }

        // 11. Calendar Events (General & Specific Accounts)
        if (lower.contains("action:calendar_outlook=")) {
            val eventVal = extractValue(aiText, "ACTION:CALENDAR_OUTLOOK=")
            addOutlookCalendarEvent(eventVal)
        } else if (lower.contains("action:calendar_google=")) {
            val eventVal = extractValue(aiText, "ACTION:CALENDAR_GOOGLE=")
            addGoogleCalendarEvent(eventVal)
        } else if (lower.contains("action:calendar=")) {
            val eventVal = extractValue(aiText, "ACTION:CALENDAR=")
            addCalendarEvent(eventVal)
        }

        // 12. Notes (Google Keep vs Notes with Tags vs Quick Notes & Delete)
        if (lower.contains("action:note_delete=")) {
            val noteTarget = extractValue(aiText, "ACTION:NOTE_DELETE=")
            deleteNoteAction(noteTarget)
        } else if (lower.contains("action:note_update=")) {
            val noteTarget = extractValue(aiText, "ACTION:NOTE_UPDATE=")
            updateNoteAction(noteTarget)
        } else if (lower.contains("action:note_keep=")) {
            val noteText = extractValue(aiText, "ACTION:NOTE_KEEP=")
            createKeepNote(noteText)
        } else if (lower.contains("action:note_tags=")) {
            val noteVal = extractValue(aiText, "ACTION:NOTE_TAGS=")
            createNoteWithTags(noteVal)
        } else if (lower.contains("action:note=")) {
            val noteText = extractValue(aiText, "ACTION:NOTE=")
            createNote(noteText)
        }

        // 13. Multi-Module Agentic Search (Notes, Reminders, Voice Recordings, Todos)
        if (lower.contains("action:note_search=") || lower.contains("action:search_notes=")) {
            val query = extractValue(aiText, "ACTION:NOTE_SEARCH=").ifBlank { extractValue(aiText, "ACTION:SEARCH_NOTES=") }
            val searchResults = searchNotesSummary(query)
            return stripActionTags(aiText) + "\n\n" + searchResults
        }
        if (lower.contains("action:reminder_search=") || lower.contains("action:search_reminders=")) {
            val summary = listRemindersSummary()
            return stripActionTags(aiText) + "\n\n" + summary
        }
        if (lower.contains("action:voice_recording_search=") || lower.contains("action:recording_search=") || lower.contains("action:search_recordings=")) {
            val query = extractValue(aiText, "ACTION:VOICE_RECORDING_SEARCH=").ifBlank {
                extractValue(aiText, "ACTION:RECORDING_SEARCH=").ifBlank { extractValue(aiText, "ACTION:SEARCH_RECORDINGS=") }
            }
            val searchResults = searchVoiceRecordingsSummary(if (query.isNotBlank()) query else null)
            return stripActionTags(aiText) + "\n\n" + searchResults
        }
        if (lower.contains("action:todo_search=") || lower.contains("action:search_todos=")) {
            val list = extractValue(aiText, "ACTION:TODO_SEARCH=").ifBlank { extractValue(aiText, "ACTION:SEARCH_TODOS=") }
            val summary = listTodosSummary(if (list.isNotBlank()) list else null)
            return stripActionTags(aiText) + "\n\n" + summary
        }

        // 13. Teleprompter
        if (lower.contains("action:teleprompter=")) {
            val promptText = extractValue(aiText, "ACTION:TELEPROMPTER=")
            openTeleprompter(promptText)
        }

        // 14. Weather Refresh
        if (lower.contains("action:weather_refresh")) {
            refreshWeather()
        }

        // 15. Specific Reminders
        if (lower.contains("action:reminder=")) {
            val remVal = extractValue(aiText, "ACTION:REMINDER=")
            createSpecificReminder(remVal)
        }

        // 16. Summarize pending unread notifications (Email, WhatsApp, Telegram, All)
        if (lower.contains("action:summary=") || lower.contains("action:notifications")) {
            val cat = extractValue(aiText, "ACTION:SUMMARY=").ifBlank { null }
            val summary = MirrorNotificationListener.getUnreadSummary(cat)
            return stripActionTags(aiText) + "\n\n" + summary
        }

        if (lower.contains("action:emails")) {
            val summary = MirrorNotificationListener.getUnreadSummary("correo")
            return stripActionTags(aiText) + "\n\n" + summary
        }

        if (lower.contains("action:battery")) {
            val battInfo = queryBatteryStatus()
            return stripActionTags(aiText) + "\n\n" + battInfo
        }

        if (lower.contains("action:calendar") && !lower.contains("action:calendar_")) {
            val events = CalendarService.getUpcomingEvents(context)
            return stripActionTags(aiText) + "\n\n" + events
        }

        // 17. Health & Wellness Metrics (Steps, Stress, Activity Summary)
        if (lower.contains("action:health_steps") || lower.contains("action:steps")) {
            val stepsInfo = com.myvu.client.health.HealthService.getInstance(context).getStepsSummary()
            return stripActionTags(aiText) + "\n\n" + stepsInfo
        }

        if (lower.contains("action:health_stress") || lower.contains("action:stress")) {
            val stressInfo = com.myvu.client.health.HealthService.getInstance(context).getStressSummary()
            return stripActionTags(aiText) + "\n\n" + stressInfo
        }

        if (lower.contains("action:health_summary") || lower.contains("action:health")) {
            val healthInfo = com.myvu.client.health.HealthService.getInstance(context).getFullHealthSummary()
            return stripActionTags(aiText) + "\n\n" + healthInfo
        }

        return stripActionTags(aiText)
    }

    fun setVolume(level: Int) {
        val am = audioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = Math.max(0, Math.min(level, max))
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        LogBus.log("voice action -> phone volume set to $target/$max")
    }

    fun adjustVolume(increase: Boolean): String {
        val am = audioManager ?: return "No se pudo acceder al control de audio."
        val dir = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val pct = if (max > 0) (cur * 100) / max else 0
        LogBus.log("voice action -> volume adjusted (${if (increase) "+1" else "-1"}): $cur/$max ($pct%)")
        return "Volumen ${if (increase) "subido" else "bajado"} al $pct%."
    }

    fun setRingerMode(mode: Int): String {
        val am = audioManager ?: return "No se pudo acceder al control de sonido."
        return try {
            am.ringerMode = mode
            when (mode) {
                AudioManager.RINGER_MODE_SILENT -> {
                    LogBus.log("voice action -> ringer mode SILENT")
                    "Teléfono en modo silencio."
                }
                AudioManager.RINGER_MODE_VIBRATE -> {
                    LogBus.log("voice action -> ringer mode VIBRATE")
                    "Teléfono en vibración."
                }
                else -> {
                    LogBus.log("voice action -> ringer mode NORMAL")
                    "Sonido del teléfono activado."
                }
            }
        } catch (e: Exception) {
            LogBus.warn("Could not set ringer mode: ${e.message}")
            "No se pudo cambiar el modo de sonido."
        }
    }

    fun setFlashlight(enabled: Boolean): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager == null) {
                "El dispositivo no cuenta con servicio de cámara para linterna."
            } else {
                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
                } ?: cameraManager.cameraIdList.firstOrNull()

                if (cameraId != null) {
                    cameraManager.setTorchMode(cameraId, enabled)
                    LogBus.log("voice action -> flashlight turned ${if (enabled) "ON" else "OFF"}")
                    if (enabled) "Linterna encendida." else "Linterna apagada."
                } else {
                    "No se encontró flash en la cámara del dispositivo."
                }
            }
        } catch (e: Exception) {
            LogBus.error("Could not toggle flashlight", e)
            "No se pudo controlar la linterna."
        }
    }

    fun sendMediaKey(keyCode: Int) {
        com.myvu.client.media.MediaPlaybackHelper.sendMediaKeyEvent(context, keyCode)
    }

    fun openAppByName(rawName: String?): String {
        try {
            if (rawName.isNullOrBlank()) return "Nombre de aplicación no especificado."
            val cleanName = normalize(rawName.trim().replace(Regex("(?i)^(abrir?\\s+(la\\s+app\\s+de\\s+|la\\s+aplicacion\\s+de\\s+|el\\s+|la\\s+)?|lanzar?\\s+)"), ""))
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)

            var bestPkg: String? = null
            var bestScore = Int.MIN_VALUE

            for (p in packages) {
                val appInfo = p.applicationInfo ?: continue
                if ((appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 && pm.getLaunchIntentForPackage(p.packageName) == null) {
                    continue
                }
                val label = normalize(pm.getApplicationLabel(appInfo).toString())
                val pkgName = p.packageName.lowercase()

                if (label == cleanName || pkgName == cleanName) {
                    bestPkg = p.packageName
                    break
                }

                var score = 0
                if (label.contains(cleanName) || pkgName.contains(cleanName)) score += 50
                if (cleanName.contains(label) && label.length > 2) score += 30
                val dist = levenshteinDistance(cleanName, label)
                if (dist <= 2) score += 40

                if (score > bestScore && score >= 30) {
                    bestScore = score
                    bestPkg = p.packageName
                }
            }

            if (bestPkg != null) {
                val intent = pm.getLaunchIntentForPackage(bestPkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val isLocked = LockScreenHelper.isDeviceLocked(context)
                    if (isLocked) {
                        LockScreenHelper.wakeUpScreen(context, "MYVU:OpenApp")
                        SendTrampolineActivity.launchWithKeyguardDismiss(context, intent)
                    } else {
                        context.startActivity(intent)
                    }
                    LogBus.log("voice action -> launched app '$rawName' (pkg: $bestPkg, locked=$isLocked)")
                    return "Abriendo $rawName..."
                }
            }
            LogBus.warn("voice action -> app '$rawName' not found on device")
            return "No encontré la aplicación $rawName en el dispositivo."
        } catch (e: Exception) {
            LogBus.error("could not open app '$rawName'", e)
            return "No se pudo abrir la aplicación $rawName."
        }
    }

    fun playInThirdPartyApp(appAndQuery: String?): String {
        if (appAndQuery.isNullOrBlank()) return "Consulta de canción vacía."
        var appName = ""
        var query = appAndQuery.trim()
        if (appAndQuery.contains(":") || appAndQuery.contains("|")) {
            val parts = appAndQuery.split(Regex("[:|]"), 2)
            appName = parts[0].trim()
            query = parts[1].trim()
        }
        return com.myvu.client.media.MediaPlaybackHelper.playFromSearch(context, appName, query)
    }

    fun searchInThirdPartyApp(appAndQuery: String?): String {
        if (appAndQuery.isNullOrBlank()) return "Término de búsqueda vacío."
        var appName = ""
        var query = appAndQuery.trim()
        if (appAndQuery.contains(":") || appAndQuery.contains("|")) {
            val parts = appAndQuery.split(Regex("[:|]"), 2)
            appName = parts[0].trim()
            query = parts[1].trim()
        }
        return com.myvu.client.media.MediaPlaybackHelper.searchInApp(context, appName, query)
    }

    fun playFromSearchInOpenTune(query: String?): String {
        return playInThirdPartyApp("opentune: ${query ?: ""}")
    }

    fun pauseMusic(): Boolean = com.myvu.client.media.MediaPlaybackHelper.pause(context)
    fun resumeMusic(): Boolean = com.myvu.client.media.MediaPlaybackHelper.resume(context)
    fun nextTrack(): Boolean = com.myvu.client.media.MediaPlaybackHelper.skipToNext(context)
    fun previousTrack(): Boolean = com.myvu.client.media.MediaPlaybackHelper.skipToPrevious(context)
    fun stopMusic(): Boolean = com.myvu.client.media.MediaPlaybackHelper.stop(context)
    fun queryNowPlaying(): String = com.myvu.client.media.MediaPlaybackHelper.queryNowPlaying(context)

    fun queryBatteryStatus(): String {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val phoneBatt = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val conn = com.myvu.client.service.MyvuService.activeConnection()
            val glassesBatt = if (conn?.state == com.myvu.client.service.ConnectionState.READY) {
                conn.glassesInfo()?.battery?.takeIf { it > 0 } ?: 85
            } else null

            val sb = StringBuilder("Batería del móvil: ${if (phoneBatt >= 0) "$phoneBatt%" else "desconocida"}. ")
            if (glassesBatt != null) {
                sb.append("Batería de las gafas MYVU: $glassesBatt%.")
            } else {
                sb.append("Gafas MYVU no conectadas.")
            }
            sb.toString()
        } catch (e: Exception) {
            "No se pudo consultar la batería del dispositivo."
        }
    }

    private fun dispatchMessagingIntent(intent: Intent, targetPackage: String? = null) {
        val isLocked = LockScreenHelper.isDeviceLocked(context)
        if (AutoSendAccessibilityService.isAccessibilityServiceEnabled(context)) {
            AutoSendAccessibilityService.triggerAutoSend(targetPackage, isDeviceLocked = isLocked)
        } else {
            LogBus.warn("AutoSendAccessibilityService is NOT enabled in Android Settings. Message will be pre-filled, but automated send requires enabling accessibility service.")
        }

        if (isLocked) {
            LogBus.log("PhoneActionExecutor: Device is locked, dispatching via SendTrampolineActivity ($targetPackage)")
            try {
                SendTrampolineActivity.launchWithKeyguardDismiss(context, intent)
            } catch (e: Exception) {
                LogBus.warn("Failed to launch SendTrampolineActivity, falling back to direct startActivity: ${e.message}")
                context.startActivity(intent)
            }
        } else {
            context.startActivity(intent)
        }
    }

    fun openWhatsApp(text: String?) {
        try {
            if (text.isNullOrBlank()) return
            // Wake up screen so WhatsApp activity and AccessibilityService can interact
            LockScreenHelper.wakeUpScreen(context, "MYVU:WhatsApp")

            val parsed = ContactHelper.extractRecipientAndMessage(context, text)
            var recipient = parsed.resolvedName ?: parsed.recipientQuery
            val message = parsed.message
            var number = parsed.resolvedPhone

            if (number.isNullOrEmpty() && recipient.isNotBlank()) {
                if (recipient.matches(Regex("^[0-9+#* -]+$"))) {
                    number = recipient
                } else {
                    val resolved = ContactHelper.resolveContactPhone(context, recipient)
                    number = resolved?.first
                    if (resolved != null) recipient = resolved.second
                }
            }

            val cleanNum = ContactHelper.formatColombianPhone(number ?: "")
            val encodedMsg = if (message.isNotEmpty()) URLEncoder.encode(message, "UTF-8") else ""

            // Strategy 1: WhatsApp native URI scheme (whatsapp://send?phone=...)
            // This opens the exact chat for that phone number without triggering the
            // "send invitation" screen — WhatsApp handles the number lookup internally.
            if (cleanNum.isNotEmpty()) {
                val waUri = Uri.parse("whatsapp://send?phone=$cleanNum&text=$encodedMsg")
                val waIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    dispatchMessagingIntent(waIntent, "com.whatsapp")
                    LogBus.log("voice action -> opened WhatsApp (whatsapp:// scheme) for $recipient ($cleanNum) with text: $message")
                    return
                } catch (e: Exception) {
                    LogBus.warn("openWhatsApp: whatsapp:// scheme failed (${e.message}), trying API URL")
                }
            }

            // Strategy 2: WhatsApp API deep-link (works also when WhatsApp is not set as handler)
            val url = StringBuilder("https://api.whatsapp.com/send?")
            if (cleanNum.isNotEmpty()) {
                url.append("phone=").append(cleanNum).append("&")
            }
            if (message.isNotEmpty()) {
                url.append("text=").append(encodedMsg)
            }

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                dispatchMessagingIntent(intent, "com.whatsapp")
                LogBus.log("voice action -> opened WhatsApp (API URL) for $recipient ($cleanNum) with text: $message")
            } catch (e: Exception) {
                val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                dispatchMessagingIntent(genericIntent, null)
                LogBus.log("voice action -> opened generic WhatsApp browser fallback for: $message")
            }
        } catch (e: Exception) {
            LogBus.error("could not open WhatsApp", e)
        }
    }

    fun sendSms(text: String?) {
        try {
            if (text.isNullOrBlank()) return
            val parsed = ContactHelper.extractRecipientAndMessage(context, text)
            var recipient = parsed.resolvedName ?: parsed.recipientQuery
            val message = parsed.message
            var number = parsed.resolvedPhone

            if (number.isNullOrEmpty() && recipient.isNotBlank()) {
                if (recipient.matches(Regex("^[0-9+#* -]+$"))) {
                    number = recipient
                } else {
                    val resolved = ContactHelper.resolveContactPhone(context, recipient)
                    number = resolved?.first
                    if (resolved != null) recipient = resolved.second
                }
            }

            val cleanNum = ContactHelper.formatColombianPhone(number ?: "")
            if (cleanNum.isEmpty()) {
                LogBus.warn("sendSms -> No phone number found for recipient: $recipient")
                return
            }

            val hasSmsPerm = context.checkSelfPermission(android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasSmsPerm) {
                try {
                    val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        context.getSystemService(android.telephony.SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        android.telephony.SmsManager.getDefault()
                    }
                    val parts = smsManager.divideMessage(message)
                    if (parts.size > 1) {
                        smsManager.sendMultipartTextMessage(cleanNum, null, parts, null, null)
                    } else {
                        smsManager.sendTextMessage(cleanNum, null, message, null, null)
                    }
                    LogBus.log("voice action -> sent direct SMS to $recipient ($cleanNum): '$message'")
                    return
                } catch (e: Exception) {
                    LogBus.warn("sendSms -> Direct SmsManager send failed: ${e.message}, falling back to intent")
                }
            }

            // Fallback: abrir app de SMS con trampolín y auto-envío
            LockScreenHelper.wakeUpScreen(context, "MYVU:SMS")
            val smsUri = Uri.parse("smsto:$cleanNum")
            val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                if (message.isNotEmpty()) {
                    putExtra("sms_body", message)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            dispatchMessagingIntent(intent, "com.google.android.apps.messaging")
            LogBus.log("voice action -> opened SMS app for $recipient ($cleanNum) with text: '$message'")
        } catch (e: Exception) {
            LogBus.error("could not send SMS", e)
        }
    }

    fun openTelegram(text: String?) {
        try {
            if (text.isNullOrBlank()) return
            LockScreenHelper.wakeUpScreen(context, "MYVU:Telegram")
            val parsed = ContactHelper.extractRecipientAndMessage(context, text)
            val message = if (parsed.message.isNotBlank()) parsed.message else text.trim()

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("tg://msg?text=" + URLEncoder.encode(message, "UTF-8"))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            dispatchMessagingIntent(intent, "org.telegram.messenger")
            LogBus.log("voice action -> opened Telegram with text: $message")
        } catch (e: Exception) {
            LogBus.error("could not open Telegram", e)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun placeCall(tm: android.telecom.TelecomManager, number: String, extras: android.os.Bundle) {
        tm.placeCall(Uri.parse("tel:" + Uri.encode(number)), extras)
    }

    private fun normalize(text: String): String {
        return ContactHelper.cleanText(text)
    }

    fun makeCall(target: String?) {
        try {
            if (target.isNullOrBlank()) return
            val cleanTarget = target.trim()
                .replace(Regex("(?i)^(llamar?\\s+(a|al)?\\s*|marcar?\\s+(a|al)?\\s*|a\\s+mi\\s+|a\\s+|al\\s+)"), "")
                .trim()
            var number: String? = null
            var displayName: String = cleanTarget

            if (cleanTarget.matches(Regex("^[0-9+#* -]+$"))) {
                number = cleanTarget
            } else {
                val resolved = ContactHelper.resolveContactPhone(context, cleanTarget)
                if (resolved != null) {
                    number = resolved.first
                    displayName = resolved.second
                }
            }

            val intent: Intent
            if (!number.isNullOrEmpty()) {
                val hasCallPerm = context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasCallPerm) {
                    try {
                        val tm = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                        if (tm != null) {
                            val extras = android.os.Bundle()
                            extras.putBoolean(android.telecom.TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
                            @android.annotation.SuppressLint("MissingPermission")
                            placeCall(tm, number, extras)
                            LogBus.log("voice action -> TelecomManager placed direct call to $displayName ($number)")
                            return
                        }
                    } catch (e: Exception) {
                        LogBus.warn("TelecomManager placeCall failed: ${e.message}, falling back to Intent")
                    }
                }
                intent = Intent(if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                LogBus.log("voice action -> placing " + (if (hasCallPerm) "direct call" else "dialer call") + " to $displayName ($number)")
            } else {
                intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                LogBus.warn("voice action -> contact number not found for $target, opening dialer")
            }
        } catch (e: Exception) {
            LogBus.error("could not place call for $target", e)
        }
    }

    fun makeWhatsAppCall(target: String?) {
        try {
            if (target.isNullOrBlank()) return
            val cleanTarget = target.trim()
                .replace(Regex("(?i)^(llamar?\\s+(a|al)?\\s*|marcar?\\s+(a|al)?\\s*|a\\s+mi\\s+|a\\s+|al\\s+)"), "")
                .replace(Regex("(?i)\\s+(por|en|de)?\\s*whatsapp$"), "")
                .trim()

            LockScreenHelper.wakeUpScreen(context, "MYVU:WhatsAppCall")
            com.myvu.client.service.AutoSendAccessibilityService.isAutoSendActive = false

            var phoneNumber: String? = null
            var displayName: String = cleanTarget

            if (cleanTarget.matches(Regex("^[0-9+#* -]+$"))) {
                phoneNumber = cleanTarget
            } else {
                val resolved = ContactHelper.resolveContactPhone(context, cleanTarget)
                if (resolved != null) {
                    phoneNumber = resolved.first
                    displayName = resolved.second
                }
            }

            // 1. Try resolving WhatsApp VoIP dataId in ContactsContract
            val dataId = ContactHelper.resolveWhatsAppVoipDataId(context, cleanTarget)
                ?: (if (!phoneNumber.isNullOrEmpty()) ContactHelper.resolveWhatsAppVoipDataId(context, phoneNumber) else null)

            val isLocked = LockScreenHelper.isDeviceLocked(context)

            if (dataId != null && dataId > 0) {
                val voipIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        android.content.ContentUris.withAppendedId(android.provider.ContactsContract.Data.CONTENT_URI, dataId),
                        "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
                    )
                    `package` = "com.whatsapp"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (isLocked) {
                    SendTrampolineActivity.launchWithKeyguardDismiss(context, voipIntent)
                } else {
                    context.startActivity(voipIntent)
                }
                LogBus.log("PhoneActionExecutor -> WhatsApp direct VoIP call launched for $displayName ($phoneNumber, dataId=$dataId)")
                return
            }

            // 2. Fallback: Open WhatsApp chat and trigger Auto-Call click
            val formatted = if (!phoneNumber.isNullOrBlank()) ContactHelper.formatColombianPhone(phoneNumber) else ""
            val chatUri = if (formatted.isNotBlank()) {
                Uri.parse("https://api.whatsapp.com/send?phone=$formatted")
            } else {
                Uri.parse("whatsapp://send")
            }

            AutoSendAccessibilityService.triggerAutoCall("com.whatsapp", isDeviceLocked = isLocked)

            val chatIntent = Intent(Intent.ACTION_VIEW, chatUri).apply {
                `package` = "com.whatsapp"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (isLocked) {
                SendTrampolineActivity.launchWithKeyguardDismiss(context, chatIntent)
            } else {
                context.startActivity(chatIntent)
            }
            LogBus.log("PhoneActionExecutor -> WhatsApp chat call fallback dispatched for $displayName ($formatted)")
        } catch (e: Exception) {
            LogBus.error("could not make WhatsApp call for $target", e)
        }
    }

    fun makeTeamsCall(target: String?) {
        try {
            if (target.isNullOrBlank()) return
            val cleanTarget = target.trim()
                .replace(Regex("(?i)^(llamar?\\s+(a|al)?\\s*|marcar?\\s+(a|al)?\\s*|a\\s+mi\\s+|a\\s+|al\\s+)"), "")
                .replace(Regex("(?i)\\s+(por|en|de)?\\s*teams$"), "")
                .trim()

            LockScreenHelper.wakeUpScreen(context, "MYVU:TeamsCall")
            com.myvu.client.service.AutoSendAccessibilityService.isAutoSendActive = false

            // Try resolving email first, then phone
            val emailResolved = ContactHelper.resolveContactEmail(context, cleanTarget)
            val phoneResolved = if (emailResolved == null) ContactHelper.resolveContactPhone(context, cleanTarget) else null

            val userIdentifier = emailResolved?.first ?: phoneResolved?.first ?: cleanTarget
            val displayName = emailResolved?.second ?: phoneResolved?.second ?: cleanTarget

            val teamsUri = Uri.parse("https://teams.microsoft.com/l/call/0/0?users=" + Uri.encode(userIdentifier))
            val intent = Intent(Intent.ACTION_VIEW, teamsUri).apply {
                `package` = "com.microsoft.teams"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val isLocked = LockScreenHelper.isDeviceLocked(context)
            if (isLocked) {
                SendTrampolineActivity.launchWithKeyguardDismiss(context, intent)
            } else {
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    openAppByName("teams")
                }
            }
            LogBus.log("PhoneActionExecutor -> Teams call dispatched to $displayName ($userIdentifier)")
        } catch (e: Exception) {
            LogBus.error("could not make Teams call for $target", e)
        }
    }

    fun makeGoogleChatCall(target: String?) {
        try {
            if (target.isNullOrBlank()) return
            val cleanTarget = target.trim()
                .replace(Regex("(?i)^(llamar?\\s+(a|al)?\\s*|marcar?\\s+(a|al)?\\s*|a\\s+mi\\s+|a\\s+|al\\s+)"), "")
                .replace(Regex("(?i)\\s+(por|en|de)?\\s*(google chat|google meet|meet|chat)$"), "")
                .trim()

            LockScreenHelper.wakeUpScreen(context, "MYVU:GoogleCall")
            com.myvu.client.service.AutoSendAccessibilityService.isAutoSendActive = false

            val emailResolved = ContactHelper.resolveContactEmail(context, cleanTarget)
            val phoneResolved = if (emailResolved == null) ContactHelper.resolveContactPhone(context, cleanTarget) else null

            val userIdentifier = emailResolved?.first ?: phoneResolved?.first ?: cleanTarget
            val displayName = emailResolved?.second ?: phoneResolved?.second ?: cleanTarget

            // Try Meet / Duo call deep link
            val meetUri = Uri.parse("https://meet.google.com/call/" + Uri.encode(userIdentifier))
            val intent = Intent(Intent.ACTION_VIEW, meetUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val isLocked = LockScreenHelper.isDeviceLocked(context)
            if (isLocked) {
                SendTrampolineActivity.launchWithKeyguardDismiss(context, intent)
            } else {
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    openAppByName("meet")
                }
            }
            LogBus.log("PhoneActionExecutor -> Google Chat/Meet call dispatched to $displayName ($userIdentifier)")
        } catch (e: Exception) {
            LogBus.error("could not make Google Chat call for $target", e)
        }
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        return ContactHelper.levenshteinDistance(s1, s2)
    }

    data class ContactMatchInfo(
        val number: String?,
        val score: Int,
        val isExact: Boolean
    )

    fun lookupContactNumberWithScore(name: String?): ContactMatchInfo {
        if (name.isNullOrBlank()) return ContactMatchInfo(null, 0, false)
        val match = ContactHelper.findBestContactMatch(context, name)
        return if (match != null) {
            ContactMatchInfo(match.number, match.score, match.isExact)
        } else {
            ContactMatchInfo(null, 0, false)
        }
    }

    private fun lookupContactNumber(name: String?): String? {
        return ContactHelper.findBestContactMatch(context, name ?: "")?.number
    }

    fun queryExternal(query: String?, callback: (String, Boolean) -> Unit) {
        if (query.isNullOrBlank()) {
            callback("Consulta vacía.", false)
            return
        }
        ExternalInfoService.search(query.trim()) { resultText, success ->
            callback(resultText, success)
        }
    }

    fun openWebSearch(query: String?) {
        try {
            if (query.isNullOrBlank()) return
            queryExternal(query) { resultText, success ->
                LogBus.log("PhoneActionExecutor -> openWebSearch result: $resultText (success=$success)")
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(query.trim(), "UTF-8")))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            LogBus.log("voice action -> opened web search for: $query")
        } catch (e: Exception) {
            LogBus.error("could not open web search", e)
        }
    }

    fun setAlarm(valStr: String?) {
        try {
            if (valStr.isNullOrBlank()) return
            var message = "Alarma"
            var timeStr = valStr.trim()
            if (valStr.contains(":") || valStr.contains("|")) {
                val parts = valStr.split(Regex("[:|]"), 2)
                timeStr = parts[0].trim()
                message = parts[1].trim()
            }
            val timeParts = timeStr.split(Regex("[:\\.]"))
            val hour = timeParts[0].trim().toInt()
            val minute = if (timeParts.size > 1) timeParts[1].trim().toInt() else 0

            val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
            intent.putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
            intent.putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
            intent.putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message)
            intent.putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            LogBus.log("voice action -> alarm set for $hour:$minute message: $message")
        } catch (e: Exception) {
            LogBus.error("could not set alarm for $valStr", e)
        }
    }

    fun setTimer(valStr: String?) {
        try {
            if (valStr.isNullOrBlank()) return
            var message = "Temporizador"
            var durationStr = valStr.trim()
            if (valStr.contains(":") || valStr.contains("|")) {
                val parts = valStr.split(Regex("[:|]"), 2)
                durationStr = parts[0].trim()
                message = parts[1].trim()
            }
            val seconds = durationStr.replace(Regex("[^0-9]"), "").toInt()

            val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
            intent.putExtra(android.provider.AlarmClock.EXTRA_LENGTH, seconds)
            intent.putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message)
            intent.putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            LogBus.log("voice action -> timer set for ${seconds}s message: $message")
        } catch (e: Exception) {
            LogBus.error("could not set timer for $valStr", e)
        }
    }

    fun startNavigation(destination: String?) {
        try {
            if (destination.isNullOrBlank()) return
            val cleanDest = destination.trim()

            // 1. Iniciar HUD de navegación en las gafas AR vía MyvuService
            val conn = MyvuService.activeConnection()
            if (conn != null) {
                conn.nav().start(cleanDest)
                LogBus.log("voice action -> started Glasses AR HUD Navigation to: $cleanDest")
            }

            // 2. Abrir navegación GPS en el teléfono
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + URLEncoder.encode(cleanDest, "UTF-8"))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            LogBus.log("voice action -> started phone GPS navigation to: $cleanDest")
        } catch (e: Exception) {
            LogBus.error("could not start navigation for $destination", e)
        }
    }

    fun stopNavigation() {
        try {
            val conn = MyvuService.activeConnection()
            conn?.nav()?.stop()
            LogBus.log("voice action -> stopped Glasses AR Navigation")
        } catch (e: Exception) {
            LogBus.error("could not stop navigation", e)
        }
    }

    fun addOutlookCalendarEvent(valStr: String?) {
        try {
            if (valStr.isNullOrBlank()) return
            var title = valStr.trim()
            if (valStr.contains(":") || valStr.contains("|")) {
                val parts = valStr.split(Regex("[:|]"), 2)
                title = parts[1].trim()
            }

            val intent = Intent(Intent.ACTION_INSERT)
            intent.data = android.provider.CalendarContract.Events.CONTENT_URI
            intent.putExtra(android.provider.CalendarContract.Events.TITLE, title)
            intent.setPackage("com.microsoft.office.outlook")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                LogBus.log("voice action -> added Outlook calendar event: $title")
            } catch (e: Exception) {
                addCalendarEvent(valStr)
            }
        } catch (e: Exception) {
            LogBus.error("could not add Outlook calendar event for $valStr", e)
        }
    }

    fun addGoogleCalendarEvent(valStr: String?) {
        try {
            if (valStr.isNullOrBlank()) return
            var title = valStr.trim()
            if (valStr.contains(":") || valStr.contains("|")) {
                val parts = valStr.split(Regex("[:|]"), 2)
                title = parts[1].trim()
            }

            val intent = Intent(Intent.ACTION_INSERT)
            intent.data = android.provider.CalendarContract.Events.CONTENT_URI
            intent.putExtra(android.provider.CalendarContract.Events.TITLE, title)
            intent.setPackage("com.google.android.calendar")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                LogBus.log("voice action -> added Google calendar event: $title")
            } catch (e: Exception) {
                addCalendarEvent(valStr)
            }
        } catch (e: Exception) {
            LogBus.error("could not add Google calendar event for $valStr", e)
        }
    }

    fun addCalendarEvent(valStr: String?) {
        try {
            if (valStr.isNullOrBlank()) return
            var title = valStr.trim()
            if (valStr.contains(":") || valStr.contains("|")) {
                val parts = valStr.split(Regex("[:|]"), 2)
                title = parts[1].trim()
            }

            val intent = Intent(Intent.ACTION_INSERT)
            intent.data = android.provider.CalendarContract.Events.CONTENT_URI
            intent.putExtra(android.provider.CalendarContract.Events.TITLE, title)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            LogBus.log("voice action -> added calendar event: $title")
        } catch (e: Exception) {
            LogBus.error("could not add calendar event for $valStr", e)
        }
    }

    fun createKeepNote(text: String?) {
        try {
            if (text.isNullOrBlank()) return
            val intent = Intent("com.google.android.keep.action.CREATE_NOTE")
            intent.setPackage("com.google.android.keep")
            intent.putExtra(Intent.EXTRA_TEXT, text.trim())
            intent.type = "text/plain"
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                LogBus.log("voice action -> created note in Google Keep: $text")
            } catch (e: Exception) {
                createNote(text)
            }
        } catch (e: Exception) {
            LogBus.error("could not create Keep note for $text", e)
        }
    }

    fun createSpecificReminder(valStr: String?) {
        try {
            if (valStr.isNullOrBlank()) return
            var rawTime = valStr.trim()
            var message = "Recordatorio"

            if (valStr.contains(":") || valStr.contains("|")) {
                val parts = valStr.split(Regex("[:|]"), 2)
                rawTime = parts[0].trim()
                message = parts[1].trim()
            }

            val triggerAt = ReminderTimeParser.parseTimeToMillis(rawTime)
            val requestCode = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()

            val repo = ReminderRepository(context)
            val reminder = repo.createReminder(message, triggerAt)

            if (reminder != null) {
                val scheduled = ReminderScheduler.scheduleReminder(context, reminder.id, triggerAt, reminder.alarmRequestCode)
                if (!scheduled) {
                    repo.updateReminderState(reminder.id, "FAILED")
                }
                LogBus.log("voice action -> created local reminder #${reminder.id}: $message at $triggerAt")
                NoteAiProcessor(context).processReminder(reminder.id) { _ -> }
            }
        } catch (e: Exception) {
            LogBus.error("could not create local reminder for $valStr", e)
        }
    }

    fun createReminderAction(valStr: String?) {
        createSpecificReminder(valStr)
    }

    fun deleteReminderAction(target: String?) {
        try {
            if (target.isNullOrBlank()) return
            val repo = ReminderRepository(context)
            val cleanTarget = target.trim()
            val id = cleanTarget.toLongOrNull()
            if (id != null) {
                repo.deleteReminder(id)
            } else {
                repo.deleteByTitle(cleanTarget)
            }
            LogBus.log("voice action -> deleted reminder '$target'")
        } catch (e: Exception) {
            LogBus.error("could not delete reminder '$target'", e)
        }
    }

    fun addTodoAction(valStr: String?) {
        try {
            if (valStr.isNullOrBlank()) return
            var listName = "General"
            var title = valStr.trim()
            var tags = ""

            if (valStr.contains(":") || valStr.contains("|")) {
                val parts = valStr.split(Regex("[:|]"), 2)
                listName = parts[0].trim().ifBlank { "General" }
                title = parts[1].trim()
            }

            if (title.contains("[tags:") || title.contains("[tag:")) {
                val tagMatch = Regex("\\[tags?:\\s*([^\\]]+)\\]", RegexOption.IGNORE_CASE).find(title)
                if (tagMatch != null) {
                    tags = tagMatch.groupValues[1].trim()
                    title = title.replace(tagMatch.value, "").trim()
                }
            }

            val repo = TodoRepository(context)
            val id = repo.createTodo(title = title, listName = listName, tags = tags)
            LogBus.log("voice action -> added todo #$id in [$listName]: '$title'")
        } catch (e: Exception) {
            LogBus.error("could not add todo for '$valStr'", e)
        }
    }

    fun markTodoDoneAction(target: String?) {
        try {
            if (target.isNullOrBlank()) return
            val repo = TodoRepository(context)
            val cleanTarget = target.trim()
            val id = cleanTarget.toLongOrNull()
            if (id != null) {
                repo.markCompleted(id, true)
            } else {
                repo.markCompletedByTitle(cleanTarget, true)
            }
            LogBus.log("voice action -> marked todo done '$target'")
        } catch (e: Exception) {
            LogBus.error("could not mark todo done '$target'", e)
        }
    }

    fun deleteTodoAction(target: String?) {
        try {
            if (target.isNullOrBlank()) return
            val repo = TodoRepository(context)
            val cleanTarget = target.trim()
            val id = cleanTarget.toLongOrNull()
            if (id != null) {
                repo.deleteTodo(id)
            } else {
                repo.deleteByTitle(cleanTarget)
            }
            LogBus.log("voice action -> deleted todo '$target'")
        } catch (e: Exception) {
            LogBus.error("could not delete todo '$target'", e)
        }
    }

    fun listTodosSummary(listName: String?): String {
        try {
            val repo = TodoRepository(context)
            val todos = repo.getPendingTodos(listName)
            if (todos.isEmpty()) {
                return "No tienes tareas pendientes" + (if (!listName.isNullOrBlank() && !listName.equals("all", ignoreCase = true)) " en la lista $listName." else ".")
            }
            val sb = StringBuilder("📋 Tareas pendientes:\n")
            todos.take(5).forEachIndexed { idx, t ->
                val tagStr = if (t.tags.isNotBlank()) " [${t.tags}]" else ""
                sb.append("${idx + 1}. [${t.listName}] ${t.title}$tagStr\n")
            }
            return sb.toString().trim()
        } catch (e: Exception) {
            LogBus.error("could not list todos", e)
            return "Error al consultar tareas."
        }
    }

    fun deleteNoteAction(target: String?) {
        try {
            if (target.isNullOrBlank()) return
            val repo = NoteRepository(context)
            val cleanTarget = target.trim()
            val id = cleanTarget.toLongOrNull()
            if (id != null) {
                repo.deleteNote(id)
            } else {
                repo.deleteByTitle(cleanTarget)
            }
            LogBus.log("voice action -> deleted note '$target'")
        } catch (e: Exception) {
            LogBus.error("could not delete note '$target'", e)
        }
    }

    fun updateNoteAction(valStr: String?) {
        try {
            if (valStr.isNullOrBlank()) return
            var target = ""
            var newBody = valStr.trim()
            if (valStr.contains(":") || valStr.contains("|")) {
                val parts = valStr.split(Regex("[:|]"), 2)
                target = parts[0].trim()
                newBody = parts[1].trim()
            }
            val repo = NoteRepository(context)
            val id = target.toLongOrNull()
            if (id != null) {
                repo.updateNote(id, newBody)
            } else {
                val existing = repo.search(target).firstOrNull()
                if (existing != null) {
                    repo.updateNote(existing.id, newBody)
                }
            }
            LogBus.log("voice action -> updated note '$target' with: $newBody")
        } catch (e: Exception) {
            LogBus.error("could not update note '$valStr'", e)
        }
    }

    fun createNote(text: String?) {
        try {
            if (text.isNullOrBlank()) return
            var cleanText = text.trim()
            var tags = ""
            if (cleanText.contains("[tags:") || cleanText.contains("[tag:")) {
                val tagMatch = Regex("\\[tags?:\\s*([^\\]]+)\\]", RegexOption.IGNORE_CASE).find(cleanText)
                if (tagMatch != null) {
                    tags = tagMatch.groupValues[1].trim()
                    cleanText = cleanText.replace(tagMatch.value, "").trim()
                }
            }
            val repo = NoteRepository(context)
            val id = repo.createNote(title = "", body = cleanText, tags = tags)
            if (id > 0) {
                NoteAiProcessor(context).processNote(id) { _ -> }
            }
            LogBus.log("voice action -> created local note #$id: $cleanText (tags: $tags)")
        } catch (e: Exception) {
            LogBus.error("could not create local note for $text", e)
        }
    }

    fun createNoteWithTags(valStr: String?) {
        try {
            if (valStr.isNullOrBlank()) return
            val parts = valStr.split("|")
            val title: String
            val body: String
            val tags: String
            when {
                parts.size >= 3 -> {
                    title = parts[0].trim()
                    body = parts[1].trim()
                    tags = parts[2].trim()
                }
                parts.size == 2 -> {
                    title = parts[0].trim()
                    body = parts[1].trim()
                    tags = ""
                }
                else -> {
                    title = ""
                    body = valStr.trim()
                    tags = ""
                }
            }
            val repo = NoteRepository(context)
            val id = repo.createNote(title = title, body = body, tags = tags)
            if (id > 0) {
                NoteAiProcessor(context).processNote(id) { _ -> }
            }
            LogBus.log("voice action -> created local note with tags #$id title='$title', tags='$tags'")
        } catch (e: Exception) {
            LogBus.error("could not create note with tags for $valStr", e)
        }
    }

    fun searchNotesSummary(query: String?): String {
        try {
            if (query.isNullOrBlank()) return "No se especificó término de búsqueda."
            val repo = NoteRepository(context)
            val notes = repo.search(query.trim())
            if (notes.isEmpty()) {
                return "No se encontraron notas para: '$query'."
            }
            val sb = StringBuilder("Notas encontradas (${notes.size}):\n")
            notes.take(5).forEachIndexed { index, note ->
                val titleStr = if (note.title.isNotBlank()) "[${note.title}] " else ""
                val tagsStr = if (note.tags.isNotBlank()) " (${note.tags})" else ""
                sb.append("${index + 1}. $titleStr${note.body}$tagsStr\n")
            }
            return sb.toString().trim()
        } catch (e: Exception) {
            LogBus.error("could not search notes for $query", e)
            return "Error al buscar notas."
        }
    }

    fun searchVoiceRecordingsSummary(query: String?): String {
        try {
            val repo = com.myvu.client.database.VoiceRecordingRepository(context)
            val recordings = repo.searchRecordings(query = query)
            if (recordings.isEmpty()) {
                return "No se encontraron grabaciones de voz" + (if (!query.isNullOrBlank()) " para: '$query'." else ".")
            }
            val sb = StringBuilder("🎙️ Grabaciones de voz (${recordings.size}):\n")
            recordings.take(5).forEachIndexed { index, rec ->
                val titleStr = rec.title.ifBlank { "Grabación #${rec.id}" }
                val summaryText = rec.summary.ifBlank { rec.rawTranscript }
                val snippet = if (summaryText.length > 100) summaryText.substring(0, 100) + "..." else summaryText
                val tagsStr = if (rec.tags.isNotBlank()) " [${rec.tags}]" else ""
                sb.append("${index + 1}. $titleStr: $snippet$tagsStr\n")
            }
            return sb.toString().trim()
        } catch (e: Exception) {
            LogBus.error("could not search voice recordings for $query", e)
            return "Error al buscar grabaciones de voz."
        }
    }

    fun listRemindersSummary(): String {
        try {
            val repo = ReminderRepository(context)
            val reminders = repo.getPendingReminders()
            if (reminders.isEmpty()) {
                return "No tienes recordatorios pendientes."
            }
            val sb = StringBuilder("⏰ Recordatorios pendientes (${reminders.size}):\n")
            reminders.take(5).forEachIndexed { idx, r ->
                val titleStr = r.title.ifBlank { r.body }
                val timeStr = r.formattedTriggerDate()
                sb.append("${idx + 1}. $titleStr (a las $timeStr)\n")
            }
            return sb.toString().trim()
        } catch (e: Exception) {
            LogBus.error("could not list reminders", e)
            return "Error al consultar recordatorios."
        }
    }

    fun openTeleprompter(text: String?) {
        try {
            if (text.isNullOrBlank()) return
            val cleanText = text.trim()
            val conn = MyvuService.activeConnection()
            if (conn != null) {
                conn.openTeleprompter(cleanText, "Prompter")
                LogBus.log("voice action -> opened teleprompter via MyvuService with text: $cleanText")
            } else {
                val intent = Intent("com.myvu.client.ACTION_TELEPROMPTER")
                intent.putExtra("text", cleanText)
                context.sendBroadcast(intent)
                LogBus.warn("voice action -> active connection null, sent teleprompter broadcast: $cleanText")
            }
        } catch (e: Exception) {
            LogBus.error("could not open teleprompter for $text", e)
        }
    }

    fun refreshWeather() {
        try {
            val conn = MyvuService.activeConnection()
            if (conn != null) {
                conn.weather().refresh()
                LogBus.log("voice action -> triggered weather refresh via active connection")
            } else {
                val intent = Intent("com.myvu.client.ACTION_REFRESH_WEATHER")
                context.sendBroadcast(intent)
                LogBus.log("voice action -> sent weather refresh broadcast")
            }
        } catch (e: Exception) {
            LogBus.error("could not refresh weather", e)
        }
    }

    fun queryWeather(callback: (String, Boolean) -> Unit) {
        try {
            val conn = MyvuService.activeConnection()
            if (conn == null) {
                callback("No hay conexión activa para consultar el clima.", false)
                return
            }
            conn.weather().query(object : com.myvu.client.weather.WeatherSync.QueryCallback {
                override fun onSuccess(reading: Weather.Reading) {
                    conn.weather().syncReading(reading)
                    callback(formatWeather(reading), true)
                }

                override fun onFailure(error: Exception) {
                    LogBus.warn("weather query failed: ${error.message}")
                    callback("No pude consultar el clima en este momento.", false)
                }
            })
        } catch (e: Exception) {
            LogBus.error("could not query weather", e)
            callback("No pude consultar el clima en este momento.", false)
        }
    }

    private fun formatWeather(reading: Weather.Reading): String {
        val place = reading.areaName?.takeIf { it.isNotBlank() }?.let { " en $it" } ?: ""
        val current = reading.temp?.let { "Temperatura actual$place: $it °C" }
        val range = if (reading.dayTempMax != null && reading.dayTempMin != null) {
            "Máxima ${reading.dayTempMax} °C y mínima ${reading.dayTempMin} °C"
        } else null
        val condition = reading.condition?.takeIf { it.isNotBlank() }
        return listOfNotNull(current, range, condition?.let { "Cielo $it" }).joinToString(". ") + "."
    }

    companion object {
        fun extractValue(text: String, tag: String): String {
            val idx = text.uppercase().indexOf(tag.uppercase())
            if (idx == -1) return ""
            val start = idx + tag.length
            var end = text.indexOf("\n", start)
            if (end == -1) end = text.length
            return text.substring(start, end).trim()
        }
    }

    private fun stripActionTags(text: String?): String {
        if (text == null) return ""
        var clean = text.replace(Regex("(?i)ACTION:[A-Z_]+(=|:)?([^\n]*)?"), "")
        clean = clean.replace(Regex("(?i)\\[Contexto del Sistema:[^\\]]*\\]"), "")
        clean = clean.replace(Regex("(?i)\\b(call|action)\\s*(=|igual a|dos puntos)\\s*([a-zA-Z0-9_ ]+)"), "")
        clean = clean.replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
        clean = clean.replace(Regex("[*_`~#>]"), "")
        clean = clean.replace(Regex("(?m)^[\\s*\\-]+\\s*"), "")
        return clean.replace(Regex("[ \\t]+"), " ").trim()
    }
}
