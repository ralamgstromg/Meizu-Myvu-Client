package com.myvu.client.app

import com.myvu.client.protocol.Pb
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The StMessage envelope and the action-JSON builders, ported from
 * myvu_client/myvu/applayer.py.
 */
class AppLayer {

    companion object {
        const val PKG_LAUNCHER: String = "com.upuphone.star.launcher"
        const val PKG_TICI: String = "com.upuphone.ar.tici"
        const val PKG_AI: String = "com.upuphone.ai.assistant"
        const val PKG_NAV_GLASS: String = "com.upuphone.ar.navi.glass"
        const val PKG_NAV_PHONE: String = "com.upuphone.ar.navi.lite"
        const val PKG_INTERCONNECT: String = "com.upuphone.xr.interconnect"
        const val PKG_SELF: String = "com.myvu.client"

        private const val DEFAULT_APP_NAME = "ARIA"

        @JvmStatic
        @JvmOverloads
        @Throws(JSONException::class)
        fun buildNotificationAction(
            title: String,
            content: String,
            appName: String = DEFAULT_APP_NAME
        ): String {
            val now = System.currentTimeMillis()
            val entry = JSONObject()
                .put("appName", appName)
                .put("title", title)
                .put("content", content)
                .put("canReply", false)
                .put("type", "MSG_TYPE_NORMAL")
                .put("id", "phone-android-" + (now / 1000))
                .put("packageName", PKG_SELF)
                .put("crateTime", now)
                .put("extra", "{}")

            return JSONObject()
                .put("action", "notification")
                .put("data", JSONObject()
                    .put("notificationAction", "SHOW_NOTIFICATION")
                    .put("data", JSONArray().put(entry)))
                .toString()
        }
    }

    /** Matches the Python client, which sends 5001 first. */
    private var appMsgId: Int = 5000

    @JvmOverloads
    fun buildSendActionBody(
        actionJson: String,
        targetPkg: String = PKG_LAUNCHER,
        sourcePkg: String = PKG_LAUNCHER
    ): ByteArray {
        appMsgId += 1
        var body = Pb.string(2, sourcePkg)
        body = Pb.concat(body, Pb.string(3, targetPkg))
        body = Pb.concat(body, Pb.string(4, actionJson))
        body = Pb.concat(body, Pb.varintField(6, appMsgId.toLong()))
        return body
    }

    fun lastAppMsgId(): Int = appMsgId
}
