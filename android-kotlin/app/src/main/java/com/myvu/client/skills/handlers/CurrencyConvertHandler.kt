package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.ai.ExternalInfoService
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CurrencyConvertHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val amountStr = args.optString("amount", "1").trim()
            val from = args.optString("from", "").trim()
            val to = args.optString("to", "").trim()

            val amount = amountStr.toDoubleOrNull() ?: 1.0

            if (from.isEmpty() || to.isEmpty()) {
                return SkillResult(false, "Falta especificar las divisas para conversión.")
            }

            val rate = withContext(Dispatchers.IO) {
                ExternalInfoService.fetchCurrencyRate(from, to)
            }

            if (rate != null) {
                val converted = amount * rate
                val text = ExternalInfoService.formatCurrencyResult(amount, from, converted, to)
                SkillResult(true, text, text)
            } else {
                SkillResult(false, "No se pudo realizar la conversión de $amount $from a $to.")
            }
        } catch (e: Exception) {
            LogBus.error("CurrencyConvertHandler -> Exception during execution", e)
            SkillResult(false, "Error al calcular la conversión de divisas: ${e.message}")
        }
    }
}
