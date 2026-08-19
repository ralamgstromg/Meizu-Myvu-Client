package com.myvu.client.ai

import com.myvu.client.app.feature.Weather
import com.myvu.client.core.LogBus
import com.myvu.client.core.SslUtils
import com.myvu.client.weather.OpenMeteo
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Service for live external queries: Weather (Open-Meteo), Currencies (ExchangeRate API),
 * and Web/Google Search answers formatted into concise plain-text for AR glasses HUD and TTS playback.
 */
object ExternalInfoService {

    private const val USER_AGENT_MOBILE = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val USER_AGENT_APP = "myvu-android-client/1.0"
    private const val DEFAULT_TIMEOUT_MS = 6000

    private val executor = Executors.newCachedThreadPool()

    data class CurrencyRequest(
        val amount: Double,
        val from: String,
        val to: String
    )

    // ==========================================
    // Query Classification
    // ==========================================

    @JvmStatic
    fun isWeatherQuery(query: String): Boolean {
        val norm = normalize(query)
        return norm.matches(Regex(".*\\b(clima|temperatura|tiempo|pronostico|llover|lluvia|frio|calor)\\b.*")) ||
                norm.startsWith("como esta el tiempo") ||
                norm.startsWith("que tiempo hace") ||
                norm.startsWith("va a llover")
    }

    @JvmStatic
    fun isCurrencyQuery(query: String): Boolean {
        val norm = normalize(query)
        val currencyKeywords = listOf(
            "dolar", "dolares", "euro", "euros", "peso", "pesos", "cop", "usd", "eur",
            "divisa", "divisas", "moneda", "tasa de cambio", "tipo de cambio", "cotizacion",
            "precio del dolar", "precio del euro", "cuanto esta el dolar", "a como esta el dolar",
            "soles", "reales", "libras", "yen", "yenes"
        )
        return currencyKeywords.any { norm.contains(it) } ||
                norm.matches(Regex(".*\\b(convertir|cambio de|tasa|precio|cotizacion)\\b.*\\b(a|en)\\b.*"))
    }

    @JvmStatic
    fun isGeneralSearchQuery(query: String): Boolean {
        val norm = normalize(query)
        return norm.startsWith("busca ") ||
                norm.startsWith("buscar ") ||
                norm.startsWith("busca en google") ||
                norm.startsWith("buscar en google") ||
                norm.startsWith("google ") ||
                norm.startsWith("quien es ") ||
                norm.startsWith("quien fue ") ||
                norm.startsWith("que es ") ||
                norm.startsWith("que fue ") ||
                norm.startsWith("cuando fue ") ||
                norm.startsWith("donde queda ") ||
                norm.startsWith("donde esta ") ||
                norm.startsWith("cual es la capital ") ||
                norm.contains("noticias") ||
                norm.contains("buscar en la web")
    }

    // ==========================================
    // Parsing & Extraction
    // ==========================================

    @JvmStatic
    fun extractCityFromWeatherQuery(query: String): String? {
        val trimmed = query.trim()
        // Strip temporal phrases before matching prepositions to avoid matching "para mañana" as city
        val timeCleaned = trimmed
            .replace(Regex("(?i)\\b(para|de|en)?\\s*(hoy|mañana|manana|ayer|ahora|esta tarde|esta semana|este fin de semana|el fin de semana)\\b"), "")
            .trim()
        val norm = normalize(timeCleaned)

        // Match weather queries with location prepositions: "clima en Barranquilla", "qué temperatura en Barranquilla", "temperatura de París", "pronóstico para Madrid"
        val pattern = Regex("(?:clima|temperatura|tiempo|pronostico|llover|lluvia|frio|calor|grados)\\b(?:.+?)?\\b(?:en|de|para|por)\\s+([a-z0-9áéíóúñü\\s]+)", RegexOption.IGNORE_CASE)
        val match = pattern.find(norm)
        if (match != null) {
            val rawStart = match.groups[1]!!.range.first
            val rawEnd = match.groups[1]!!.range.last + 1
            var city = timeCleaned.substring(rawStart, minOf(rawEnd, timeCleaned.length)).trim()
            city = city.replace(Regex("[?.,!;:]+$"), "").trim()
            if (city.isNotBlank() && !isGenericStopword(city)) {
                return city
            }
        }

        // Direct fallback: if no preposition matched but a city word remains
        val directPattern = Regex("(?:clima|temperatura|tiempo|pronostico|grados)\\s+([a-z0-9áéíóúñü\\s]+)", RegexOption.IGNORE_CASE)
        val directMatch = directPattern.find(norm)
        if (directMatch != null) {
            val rawStart = directMatch.groups[1]!!.range.first
            val rawEnd = directMatch.groups[1]!!.range.last + 1
            var city = timeCleaned.substring(rawStart, minOf(rawEnd, timeCleaned.length)).trim()
            city = city.replace(Regex("[?.,!;:]+$"), "").trim()
            if (city.isNotBlank() && !isGenericStopword(city)) {
                return city
            }
        }

        return null
    }

