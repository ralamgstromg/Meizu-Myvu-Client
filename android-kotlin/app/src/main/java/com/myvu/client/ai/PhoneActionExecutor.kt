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
import com.myvu.client.reminder.ReminderScheduler
import com.myvu.client.reminder.ReminderTimeParser
import com.myvu.client.service.MirrorNotificationListener
import com.myvu.client.service.MyvuService
import java.net.URLEncoder

/**
 * Executes system & phone actions requested by voice via Gemini / AI.
 * Supports volume adjustments, media control, WhatsApp, Telegram, calls, and SMS.
 */
class PhoneActionExecutor(context: Context) {

    private val context: Context = context.applicationContext
    private val audioManager: AudioManager? = this.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

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
        if (lower.contains("action:opentune_play=")) {
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
        if (lower.contains("action:call=")) {
            val target = extractValue(aiText, "ACTION:CALL=")
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

        // 8. Timers
        if (lower.contains("action:timer=")) {
            val timerVal = extractValue(aiText, "ACTION:TIMER=")
            setTimer(timerVal)
        }

        // 9. GPS Navigation
        if (lower.contains("action:navigate=")) {
            val dest = extractValue(aiText, "ACTION:NAVIGATE=")
            startNavigation(dest)
        }

        // 10. Calendar Events (General & Specific Accounts)
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

        // 11. Notes (Google Keep vs Notes with Tags vs Quick Notes)
        if (lower.contains("action:note_keep=")) {
            val noteText = extractValue(aiText, "ACTION:NOTE_KEEP=")
            createKeepNote(noteText)
        } else if (lower.contains("action:note_tags=")) {
            val noteVal = extractValue(aiText, "ACTION:NOTE_TAGS=")
            createNoteWithTags(noteVal)
        } else if (lower.contains("action:note=")) {
            val noteText = extractValue(aiText, "ACTION:NOTE=")
            createNote(noteText)
        }

        // 12. Search Notes
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

    fun playFromSearchInOpenTune(query: String?) {
        try {
            if (query.isNullOrBlank()) {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                return
            }
            val intent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            intent.putExtra(android.app.SearchManager.QUERY, query.trim())
            intent.putExtra(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val openTunePkgs = arrayOf(
                "com.opentune.app", "org.opentune.android", "com.opentune.music",
                "com.vibe.opentune", "com.github.opentune", "com.opentune"
            )
            var launched = false
            for (pkg in openTunePkgs) {
                try {
                    val pkgIntent = Intent(intent)
                    pkgIntent.setPackage(pkg)
                    context.startActivity(pkgIntent)
                    LogBus.log("voice action -> launched OpenTune ($pkg) search/play for: $query")
                    launched = true
                    break
                } catch (ignored: Exception) {
                }
            }
            if (!launched) {
                context.startActivity(intent)
                LogBus.log("voice action -> launched generic media play from search for: $query")
            }
        } catch (e: Exception) {
            LogBus.error("could not play in OpenTune for $query", e)
        }
    }

    fun openWhatsApp(text: String?) {
        try {
            if (text.isNullOrBlank()) return
            var recipient: String? = null
            var message = text.trim()

            if (text.contains(":") || text.contains("|")) {
                val parts = text.split(Regex("[:|]"), 2)
                recipient = parts[0].trim()
                message = parts[1].trim()
            }

            val url = StringBuilder("https://api.whatsapp.com/send?")
            if (!recipient.isNullOrEmpty()) {
                val number = lookupContactNumber(recipient)
                if (!number.isNullOrEmpty()) {
                    val cleanNum = number.replace(Regex("[^0-9]"), "")
                    url.append("phone=").append(cleanNum).append("&")
                }
            }
            url.append("text=").append(URLEncoder.encode(message, "UTF-8"))

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            LogBus.log("voice action -> opened WhatsApp (recipient=$recipient) with text: $message")
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

    fun makeCall(target: String?) {
        try {
            if (target.isNullOrBlank()) return
            val cleanTarget = target.trim()
            var number: String? = null

            if (cleanTarget.matches(Regex("^[0-9+#* -]+$"))) {
                number = cleanTarget
            } else {
                number = lookupContactNumber(cleanTarget)
                if (number.isNullOrEmpty()) {
                    val stripped = cleanTarget.replace(Regex("(?i)^(a\\s+)?(mi\\s+)?"), "").trim()
                    if (stripped.isNotEmpty() && !stripped.equals(cleanTarget, ignoreCase = true)) {
                        number = lookupContactNumber(stripped)
                    }
                }
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
                            tm.placeCall(Uri.parse("tel:" + Uri.encode(number)), extras)
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
                intent = Intent(Intent.ACTION_DIAL)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                LogBus.warn("voice action -> contact number not found for $target, opening dialer")
            }
        } catch (e: Exception) {
            LogBus.error("could not place call for $target", e)
        }
    }

    private fun lookupContactNumber(name: String?): String? {
        if (name.isNullOrBlank()) return null
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            LogBus.warn("READ_CONTACTS permission not granted -- cannot lookup $name")
            return null
        }
        try {
            context.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER, android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                arrayOf("%" + name.trim() + "%"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (idx >= 0) return cursor.getString(idx)
                }
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
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + URLEncoder.encode(destination.trim(), "UTF-8")))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            LogBus.log("voice action -> started GPS navigation to: $destination")
        } catch (e: Exception) {
            LogBus.error("could not start navigation for $destination", e)
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

    fun createNote(text: String?) {
        try {
            if (text.isNullOrBlank()) return
            val repo = NoteRepository(context)
            val id = repo.createNote(text.trim())
            LogBus.log("voice action -> created local note #$id: $text")
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
        var clean = text.replace(Regex("(?i)ACTION:[A-Z_]+(=[^\n]*)?"), "")
        clean = clean.replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
        clean = clean.replace(Regex("[*_`~#>]"), "")
        clean = clean.replace(Regex("(?m)^[\\s*\\-]+\\s*"), "")
        return clean.replace(Regex("[ \\t]+"), " ").trim()
    }
}
