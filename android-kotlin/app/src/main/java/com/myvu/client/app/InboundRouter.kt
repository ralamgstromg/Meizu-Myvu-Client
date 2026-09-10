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
            try {
                checkLaunchAppRequest(obj)
                checkTimeSyncRequest(obj)
                checkWeatherRequest(obj)
                checkAiTrigger(obj)
                checkBatteryInfo(obj)
                checkGestureTracking(obj)
            } catch (e: Throwable) {
                LogBus.error("InboundRouter: Exception handling candidate packet", e)
            }
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
        var action = msg.optString("action")
        if (action.isEmpty()) action = msg.optString("_action_name_")
        if (action.isEmpty()) action = msg.optString("name")

        if (action == "event_tracking" || action == "track_event") {
            val dataObj = msg.optJSONObject("data")
            if (dataObj != null) {
                val dataAction = dataObj.optString("action", dataObj.optString("_action_name_", ""))
                if (dataAction == "sync_glass_event" || dataAction.contains("event") || dataAction.contains("gesture")) {
                    processGestureValue(dataObj.opt("value") ?: dataObj, listener)
                    return
                }
            } else {
                val dataStr = msg.optString("data", "")
                if (dataStr.isNotEmpty()) {
                    try {
                        val parsedData = JSONObject(dataStr)
                        val dataAction = parsedData.optString("action", parsedData.optString("_action_name_", ""))
                        if (dataAction == "sync_glass_event" || dataAction.contains("event") || dataAction.contains("gesture")) {
                            processGestureValue(parsedData.opt("value") ?: parsedData, listener)
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

        if (action == "sync_glass_event" || action == "touch_gesture" || action == "glass_event" ||
            action.contains("gesture") || action.contains("pad") || action.contains("touch") ||
            action.contains("key") || action == "button" || action == "phonepad" || action == "trackpad") {
            if (msg.has("value")) {
                processGestureValue(msg.opt("value"), listener)
            } else if (msg.has("data")) {
                val dataObj = msg.optJSONObject("data")
                if (dataObj != null) {
                    processGestureValue(dataObj.opt("value") ?: dataObj, listener)
                } else {
                    processGestureValue(msg.opt("data"), listener)
                }
            }
        }
    }

    private data class ParsedGesture(
        val gesture: GlassGesture,
        val actionValue: Int,
        val actionName: String,
        val sender: Int,
        val eventTime: Long
    )

    private fun processGestureValue(valueRaw: Any?, listener: TouchGestureListener) {
        if (valueRaw == null) return

        when (valueRaw) {
            is JSONArray -> {
                val list = ArrayList<JSONObject>()
                for (i in 0 until valueRaw.length()) {
                    val item = valueRaw.optJSONObject(i)
                    if (item != null) list.add(item)
                }
                dispatchGestureBatch(list, listener)
            }
            is JSONObject -> {
                dispatchGestureBatch(listOf(valueRaw), listener)
            }
            is String -> {
                val str = valueRaw.trim()
                if (str.startsWith("[")) {
                    try {
                        val arr = JSONArray(str)
                        val list = ArrayList<JSONObject>()
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i)
                            if (item != null) list.add(item)
                        }
                        dispatchGestureBatch(list, listener)
                    } catch (ignored: JSONException) {
                    }
                } else if (str.startsWith("{")) {
                    try {
                        dispatchGestureBatch(listOf(JSONObject(str)), listener)
                    } catch (ignored: JSONException) {
                    }
                }
            }
            is Number -> {
                val code = valueRaw.toInt()
                val gesture = GlassGesture.fromCode(code)
                if (gesture == GlassGesture.UNKNOWN) return
                LogBus.log("Touch gesture received: $gesture (code=$code)")
                listener.onTouchGesture(gesture, code, "touch_gesture")
            }
        }
    }

    private fun parseGestureItem(item: JSONObject): ParsedGesture? {
        var actionName = ""
        val nameKeys = listOf(
            "key_name", "keyName",
            "gesture_name", "gestureName",
            "touch_name", "touchName",
            "_action_name_", "action_name",
            "_event_name_", "event_name",
            "_event_id_", "event_id",
            "actionName", "eventName",
            "name", "action", "type", "key"
        )
        for (k in nameKeys) {
            val v = item.optString(k, "")
            if (v.isNotEmpty() && v != "action" && v != "key_event" && v != "sync_glass_event") {
                actionName = v
                break
            }
        }
        if (actionName.isEmpty()) {
            actionName = item.optString("name", item.optString("action", ""))
        }

        val nonTouchTelemetry = setOf(
            "suspend_stats", "iot_screen_status_change", "iot_sys_usages",
            "battery_stats", "iot_notification_reminder", "sync_glass_battery_info",
            "iot_voice_wakeup", "iot_voice_quit", "voice_wakeup", "voice_quit",
            "iot_a2dp_status_change", "audio_stats", "air_starrynet_bt",
            "iot_voice_asr_time", "wear_data_collect", "starrynet_devices_disconnect",
            "starrynet_devices_reconnect", "screen_off_timeout_change", "standby_position"
        )
        if (actionName in nonTouchTelemetry) {
            return null
        }

        val attrObj = item.optJSONObject("_event_attr_value_")
        val sender = attrObj?.optInt("key_event_sender", item.optInt("key_event_sender", 0)) ?: item.optInt("key_event_sender", 0)
        val downOrUp = attrObj?.optInt("down_or_up", item.optInt("down_or_up", -1)) ?: item.optInt("down_or_up", -1)
        if (downOrUp == 0) {
            // Key release/up event -- ignore to prevent duplicate triggers
            return null
        }

        var eventTime = -1L
        val timeKeys = listOf("key_event_time", "event_time", "_event_time_", "timestamp", "time")
        if (attrObj != null) {
            for (k in timeKeys) {
                if (attrObj.has(k)) {
                    val raw = attrObj.opt(k)
                    if (raw is Number) {
                        eventTime = raw.toLong()
                        break
                    } else if (raw is String) {
                        val parsed = raw.toLongOrNull()
                        if (parsed != null) {
                            eventTime = parsed
                            break
                        }
                    }
                }
            }
        }
        if (eventTime == -1L) {
            for (k in timeKeys) {
                if (item.has(k)) {
                    val raw = item.opt(k)
                    if (raw is Number) {
                        eventTime = raw.toLong()
                        break
                    } else if (raw is String) {
                        val parsed = raw.toLongOrNull()
                        if (parsed != null) {
                            eventTime = parsed
                            break
                        }
                    }
                }
            }
        }

        var actionValue = -1
        val valKeys = listOf(
            "key_code", "keyCode", "keycode",
            "keyValue", "key_value",
            "_action_value_", "action_value",
            "_event_value_", "event_value",
            "actionType", "action_type",
            "gesture_code", "gestureCode",
            "gesture_type", "gestureType",
            "touch_type", "touchType",
            "direction", "value", "code", "event_code", "key"
        )
        // 1. Check inside nested _event_attr_value_ first
        if (attrObj != null) {
            for (k in valKeys) {
                if (attrObj.has(k)) {
                    val raw = attrObj.opt(k)
                    if (raw is Number) {
                        actionValue = raw.toInt()
                        break
                    } else if (raw is String) {
                        val parsed = raw.toIntOrNull()
                        if (parsed != null) {
                            actionValue = parsed
                            break
                        }
                    }
                }
            }
        }
        // 2. Check top-level item if not found
        if (actionValue == -1) {
            for (k in valKeys) {
                if (item.has(k)) {
                    val raw = item.opt(k)
                    if (raw is Number) {
                        actionValue = raw.toInt()
                        break
                    } else if (raw is String) {
                        val parsed = raw.toIntOrNull()
                        if (parsed != null) {
                            actionValue = parsed
                            break
                        }
                    }
                }
            }
        }

        if (actionName.isEmpty() && actionValue == -1) return null

        val gesture = GlassGesture.fromCode(actionValue, actionName)
        if (gesture == GlassGesture.UNKNOWN) {
            if (actionName.isNotEmpty() && actionName !in nonTouchTelemetry) {
                LogBus.log("Glass event unmapped: $item (actionName=$actionName, actionValue=$actionValue)")
            }
            return null
        }

        return ParsedGesture(gesture, actionValue, actionName, sender, eventTime)
    }

    private fun dispatchGestureBatch(rawItems: List<JSONObject>, listener: TouchGestureListener) {
        val parsedList = rawItems.mapNotNull { parseGestureItem(it) }
        if (parsedList.isEmpty()) return

        // 1. Deduplicate identical bounce events in the same batch
        val deduplicated = ArrayList<ParsedGesture>()
        for (item in parsedList) {
            val isDuplicateBounce = deduplicated.any { existing ->
                existing.gesture == item.gesture &&
                        (existing.sender == 0 || item.sender == 0 || existing.sender == item.sender) &&
                        existing.eventTime != -1L && item.eventTime != -1L &&
                        Math.abs(existing.eventTime - item.eventTime) <= 50L
            }
            if (!isDuplicateBounce) {
                deduplicated.add(item)
            } else {
                LogBus.log("Filtered duplicate bounce ${item.gesture} (time=${item.eventTime}, sender=${item.sender})")
            }
        }

        // 2. Consolidate sender 2 (left temple) touch down (200) and touch up (203)
        // If code 200 is present, code 203 in the same batch is release confirmation noise
        val consolidated = if (deduplicated.any { it.actionValue == 200 }) {
            deduplicated.filterNot { it.actionValue == 203 }
        } else {
            deduplicated
        }

        val isTapOrPress = { g: GlassGesture ->
            g == GlassGesture.TAP || g == GlassGesture.DOUBLE_TAP ||
                    g == GlassGesture.TRIPLE_TAP || g == GlassGesture.LONG_PRESS
        }
        val isSwipe = { g: GlassGesture ->
            g == GlassGesture.SWIPE_FORWARD || g == GlassGesture.SWIPE_BACKWARD
        }

        // 3. Filter out parasitic micro-swipes occurring simultaneously with a tap/press
        val noSwipes = if (consolidated.size > 1) {
            val tapEvents = consolidated.filter { isTapOrPress(it.gesture) }
            if (tapEvents.isNotEmpty()) {
                consolidated.filterNot { swipeCandidate ->
                    if (!isSwipe(swipeCandidate.gesture)) return@filterNot false
                    tapEvents.any { tap ->
                        val sameSender = (swipeCandidate.sender == 0 || tap.sender == 0 || swipeCandidate.sender == tap.sender)
                        val sameTime = swipeCandidate.eventTime != -1L && tap.eventTime != -1L &&
                                Math.abs(swipeCandidate.eventTime - tap.eventTime) <= 50L
                        if (sameSender && sameTime) {
                            LogBus.log("Filtered parasitic ${swipeCandidate.gesture} occurring simultaneously with ${tap.gesture} (time=${swipeCandidate.eventTime}, sender=${swipeCandidate.sender})")
                            true
                        } else {
                            false
                        }
                    }
                }
            } else {
                consolidated
            }
        } else {
            consolidated
        }

        // 4. Synthesize DOUBLE_TAP if the batch contains two consecutive TAPs within 60..500ms
        val synthesized = ArrayList<ParsedGesture>()
        var i = 0
        while (i < noSwipes.size) {
            val current = noSwipes[i]
            if (current.gesture == GlassGesture.TAP && i + 1 < noSwipes.size) {
                val next = noSwipes[i + 1]
                val sameSender = (current.sender == 0 || next.sender == 0 || current.sender == next.sender)
                val dt = if (current.eventTime != -1L && next.eventTime != -1L) {
                    Math.abs(next.eventTime - current.eventTime)
                } else {
                    -1L
                }
                if (next.gesture == GlassGesture.TAP && sameSender && dt in 60L..500L) {
                    LogBus.log("Synthesized DOUBLE_TAP from batch containing 2 TAPs (${dt}ms apart, sender=${current.sender})")
                    synthesized.add(
                        ParsedGesture(
                            GlassGesture.DOUBLE_TAP,
                            211,
                            "double_tap",
                            current.sender,
                            next.eventTime
                        )
                    )
                    i += 2
                    continue
                }
            }
            synthesized.add(current)
            i++
        }

        val gesturePriority = { g: GlassGesture ->
            when (g) {
                GlassGesture.DOUBLE_TAP -> 1
                GlassGesture.TRIPLE_TAP -> 2
                GlassGesture.TAP -> 3
                GlassGesture.LONG_PRESS -> 4
                GlassGesture.SWIPE_FORWARD -> 5
                GlassGesture.SWIPE_BACKWARD -> 6
                GlassGesture.UNKNOWN -> 99
            }
        }

        // Sort events with matching timestamps by priority, otherwise keep arrival order
        val sorted = if (synthesized.size > 1) {
            synthesized.sortedWith { a, b ->
                if (a.eventTime != -1L && b.eventTime != -1L && Math.abs(a.eventTime - b.eventTime) <= 50L) {
                    gesturePriority(a.gesture).compareTo(gesturePriority(b.gesture))
                } else {
                    0
                }
            }
        } else {
            synthesized
        }

        for (item in sorted) {
            LogBus.log("Touch gesture received: ${item.gesture} (code=${item.actionValue}, name=${item.actionName}, sender=${item.sender})")
            listener.onTouchGesture(item.gesture, item.actionValue, item.actionName)
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