    private fun isGenericStopword(word: String): Boolean {
        val n = normalize(word)
        return n in setOf("hoy", "manana", "ayer", "mi ciudad", "aqui", "aca", "este momento", "el mundo", "donde estoy", "esta zona", "la ciudad")
    }

    @JvmStatic
    fun extractCurrencyRequest(query: String): CurrencyRequest? {
        val norm = normalize(query)

        // Extract amount if present (e.g. 100 dólares, 50.5 eur)
        val amountMatch = Regex("(?:cuanto (?:equivale|son|cuestan)?\\s*)?([0-9]+(?:[.,][0-9]+)?)\\s*").find(norm)
        var amount = 1.0
        if (amountMatch != null) {
            val numStr = amountMatch.groupValues[1].replace(',', '.')
            amount = numStr.toDoubleOrNull() ?: 1.0
        }

        // Currency aliases map to ISO 4217
        val currencyAliases = listOf(
            Pair(Regex("\\b(dolar|dolares|usd|dollar|dollars)\\b"), "USD"),
            Pair(Regex("\\b(euro|euros|eur)\\b"), "EUR"),
            Pair(Regex("\\b(peso colombiano|pesos colombianos|cop|colombianos)\\b"), "COP"),
            Pair(Regex("\\b(peso mexicano|pesos mexicanos|mxn|mexicanos)\\b"), "MXN"),
            Pair(Regex("\\b(peso argentino|pesos argentinos|ars|argentinos)\\b"), "ARS"),
            Pair(Regex("\\b(peso chileno|pesos chilenos|clp|chilenos)\\b"), "CLP"),
            Pair(Regex("\\b(sol|soles|pen|soles peruanos)\\b"), "PEN"),
            Pair(Regex("\\b(real|reales|brl|reales brasilenos)\\b"), "BRL"),
            Pair(Regex("\\b(libra|libras|gbp|libra esterlina|libras esterlinas)\\b"), "GBP"),
            Pair(Regex("\\b(yen|yenes|jpy)\\b"), "JPY"),
            Pair(Regex("\\b(yuan|yuanes|cny|renminbi)\\b"), "CNY"),
            Pair(Regex("\\b(franco|francos|chf|francos suizos)\\b"), "CHF"),
            Pair(Regex("\\b(cad|dolar canadiense)\\b"), "CAD"),
            Pair(Regex("\\b(aud|dolar australiano)\\b"), "AUD"),
            Pair(Regex("\\b(btc|bitcoin|bitcoins)\\b"), "BTC"),
            Pair(Regex("\\b(peso|pesos)\\b"), "COP")
        )

        // Find all currency matches and their positions in text
        data class MatchedCurrency(val code: String, val start: Int, val end: Int)
        val found = mutableListOf<MatchedCurrency>()

        for ((regex, code) in currencyAliases) {
            val matches = regex.findAll(norm)
            for (m in matches) {
                // Ensure non-overlapping matches
                if (found.none { it.start <= m.range.last && it.end >= m.range.first }) {
                    found.add(MatchedCurrency(code, m.range.first, m.range.last))
                }
            }
        }

        found.sortBy { it.start }

        if (found.isEmpty()) return null

        var fromCurrency = "USD"
        var toCurrency = "COP"

        if (found.size >= 2) {
            fromCurrency = found[0].code
            toCurrency = found[1].code
            if (fromCurrency == toCurrency) {
                toCurrency = if (fromCurrency == "USD") "COP" else "USD"
            }
        } else {
            // Only 1 currency mentioned
            val single = found[0].code
            when (single) {
                "USD" -> {
                    fromCurrency = "USD"
                    toCurrency = "COP"
                }
                "EUR" -> {
                    fromCurrency = "EUR"
                    toCurrency = "COP"
                }
                "COP" -> {
                    fromCurrency = "USD"
                    toCurrency = "COP"
                }
                else -> {
                    fromCurrency = single
                    toCurrency = "COP"
                }
            }
        }

        return CurrencyRequest(amount = amount, from = fromCurrency, to = toCurrency)
    }

