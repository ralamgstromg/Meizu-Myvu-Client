package com.myvu.client.app

import com.myvu.client.app.feature.ClockSync
import com.myvu.client.app.feature.Weather
import com.myvu.client.core.LogBus
import org.json.JSONException
import org.json.JSONObject

/**
 * Handles messages the glasses send US, ported from the _check_* helpers in
 * applayer.py.
 */
class InboundRouter(private val sender: Sender) {

    fun interface Sender {
        /** Sends an action with explicit routing packages. */
        fun send(actionJson: String, targetPkg: String, sourcePkg: String)
    }

    /** Fired when the glasses' AI button or wake word triggers. */
    fun interface AiTriggerListener {
        fun onAiTrigger(code: Int, payload: JSONObject?)
    }

    /** Fired when the glasses ask for a fresh weather push. */
    fun interface WeatherRequestListener {
        fun onWeatherRequested()
    }

    /** Fired when the glasses send a battery status update. */
    fun interface BatteryUpdateListener {
        fun onBatteryUpdated(battery: Int, isCharging: Boolean)
    }

    private var aiListener: AiTriggerListener? = null
    private var weatherListener: WeatherRequestListener? = null
    private var batteryListener: BatteryUpdateListener? = null

    fun setAiTriggerListener(listener: AiTriggerListener?) {
        this.aiListener = listener
    }

    fun setWeatherRequestListener(listener: WeatherRequestListener?) {
        this.weatherListener = listener
    }

    fun setBatteryUpdateListener(listener: BatteryUpdateListener?) {
        this.batteryListener = listener
    }

    /** Inspects one inbound relay body and answers anything that needs answering. */
    fun handle(body: String) {
        for (candidate in findJsonObjects(body)) {
            val obj: JSONObject = try {
                JSONObject(candidate)
            } catch (e: JSONException) {
                continue
            }
            checkLaunchAppRequest(obj)
            checkTimeSyncRequest(obj)
            checkWeatherRequest(obj)
            checkAiTrigger(obj)
            checkBatteryInfo(obj)
        }
    }

    private fun checkWeatherRequest(msg: JSONObject) {
        if (!Weather.isSyncRequest(msg)) return
        LogBus.log("<- the glasses asked for weather")
        weatherListener?.onWeatherRequested()
    }

    private fun checkLaunchAppRequest(msg: JSONObject) {
        if (msg.optInt("type", -1) != 11) return
        val data = msg.optJSONObject("data") ?: return
        val appId = data.optString("appId", "")
        if (appId.isEmpty()) return

        try {
            val response = JSONObject()
                .put("type", 12)
                .put("data", JSONObject()
                    .put("appId", appId)
                    .put("code", 200)
                    .put("menuId", if (data.isNull("menuId")) "" else data.opt("menuId"))
                    .put("requestId", if (data.isNull("requestId")) "" else data.opt("requestId"))
                    .put("success", true))
            LogBus.log("glasses asked to launch $appId -- acking type:12")
            sender.send(
                response.toString(),
                AppLayer.PKG_INTERCONNECT,
                AppLayer.PKG_INTERCONNECT
            )
        } catch (e: JSONException) {
            LogBus.error("could not build the launch-app ack", e)
        }
    }

    private fun checkTimeSyncRequest(msg: JSONObject) {
        if (!ClockSync.isRequest(msg)) return
        try {
            LogBus.log("glasses requested a time sync -- replying")
            sender.send(ClockSync.build(), AppLayer.PKG_LAUNCHER, AppLayer.PKG_LAUNCHER)
        } catch (e: JSONException) {
            LogBus.error("could not build the time sync reply", e)
        }
    }

    private fun checkAiTrigger(msg: JSONObject) {
        if (!msg.has("code")) return
        val code = msg.optInt("code", -1)
        if (code != 3 && code != 7) return

        val payload = msg.optJSONObject("payload") ?: msg.optJSONObject("data")
        LogBus.log(
            "Hardware trigger: code=$code" +
                    if (code == 3) " (button/deep-touch)" else " (wake word)"
        )
        aiListener?.onAiTrigger(code, payload)
    }

    private fun checkBatteryInfo(msg: JSONObject) {
        val listener = batteryListener ?: return

        // 1. Action: sync_glass_battery_info
        if ("sync_glass_battery_info" == msg.optString("action")) {
            parseValueBattery(msg.optString("value"))
            return
        }

        // 2. Action: air_ota -> data -> action: get_air_glass_info
        if ("air_ota" == msg.optString("action")) {
            val data = msg.optJSONObject("data")
            if (data != null) {
                parseValueBattery(data.optString("value"))
            }
            return
        }

        // 3. Top-level device_info
        val devInfo = msg.optJSONObject("device_info")
        if (devInfo != null && devInfo.has("battery")) {
            val battery = devInfo.optInt("battery", -1)
            if (battery >= 0) {
                listener.onBatteryUpdated(battery, devInfo.optBoolean("is_charging", false))
            }
            return
        }

        // 4. Action containing battery or get_device_info
        if (msg.has("action") && msg.optString("action").contains("battery")) {
            val data = msg.optJSONObject("data")
            if (data != null && data.has("battery")) {
                val battery = data.optInt("battery", -1)
                if (battery >= 0) {
                    val isCharging = data.optBoolean("is_charging", data.optBoolean("isCharging", false))
                    listener.onBatteryUpdated(battery, isCharging)
                    return
                }
            }
            parseValueBattery(msg.optString("value"))
        }
    }

    private fun parseValueBattery(valueJson: String?) {
        if (valueJson.isNullOrEmpty()) return
        try {
            val valObj = JSONObject(valueJson)
            var battery = valObj.optInt("battery", -1)
            if (battery < 0 && valObj.has("capacity")) {
                battery = valObj.optInt("capacity", -1)
            }
            if (battery >= 0) {
                val isCharging = valObj.optBoolean("isCharging", valObj.optBoolean("is_charging", false))
                batteryListener?.onBatteryUpdated(battery, isCharging)
            }
        } catch (ignored: JSONException) {
        }
    }

    companion object {
        @JvmStatic
        fun findJsonObjects(s: String): List<String> {
            val out = ArrayList<String>()
            var depth = 0
            var start = -1
            for (i in s.indices) {
                val c = s[i]
                if (c == '{') {
                    if (depth == 0) start = i
                    depth++
                } else if (c == '}' && depth > 0) {
                    depth--
                    if (depth == 0 && start >= 0) {
                        out.add(s.substring(start, i + 1))
                    }
                }
            }
            return out
        }
    }
}
