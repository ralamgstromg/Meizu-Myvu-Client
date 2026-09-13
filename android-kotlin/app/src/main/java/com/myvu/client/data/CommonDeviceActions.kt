package com.myvu.client.data

data class DeviceActionItem(
    val id: String,
    val displayName: String,
    val description: String = ""
)

/**
 * Universal catalog of actions that can be triggered by any connected Bluetooth device
 * (Smart Glasses, Headphones, Earbuds, Smartwatches, Generic Wearables).
 */
object CommonDeviceActions {

    val ALL_ACTIONS: List<DeviceActionItem> = listOf(
        DeviceActionItem("VOICE_AGENT_AURA", "Agente de Voz Aura (STT + API)", "Activa el asistente Aura usando el micrófono y la IA configurada"),
        DeviceActionItem("HUD_DASHBOARD", "Ver Dashboard / HUD de Gafas", "Muestra el panel de estado e información de las gafas"),
        DeviceActionItem("MEDIA_PLAY_PAUSE", "Play / Pausa de Música", "Controla la reproducción de medios activa"),
        DeviceActionItem("LAUNCH_GEMINI", "Lanzar Asistente Gemini", "Abre el asistente inteligente con micrófono activo"),
        DeviceActionItem("LAUNCH_GEMINI_LIVE", "Gemini Live (Voz Continua)", "Inicia conversación bidireccional continua"),
        DeviceActionItem("LAUNCH_PHONE_ASSISTANT", "Asistente del Teléfono (Google)", "Abre el asistente predeterminado del sistema"),
        DeviceActionItem("CREATE_AI_NOTE", "Crear Nota de Voz con IA", "Inicia grabación con transcripción y análisis"),
        DeviceActionItem("DAILY_BRIEFING", "Resumen de mi Día (Briefing)", "Genera y lee el estado de agenda, clima y tareas"),
        DeviceActionItem("READ_UNREAD_NOTIFICATIONS", "Leer Notificaciones en Voz Alta", "Sintetiza las notificaciones pendientes"),
        DeviceActionItem("MEDIA_NEXT", "Siguiente Pista de Audio", "Avanza a la siguiente canción"),
        DeviceActionItem("MEDIA_PREV", "Pista de Audio Anterior", "Regresa a la canción previa"),
        DeviceActionItem("WEATHER_SYNC", "Sincronizar y Reportar Clima", "Actualiza la telemetría climática local"),
        DeviceActionItem("OPEN_TELEPROMPTER", "Abrir Teleprompter", "Inicia la lectura en HUD"),
        DeviceActionItem("ZEN_MODE", "Modo Zen / No Molestar", "Silencia interrupciones visuales"),
        DeviceActionItem("NONE", "Ninguna Acción (Desactivado)", "Ignora este gesto")
    )

    fun getLabels(): List<String> = ALL_ACTIONS.map { it.displayName }

    fun getActionId(index: Int): String {
        return ALL_ACTIONS.getOrNull(index)?.id ?: "NONE"
    }

    fun getIndexForAction(actionId: String?): Int {
        if (actionId.isNullOrBlank()) return ALL_ACTIONS.indexOfFirst { it.id == "NONE" }.coerceAtLeast(0)
        val direct = ALL_ACTIONS.indexOfFirst { it.id.equals(actionId, ignoreCase = true) }
        if (direct >= 0) return direct
        val mapped = com.myvu.client.app.feature.GestureAction.fromId(actionId)
        val mappedIdx = ALL_ACTIONS.indexOfFirst {
            it.id.equals(mapped.name, ignoreCase = true) ||
            it.id.equals(mapped.id, ignoreCase = true)
        }
        return if (mappedIdx >= 0) mappedIdx else ALL_ACTIONS.indexOfFirst { it.id == "NONE" }.coerceAtLeast(0)
    }

    fun getDisplayName(actionId: String?): String {
        if (actionId.isNullOrBlank()) return "Ninguna Acción"
        return ALL_ACTIONS.firstOrNull { it.id.equals(actionId, ignoreCase = true) }?.displayName ?: actionId
    }
}
