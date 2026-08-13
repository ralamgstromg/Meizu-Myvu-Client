package com.myvu.client.app.feature

import com.myvu.client.app.AppLayer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The notification action family.
 */
object Notifications {
    const val SHOW: String = "SHOW_NOTIFICATION"
    const val DISMISS: String = "DISMISS_NOTIFICATION"

    private const val MAX_TITLE = 100
    private const val MAX_CONTENT = 500

    private fun envelope(subAction: String, payload: Any): JSONObject {
        return JSONObject()
            .put("action", "notification")
            .put("data", JSONObject()
                .put("notificationAction", subAction)
                .put("data", payload))
    }

    @JvmStatic
    fun notificationId(packageName: String, numericId: Int): String {
        return "phone-$packageName-$numericId"
    }

    private fun sanitize(s: String?, max: Int): String {
        if (s == null) return ""
        val sb = StringBuilder(Math.min(s.length, max))
        var i = 0
        while (i < s.length && sb.length < max) {
            val c = s[i]
            if (c == '\n' || c == '\r' || c == '\t') {
                sb.append(' ')
            } else if (c.code >= 0x20) {
                sb.append(c)
            }
            i++
        }
        return sb.toString().trim()
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun entry(
        packageName: String,
        numericId: Int,
        title: String?,
        content: String?,
        appName: String?,
        postTime: Long,
        canReply: Boolean
    ): JSONObject {
        return JSONObject()
            .put("appName", sanitize(appName, MAX_TITLE))
            .put("title", sanitize(title, MAX_TITLE))
            .put("content", sanitize(content, MAX_CONTENT))
            .put("canReply", canReply)
            .put("type", "MSG_TYPE_NORMAL")
            .put("id", notificationId(packageName, numericId))
            .put("packageName", packageName)
            .put("crateTime", postTime)
            .put("extra", "{}")
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun buildShow(vararg entries: JSONObject): String {
        val arr = JSONArray()
        for (e in entries) arr.put(e)
        return envelope(SHOW, arr).toString()
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun buildShow(title: String, content: String): String {
        val now = System.currentTimeMillis()
        return buildShow(
            entry(
                AppLayer.PKG_SELF,
                (now / 1000).toInt() and 0x7FFFFFFF,
                title,
                content,
                "ARIA",
                now,
                false
            )
        )
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun buildDismiss(vararg ids: String): String {
        val arr = JSONArray()
        for (id in ids) arr.put(id)
        val payload = JSONObject()
            .put("type", 0)
            .put("ids", arr)
        return envelope(DISMISS, payload).toString()
    }
}
