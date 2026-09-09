package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.service.MyvuService
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Enhanced Real-time Translation & HUD Display Handler:
 * Translates phrases across languages (en, es, fr, de, it, pt, zh, ja) using fast online translation
 * and directly projects the output onto Meizu Myvu AR Smart Glasses via openTeleprompter().
 */
class SmartTranslateHudHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult = withContext(Dispatchers.IO) {
        try {
            val text = args.optString("text", "").trim()
            val targetLang = args.optString("target_language", "en").lowercase().trim()
            val sendToHud = args.optBoolean("send_to_hud", true)

            if (text.isEmpty()) {
                return@withContext SkillResult(false, "Falta ingresar el texto a traducir ('text').")
            }

            val languageName = when (targetLang) {
                "en" -> "Inglés 🇺🇸"
                "fr" -> "Francés 🇫🇷"
                "de" -> "Alemán 🇩🇪"
                "zh", "zh-cn" -> "Chino 🇨🇳"
                "pt" -> "Portugués 🇧🇷"
                "es" -> "Español 🇪🇸"
                "it" -> "Italiano 🇮🇹"
                "ja" -> "Japonés 🇯🇵"
                else -> targetLang.uppercase()
            }

            val translatedText = fetchTranslation(text, targetLang) ?: text

            // Project to Smart Glasses HUD
            var projectedOnGlasses = false
            if (sendToHud) {
                val conn = MyvuService.activeConnection()
                if (conn != null && conn.isRelayConnected()) {
                    conn.openTeleprompter(
                        text = "[$targetLang] $translatedText\n\n(Original: $text)",
                        title = "Traductor AR"
                    )
                    projectedOnGlasses = true
                    LogBus.log("SmartTranslateHudHandler: Projected translation to glasses HUD via openTeleprompter")
                }
            }

            val sb = StringBuilder()
            sb.append("🌐 **Traducción ($languageName)**:\n\n")
            sb.append("• **Original**: \"$text\"\n")
            sb.append("• **Traducción**: **$translatedText**\n")
            if (projectedOnGlasses) {
                sb.append("\n🕶️ *Proyectado en pantalla HUD de las gafas Meizu Myvu.*")
            }

            SkillResult(
                success = true,
                message = sb.toString(),
                payload = mapOf(
                    "original" to text,
                    "translated" to translatedText,
                    "targetLanguage" to targetLang,
                    "projectedOnGlasses" to projectedOnGlasses
                )
            )
        } catch (e: Exception) {
            LogBus.error("SmartTranslateHudHandler -> Error during translation", e)
            SkillResult(false, "Error al ejecutar la traducción: ${e.message}")
        }
    }

    private fun fetchTranslation(text: String, targetLang: String): String? {
        return try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val urlString = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=$encodedText"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (conn.responseCode in 200..299) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val jsonArray = JSONArray(responseStr)
                val sentencesArray = jsonArray.optJSONArray(0)
                if (sentencesArray != null && sentencesArray.length() > 0) {
                    val sb = StringBuilder()
                    for (i in 0 until sentencesArray.length()) {
                        val sentence = sentencesArray.optJSONArray(i)
                        if (sentence != null && sentence.length() > 0) {
                            sb.append(sentence.optString(0, ""))
                        }
                    }
                    val res = sb.toString().trim()
                    if (res.isNotEmpty()) return res
                }
            } else {
                conn.disconnect()
            }
            null
        } catch (e: Exception) {
            LogBus.warn("SmartTranslateHudHandler: Free translation API failed: ${e.message}")
            null
        }
    }
}
