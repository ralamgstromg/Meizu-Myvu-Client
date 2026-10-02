package com.myvu.client.core.locale

/**
 * Rewrites text so a Spanish (Colombia) TTS voice reads it naturally:
 * - US-grouped numbers ("4,150.32") become es-CO ("4.150,32")
 * - currency codes/symbols become words, with cents spoken ("4.150 pesos con 32 centavos")
 * - 24 h and am/pm times become "3:30 de la tarde"
 * - %, °C, km/h, km and URLs become words
 *
 * Input is expected to be plain text (Markdown already stripped). Idempotent:
 * normalizing already-normalized text returns it unchanged.
 */
object SpeechNormalizer {

    private val URL = Regex("https?://\\S+|www\\.\\S+")
    private val US_NUMBER = Regex("(?<![\\d.,])(\\d{1,3}(?:,\\d{3})+)(?:\\.(\\d+))?(?![\\d,])|(?<![\\d.,])(\\d+)\\.(\\d{1,2})(?![\\d.,])")

    private data class Currency(val singular: String, val plural: String, val cents: String)

    private val PESO = Currency("peso", "pesos", "centavos")
    private val DOLAR = Currency("dólar", "dólares", "centavos")
    private val EURO = Currency("euro", "euros", "céntimos")

    private val CODE_TO_CURRENCY = mapOf(
        "COP" to PESO, "USD" to DOLAR, "US$" to DOLAR, "EUR" to EURO, "€" to EURO, "$" to PESO
    )

    private const val AMOUNT = "(\\d{1,3}(?:\\.\\d{3})+|\\d+)(?:,(\\d{1,2}))?"
    private val PREFIX_CURRENCY = Regex("(COP|USD|US\\$|EUR|€|\\$)\\s?$AMOUNT(?!\\d)")
    private val SUFFIX_CURRENCY = Regex("(?<![\\d.,])$AMOUNT\\s?(COP|USD|EUR|€)(?![A-Za-z])")

    // The final lookahead keeps normalize() idempotent: "3:30 de la tarde" is not rewritten again.
    private val TIME = Regex(
        "\\b([01]?\\d|2[0-3]):([0-5]\\d)\\s*(a\\.?\\s?m\\.?|p\\.?\\s?m\\.?)?(?![\\w:])(?!\\s*de la (?:mañana|tarde|noche))",
        RegexOption.IGNORE_CASE
    )

    fun normalize(input: String): String {
        var s = input
        s = URL.replace(s, "enlace")
        s = toColombianNumbers(s)
        s = PREFIX_CURRENCY.replace(s) { m -> spokenAmount(m.groupValues[2], m.groupValues[3], CODE_TO_CURRENCY.getValue(m.groupValues[1])) }
        s = SUFFIX_CURRENCY.replace(s) { m -> spokenAmount(m.groupValues[1], m.groupValues[2], CODE_TO_CURRENCY.getValue(m.groupValues[3])) }
        s = TIME.replace(s, ::spokenTime)
        s = s.replace(Regex("\\s?%"), " por ciento")
            .replace(Regex("\\s?°\\s?C\\b"), " grados")
            .replace(Regex("\\s?°"), " grados")
            .replace(Regex("\\s?km/h\\b"), " kilómetros por hora")
            .replace(Regex("(?<=\\d)\\s?km\\b"), " kilómetros")
        return s.replace(Regex("[ \\t]{2,}"), " ").trim()
    }

    /** "4,150.32" -> "4.150,32"; "3.5" -> "3,5". Leaves es-CO numbers untouched. */
    private fun toColombianNumbers(s: String): String = US_NUMBER.replace(s) { m ->
        if (m.groupValues[1].isNotEmpty()) {
            val integer = m.groupValues[1].replace(",", ".")
            val decimals = m.groupValues[2]
            if (decimals.isEmpty()) integer else "$integer,$decimals"
        } else {
            "${m.groupValues[3]},${m.groupValues[4]}"
        }
    }

    private fun spokenAmount(integer: String, decimals: String, currency: Currency): String {
        val isOne = integer == "1"
        val unit = if (isOne) currency.singular else currency.plural
        val cents = decimals.padEnd(2, '0').trimStart('0')
        return if (cents.isEmpty()) "$integer $unit" else "$integer $unit con $cents ${currency.cents}"
    }

    private fun spokenTime(m: MatchResult): String {
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2]
        val marker = m.groupValues[3].lowercase().replace(Regex("[^ap]"), "")
        if (marker == "p" && hour < 12) hour += 12
        if (marker == "a" && hour == 12) hour = 0
        val period = when (hour) {
            in 0..11 -> "de la mañana"
            in 12..18 -> "de la tarde"
            else -> "de la noche"
        }
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val time = if (minute == "00") "$h12" else "$h12:$minute"
        return "$time $period"
    }
}
