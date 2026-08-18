package com.myvu.client.plugin.tasker

import android.os.Bundle

data class TaskerAction(
    val type: String,
    val title: String? = null,
    val content: String? = null,
    val valueInt: Int? = null,
    val valueBoolean: Boolean? = null,
    val valueString: String? = null,
    val rawJson: String? = null
)

data class TaskerEvent(
    val eventType: String,
    val gestureCode: Int? = null,
    val gestureName: String? = null,
    val buttonCode: Int? = null,
    val batteryLevel: Int? = null,
    val isCharging: Boolean? = null,
    val connectionState: String? = null
)

object TaskerBundleManager {

    private val VARIABLE_REGEX = Regex("%[a-zA-Z0-9_]+")

    // ------------------------------------------------------------- Action Builders

    fun buildActionBundle(action: TaskerAction): Bundle {
        val bundle = Bundle()
        bundle.putString(TaskerConstants.KEY_ACTION_TYPE, action.type)
        action.title?.let { bundle.putString(TaskerConstants.KEY_TITLE, it) }
        action.content?.let { bundle.putString(TaskerConstants.KEY_CONTENT, it) }
        action.valueInt?.let { bundle.putInt(TaskerConstants.KEY_VALUE_INT, it) }
        action.valueBoolean?.let { bundle.putBoolean(TaskerConstants.KEY_VALUE_BOOLEAN, it) }
        action.valueString?.let { bundle.putString(TaskerConstants.KEY_VALUE_STRING, it) }
        action.rawJson?.let { bundle.putString(TaskerConstants.KEY_RAW_JSON, it) }
        return bundle
    }

    fun buildHudBundle(title: String? = null, content: String): Bundle {
        return buildActionBundle(
            TaskerAction(
                type = TaskerConstants.TYPE_SHOW_HUD,
                title = title,
                content = content
            )
        )
    }

    fun buildTeleprompterBundle(text: String, title: String? = null): Bundle {
        return buildActionBundle(
            TaskerAction(
                type = TaskerConstants.TYPE_SHOW_TELEPROMPTER,
                title = title,
                content = text
            )
        )
    }

    fun buildBrightnessBundle(brightness: Int): Bundle {
        return buildActionBundle(
            TaskerAction(
                type = TaskerConstants.TYPE_SET_BRIGHTNESS,
                valueInt = brightness
            )
        )
    }

    fun buildVolumeBundle(volume: Int): Bundle {
        return buildActionBundle(
            TaskerAction(
                type = TaskerConstants.TYPE_SET_VOLUME,
                valueInt = volume
            )
        )
    }

    fun buildWifiBundle(enabled: Boolean): Bundle {
        return buildActionBundle(
            TaskerAction(
                type = TaskerConstants.TYPE_TOGGLE_WIFI,
                valueBoolean = enabled
            )
        )
    }

    fun buildZenModeBundle(enabled: Boolean): Bundle {
        return buildActionBundle(
            TaskerAction(
                type = TaskerConstants.TYPE_SET_ZEN_MODE,
                valueBoolean = enabled
            )
        )
    }

    fun buildAirModeBundle(enabled: Boolean): Bundle {
        return buildActionBundle(
            TaskerAction(
                type = TaskerConstants.TYPE_SET_AIR_MODE,
                valueBoolean = enabled
            )
        )
    }

    fun buildStandbyPosBundle(position: Int): Bundle {
        return buildActionBundle(
            TaskerAction(
                type = TaskerConstants.TYPE_SET_STANDBY_POS,
                valueInt = position
            )
        )
    }

    fun buildRawJsonBundle(rawJson: String): Bundle {
        return buildActionBundle(
            TaskerAction(
                type = TaskerConstants.TYPE_SEND_RAW,
                rawJson = rawJson
            )
        )
    }

    // ------------------------------------------------------------- Event Builders

    fun buildEventBundle(event: TaskerEvent): Bundle {
        val bundle = Bundle()
        bundle.putString(TaskerConstants.KEY_EVENT_TYPE, event.eventType)
        event.gestureCode?.let { bundle.putInt(TaskerConstants.KEY_GESTURE_CODE, it) }
        event.gestureName?.let { bundle.putString(TaskerConstants.KEY_GESTURE_NAME, it) }
        event.buttonCode?.let { bundle.putInt(TaskerConstants.KEY_BUTTON_CODE, it) }
        event.batteryLevel?.let { bundle.putInt(TaskerConstants.KEY_BATTERY_LEVEL, it) }
        event.isCharging?.let { bundle.putBoolean(TaskerConstants.KEY_IS_CHARGING, it) }
        event.connectionState?.let { bundle.putString(TaskerConstants.KEY_STATE, it) }
        return bundle
    }

