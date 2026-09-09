package com.myvu.client.skills.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.myvu.client.core.LogBus
import com.myvu.client.service.MyvuService
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Enhanced AR Navigation Handler:
 * Coordinates turn-by-turn navigation projected onto Meizu Myvu AR Smart Glasses,
 * with seamless mobile fallback to Google Maps or Waze.
 */
class HudNavigationHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val destination = args.optString("destination", "").trim()
            val neighborhood = args.optString("neighborhood", "").trim()
            val city = args.optString("city", "").trim()
            val mode = args.optString("mode", "driving").lowercase().trim()
            val navApp = args.optString("app", "maps").lowercase().trim()

            if (destination.isEmpty()) {
                return SkillResult(false, "Indica la dirección, sitio de interés o barrio de destino.")
            }

            val addressBuilder = StringBuilder(destination)
            if (neighborhood.isNotEmpty()) addressBuilder.append(", ").append(neighborhood)
            if (city.isNotEmpty()) addressBuilder.append(", ").append(city)

            val fullAddress = addressBuilder.toString()
            val pm = context.packageManager

            val connection = MyvuService.activeConnection()
            if (connection != null && connection.isRelayConnected()) {
                withContext(Dispatchers.Main) {
                    connection.nav().start(fullAddress)
                }
                val msg = "🧭 **Navegación HUD activada**: Proyectando indicaciones paso a paso hacia **$fullAddress** en las gafas."
                return SkillResult(true, msg, msg)
            }

            // Mobile fallback: Waze or Google Maps
            if (navApp.contains("waze")) {
                val wazeUri = Uri.parse("waze://?q=" + Uri.encode(fullAddress) + "&navigate=yes")
                val wazeIntent = Intent(Intent.ACTION_VIEW, wazeUri).apply {
                    setPackage("com.waze")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (wazeIntent.resolveActivity(pm) != null) {
                    context.startActivity(wazeIntent)
                    return SkillResult(true, "🚗 **Navegación iniciada en Waze** hacia **$fullAddress**.")
                }
            }

            // Google Maps navigation
            val gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(fullAddress) + "&mode=" + if (mode.contains("walk") || mode.contains("pie")) "w" else "d")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(mapIntent)
                SkillResult(true, "📍 **Navegación iniciada en Google Maps** hacia **$fullAddress**.")
            } catch (e: Exception) {
                val geoUri = Uri.parse("geo:0,0?q=" + Uri.encode(fullAddress))
                val genericIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(genericIntent)
                SkillResult(true, "🗺️ **Ubicación abierta** para **$fullAddress**.")
            }
        } catch (e: Exception) {
            LogBus.error("HudNavigationHandler -> Error starting navigation", e)
            SkillResult(false, "Error al iniciar la navegación HUD: ${e.message}")
        }
    }
}
