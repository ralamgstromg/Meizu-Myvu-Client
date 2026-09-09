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
 * Hands-Free Telegram Messaging Handler:
 * Supports usernames, phone numbers, contact lookup, arms auto-send accessibility,
 * and launches Telegram direct share/message.
 */
class SendTelegramHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val target = args.optString("username_or_phone", "").trim()
        val message = args.optString("message", "").trim()

        if (message.isEmpty()) {
            return SkillResult(false, "Falta especificar el mensaje a enviar por Telegram.")
        }

        // 1. Arm auto-send accessibility
        try {
            AutoSendAccessibilityService.triggerTelegramAutoSend()
            LogBus.log("SendTelegramHandler: AutoSendAccessibilityService armed for Telegram")
        } catch (e: Exception) {
            LogBus.warn("SendTelegramHandler: Could not arm accessibility auto-send: ${e.message}")
        }

        val encodedMessage = URLEncoder.encode(message, "UTF-8")

        // 2. Determine target URI
        val intentUri: Uri = if (target.startsWith("@")) {
            val username = target.removePrefix("@")
            Uri.parse("https://t.me/$username?text=$encodedMessage")
        } else if (target.isNotEmpty()) {
            val resolved = ContactHelper.resolveContactPhone(context, target)
            val phone = resolved?.first ?: target
            val cleanPhone = ContactHelper.formatColombianPhone(phone)
            if (cleanPhone.isNotEmpty()) {
                Uri.parse("tg://msg?text=$encodedMessage&to=+$cleanPhone")
            } else {
                Uri.parse("tg://msg?text=$encodedMessage")
            }
        } else {
            Uri.parse("tg://msg?text=$encodedMessage")
        }

        val telegramIntent = Intent(Intent.ACTION_VIEW, intentUri).apply {
            setPackage("org.telegram.messenger")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(telegramIntent)
            SkillResult(true, "✈️ **Enviando Telegram** a **${target.ifEmpty { "chat" }}**: \"$message\"")
        } catch (e: Exception) {
            LogBus.warn("SendTelegramHandler: Telegram app not found, trying generic share intent")
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(shareIntent)
                SkillResult(true, "✈️ Compartiendo mensaje en Telegram/aplicaciones.")
            } catch (ex: Exception) {
                LogBus.error("SendTelegramHandler: Failed to launch Telegram", ex)
                SkillResult(false, "No se pudo abrir Telegram: ${ex.message}")
            }
        }
    }
}
