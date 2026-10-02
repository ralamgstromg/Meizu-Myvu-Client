package com.myvu.client.routines

import android.content.Context
import com.myvu.client.core.GoogleDriveSyncHelper
import com.myvu.client.core.LogBus
import com.myvu.client.service.MirrorNotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Unread inbox summary through the Gmail REST API (read-only scope), using the
 * Google account already linked for Drive backups. Falls back to email
 * notifications when the account is not linked or lacks the Gmail scope.
 */
object GmailService {

    private const val API = "https://gmail.googleapis.com/gmail/v1/users/me"

    data class Mail(val from: String, val subject: String)

    suspend fun unreadSummary(context: Context, max: Int = 5): String {
        val mails = fetchUnread(context, max)
            ?: return MirrorNotificationListener.getUnreadSummary("email")
        if (mails.isEmpty()) return "No tienes correos sin leer."
        val list = mails.joinToString("; ") { "de ${it.from}: ${it.subject}" }
        val label = if (mails.size == 1) "correo sin leer" else "correos sin leer recientes"
        return "Tienes ${mails.size} $label: $list."
    }

    /** Null when Gmail is unavailable (no account, no scope, network error). */
    suspend fun fetchUnread(context: Context, max: Int): List<Mail>? = withContext(Dispatchers.IO) {
        val token = GoogleDriveSyncHelper.getValidAccessToken(context) ?: return@withContext null
        try {
            val q = URLEncoder.encode("is:unread category:primary", "UTF-8")
            val list = get("$API/messages?q=$q&maxResults=$max", token) ?: return@withContext null
            val ids = list.optJSONArray("messages") ?: return@withContext emptyList()
            (0 until ids.length()).mapNotNull { i ->
                val id = ids.getJSONObject(i).getString("id")
                val msg = get("$API/messages/$id?format=metadata&metadataHeaders=From&metadataHeaders=Subject", token)
                    ?: return@mapNotNull null
                val headers = msg.optJSONObject("payload")?.optJSONArray("headers") ?: return@mapNotNull null
                var from = ""
                var subject = ""
                for (h in 0 until headers.length()) {
                    val header = headers.getJSONObject(h)
                    when (header.optString("name")) {
                        "From" -> from = senderName(header.optString("value"))
                        "Subject" -> subject = header.optString("value")
                    }
                }
                Mail(from.ifBlank { "remitente desconocido" }, subject.ifBlank { "(sin asunto)" })
            }
        } catch (e: Exception) {
            LogBus.warn("GmailService -> unread fetch failed: ${e.message}")
            null
        }
    }

    /** "Ana Pérez <ana@x.com>" -> "Ana Pérez"; bare address -> local part. */
    internal fun senderName(raw: String): String {
        val name = raw.substringBefore('<').trim().trim('"')
        if (name.isNotEmpty()) return name
        return raw.trim('<', '>', ' ').substringBefore('@')
    }

    private fun get(url: String, token: String): JSONObject? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        return try {
            when (conn.responseCode) {
                200 -> JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                401, 403 -> {
                    LogBus.warn("GmailService -> HTTP ${conn.responseCode}: link Google again to grant Gmail read access")
                    null
                }
                else -> {
                    LogBus.warn("GmailService -> HTTP ${conn.responseCode}")
                    null
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
