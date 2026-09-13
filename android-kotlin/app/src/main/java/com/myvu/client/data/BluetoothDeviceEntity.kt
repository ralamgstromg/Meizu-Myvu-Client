package com.myvu.client.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Supported device categories in the universal agent platform.
 */
enum class BluetoothDeviceType {
    SMART_GLASSES,
    HEADPHONES,
    GENERIC
}

/**
 * Modes of handling incoming notifications on a connected device:
 * - BOTH: Visual (HUD/Screen) + Spoken via TTS
 * - VISUAL_ONLY: Visual HUD display only (silent, no speech)
 * - AUDIO_ONLY: Audio readout via TTS only (no HUD visual)
 * - NONE: Do not display or speak notifications on this device
 */
enum class DeviceNotificationMode(val id: String, val displayName: String, val description: String) {
    BOTH("BOTH", "Visual (HUD) y Sonora (TTS)", "Muestra en pantalla y lee por voz"),
    VISUAL_ONLY("VISUAL_ONLY", "Solo Visual (HUD)", "Muestra en pantalla sin reproducir audio"),
    AUDIO_ONLY("AUDIO_ONLY", "Solo Sonora (TTS)", "Lee por voz sin mostrar en pantalla"),
    NONE("NONE", "Desactivadas", "No emitir ni mostrar notificaciones");

    fun isVisualNotificationEnabled(): Boolean = this == VISUAL_ONLY || this == BOTH
    fun isAudioNotificationEnabled(): Boolean = this == AUDIO_ONLY || this == BOTH

    companion object {
        fun fromId(id: String?): DeviceNotificationMode {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: BOTH
        }

        fun getLabels(): List<String> = entries.map { it.displayName }

        fun getIndex(mode: String?): Int {
            val m = fromId(mode)
            return entries.indexOf(m).coerceAtLeast(0)
        }

        fun getModeByIndex(index: Int): DeviceNotificationMode {
            return entries.getOrNull(index) ?: BOTH
        }
    }
}

/**
 * Entity representing any connected or paired Bluetooth device with its specific configuration.
 */
@Entity(tableName = "bluetooth_devices")
data class BluetoothDeviceEntity(
    @PrimaryKey
    val macAddress: String,
    val name: String,
    val deviceType: String = BluetoothDeviceType.GENERIC.name,
    val isConnected: Boolean = false,
    val batteryLevel: Int? = null,
    val lastConnectedTime: Long = System.currentTimeMillis(),

    // Customizable Gestures (Common & Specific)
    val tap1Action: String = "MEDIA_PLAY_PAUSE",
    val tap2Action: String = "LAUNCH_GEMINI",
    val tap3Action: String = "LAUNCH_PHONE_ASSISTANT",
    val longPressAction: String = "CREATE_AI_NOTE",
    val swipeForwardAction: String = "MEDIA_NEXT",
    val swipeBackwardAction: String = "MEDIA_PREV",
    val actionButtonAction: String = "VOICE_AI_FIXED",

    // Audio, Voice, Notification & Screen features
    val ttsEnabled: Boolean = true,
    val autoReadNotifications: Boolean = false,
    val notificationMode: String = DeviceNotificationMode.BOTH.name,
    val hudBrightness: Int = 80,
    val isFavorite: Boolean = false,
    val activeListeningEnabled: Boolean = false
) {
    fun isVisualNotificationEnabled(): Boolean {
        return notificationMode == DeviceNotificationMode.VISUAL_ONLY.name ||
               notificationMode == DeviceNotificationMode.BOTH.name
    }

    fun isAudioNotificationEnabled(): Boolean {
        return notificationMode == DeviceNotificationMode.AUDIO_ONLY.name ||
               notificationMode == DeviceNotificationMode.BOTH.name ||
               autoReadNotifications
    }
}
