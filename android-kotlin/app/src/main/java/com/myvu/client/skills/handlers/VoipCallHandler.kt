package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.ai.PhoneActionExecutor
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

/**
 * Skill Handler for VoIP Calls:
 * Dispatches voice calls to WhatsApp, Microsoft Teams, or Google Chat / Meet.
 */
class VoipCallHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val platform = args.optString("platform", "whatsapp").lowercase().trim()
        val contact = args.optString("contact", "").ifBlank {
            args.optString("contact_or_number", "")
        }.trim()

        if (contact.isBlank()) {
            return SkillResult(false, "Falta especificar el contacto para la llamada.")
        }

        val executor = PhoneActionExecutor(context)

        return when {
            platform.contains("whatsapp") || platform.contains("wasap") -> {
                executor.makeWhatsAppCall(contact)
                SkillResult(true, "📞 **Llamando por WhatsApp** a **$contact**...")
            }
            platform.contains("teams") -> {
                executor.makeTeamsCall(contact)
                SkillResult(true, "📞 **Iniciando llamada de Microsoft Teams** con **$contact**...")
            }
            platform.contains("google") || platform.contains("meet") || platform.contains("chat") -> {
                executor.makeGoogleChatCall(contact)
                SkillResult(true, "📞 **Iniciando llamada por Google Chat/Meet** con **$contact**...")
            }
            else -> {
                executor.makeCall(contact)
                SkillResult(true, "📞 **Llamando** a **$contact**...")
            }
        }
    }
}
