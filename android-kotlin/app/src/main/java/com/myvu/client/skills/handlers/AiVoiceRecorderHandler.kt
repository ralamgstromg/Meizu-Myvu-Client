package com.myvu.client.skills.handlers

import android.content.Context
import android.content.Intent
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import com.myvu.client.ui.VoiceRecorderActivity
import org.json.JSONObject

class AiVoiceRecorderHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val intent = Intent(context, VoiceRecorderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            SkillResult(true, "Abriendo Grabadora de Voz IA con transcripción Whisper")
        } catch (e: Exception) {
            LogBus.error("AiVoiceRecorderHandler: Failed to launch VoiceRecorderActivity", e)
            SkillResult(false, "No se pudo abrir la Grabadora de Voz IA: ${e.message}")
        }
    }
}
