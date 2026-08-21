package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.ai.ExternalInfoService
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WeatherForecastHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val city = args.optString("city", "").trim()
            if (city.isEmpty()) {
                return SkillResult(false, "Falta especificar la ciudad para el clima.")
            }

            val weatherResult = withContext(Dispatchers.IO) {
                ExternalInfoService.executeSearch("clima en $city")
            }

            if (weatherResult.isNotBlank() && !weatherResult.startsWith("No se encontraron")) {
                SkillResult(true, weatherResult, weatherResult)
            } else {
                SkillResult(false, "No se pudo obtener el clima para '$city'.")
            }
        } catch (e: Exception) {
            LogBus.error("WeatherForecastHandler -> Exception during execution", e)
            SkillResult(false, "Error al consultar pronóstico del tiempo: ${e.message}")
        }
    }
}
