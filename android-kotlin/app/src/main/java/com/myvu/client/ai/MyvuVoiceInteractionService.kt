package com.myvu.client.ai

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.myvu.client.core.LogBus

/**
 * Android Digital Assistant Service integration for MYVU.
 * Allows MYVU Client to be selected as the System Default Assistant App in Android Settings,
 * providing system-level voice interaction privileges over the lock screen.
 */
class MyvuVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        LogBus.log("MyvuVoiceInteractionService ready (System Digital Assistant mode)")
    }
}

class MyvuVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return MyvuVoiceInteractionSession(this)
    }
}

class MyvuVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        super.onHandleAssist(data, structure, content)
        LogBus.log("MyvuVoiceInteractionSession: Assistant triggered by system")
    }
}

class MyvuRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        LogBus.log("MyvuRecognitionService: Recognition start requested")
    }

    override fun onCancel(listener: Callback?) {
        LogBus.log("MyvuRecognitionService: Recognition cancel requested")
    }

    override fun onStopListening(listener: Callback?) {
        LogBus.log("MyvuRecognitionService: Recognition stop requested")
    }
}
