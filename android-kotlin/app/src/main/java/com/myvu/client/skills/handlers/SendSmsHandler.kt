package com.myvu.client.skills.handlers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import com.myvu.client.core.ContactHelper
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

/**
 * Hands-Free SMS Messaging Handler:
 * Resolves contact names with fuzzy/subset matching, attempts zero-touch dispatch via SmsManager
 * if SEND_SMS permission is granted, with fallback to ACTION_SENDTO composer.
 */
class SendSmsHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val contactOrPhone = args.optString("contact_or_phone", "").ifEmpty {
            args.optString("recipient", "")
        }.trim()
        val message = args.optString("message", "").trim()

        if (contactOrPhone.isEmpty() || message.isEmpty()) {
            return SkillResult(false, "Falta especificar el destinatario y el mensaje a enviar por SMS.")
        }

        // 1. Resolve contact name to phone number
        val resolved = ContactHelper.resolveContactPhone(context, contactOrPhone)
        val rawPhone = resolved?.first ?: contactOrPhone
        val displayName = resolved?.second ?: contactOrPhone

        val cleanPhone = rawPhone.replace(Regex("[^0-9+]"), "")
        if (cleanPhone.isEmpty()) {
            return SkillResult(false, "No se encontró un número telefónico válido para '$contactOrPhone'.")
        }

        val hasSmsPerm = context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        if (hasSmsPerm) {
            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(cleanPhone, null, message, null, null)
                }
                LogBus.log("SendSmsHandler: Sent SMS directly hands-free to '$displayName' ($cleanPhone)")
                return SkillResult(true, "✉️ **SMS enviado manos libres** a **$displayName** ($cleanPhone): \"$message\"")
            } catch (e: Exception) {
                LogBus.warn("SendSmsHandler: Direct SMS send failed: ${e.message}, falling back to Intent")
            }
        }

        return try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(cleanPhone)}")).apply {
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            SkillResult(true, "✉️ **Abriendo SMS** para **$displayName** ($cleanPhone) con el texto: \"$message\"")
        } catch (e: Exception) {
            LogBus.error("SendSmsHandler: Could not launch SMS composer", e)
            SkillResult(false, "Error al enviar mensaje de texto: ${e.message}")
        }
    }
}
