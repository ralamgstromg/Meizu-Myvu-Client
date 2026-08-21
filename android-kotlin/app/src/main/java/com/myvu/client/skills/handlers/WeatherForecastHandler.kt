package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.ai.ExternalInfoService
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WeatherForecastHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val city = args.optString("city", "").trim()
        if (city.isEmpty()) {
            return SkillResult(false, "Falta especificar la ciudad para el clima.")
        }

        val weatherResult = withContext(Dispatchers.IO) {
            ExternalInfoService.fetchWeather(city)
        }

        return if (!weatherResult.isNullOrBlank()) {
            SkillResult(true, weatherResult, weatherResult)
        } else {
            SkillResult(false, "No se pudo obtener el clima para '$city'.")
        }
    }
}