    fun buildGestureEventBundle(gestureCode: Int, gestureName: String): Bundle {
        return buildEventBundle(
            TaskerEvent(
                eventType = TaskerConstants.EVENT_TOUCH_GESTURE,
                gestureCode = gestureCode,
                gestureName = gestureName
            )
        )
    }

    fun buildAiButtonEventBundle(buttonCode: Int): Bundle {
        return buildEventBundle(
            TaskerEvent(
                eventType = TaskerConstants.EVENT_AI_BUTTON,
                buttonCode = buttonCode
            )
        )
    }

    fun buildBatteryEventBundle(batteryLevel: Int, isCharging: Boolean): Bundle {
        return buildEventBundle(
            TaskerEvent(
                eventType = TaskerConstants.EVENT_BATTERY_CHANGED,
                batteryLevel = batteryLevel,
                isCharging = isCharging
            )
        )
    }

    fun buildConnectionEventBundle(connected: Boolean): Bundle {
        val eventType = if (connected) TaskerConstants.EVENT_CONNECTED else TaskerConstants.EVENT_DISCONNECTED
        val stateName = if (connected) "CONNECTED" else "DISCONNECTED"
        return buildEventBundle(
            TaskerEvent(
                eventType = eventType,
                connectionState = stateName
            )
        )
    }

    // ------------------------------------------------------------- Action Parsing

    fun parseAction(bundle: Bundle?): TaskerAction {
        if (bundle == null) return TaskerAction(type = "")
        val type = bundle.getString(TaskerConstants.KEY_ACTION_TYPE) ?: ""
        val title = if (bundle.containsKey(TaskerConstants.KEY_TITLE)) bundle.getString(TaskerConstants.KEY_TITLE) else null
        val content = if (bundle.containsKey(TaskerConstants.KEY_CONTENT)) bundle.getString(TaskerConstants.KEY_CONTENT) else null
        val valueInt = if (bundle.containsKey(TaskerConstants.KEY_VALUE_INT)) bundle.getInt(TaskerConstants.KEY_VALUE_INT) else null
        val valueBoolean = if (bundle.containsKey(TaskerConstants.KEY_VALUE_BOOLEAN)) bundle.getBoolean(TaskerConstants.KEY_VALUE_BOOLEAN) else null
        val valueString = if (bundle.containsKey(TaskerConstants.KEY_VALUE_STRING)) bundle.getString(TaskerConstants.KEY_VALUE_STRING) else null
        val rawJson = if (bundle.containsKey(TaskerConstants.KEY_RAW_JSON)) bundle.getString(TaskerConstants.KEY_RAW_JSON) else null

        return TaskerAction(
            type = type,
            title = title,
            content = content,
            valueInt = valueInt,
            valueBoolean = valueBoolean,
            valueString = valueString,
            rawJson = rawJson
        )
    }

    fun extractAction(bundle: Bundle?): TaskerAction? {
        if (bundle == null || !bundle.containsKey(TaskerConstants.KEY_ACTION_TYPE)) return null
        return parseAction(bundle)
    }

    // ------------------------------------------------------------- Event Parsing

    fun parseEvent(bundle: Bundle?): TaskerEvent {
        if (bundle == null) return TaskerEvent(eventType = "")
        val eventType = bundle.getString(TaskerConstants.KEY_EVENT_TYPE) ?: ""
        val gestureCode = if (bundle.containsKey(TaskerConstants.KEY_GESTURE_CODE)) bundle.getInt(TaskerConstants.KEY_GESTURE_CODE) else null
        val gestureName = if (bundle.containsKey(TaskerConstants.KEY_GESTURE_NAME)) bundle.getString(TaskerConstants.KEY_GESTURE_NAME) else null
        val buttonCode = if (bundle.containsKey(TaskerConstants.KEY_BUTTON_CODE)) bundle.getInt(TaskerConstants.KEY_BUTTON_CODE) else null
        val batteryLevel = if (bundle.containsKey(TaskerConstants.KEY_BATTERY_LEVEL)) bundle.getInt(TaskerConstants.KEY_BATTERY_LEVEL) else null
        val isCharging = if (bundle.containsKey(TaskerConstants.KEY_IS_CHARGING)) bundle.getBoolean(TaskerConstants.KEY_IS_CHARGING) else null
        val connectionState = if (bundle.containsKey(TaskerConstants.KEY_STATE)) bundle.getString(TaskerConstants.KEY_STATE) else null

        return TaskerEvent(
            eventType = eventType,
            gestureCode = gestureCode,
            gestureName = gestureName,
            buttonCode = buttonCode,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            connectionState = connectionState
        )
    }

    fun extractEvent(bundle: Bundle?): TaskerEvent? {
        if (bundle == null || !bundle.containsKey(TaskerConstants.KEY_EVENT_TYPE)) return null
        return parseEvent(bundle)
    }

