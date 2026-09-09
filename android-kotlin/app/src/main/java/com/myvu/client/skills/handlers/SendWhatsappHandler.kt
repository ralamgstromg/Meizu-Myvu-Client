package com.myvu.client.skills.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.myvu.client.core.ContactHelper
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Hands-Free WhatsApp Messaging Handler.
 *
 * Strategy (in order of preference):
 * 1. Direct ACTION_SEND to com.whatsapp — sends without any UI interaction needed.
 *    Works even with the screen locked on most Android versions.
 * 2. WhatsApp API deep-link (fallback when no phone found or ACTION_SEND rejected).
 * 3. Generic browser fallback if WhatsApp is not installed.
 *
 * Phone numbers are prefixed with +57 (Colombia) when no country code is present.
 */
class SendWhatsappHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val contactOrPhone = args.optString("contact_or_phone", "").trim()
        val message = args.optString("message", "").trim()

        if (contactOrPhone.isEmpty() || message.isEmpty()) {
            return SkillResult(false, "Falta especificar el destinatario y el mensaje a enviar por WhatsApp.")
        }

        // 1. Resolve contact name to phone number
        val resolved = ContactHelper.resolveContactPhone(context, contactOrPhone)
        val rawPhone = resolved?.first ?: contactOrPhone
        val displayName = resolved?.second ?: contactOrPhone
        val cleanPhone = ContactHelper.formatColombianPhone(rawPhone)

        // 2. Try direct ACTION_SEND — works without unlocking the screen
        if (cleanPhone.isNotEmpty()) {
            val directIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra("address", cleanPhone)
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra("chat", true)
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(directIntent)
                LogBus.log("SendWhatsappHandler: Sent via ACTION_SEND direct to $cleanPhone")
                return SkillResult(
                    true,
                    "Enviando WhatsApp a $displayName ($cleanPhone): \"$message\""
                )
            } catch (e: Exception) {
                LogBus.warn("SendWhatsappHandler: ACTION_SEND failed (${e.message}), falling back to API link")
            }
        }

        // 3. Fallback: deep-link via WhatsApp API URL
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
            LogBus.log("SendWhatsappHandler: Opened WhatsApp via API link for $displayName")
            SkillResult(true, "Abriendo WhatsApp para $displayName. Confirma el envio en pantalla.")
        } catch (e: Exception) {
            LogBus.warn("SendWhatsappHandler: WhatsApp not installed, trying browser")
            val fallbackIntent = Intent(Intent.ACTION_VIEW, intentUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(fallbackIntent)
                SkillResult(true, "Abriendo enlace de WhatsApp para $displayName en el navegador.")
            } catch (ex: Exception) {
                LogBus.error("SendWhatsappHandler: Failed to launch WhatsApp intent", ex)
                SkillResult(false, "No se pudo abrir WhatsApp para '$displayName': ${ex.message}")
            }
        }
    }
}
