package com.myvu.client.skills.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject
import java.net.URLEncoder

class SendWhatsappHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val contactOrPhone = args.optString("contact_or_phone", "").trim()
        val message = args.optString("message", "").trim()

        if (contactOrPhone.isEmpty() || message.isEmpty()) {
            return SkillResult(false, "Falta el número/contacto o el contenido del mensaje de WhatsApp.")
        }

        val encodedMessage = URLEncoder.encode(message, "UTF-8")
        val cleanPhone = contactOrPhone.replace("[^0-9+]".toRegex(), "")

        val intentUri = if (cleanPhone.isNotEmpty()) {
            Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage")
        } else {
            Uri.parse("https://api.whatsapp.com/send?text=$encodedMessage")
        }

        val whatsappIntent = Intent(Intent.ACTION_VIEW, intentUri).apply {
            setPackage("com.whatsapp")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(whatsappIntent)
            SkillResult(true, "Abriendo WhatsApp para enviar mensaje a $contactOrPhone")
        } catch (e: Exception) {
            LogBus.warn("SendWhatsappHandler: WhatsApp app not installed, falling back to browser URI")
            val browserIntent = Intent(Intent.ACTION_VIEW, intentUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(browserIntent)
                SkillResult(true, "Abriendo enlace de WhatsApp en navegador para $contactOrPhone")
            } catch (ex: Exception) {
                LogBus.error("SendWhatsappHandler: Failed to launch WhatsApp intent", ex)
                SkillResult(false, "No se pudo abrir WhatsApp: ${ex.message}")
            }
        }
    }
}
