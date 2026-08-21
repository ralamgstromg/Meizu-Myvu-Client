package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.ai.ExternalInfoService
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CurrencyConvertHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val amountStr = args.optString("amount", "1").trim()
        val from = args.optString("from", "").trim()
        val to = args.optString("to", "").trim()

        val amount = amountStr.toDoubleOrNull() ?: 1.0

        if (from.isEmpty() || to.isEmpty()) {
            return SkillResult(false, "Falta especificar el monto o las divisas para conversión.")
        }

        val convertResult = withContext(Dispatchers.IO) {
            ExternalInfoService.fetchCurrencyRate(amount, from, to)
        }

        return if (!convertResult.isNullOrBlank()) {
            SkillResult(true, convertResult, convertResult)
        } else {
            SkillResult(false, "No se pudo realizar la conversión de $amount $from a $to.")
        }
    }
}
