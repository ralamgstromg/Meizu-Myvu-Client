package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.ai.ExternalInfoService
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CurrencyRateHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val from = args.optString("from", "").trim()
            val to = args.optString("to", "").trim()

            if (from.isEmpty() || to.isEmpty()) {
                return SkillResult(false, "Falta especificar las divisas de origen y destino.")
            }

            val rate = withContext(Dispatchers.IO) {
                ExternalInfoService.fetchCurrencyRate(from, to)
            }

            if (rate != null) {
                val text = ExternalInfoService.formatCurrencyResult(1.0, from, rate, to)
                SkillResult(true, text, text)
            } else {
                SkillResult(false, "No se pudo obtener el tipo de cambio entre $from y $to.")
            }
        } catch (e: Exception) {
            LogBus.error("CurrencyRateHandler -> Exception during execution", e)
            SkillResult(false, "Error al consultar tasa de cambio: ${e.message}")
        }
    }
}
