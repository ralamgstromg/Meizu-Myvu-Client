package com.myvu.client.ai

import android.content.Context
import android.media.AudioManager
import com.myvu.client.core.GlassesConfig
import com.myvu.client.app.feature.SystemSettings
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.health.HealthService
import com.myvu.client.service.MyvuService

/**
 * Gestor de Modos Contextuales y Rutinas Automatizadas (Macros verbales).
 * Permite ejecutar conjuntos de acciones de sistema con una sola orden.
 */
object RoutineManager {

    fun setMeetingMode(context: Context, enable: Boolean): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val conn = MyvuService.activeConnection()

        return if (enable) {
            try {
                conn?.sendAction(SystemSettings.setZenMode(true))
                audioManager?.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                Prefs.setActiveRoutineMode(context, "meeting")
                LogBus.log("RoutineManager -> Modo Reunión ACTIVADO")
                "Modo reunión activado. Pantalla en reposo (Zen) y teléfono en vibración."
            } catch (e: Exception) {
                LogBus.error("RoutineManager -> Error activando modo reunión", e)
                "Modo reunión activado con limitaciones: ${e.message}"
            }
        } else {
            try {
                conn?.sendAction(SystemSettings.setZenMode(false))
                audioManager?.ringerMode = AudioManager.RINGER_MODE_NORMAL
                Prefs.setActiveRoutineMode(context, "none")
                LogBus.log("RoutineManager -> Modo Reunión DESACTIVADO")
                "Modo reunión finalizado. Notificaciones y sonido restaurados."
            } catch (e: Exception) {
                LogBus.error("RoutineManager -> Error desactivando modo reunión", e)
                "Modo reunión desactivado."
            }
        }
    }

    fun setDriveMode(context: Context, enable: Boolean): String {
        val conn = MyvuService.activeConnection()
        return if (enable) {
            try {
                // Brillo alto para máxima visibilidad en conducción
                conn?.sendAction(SystemSettings.setBrightness(3))
                Prefs.setActiveRoutineMode(context, "drive")
                LogBus.log("RoutineManager -> Modo Conducción ACTIVADO")
                "Modo conducción activo: brillo de gafas al máximo para carretera. Manos libres listo."
            } catch (e: Exception) {
                "Modo conducción activado."
            }
        } else {
            try {
                val normalBrightness = GlassesConfig.getBrightness(context)
                conn?.sendAction(SystemSettings.setBrightness(normalBrightness))
                Prefs.setActiveRoutineMode(context, "none")
                LogBus.log("RoutineManager -> Modo Conducción DESACTIVADO")
                "Modo conducción desactivado."
            } catch (e: Exception) {
                "Modo conducción desactivado."
            }
        }
    }

    fun setGymMode(context: Context, enable: Boolean): String {
        return if (enable) {
            Prefs.setActiveRoutineMode(context, "gym")
            val steps = try {
                HealthService.getInstance(context).getStepsSummary()
            } catch (e: Exception) {
                ""
            }
            LogBus.log("RoutineManager -> Modo Gimnasio ACTIVADO")
            val stepsPart = if (steps.isNotBlank()) " $steps." else ""
            "Modo entrenamiento activado.$stepsPart ¡A darle con todo!"
        } else {
            Prefs.setActiveRoutineMode(context, "none")
            LogBus.log("RoutineManager -> Modo Gimnasio DESACTIVADO")
            "Modo entrenamiento finalizado. Buen trabajo."
        }
    }

    fun setNightMode(context: Context, enable: Boolean): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val conn = MyvuService.activeConnection()

        return if (enable) {
            try {
                conn?.sendAction(SystemSettings.setBrightness(1))
                audioManager?.ringerMode = AudioManager.RINGER_MODE_SILENT
                Prefs.setActiveRoutineMode(context, "night")
                val battery = conn?.glassesInfo()?.battery
                val battText = if (battery != null && battery >= 0) " Batería de gafas: $battery%." else ""
                LogBus.log("RoutineManager -> Modo Noche ACTIVADO")
                "Modo descanso activado. Brillo en 1 y celular en silencio.$battText Buenas noches."
            } catch (e: Exception) {
                "Modo descanso activado. Buenas noches."
            }
        } else {
            try {
                val normalBrightness = GlassesConfig.getBrightness(context)
                conn?.sendAction(SystemSettings.setBrightness(normalBrightness))
                audioManager?.ringerMode = AudioManager.RINGER_MODE_NORMAL
                Prefs.setActiveRoutineMode(context, "none")
                LogBus.log("RoutineManager -> Modo Noche DESACTIVADO")
                "Modo descanso desactivado. Buen día."
            } catch (e: Exception) {
                "Modo descanso desactivado."
            }
        }
    }

    fun getActiveMode(context: Context): String {
        return Prefs.activeRoutineMode(context)
    }
}
