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
            val date = args.optString("date", "").trim()
            val timeFrame = args.optString("time_frame", "").trim()

            if (city.isEmpty()) {
                return SkillResult(false, "Falta especificar la ciudad para el clima.")
            }

            val searchQuery = StringBuilder("clima en ").append(city)
            if (date.isNotEmpty()) searchQuery.append(" para ").append(date)
            if (timeFrame.isNotEmpty()) searchQuery.append(" en la ").append(timeFrame)

            val weatherResult = withContext(Dispatchers.IO) {
                ExternalInfoService.executeSearch(searchQuery.toString())
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
