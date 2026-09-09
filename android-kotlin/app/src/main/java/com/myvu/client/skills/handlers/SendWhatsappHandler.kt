package com.myvu.client.skills.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.myvu.client.core.ContactHelper
import com.myvu.client.core.LogBus
import com.myvu.client.service.AutoSendAccessibilityService
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Hands-Free WhatsApp Messaging Handler:
 * Resolves contact names, standardizes phone numbers (prefix +57 Colombia),
 * arms AutoSendAccessibilityService, and launches direct WhatsApp chat.
 */
class SendWhatsappHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val contactOrPhone = args.optString("contact_or_phone", "").trim()
        val message = args.optString("message", "").trim()

        if (contactOrPhone.isEmpty() || message.isEmpty()) {
            return SkillResult(false, "Falta especificar el destinatario y el mensaje a enviar por WhatsApp.")
        }

        // 1. Resolve contact name to phone number if necessary
        val resolved = ContactHelper.resolveContactPhone(context, contactOrPhone)
        val rawPhone = resolved?.first ?: contactOrPhone
        val displayName = resolved?.second ?: contactOrPhone

        // 2. Format Colombian / International phone
        val cleanPhone = ContactHelper.formatColombianPhone(rawPhone)

        // 3. Arm accessibility auto-send service for zero-touch dispatch
        try {
            AutoSendAccessibilityService.triggerWhatsAppAutoSend()
            LogBus.log("SendWhatsappHandler: AutoSendAccessibilityService armed for WhatsApp")
        } catch (e: Exception) {
            LogBus.warn("SendWhatsappHandler: Could not arm accessibility auto-send: ${e.message}")
        }

        val encodedMessage = URLEncoder.encode(message, "UTF-8")
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
            SkillResult(true, "💬 **Enviando WhatsApp** a **$displayName** (${if (cleanPhone.isNotEmpty()) cleanPhone else "chat"}): \"$message\"")
        } catch (e: Exception) {
            LogBus.warn("SendWhatsappHandler: WhatsApp app not installed, falling back to browser/generic Intent")
            val fallbackIntent = Intent(Intent.ACTION_VIEW, intentUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(fallbackIntent)
                SkillResult(true, "💬 Abriendo enlace de WhatsApp para **$displayName** en navegador.")
            } catch (ex: Exception) {
                LogBus.error("SendWhatsappHandler: Failed to launch WhatsApp intent", ex)
                SkillResult(false, "No se pudo abrir WhatsApp para '$displayName': ${ex.message}")
            }
        }
    }
}
