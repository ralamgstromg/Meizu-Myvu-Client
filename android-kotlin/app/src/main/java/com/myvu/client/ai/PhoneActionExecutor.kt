package com.myvu.client.ai

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import com.myvu.client.core.LogBus
import com.myvu.client.database.NoteRepository
import com.myvu.client.database.ReminderRepository
import com.myvu.client.database.TodoRepository
import com.myvu.client.database.TodoItem
import com.myvu.client.reminder.ReminderScheduler
import com.myvu.client.reminder.ReminderTimeParser
import com.myvu.client.service.MirrorNotificationListener
import com.myvu.client.service.MyvuService
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
            "make_call" -> {
                val target = action.arguments["target"] ?: action.arguments["number"]
                makeCall(target)
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
                val command = action.arguments["command"]
                when (command?.lowercase()) {
                    "pause" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
                    "resume", "play" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                    "next" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                    "prev", "previous" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    else -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                }
            }
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
        } else if (lower.contains("action:app_open=")) {
            val appName = extractValue(aiText, "ACTION:APP_OPEN=")
            openAppByName(appName)
        } else if (lower.contains("action:opentune_play=")) {
            val query = extractValue(aiText, "ACTION:OPENTUNE_PLAY=")
            playFromSearchInOpenTune(query)
        } else if (lower.contains("action:opentune_search=")) {
            val query = extractValue(aiText, "ACTION:OPENTUNE_SEARCH=")
            playFromSearchInOpenTune(query)
        } else if (lower.contains("action:opentune_pause")) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        } else if (lower.contains("action:opentune_resume")) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        } else if (lower.contains("action:opentune_next") || lower.contains("action:media_next")) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        } else if (lower.contains("action:opentune_prev") || lower.contains("action:media_prev")) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        } else if (lower.contains("action:opentune_repeat")) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_RECORD)
        } else if (lower.contains("action:media_play")) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
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

        // 5. Calls / Dialing
        if (lower.contains("action:call=") || lower.contains("action:call:") || lower.contains("action:call ")) {
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

        // 13. Search Notes
        if (lower.contains("action:search_notes=")) {
            val query = extractValue(aiText, "ACTION:SEARCH_NOTES=")
            val searchResults = searchNotesSummary(query)
            return stripActionTags(aiText) + "\n\n" + searchResults
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
        if (lower.contains("action:summary=")) {
            val cat = extractValue(aiText, "ACTION:SUMMARY=")
            val summary = MirrorNotificationListener.getUnreadSummary(cat)
            return stripActionTags(aiText) + "\n\n" + summary
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

    fun sendMediaKey(keyCode: Int) {
        val am = audioManager ?: return
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
        am.dispatchMediaKeyEvent(down)
        am.dispatchMediaKeyEvent(up)
        LogBus.log("voice action -> sent media key $keyCode")
    }

    fun openAppByName(rawName: String?) {
        try {
            if (rawName.isNullOrBlank()) return
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
                    context.startActivity(intent)
                    LogBus.log("voice action -> launched app '$rawName' (pkg: $bestPkg)")
                    return
                }
            }
            LogBus.warn("voice action -> app '$rawName' not found on device")
        } catch (e: Exception) {
            LogBus.error("could not open app '$rawName'", e)
        }
    }

    fun playInThirdPartyApp(appAndQuery: String?) {
        try {
            if (appAndQuery.isNullOrBlank()) return
            var appName = "music"
            var query = appAndQuery.trim()

            if (appAndQuery.contains(":") || appAndQuery.contains("|")) {
                val parts = appAndQuery.split(Regex("[:|]"), 2)
                appName = parts[0].trim().lowercase()
                query = parts[1].trim()
            }

            val targetPkgs = when {
                appName.contains("youtube music") || appName.contains("yt music") ->
                    listOf("com.google.android.apps.youtube.music")
                appName.contains("spotify") ->
                    listOf("com.spotify.music", "com.spotify.lite")
                appName.contains("youtube") ->
                    listOf("com.google.android.youtube")
                appName.contains("deezer") ->
                    listOf("deezer.android.app")
                appName.contains("amazon") ->
                    listOf("com.amazon.mp3")
                appName.contains("soundcloud") ->
                    listOf("com.soundcloud.android")
                appName.contains("apple") ->
                    listOf("com.apple.android.music")
                appName.contains("opentune") ->
                    listOf("com.opentune.app", "org.opentune.android", "com.opentune.music")
                else -> emptyList()
            }

            val pm = context.packageManager
            var resolvedPkg: String? = targetPkgs.firstOrNull { pkg ->
                try {
                    pm.getPackageInfo(pkg, 0)
                    true
                } catch (ignored: Exception) {
                    false
                }
            }

            if (resolvedPkg == null && targetPkgs.isEmpty()) {
                // Búsqueda difusa en caso de que sea otra app de música instalada
                val packages = pm.getInstalledPackages(0)
                for (p in packages) {
                    val label = normalize(pm.getApplicationLabel(p.applicationInfo ?: continue).toString())
                    if (label.contains(appName)) {
                        resolvedPkg = p.packageName
                        break
                    }
                }
            }

            // Intent estándar de reproducción multimedia
            val mediaIntent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(android.app.SearchManager.QUERY, query)
                putExtra(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (resolvedPkg != null) {
                mediaIntent.setPackage(resolvedPkg)
                try {
                    context.startActivity(mediaIntent)
                    LogBus.log("voice action -> launched MediaPlay in $resolvedPkg for: $query")
                } catch (e: Exception) {
                    // Fallback a deep link según la app
                    if (resolvedPkg.contains("youtube")) {
                        val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=" + URLEncoder.encode(query, "UTF-8"))).apply {
                            setPackage(resolvedPkg)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(ytIntent)
                    } else if (resolvedPkg.contains("spotify")) {
                        val spotIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:" + URLEncoder.encode(query, "UTF-8"))).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(spotIntent)
                    }
                }
            } else {
                // Lanzador genérico
                context.startActivity(mediaIntent)
                LogBus.log("voice action -> launched generic media play from search for: $query")
            }

            // Disparo de Play diferido para asegurar que comience a reproducir
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            }, 1000L)

        } catch (e: Exception) {
            LogBus.error("could not execute playInThirdPartyApp for '$appAndQuery'", e)
        }
    }

    fun playFromSearchInOpenTune(query: String?) {
        playInThirdPartyApp("opentune: ${query ?: ""}")
    }

    fun openWhatsApp(text: String?) {
        try {
            if (text.isNullOrBlank()) return
            var recipient: String? = null
            var message = text.trim()

            if (text.contains(":") || text.contains("|")) {
                val parts = text.split(Regex("[:|]"), 2)
                recipient = parts[0].trim()
                    .replace(Regex("(?i)^(enviar?\\s+(un\\s+)?(mensaje|whatsapp)(\\s+a|\\s+al)?\\s*|a\\s+mi\\s+|a\\s+|al\\s+)"), "")
                    .trim()
                message = parts[1].trim()
            }

            var number: String? = null
            if (!recipient.isNullOrEmpty()) {
                if (recipient.matches(Regex("^[0-9+#* -]+$"))) {
                    number = recipient
                } else {
                    number = lookupContactNumber(recipient)
                    if (number.isNullOrEmpty()) {
                        val parts = recipient.split(Regex("\\s+"))
                        for (part in parts) {
                            if (part.length >= 3) {
                                number = lookupContactNumber(part)
                                if (!number.isNullOrEmpty()) break
                            }
                        }
                    }
                }
            }

            var cleanNum = number?.replace(Regex("[^0-9]"), "") ?: ""
            // Si es un celular colombiano de 10 dígitos (ej: 3011161686), anteponer el código de país 57
            if (cleanNum.length == 10 && (cleanNum.startsWith("3") || cleanNum.startsWith("6"))) {
                cleanNum = "57$cleanNum"
            }

            val url = StringBuilder("https://api.whatsapp.com/send?")
            if (cleanNum.isNotEmpty()) {
                url.append("phone=").append(cleanNum).append("&")
            }
            if (message.isNotEmpty()) {
                url.append("text=").append(URLEncoder.encode(message, "UTF-8"))
            }

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))
            intent.setPackage("com.whatsapp")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            try {
                context.startActivity(intent)
                LogBus.log("voice action -> opened WhatsApp (recipient=$recipient, phone=$cleanNum) with text: $message")
            } catch (e: Exception) {
                val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))
                genericIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(genericIntent)
                LogBus.log("voice action -> opened generic WhatsApp browser/app fallback for: $message")
            }
        } catch (e: Exception) {
            LogBus.error("could not open WhatsApp", e)
        }
    }

    fun openTelegram(text: String?) {
        try {
            if (text.isNullOrBlank()) return
            val message = text.trim()

            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("tg://msg?text=" + URLEncoder.encode(message, "UTF-8"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
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
        val nfd = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase().trim()
    }

    fun makeCall(target: String?) {
        try {
            if (target.isNullOrBlank()) return
            val cleanTarget = target.trim()
                .replace(Regex("(?i)^(llamar?\\s+(a|al)?\\s*|marcar?\\s+(a|al)?\\s*|a\\s+mi\\s+|a\\s+|al\\s+)"), "")
                .trim()
            var number: String? = null

            if (cleanTarget.matches(Regex("^[0-9+#* -]+$"))) {
                number = cleanTarget
            } else {
                number = lookupContactNumber(cleanTarget)
                if (number.isNullOrEmpty()) {
                    val parts = cleanTarget.split(Regex("\\s+"))
                    for (part in parts) {
                        if (part.length >= 3) {
                            number = lookupContactNumber(part)
                            if (!number.isNullOrEmpty()) break
                        }
                    }
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
                            LogBus.log("voice action -> TelecomManager placed direct call to $target ($number)")
                            return
                        }
                    } catch (e: Exception) {
                        LogBus.warn("TelecomManager placeCall failed: ${e.message}, falling back to Intent")
                    }
                }
                intent = Intent(if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                LogBus.log("voice action -> placing " + (if (hasCallPerm) "direct call" else "dialer call") + " to $target ($number)")
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

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun lookupContactNumber(name: String?): String? {
        if (name.isNullOrBlank()) return null
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            LogBus.warn("READ_CONTACTS permission not granted -- cannot lookup $name")
            return null
        }
        try {
            val normalizedSearch = normalize(name)
            val searchTokens = normalizedSearch.split(Regex("\\s+")).filter { it.length >= 2 }

            var bestNumber: String? = null
            var bestScore = Int.MIN_VALUE

            context.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val numIdx = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIdx = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                if (numIdx >= 0 && nameIdx >= 0) {
                    while (cursor.moveToNext()) {
                        val contactName = cursor.getString(nameIdx) ?: continue
                        val contactNumber = cursor.getString(numIdx) ?: continue
                        val normalizedContact = normalize(contactName)
                        val contactTokens = normalizedContact.split(Regex("\\s+")).filter { it.length >= 2 }

                        // 1. Coincidencia Exacta
                        if (normalizedContact == normalizedSearch) {
                            return contactNumber
                        }

                        // 2. Cálculo de puntuación por proximidad y tokens compartidos (FTS)
                        var currentScore = 0
                        if (normalizedContact.contains(normalizedSearch)) {
                            currentScore += 100
                        }

                        for (sToken in searchTokens) {
                            for (cToken in contactTokens) {
                                if (sToken == cToken) {
                                    currentScore += 50
                                } else if (cToken.contains(sToken) || sToken.contains(cToken)) {
                                    currentScore += 25
                                } else {
                                    val dist = levenshteinDistance(sToken, cToken)
                                    val maxLen = maxOf(sToken.length, cToken.length)
                                    if (maxLen > 3 && dist <= 2) {
                                        currentScore += (20 - (dist * 5))
                                    }
                                }
                            }
                        }

                        if (currentScore > bestScore && currentScore >= 15) {
                            bestScore = currentScore
                            bestNumber = contactNumber
                        }
                    }
                }
            }
            if (bestNumber != null) {
                LogBus.log("contact fuzzy match -> '$name' matched number ($bestScore pts)")
                return bestNumber
            }
        } catch (e: Exception) {
            LogBus.warn("could not lookup contact: ${e.message}")
        }
        return null
    }

    fun openWebSearch(query: String?) {
        try {
            if (query.isNullOrBlank()) return
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

    private fun extractValue(text: String, tag: String): String {
        val idx = text.uppercase().indexOf(tag.uppercase())
        if (idx == -1) return ""
        val start = idx + tag.length
        var end = text.indexOf("\n", start)
        if (end == -1) end = text.length
        return text.substring(start, end).trim()
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
