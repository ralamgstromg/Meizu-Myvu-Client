package com.myvu.client.ai

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import com.myvu.client.core.LogBus

/**
 * Delegates voice queries directly to the Gemini App or native Android Assistant.
 */
class AndroidAssistantClient(context: Context) : AiClient {

    private val context: Context = context.applicationContext

    override fun isConfigured(): Boolean = true

    override fun ask(question: String): String {
        // 1. Dispatch KEYCODE_VOICE_ASSIST to system AudioManager.
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null) {
                val now = SystemClock.uptimeMillis()
                val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, 0)
                val up = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOICE_ASSIST, 0)
                am.dispatchMediaKeyEvent(down)
                am.dispatchMediaKeyEvent(up)
                LogBus.log("dispatched KEYCODE_VOICE_ASSIST for system Voice Assistant (Google/Gemini)")
            }
        } catch (e: Exception) {
            LogBus.warn("could not dispatch KEYCODE_VOICE_ASSIST: ${e.message}")
        }

        // 2. Launch Google Assistant voice command Intent as fallback
        try {
            val voiceIntent = Intent(Intent.ACTION_VOICE_COMMAND)
            voiceIntent.setPackage("com.google.android.googlequicksearchbox")
            if (question.isNotBlank()) {
                voiceIntent.putExtra(SearchManager.QUERY, question)
                voiceIntent.putExtra("query", question)
            }
            voiceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(voiceIntent)
            LogBus.log("launched Google Assistant voice command for: $question")
            return "Activando Asistente de Google / Gemini por defecto..."
        } catch (e: Exception) {
            LogBus.warn("could not launch Google Assistant voice command: ${e.message}")
        }

        // 3. Fallback to generic ACTION_VOICE_COMMAND
        try {
            val voiceIntent = Intent(Intent.ACTION_VOICE_COMMAND)
            voiceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(voiceIntent)
            LogBus.log("launched generic ACTION_VOICE_COMMAND")
            return "Abriendo Asistente de Voz..."
        } catch (e: Exception) {
            LogBus.warn("could not launch generic ACTION_VOICE_COMMAND: ${e.message}")
        }

        return "No se pudo abrir el Asistente de Android."
    }
}
