package com.myvu.client.plugin.tasker

object TaskerConstants {
    // Locale / Tasker standard actions and extras
    const val ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
    const val ACTION_EDIT_SETTING = "com.twofortyfouram.locale.intent.action.EDIT_SETTING"
    const val ACTION_EDIT_CONDITION = "com.twofortyfouram.locale.intent.action.EDIT_CONDITION"
    const val ACTION_QUERY_CONDITION = "com.twofortyfouram.locale.intent.action.QUERY_CONDITION"
    const val EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    const val EXTRA_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"

    // Tasker variable replacement
    const val EXTRA_TASKER_PASS_THROUGH = "net.dinglisch.android.tasker.extras.PASS_THROUGH"
    const val EXTRA_VARIABLE_REPLACE_KEYS = "net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS"

    // Bundle Keys
    const val KEY_ACTION_TYPE = "action_type"
    const val KEY_TITLE = "title"
    const val KEY_CONTENT = "content"
    const val KEY_VALUE_INT = "value_int"
    const val KEY_VALUE_BOOLEAN = "value_boolean"
    const val KEY_VALUE_STRING = "value_string"
    const val KEY_RAW_JSON = "raw_json"

    const val KEY_EVENT_TYPE = "event_type"
    const val KEY_GESTURE_CODE = "gesture_code"
    const val KEY_GESTURE_NAME = "gesture_name"
    const val KEY_BUTTON_CODE = "button_code"
    const val KEY_BATTERY_LEVEL = "battery_level"
    const val KEY_IS_CHARGING = "is_charging"
    const val KEY_STATE = "connection_state"

    // Tipos de Acción hacia las gafas
    const val TYPE_SHOW_HUD = "show_hud"
    const val TYPE_SHOW_TELEPROMPTER = "show_teleprompter"
    const val TYPE_SET_BRIGHTNESS = "set_brightness"
    const val TYPE_SET_VOLUME = "set_volume"
    const val TYPE_TOGGLE_WIFI = "toggle_wifi"
    const val TYPE_SET_ZEN_MODE = "set_zen_mode"
    const val TYPE_SET_AIR_MODE = "set_air_mode"
    const val TYPE_SET_STANDBY_POS = "set_standby_pos"
    const val TYPE_SEND_RAW = "send_raw"

    // Tipos de Eventos desde las gafas
    const val EVENT_TOUCH_GESTURE = "touch_gesture"
    const val EVENT_AI_BUTTON = "ai_button"
    const val EVENT_CONNECTED = "glasses_connected"
    const val EVENT_DISCONNECTED = "glasses_disconnected"
    const val EVENT_BATTERY_CHANGED = "battery_changed"

    // Direct Intent Broadcasts
    const val BROADCAST_EVENT = "com.myvu.client.TASKER_EVENT"
    const val BROADCAST_ACTION = "com.myvu.client.TASKER_ACTION"

    // Tasker Open Event Broadcast
    const val TASKER_ACTION_OPEN_EVENT = "net.dinglisch.android.tasker.ACTION_OPEN_EVENT"

    // Tasker Variables
    const val VAR_EVENT = "%myvu_event"
    const val VAR_GESTURE = "%myvu_gesture"
    const val VAR_BATTERY = "%myvu_battery"
    const val VAR_STATE = "%myvu_state"
}
