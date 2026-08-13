package com.myvu.client.app.feature

import org.json.JSONException
import org.json.JSONObject

/**
 * The launcher "system" action family, ported from applayer.py.
 */
object SystemSettings {

    private fun envelope(subAction: String): JSONObject {
        return JSONObject()
            .put("action", "system")
            .put("data", JSONObject().put("action", subAction))
    }

    private fun nested(subAction: String, key: String, value: Any): String {
        val env = envelope(subAction)
        env.getJSONObject("data").put("value", JSONObject().put(key, value))
        return env.toString()
    }

    // ------------------------------------------------------- flat-value form

    @JvmStatic
    @JvmOverloads
    @Throws(JSONException::class)
    fun setVolume(value: Int, streamType: Int = 3): String {
        val env = envelope("set_volume")
        env.getJSONObject("data")
            .put("value", value.toString())
            .put("streamType", streamType)
            .put("needReply", false)
        return env.toString()
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun setBrightness(value: Int): String {
        val env = envelope("set_brightness")
        env.getJSONObject("data").put("value", value.toString())
        return env.toString()
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun toggleWifi(enable: Boolean): String {
        val env = envelope("toggle_wifi")
        env.getJSONObject("data").put("value", enable)
        return env.toString()
    }

    // ----------------------------------------------------- nested-value form

    @JvmStatic
    @Throws(JSONException::class)
    fun setLanguage(language: String, country: String): String {
        val env = envelope("set_language")
        env.getJSONObject("data").put(
            "value", JSONObject()
                .put("language", language)
                .put("country", country)
        )
        return env.toString()
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun setDeviceName(name: String): String = nested("set_device_name", "device_name", name)

    @JvmStatic
    @Throws(JSONException::class)
    fun setScreenOffTime(seconds: Int): String = nested("set_screen_off_time", "screen_off_time", seconds)

    @JvmStatic
    @Throws(JSONException::class)
    fun setZenMode(on: Boolean): String = nested("set_zen_mode", "zen_mode", on)

    @JvmStatic
    @Throws(JSONException::class)
    fun setAirMode(on: Boolean): String = nested("set_air_mode", "air_mode", on)

    @JvmStatic
    @Throws(JSONException::class)
    fun setWearDetection(on: Boolean): String = nested("set_wear_detection_mode", "wear_detection_mode", on)

    @JvmStatic
    @Throws(JSONException::class)
    fun setMusicTpControl(on: Boolean): String = nested("set_music_tp_control_mode", "music_tp_control_mode", on)

    @JvmStatic
    @Throws(JSONException::class)
    fun setStandbyPosition(position: Int): String = nested("set_standby_position", "standby_position", position)

    @JvmStatic
    @Throws(JSONException::class)
    fun setFovPosType(value: Int): String = nested("set_fov_pos_type", "fov_pos", value)

    // ------------------------------------------------------------- queries

    @JvmStatic
    @Throws(JSONException::class)
    fun query(subAction: String): String = envelope(subAction).toString()
}
