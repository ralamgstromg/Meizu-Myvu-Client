package com.myvu.client.skills.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.myvu.client.core.ContactHelper
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

/**
 * Hands-Free Email Composition Handler:
 * Resolves recipient contact names to email addresses from ContactsContract,
 * and launches default email client (Gmail, Outlook, etc.) with subject and body prefilled.
 */
class SendEmailHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val to = args.optString("to", "").trim()
        val subject = args.optString("subject", "").trim()
        val body = args.optString("body", "").trim()

        if (to.isEmpty()) {
            return SkillResult(false, "Falta especificar el destinatario de correo.")
        }

        // 1. Resolve contact name to email if needed
        val resolved = ContactHelper.resolveContactEmail(context, to)
        val emailAddress = resolved?.first ?: to
        val displayName = resolved?.second ?: to

        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$emailAddress")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(emailIntent)
            val subText = if (subject.isNotBlank()) " con asunto '$subject'" else ""
            SkillResult(true, "✉️ **Abriendo correo** para **$displayName** ($emailAddress)$subText.")
        } catch (e: Exception) {
            LogBus.error("SendEmailHandler: Error launching email client", e)
            SkillResult(false, "No se pudo abrir la aplicación de correo para '$displayName': ${e.message}")
        }
    }
}
