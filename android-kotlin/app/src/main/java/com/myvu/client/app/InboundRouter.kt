package com.myvu.client.app

import com.myvu.client.app.feature.ClockSync
import com.myvu.client.app.feature.GlassGesture
import com.myvu.client.app.feature.Weather
import com.myvu.client.core.LogBus
import org.json.JSONArray
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

    /** Fired when the glasses send a physical temple touch gesture event. */
    fun interface TouchGestureListener {
        fun onTouchGesture(gestureType: GlassGesture, rawCode: Int, gestureName: String)
    }

    private var aiListener: AiTriggerListener? = null
    private var weatherListener: WeatherRequestListener? = null
    private var batteryListener: BatteryUpdateListener? = null
    private var touchGestureListener: TouchGestureListener? = null

    fun setAiTriggerListener(listener: AiTriggerListener?) {
        this.aiListener = listener
    }

    fun setWeatherRequestListener(listener: WeatherRequestListener?) {
        this.weatherListener = listener
    }

    fun setBatteryUpdateListener(listener: BatteryUpdateListener?) {
        this.batteryListener = listener
    }

    fun setTouchGestureListener(listener: TouchGestureListener?) {
        this.touchGestureListener = listener
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
            checkGestureTracking(obj)
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

        val action = msg.optString("action")

        // Ignore accessory/unicron battery if not connected or capacity is 0
        if (action == "unicron_battery") {
            val valObj = msg.optJSONObject("value")
            if (valObj != null && valObj.optBoolean("isConnect", false)) {
                val cap = valObj.optInt("capacity", -1)
                if (cap in 1..100) {
                    listener.onBatteryUpdated(cap, false)
                }
            }
            return
        }

        // 1. Action: sync_glass_battery_info
        if ("sync_glass_battery_info" == action) {
            parseValueBattery(msg.optString("value"))
            return
        }

        // 2. Action: air_ota -> data -> action: get_air_glass_info
        if ("air_ota" == action) {
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
            if (battery in 1..100) {
                listener.onBatteryUpdated(battery, devInfo.optBoolean("is_charging", false))
            }
            return
        }

        // 4. Action containing battery or get_device_info
        if (action.contains("battery")) {
            val data = msg.optJSONObject("data")
            if (data != null && data.has("battery")) {
                val battery = data.optInt("battery", -1)
                if (battery in 1..100) {
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
            if (battery <= 0 && valObj.has("capacity")) {
                battery = valObj.optInt("capacity", -1)
            }
            if (battery in 1..100) {
                val isCharging = valObj.optBoolean("isCharging", valObj.optBoolean("is_charging", false))
                batteryListener?.onBatteryUpdated(battery, isCharging)
            }
        } catch (ignored: JSONException) {
        }
    }

    private fun checkGestureTracking(msg: JSONObject) {
        val listener = touchGestureListener ?: return
        val action = msg.optString("action", "")

        if (action == "event_tracking") {
            val dataObj = msg.optJSONObject("data")
            if (dataObj != null) {
                val dataAction = dataObj.optString("action", "")
                if (dataAction == "sync_glass_event" || dataAction.contains("event") || dataAction.contains("gesture")) {
                    processGestureValue(dataObj.opt("value"), listener)
                    return
                }
            } else {
                val dataStr = msg.optString("data", "")
                if (dataStr.isNotEmpty()) {
                    try {
                        val parsedData = JSONObject(dataStr)
                        val dataAction = parsedData.optString("action", "")
                        if (dataAction == "sync_glass_event" || dataAction.contains("event") || dataAction.contains("gesture")) {
                            processGestureValue(parsedData.opt("value"), listener)
                            return
                        }
                    } catch (ignored: JSONException) {
                    }
                }
            }
            if (msg.has("value")) {
                processGestureValue(msg.opt("value"), listener)
            }
            return
        }

        if (action == "sync_glass_event" || action == "touch_gesture") {
            if (msg.has("value")) {
                processGestureValue(msg.opt("value"), listener)
            } else if (msg.has("data")) {
                val dataObj = msg.optJSONObject("data")
                if (dataObj != null) {
                    processGestureValue(dataObj.opt("value"), listener)
                } else {
                    processGestureValue(msg.opt("data"), listener)
                }
            }
        }
    }

    private fun processGestureValue(valueRaw: Any?, listener: TouchGestureListener) {
        if (valueRaw == null) return

        when (valueRaw) {
            is JSONArray -> {
                for (i in 0 until valueRaw.length()) {
                    val item = valueRaw.optJSONObject(i)
                    if (item != null) {
                        dispatchGestureItem(item, listener)
                    }
                }
            }
            is JSONObject -> {
                dispatchGestureItem(valueRaw, listener)
            }
            is String -> {
                val str = valueRaw.trim()
                if (str.startsWith("[")) {
                    try {
                        val arr = JSONArray(str)
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i)
                            if (item != null) {
                                dispatchGestureItem(item, listener)
                            }
                        }
                    } catch (ignored: JSONException) {
                    }
                } else if (str.startsWith("{")) {
                    try {
                        dispatchGestureItem(JSONObject(str), listener)
                    } catch (ignored: JSONException) {
                    }
                }
            }
            is Number -> {
                val code = valueRaw.toInt()
                val gesture = GlassGesture.fromCode(code)
                LogBus.log("Touch gesture received: $gesture (code=$code)")
                listener.onTouchGesture(gesture, code, "touch_gesture")
            }
        }
    }

    private fun dispatchGestureItem(item: JSONObject, listener: TouchGestureListener) {
        val actionName = item.optString("action_name", item.optString("event_name", item.optString("name", "")))
        var actionValue = item.optInt("action_value", -1)
        if (actionValue == -1 && item.has("action_value")) {
            actionValue = item.optString("action_value").toIntOrNull() ?: -1
        }
        if (actionValue == -1) {
            actionValue = item.optInt("value", item.optInt("code", -1))
        }

        if (actionName.isEmpty() && actionValue == -1) return

        val gesture = GlassGesture.fromCode(actionValue, actionName)
        LogBus.log("Touch gesture received: $gesture (code=$actionValue, name=$actionName)")
        listener.onTouchGesture(gesture, actionValue, actionName)
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
