package com.myvu.client.skills.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

class SendEmailHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val to = args.optString("to", "").trim()
        val subject = args.optString("subject", "").trim()
        val body = args.optString("body", "").trim()

        if (to.isEmpty()) {
            return SkillResult(false, "Falta especificar el destinatario de correo.")
        }

        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$to")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(emailIntent)
            SkillResult(true, "Abriendo correo para $to con asunto: '$subject'")
        } catch (e: Exception) {
            LogBus.error("SendEmailHandler: Error launching email client", e)
            SkillResult(false, "No se pudo abrir la aplicación de correo: ${e.message}")
        }
    }
}
