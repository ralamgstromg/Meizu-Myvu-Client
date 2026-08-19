package com.myvu.client.app.feature

/**
 * Represents physical touch and temple gestures recognized by the Meizu MYVU glasses.
 */
enum class GlassGesture(
    val code: Int,
    val id: String,
    val displayName: String
) {
    TAP(1, "tap", "Toque Simple"),
    DOUBLE_TAP(2, "double_tap", "Doble Toque"),
    TRIPLE_TAP(3, "triple_tap", "Triple Toque"),
    LONG_PRESS(4, "long_press", "Pulsación Larga"),
    SWIPE_FORWARD(5, "swipe_forward", "Deslizar Adelante"),
    SWIPE_BACKWARD(6, "swipe_backward", "Deslizar Atrás"),
    UNKNOWN(-1, "unknown", "Desconocido");

    companion object {
        @JvmStatic
        fun fromCode(code: Int, name: String? = null): GlassGesture {
            if (!name.isNullOrEmpty()) {
                val lower = name.lowercase()
                when {
                    lower.contains("triple") -> return TRIPLE_TAP
                    lower.contains("double") -> return DOUBLE_TAP
                    lower.contains("single") || lower == "tap" || lower.contains("click") -> return TAP
                    lower.contains("long") || lower.contains("press") || lower.contains("deep_touch") -> return LONG_PRESS
                    lower.contains("forward") || lower.contains("front") -> return SWIPE_FORWARD
                    lower.contains("backward") || lower.contains("back") -> return SWIPE_BACKWARD
                }
            }
            return entries.firstOrNull { it.code == code } ?: UNKNOWN
        }

        @JvmStatic
        fun fromId(id: String?): GlassGesture {
            if (id.isNullOrEmpty()) return UNKNOWN
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: UNKNOWN
        }
    }
}
