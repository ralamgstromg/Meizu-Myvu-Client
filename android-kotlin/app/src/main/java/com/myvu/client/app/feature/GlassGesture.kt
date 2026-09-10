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
                    lower.contains("single") || lower.contains("tap") || lower.contains("click") ||
                            lower.contains("select") || lower.contains("enter") || lower.contains("center") ||
                            lower.contains("hook") -> return TAP
                    lower.contains("long") || lower.contains("press") || lower.contains("deep_touch") ||
                            lower.contains("hold") || lower.contains("assist") -> return LONG_PRESS
                    lower.contains("forward") || lower.contains("front") || lower.contains("ahead") ||
                            lower.contains("right") || lower == "up" || lower.contains("next") ||
                            lower.contains("fast_forward") || lower.contains("page_up") -> return SWIPE_FORWARD
                    lower.contains("backward") || lower.contains("back") || lower.contains("left") ||
                            lower == "down" || lower.contains("prev") || lower.contains("previous") ||
                            lower.contains("rewind") || lower.contains("page_down") -> return SWIPE_BACKWARD
                }
            }
            return when (code) {
                1, 23, 66, 79, 85, 96, 200, 203, 210 -> TAP
                2, 202, 211 -> DOUBLE_TAP
                3 -> TRIPLE_TAP
                4, 212, 219, 231 -> LONG_PRESS
                5, 19, 22, 87, 90, 92, 201, 206 -> SWIPE_FORWARD
                6, 20, 21, 88, 89, 93, 207, 237 -> SWIPE_BACKWARD
                else -> entries.firstOrNull { it.code == code } ?: UNKNOWN
            }
        }

        @JvmStatic
        fun fromId(id: String?): GlassGesture {
            if (id.isNullOrEmpty()) return UNKNOWN
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: UNKNOWN
        }
    }
}