    // ==========================================
    // Result Formatting (AR Glass HUD & TTS)
    // ==========================================

    @JvmStatic
    fun formatCurrencyResult(amount: Double, from: String, converted: Double, to: String): String {
        val df = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
        val amtStr = if (amount % 1.0 == 0.0) amount.toLong().toString() else df.format(amount)
        val convStr = if (converted % 1.0 == 0.0) converted.toLong().toString() else df.format(converted)

        return "$amtStr $from equivale a $convStr $to."
    }

    @JvmStatic
    fun formatWeatherResult(reading: Weather.Reading, cityName: String? = null): String {
        val city = reading.areaName ?: cityName ?: "tu zona"
        val cond = reading.condition?.trim() ?: "despejado"
        val temp = "${reading.temp}°C"

        val sb = StringBuilder("En $city hay $temp, $cond.")
        if (reading.dayTempMax != 0 || reading.dayTempMin != 0) {
            sb.append(" Máxima de ${reading.dayTempMax}°C y mínima de ${reading.dayTempMin}°C.")
        }
        return cleanForGlasses(sb.toString())
    }

    @JvmStatic
    fun cleanForGlasses(text: String): String {
        if (text.isBlank()) return ""

        var s = text
        // Strip HTML tags
        s = s.replace(Regex("<[^>]*>"), " ")

        // Decode HTML entities
        s = s.replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#x27;", "'")
            .replace("&aacute;", "á")
            .replace("&eacute;", "é")
            .replace("&iacute;", "í")
            .replace("&oacute;", "ó")
            .replace("&uacute;", "ú")
            .replace("&ntilde;", "ñ")
            .replace("&Aacute;", "Á")
            .replace("&Eacute;", "É")
            .replace("&Iacute;", "Í")
            .replace("&Oacute;", "Ó")
            .replace("&Uacute;", "Ú")
            .replace("&Ntilde;", "Ñ")

        // Strip Markdown
        s = s.replace(Regex("[*#_~`>]"), "")
        s = s.replace(Regex("(?m)^\\s*[-•*]\\s+"), "")

        // Strip Emojis while preserving essential math/currency/temp symbols: °, $, €, £, ¥, %, +, -, etc.
        s = s.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\uFE00-\\uFE0F]"), "")

        // Clean whitespace around punctuation
        s = s.replace(Regex("\\s+([.,;:!?])"), "$1")
        s = s.replace(Regex("([.,;:!?])([a-zA-ZáéíóúÁÉÍÓÚñÑ])"), "$1 $2")
        s = s.replace(Regex("\\s+"), " ").trim()

        // Keep 1-2 concise sentences (max ~200 characters)
        if (s.length > 200) {
            val sentences = s.split(Regex("(?<=[.!?])\\s+"))
            if (sentences.isNotEmpty()) {
                val candidate = sentences.take(2).joinToString(" ")
                s = if (candidate.length <= 220) candidate else sentences[0]
            }
        }