    // ------------------------------------------------------------- Blurb Generation

    fun generateBlurb(action: TaskerAction): String {
        return when (action.type) {
            TaskerConstants.TYPE_SHOW_HUD -> {
                val title = action.title?.trim()
                val content = action.content?.trim()
                when {
                    !title.isNullOrEmpty() && !content.isNullOrEmpty() -> "HUD: $title - $content"
                    !title.isNullOrEmpty() -> "HUD: $title"
                    !content.isNullOrEmpty() -> "HUD: $content"
                    else -> "HUD: (sin mensaje)"
                }
            }
            TaskerConstants.TYPE_SHOW_TELEPROMPTER -> {
                val title = action.title?.trim()
                val content = action.content?.trim()
                when {
                    !title.isNullOrEmpty() && !content.isNullOrEmpty() -> "Teleprompter: $title - $content"
                    !content.isNullOrEmpty() -> "Teleprompter: $content"
                    else -> "Teleprompter"
                }
            }
            TaskerConstants.TYPE_SET_BRIGHTNESS -> "Brillo: ${action.valueInt ?: 0}"
            TaskerConstants.TYPE_SET_VOLUME -> "Volumen: ${action.valueInt ?: 0}"
            TaskerConstants.TYPE_TOGGLE_WIFI -> "WiFi: ${if (action.valueBoolean == true) "Activado" else "Desactivado"}"
            TaskerConstants.TYPE_SET_ZEN_MODE -> "Modo Zen: ${if (action.valueBoolean == true) "Activado" else "Desactivado"}"
            TaskerConstants.TYPE_SET_AIR_MODE -> "Modo Air: ${if (action.valueBoolean == true) "Activado" else "Desactivado"}"
            TaskerConstants.TYPE_SET_STANDBY_POS -> "Posición Standby: ${action.valueInt ?: 0}"
            TaskerConstants.TYPE_SEND_RAW -> "Raw JSON: ${action.rawJson ?: ""}"
            else -> "Acción MYVU"
        }
    }

    fun generateBlurb(bundle: Bundle?): String = if (bundle != null) generateBlurb(parseAction(bundle)) else ""

    fun generateEventBlurb(event: TaskerEvent): String {
        return when (event.eventType) {
            TaskerConstants.EVENT_TOUCH_GESTURE -> "Gesto Táctil: ${event.gestureName ?: (event.gestureCode?.toString() ?: "Cualquier Gesto")}"
            TaskerConstants.EVENT_AI_BUTTON -> "Botón AI (Código: ${event.buttonCode ?: 0})"
            TaskerConstants.EVENT_BATTERY_CHANGED -> "Batería: ${event.batteryLevel ?: 0}%${if (event.isCharging == true) " (Cargando)" else ""}"
            TaskerConstants.EVENT_CONNECTED -> "Gafas Conectadas"
            TaskerConstants.EVENT_DISCONNECTED -> "Gafas Desconectadas"
            else -> "Evento MYVU"
        }
    }

    fun generateEventBlurb(bundle: Bundle?): String = if (bundle != null) generateEventBlurb(parseEvent(bundle)) else ""

    // ------------------------------------------------------------- Tasker Variables

    fun getVariableReplaceKeys(): String =
        "${TaskerConstants.KEY_TITLE} ${TaskerConstants.KEY_CONTENT} ${TaskerConstants.KEY_VALUE_STRING} ${TaskerConstants.KEY_RAW_JSON}"

    fun getVariableReplaceKeys(bundle: Bundle?): String? {
        if (bundle == null) return null
        val action = parseAction(bundle)
        val hasVars = containsVariables(action.title) ||
                containsVariables(action.content) ||
                containsVariables(action.valueString) ||
                containsVariables(action.rawJson)
        return if (hasVars) getVariableReplaceKeys() else null
    }

    fun extractVariables(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return VARIABLE_REGEX.findAll(text).map { it.value }.distinct().toList()
    }

    fun containsVariables(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return VARIABLE_REGEX.containsMatchIn(text)
    }

    fun applyVariables(template: String?, variables: Map<String, String>): String {
        if (template.isNullOrEmpty()) return ""
        var result: String = template
        for ((k, v) in variables) {
            result = result.replace(k, v)
        }
        return result
    }

    fun resolveActionVariables(action: TaskerAction, variables: Map<String, String>): TaskerAction {
        if (variables.isEmpty()) return action
        return action.copy(
            title = action.title?.let { applyVariables(it, variables) },
            content = action.content?.let { applyVariables(it, variables) },
            valueString = action.valueString?.let { applyVariables(it, variables) },
            rawJson = action.rawJson?.let { applyVariables(it, variables) }
        )
    }
}
