package com.myvu.client.app.feature

import org.json.JSONException
import org.json.JSONObject
import java.util.TimeZone

/**
 * Clock sync, ported from applayer.sync_time.
 */
object ClockSync {
    const val ACTION: String = "SyncOffSetTime"

    @JvmStatic
    @Throws(JSONException::class)
    fun build(): String {
        val now = System.currentTimeMillis()
        val offsetMs = TimeZone.getDefault().getOffset(now)

        return JSONObject()
            .put("action", ACTION)
            .put("data", JSONObject()
                .put("syncTimeData", now.toString())
                .put("timeZoneOffSet", offsetMs))
            .toString()
    }

    @JvmStatic
    fun isRequest(action: JSONObject): Boolean {
        if (ACTION != action.optString("action")) return false
        val data = action.optJSONObject("data")
        return data == null || !data.has("syncTimeData")
    }
}
