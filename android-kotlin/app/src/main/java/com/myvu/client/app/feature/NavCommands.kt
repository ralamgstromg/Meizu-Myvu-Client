package com.myvu.client.app.feature

import com.myvu.client.app.AppLayer
import org.json.JSONException
import org.json.JSONObject

/**
 * AR navigation HUD frames, ported from applayer.py.
 */
object NavCommands {
    const val SOURCE_PKG: String = AppLayer.PKG_NAV_PHONE
    const val LAUNCH_TARGET_PKG: String = AppLayer.PKG_LAUNCHER
    const val FRAME_TARGET_PKG: String = AppLayer.PKG_NAV_GLASS

    @JvmStatic
    @Throws(JSONException::class)
    fun buildOpen(): String = openApp("")

    @JvmStatic
    @Throws(JSONException::class)
    fun buildStart(
        ic: Int,
        pathDistanceM: Int,
        remainingM: Int,
        remainingS: Int,
        nextRoadName: String,
        nextRoadDistanceM: Int,
        speed: String,
        rideDistanceM: Int,
        gpsStatus: Int,
        roadClass: Int,
        naviMode: Int,
        displayPos: Int,
        maskMsg: Boolean,
        brightness: Boolean
    ): String {
        val ext = JSONObject()
            .put("naviMode", naviMode)
            .put("displayPos", displayPos)
            .put("maskMsg", if (maskMsg) 1 else 0)
            .put("brightness", if (brightness) 1 else 0)
            .put("ic", ic)
            .put("pd", pathDistanceM)
            .put("prd", remainingM)
            .put("prt", remainingS)
            .put("nrn", nextRoadName)
            .put("nrd", nextRoadDistanceM)
            .put("ns", speed)
            .put("rdd", rideDistanceM)
            .put("gs", gpsStatus)
            .put("hsr", roadClass)
            .put("ack", System.currentTimeMillis())
        return openApp(ext.toString())
    }

    private fun openApp(ext: String): String {
        return JSONObject()
            .put("action", "app")
            .put("data", JSONObject()
                .put("launchMode", "scene")
                .put("action", "open_app")
                .put("pkg", AppLayer.PKG_NAV_GLASS)
                .put("show_status_bar", false)
                .put("ext", ext)
                .put("app_name", "Navigation"))
            .toString()
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun buildNaviInfo(
        ic: Int,
        pathDistanceM: Int,
        remainingM: Int,
        remainingS: Int,
        nextRoadName: String,
        nextRoadDistanceM: Int,
        speed: String,
        rideDistanceM: Int,
        gpsStatus: Int,
        roadClass: Int,
        brightness: Int
    ): String {
        return JSONObject()
            .put("identity", "navi_info")
            .put("ic", ic)
            .put("pd", pathDistanceM)
            .put("prd", remainingM)
            .put("prt", remainingS)
            .put("nrn", nextRoadName)
            .put("nrd", nextRoadDistanceM)
            .put("ns", speed)
            .put("rdd", rideDistanceM)
            .put("gs", gpsStatus)
            .put("hsr", roadClass)
            .put("bts", brightness)
            .put("ack", System.currentTimeMillis())
            .toString()
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun buildEvent(event: String, naviMode: Int): String {
        return JSONObject()
            .put("identity", "navi_event")
            .put("naviMode", naviMode)
            .put("data", event)
            .put("ack", System.currentTimeMillis())
            .toString()
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun buildStop(): String = buildEvent("navi_stop", 0)
}
