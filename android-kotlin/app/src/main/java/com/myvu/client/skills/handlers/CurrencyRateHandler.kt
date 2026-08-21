package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.ai.ExternalInfoService
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CurrencyRateHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val from = args.optString("from", "").trim()
        val to = args.optString("to", "").trim()

        if (from.isEmpty() || to.isEmpty()) {
            return SkillResult(false, "Falta especificar las divisas de origen y destino.")
        }

        val rateResult = withContext(Dispatchers.IO) {
            ExternalInfoService.fetchCurrencyRate(1.0, from, to)
        }

        return if (!rateResult.isNullOrBlank()) {
            SkillResult(true, rateResult, rateResult)
        } else {
            SkillResult(false, "No se pudo obtener el tipo de cambio entre $from y $to.")
        }
    }
}
