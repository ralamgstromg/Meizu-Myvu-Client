package com.myvu.client.app.feature

/**
 * Available custom actions that can be mapped to physical temple gestures on the Meizu MYVU glasses.
 */
enum class GestureAction(
    val id: String,
    val displayName: String
) {
    NONE("none", "Ninguna"),
    LAUNCH_GEMINI("launch_gemini", "Lanzar Gemini (Manos Libres)"),
    LAUNCH_PHONE_ASSISTANT("phone_assistant", "Asistente del Teléfono (Google)"),
    LAUNCH_APP("launch_app", "Abrir Aplicación..."),
    LAUNCH_LOCAL_AI("ai_assistant", "Asistente IA de Gafas"),
    MEDIA_PLAY_PAUSE("media_play_pause", "Reproducir / Pausar"),
    MEDIA_NEXT("media_next", "Siguiente Canción"),
    MEDIA_PREV("media_prev", "Canción Anterior"),
    WEATHER_SYNC("weather_sync", "Sincronizar Clima"),
    TOGGLE_MIRROR("toggle_mirror", "Alternar Notificaciones"),
    OPEN_TELEPROMPTER("open_teleprompter", "Abrir Teleprompter"),
    ZEN_MODE("zen_mode", "Modo Zen / No Molestar");

    companion object {
        const val APP_PREFIX = "app:"

        @JvmStatic
        fun isAppAction(id: String?): Boolean = id?.startsWith(APP_PREFIX) == true

        @JvmStatic
        fun getAppPackage(id: String?): String? = if (isAppAction(id)) id!!.removePrefix(APP_PREFIX) else null

        @JvmStatic
        fun makeAppActionId(packageName: String): String = "$APP_PREFIX$packageName"

        @JvmStatic
        fun fromId(id: String?): GestureAction {
            if (id.isNullOrBlank()) return NONE
            if (isAppAction(id)) return LAUNCH_APP
            val clean = id.trim().lowercase()
            return when (clean) {
                "none" -> NONE
                "launch_gemini", "gemini", "gemini_assistant", "gemini_live" -> LAUNCH_GEMINI
                "phone_assistant", "launch_phone_assistant", "google_assistant", "assistant" -> LAUNCH_PHONE_ASSISTANT
                "launch_app", "app", "open_app" -> LAUNCH_APP
                "ai_assistant", "local_ai", "launch_local_ai", "ai" -> LAUNCH_LOCAL_AI
                "media_play_pause", "play_pause", "playpause" -> MEDIA_PLAY_PAUSE
                "media_next", "next" -> MEDIA_NEXT
                "media_prev", "media_previous", "prev", "previous" -> MEDIA_PREV
                "weather_sync", "weather" -> WEATHER_SYNC
                "toggle_mirror", "mirror" -> TOGGLE_MIRROR
                "open_teleprompter", "teleprompter" -> OPEN_TELEPROMPTER
                "zen_mode", "zen" -> ZEN_MODE
                else -> entries.firstOrNull {
                    it.id.equals(clean, ignoreCase = true) || it.name.equals(clean, ignoreCase = true)
                } ?: NONE
            }
        }
    }
}