        return s.trim()
    }

    // ==========================================
    // Network Operations
    // ==========================================

    @JvmStatic
    fun fetchCurrencyRate(from: String, to: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Double? {
        // 1. Primary: open.er-api.com (free, high coverage including COP, MXN, ARS, EUR, USD)
        try {
            val url = "https://open.er-api.com/v6/latest/$from"
            val jsonStr = httpGet(url, USER_AGENT_APP, timeoutMs)
            val root = JSONObject(jsonStr)
            val rates = root.optJSONObject("rates")
            if (rates != null && rates.has(to)) {
                val rate = rates.optDouble(to, -1.0)
                if (rate > 0.0) return rate
            }
        } catch (e: Exception) {
            LogBus.warn("ExternalInfoService -> open.er-api.com failed for $from->$to: ${e.message}")
        }

        // 2. Secondary fallback: api.frankfurter.app
        try {
            val url = "https://api.frankfurter.app/latest?from=$from&to=$to"
            val jsonStr = httpGet(url, USER_AGENT_APP, timeoutMs)
            val root = JSONObject(jsonStr)
            val rates = root.optJSONObject("rates")
            if (rates != null && rates.has(to)) {
                val rate = rates.optDouble(to, -1.0)
                if (rate > 0.0) return rate
            }
        } catch (e: Exception) {
            LogBus.warn("ExternalInfoService -> frankfurter.app failed for $from->$to: ${e.message}")
        }

        return null
    }

    @JvmStatic
    fun fetchWeather(cityName: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): String? {
        return try {
            val reading = OpenMeteo.fetchByCity(cityName, timeoutMs)
            if (reading != null) {
                formatWeatherResult(reading, cityName)
            } else {
                null
            }
        } catch (e: Exception) {
            LogBus.warn("ExternalInfoService -> OpenMeteo failed for '$cityName': ${e.message}")
            null
        }
    }

    @JvmStatic
    fun fetchGoogleOrWebSearch(rawQuery: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): String? {
        val cleanQuery = rawQuery
            .replace(Regex("(?i)^(busca en google|buscar en google|google|busca|buscar)\\s+"), "")
            .trim()

        if (cleanQuery.isBlank()) return null

        // 1. Wikipedia Summary API for definitions / entities
        if (isDefinitionQuery(cleanQuery)) {
            val topic = extractTopicFromDefinition(cleanQuery)
            if (topic.isNotBlank()) {
                try {
                    val encoded = URLEncoder.encode(topic, "UTF-8")
                    val wikiUrl = "https://es.wikipedia.org/api/rest_v1/page/summary/$encoded"
                    val jsonStr = httpGet(wikiUrl, USER_AGENT_APP, timeoutMs)
                    val root = JSONObject(jsonStr)
                    val extract = root.optString("extract")
                    if (extract.isNotBlank()) {
                        return cleanForGlasses(extract)
                    }
                } catch (e: Exception) {
                    LogBus.log("ExternalInfoService -> Wikipedia fallback for '$topic': ${e.message}")
                }
            }
        }

        // 2. DuckDuckGo Instant Answer API
        try {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val ddgUrl = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val jsonStr = httpGet(ddgUrl, USER_AGENT_APP, timeoutMs)
            val root = JSONObject(jsonStr)
            val answer = root.optString("Answer")
            if (answer.isNotBlank()) {
                return cleanForGlasses(answer)
            }
            val abstractText = root.optString("AbstractText")
            if (abstractText.isNotBlank()) {
                return cleanForGlasses(abstractText)
            }
        } catch (e: Exception) {
            LogBus.log("ExternalInfoService -> DuckDuckGo API fallback: ${e.message}")
        }

        // 3. Google Search HTML Snippet parser
        try {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val googleUrl = "https://www.google.com/search?q=$encoded&hl=es"
            val html = httpGet(googleUrl, USER_AGENT_MOBILE, timeoutMs)

            // Extract Google quick answers / featured snippet
            val snippetPatterns = listOf(
                Regex("<div[^>]+class=\"[^\"]*BNeawe iBp4i AP7Wnd[^\"]*\"[^>]*>(.*?)</div>"),
                Regex("<div[^>]+class=\"[^\"]*BNeawe s3v9rd AP7Wnd[^\"]*\"[^>]*>(.*?)</div>"),
                Regex("<span[^>]+class=\"[^\"]*hgKElc[^\"]*\"[^>]*>(.*?)</span>"),
                Regex("<div[^>]+class=\"[^\"]*Z0LcW[^\"]*\"[^>]*>(.*?)</div>"),
                Regex("<div[^>]+class=\"[^\"]*kCrYT[^\"]*\"[^>]*>(.*?)</div>")
            )

            for (p in snippetPatterns) {
                val match = p.find(html)
                if (match != null) {
                    val rawSnippet = match.groupValues[1]
                    val cleaned = cleanForGlasses(rawSnippet)
                    if (cleaned.length >= 15 && !cleaned.startsWith("http") && !cleaned.contains("Imágenes")) {
                        return cleaned
                    }
                }
            }
        } catch (e: Exception) {
            LogBus.warn("ExternalInfoService -> Google Search failed: ${e.message}")
        }

        return null
    }

    @JvmStatic
    fun isNewsQuery(query: String): Boolean {
        val norm = normalize(query)
        return norm.contains("noticia") || norm.contains("noticias") ||
                norm.contains("novedades") || norm.contains("titulares") ||
                norm.startsWith("que pasa en ") || norm.startsWith("que paso en ")
    }

    @JvmStatic
    fun fetchNewsSearch(rawQuery: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): String? {
        val topic = rawQuery
            .replace(Regex("(?i)^(dame|busca|buscar|ver|cuales son|noticias sobre|noticias de|noticias|titulares de|titulares)\\s+"), "")
            .trim()
        if (topic.isBlank()) return null
        try {
            val encoded = URLEncoder.encode(topic, "UTF-8")
            val newsUrl = "https://news.google.com/rss/search?q=$encoded&hl=es-419&gl=CO&ceid=CO:es-419"
            val xmlStr = httpGet(newsUrl, USER_AGENT_APP, timeoutMs)
            val titles = mutableListOf<String>()
            val itemMatcher = Regex("<item>.*?<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
            val matches = itemMatcher.findAll(xmlStr)
            for (m in matches) {
                val rawTitle = m.groupValues[1].replace(Regex("(?i)\\s*-\\s*[^-]+$"), "").replace(Regex("<[^>]*>"), "")
                val t = cleanForGlasses(rawTitle)
                if (t.isNotBlank() && !t.contains("Google News") && titles.size < 3) {
                    titles.add(t)
                }
            }
            if (titles.isNotEmpty()) {
                return "Noticias de $topic: " + titles.joinToString(". ") + "."
            }
        } catch (e: Exception) {
            LogBus.warn("ExternalInfoService -> Google News RSS failed for '$topic': ${e.message}")
        }
        return null
    }

    private fun isDefinitionQuery(query: String): Boolean {
        val norm = normalize(query)
        return norm.startsWith("quien es ") ||
                norm.startsWith("quien fue ") ||
                norm.startsWith("que es ") ||
                norm.startsWith("que fue ") ||
                norm.startsWith("capital de ")
    }

    private fun extractTopicFromDefinition(query: String): String {
        return query.replace(Regex("(?i)^(quien fue|quien es|que fue|que es|capital de)\\s+"), "").trim()
    }

    // ==========================================
    // Unified Search Entry Points
    // ==========================================

    @JvmStatic
    fun executeSearch(query: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): String {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return "Consulta vacía."

        LogBus.log("ExternalInfoService -> Executing search for: '$trimmed'")

        // 1. Weather Query
        if (isWeatherQuery(trimmed)) {
            val city = extractCityFromWeatherQuery(trimmed)
            if (city != null) {
                val weatherResult = fetchWeather(city, timeoutMs)
                if (weatherResult != null) return weatherResult
            }
        }

        // 2. Currency Query
        if (isCurrencyQuery(trimmed)) {
            val req = extractCurrencyRequest(trimmed)
            if (req != null) {
                val rate = fetchCurrencyRate(req.from, req.to, timeoutMs)
                if (rate != null) {
                    val converted = req.amount * rate
                    return formatCurrencyResult(req.amount, req.from, converted, req.to)
                }
            }
        }

        // 3. News Query
        if (isNewsQuery(trimmed)) {
            val newsResult = fetchNewsSearch(trimmed, timeoutMs)
            if (newsResult != null && newsResult.isNotBlank()) {
                return newsResult
            }
        }

        // 4. Google / Web Search
        val webResult = fetchGoogleOrWebSearch(trimmed, timeoutMs)
        if (webResult != null && webResult.isNotBlank()) {
            return webResult
        }

        return "No se encontraron resultados para '$trimmed'."
    }

    @JvmStatic
    fun search(query: String, callback: (resultText: String, success: Boolean) -> Unit) {
        executor.execute {
            try {
                val result = executeSearch(query)
                val success = !result.startsWith("No se encontraron") && !result.startsWith("Consulta vacía")
                callback(result, success)
            } catch (e: Exception) {
                LogBus.warn("ExternalInfoService -> search error: ${e.message}")
                callback("Error al consultar información externa.", false)
            }
        }
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private fun normalize(text: String): String {
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase().trim()
    }

    @Throws(IOException::class)
    private fun httpGet(urlStr: String, userAgent: String, timeoutMs: Int): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        SslUtils.applySslBypass(conn)
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs

            val code = conn.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code from $urlStr")
            }

            val input = conn.inputStream
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var n: Int
            while (input.read(buf).also { n = it } > 0) {
                out.write(buf, 0, n)
            }
            input.close()
            return out.toString("UTF-8")
        } finally {
            conn.disconnect()
        }
    }
}
