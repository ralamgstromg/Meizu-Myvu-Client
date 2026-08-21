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

class HudNavigationHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val destination = args.optString("destination", "").trim()
            val neighborhood = args.optString("neighborhood", "").trim()
            val city = args.optString("city", "").trim()
            val mode = args.optString("mode", "driving").trim()

            if (destination.isEmpty()) {
                return SkillResult(false, "Indica la dirección, sitio o barrio de destino.")
            }

            val addressBuilder = StringBuilder(destination)
            if (neighborhood.isNotEmpty()) addressBuilder.append(", ").append(neighborhood)
            if (city.isNotEmpty()) addressBuilder.append(", ").append(city)

            val fullAddress = addressBuilder.toString()

            val connection = MyvuService.activeConnection()
            if (connection != null && connection.isRelayConnected()) {
                withContext(Dispatchers.Main) {
                    connection.nav().start(fullAddress)
                }
                val msg = "🧭 Navegación AR proyectada en HUD iniciada hacia: '$fullAddress'."
                SkillResult(true, msg, msg)
            } else {
                val gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(fullAddress) + "&mode=" + if (mode.contains("walk")) "w" else "d")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    setPackage("com.google.android.apps.maps")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(mapIntent)
                    val msg = "Gafas desconectadas. Navegación iniciada en teléfono hacia '$fullAddress'."
                    SkillResult(true, msg, msg)
                } catch (e: Exception) {
                    val geoUri = Uri.parse("geo:0,0?q=" + Uri.encode(fullAddress))
                    val genericIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(genericIntent)
                    val msg = "Navegación solicitada hacia '$fullAddress'."
                    SkillResult(true, msg, msg)
                }
            }
        } catch (e: Exception) {
            LogBus.error("HudNavigationHandler -> Error starting navigation", e)
            SkillResult(false, "Error al iniciar la navegación HUD: ${e.message}")
        }
    }
}
