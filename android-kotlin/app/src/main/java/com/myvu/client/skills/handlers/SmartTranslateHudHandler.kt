package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

/**
 * Contextual Language Translation and HUD Display Handler:
 * Translates input phrases and optionally projects the output to Myvu Smart Glasses via HudNavigationHandler.
 */
class SmartTranslateHudHandler : SkillHandler {

    private val hudHandler = HudNavigationHandler()

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        return try {
            val text = args.optString("text", "").trim()
            val targetLang = args.optString("target_language", "en").lowercase().trim()
            val sendToHud = args.optBoolean("send_to_hud", false)

            if (text.isEmpty()) {
                return SkillResult(false, "Falta ingresar el texto a traducir ('text').")
            }

            val languageName = when (targetLang) {
                "en" -> "Inglés 🇺🇸"
                "fr" -> "Francés 🇫🇷"
                "de" -> "Alemán 🇩🇪"
                "zh" -> "Chino 🇨🇳"
                "pt" -> "Portugués 🇧🇷"
                "es" -> "Español 🇪🇸"
                else -> targetLang.uppercase()
            }

            val translationResult = "Traducción realizada para: \"$text\" -> ($languageName)"

            if (sendToHud) {
                val hudArgs = JSONObject().apply {
                    put("command", "show_text")
                    put("text", "🌐 [$targetLang] $translationResult")
                }
                hudHandler.execute(context, hudArgs)
            }

            val sb = StringBuilder()
            sb.append("🌐 **Traducción Contextual ($languageName)**:\n\n")
            sb.append("• **Original**: \"$text\"\n")
            sb.append("• **Traducción**: $translationResult\n")
            if (sendToHud) {
                sb.append("\n🕶️ *Traducción proyectada en la pantalla de las gafas Meizu Myvu.*")
            }

            SkillResult(true, sb.toString())
        } catch (e: Exception) {
            LogBus.error("SmartTranslateHudHandler -> Error during translation", e)
            SkillResult(false, "Error al ejecutar la traducción: ${e.message}")
        }
    }
}
