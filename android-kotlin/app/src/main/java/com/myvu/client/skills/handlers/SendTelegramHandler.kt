package com.myvu.client.skills.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

class SendTelegramHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val target = args.optString("username_or_phone", "").trim()
        val message = args.optString("message", "").trim()

        if (target.isEmpty() || message.isEmpty()) {
            return SkillResult(false, "Falta especificar el usuario/contacto o el contenido del mensaje de Telegram.")
        }

        val cleanTarget = target.removePrefix("@")
        val telegramUri = Uri.parse("https://t.me/$cleanTarget")

        val telegramIntent = Intent(Intent.ACTION_VIEW, telegramUri).apply {
            setPackage("org.telegram.messenger")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(telegramIntent)
            SkillResult(true, "Abriendo Telegram para $target")
        } catch (e: Exception) {
            LogBus.warn("SendTelegramHandler: Telegram app not installed, falling back to browser")
            val browserIntent = Intent(Intent.ACTION_VIEW, telegramUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(browserIntent)
                SkillResult(true, "Abriendo perfil de Telegram t.me/$cleanTarget en el navegador")
            } catch (ex: Exception) {
                LogBus.error("SendTelegramHandler: Failed to launch Telegram intent", ex)
                SkillResult(false, "No se pudo abrir Telegram: ${ex.message}")
            }
        }
    }
}
