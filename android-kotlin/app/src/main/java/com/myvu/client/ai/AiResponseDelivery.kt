package com.myvu.client.ai

import com.myvu.client.app.feature.AiProtocol
import com.myvu.client.core.LogBus

/** Delivers one final AI response as HUD text and, optionally, speech. */
class AiResponseDelivery(
    private val sender: (String) -> Unit,
    private val tts: Speaker,
    private val isSessionActive: (String) -> Boolean,
    private val onFinished: (Boolean) -> Unit,
    private val mode: AiResponseMode = AiResponseMode.VOICE_AND_VISUAL,
    private val modeProvider: (() -> AiResponseMode)? = null
) {
    interface Speaker {
        fun speak(text: String, callback: (Boolean) -> Unit)
        fun stop()
    }

    private var deliveredSessionId: String? = null
    private var turnFinished = false
    private var playbackStarted = false
    private var playbackEnded = false
    private var cancelled = false
    private var ttsRequested = false

        private fun cleanResponseText(raw: String): String {
            var cleaned = raw
            if (cleaned.contains("Respuesta local Gemma")) {
                cleaned = cleaned.substringAfter("para: ").trim()
            }
            if (cleaned.contains("[Contexto del Sistema:")) {
                cleaned = cleaned.substringAfter("] ").trim()
            }
            return cleaned.ifEmpty { raw }
        }

        fun deliver(response: AiResponse): Boolean {
        if (!isSessionActive(response.sessionId)) {
            LogBus.trace("AI response ignored -- stale session ${response.sessionId}")
            return false
        }
        if (deliveredSessionId == response.sessionId) {
            LogBus.trace("AI response ignored -- duplicate session ${response.sessionId}")
            return false
        }
        deliveredSessionId = response.sessionId
        turnFinished = false
        playbackStarted = false
        playbackEnded = false
        cancelled = false
        ttsRequested = false

        val rawText = response.normalizedText
        val text = cleanResponseText(rawText)
        val selectedMode = modeProvider?.invoke() ?: mode
        LogBus.log("AI_RESPONSE_MODE_SELECTED sessionId=${response.sessionId} mode=$selectedMode")
        val speak = response.shouldSpeak && selectedMode != AiResponseMode.VISUAL_ONLY
        val visual = selectedMode != AiResponseMode.VOICE_ONLY
        if (visual) {
            sender(AiProtocol.chatAnswer(response.sessionId, text, response.baseStatus))
            LogBus.log("AI_TEXT_SENT sessionId=${response.sessionId} source=${response.source} answerLength=${text.length}")
        }

        if (!speak) {
            finishTurn(true)
            return true
        }

        playbackStarted = true
        ttsRequested = true
        sender(AiProtocol.playState(AiProtocol.PLAY_STATE_START))
        LogBus.log("AI_TTS_REQUESTED sessionId=${response.sessionId} mode=$mode answerLength=${text.length}")
        tts.speak(text) { success ->
            if (playbackEnded || cancelled) return@speak
            playbackEnded = true
            sender(AiProtocol.playState(AiProtocol.PLAY_STATE_END))
            LogBus.log("AI_TTS_FINISHED sessionId=${response.sessionId} success=$success")
            finishTurn(success)
        }
        return true
    }

    fun cancel() {
        cancelled = true
        tts.stop()
        if (deliveredSessionId == null || turnFinished) return
        if (playbackStarted && !playbackEnded) {
            playbackEnded = true
            sender(AiProtocol.playState(AiProtocol.PLAY_STATE_END))
        }
        finishTurn(false)
    }

    private fun finishTurn(success: Boolean) {
        if (turnFinished) return
        turnFinished = true
        sender(AiProtocol.endTurn())
        LogBus.log("AI_TURN_FINISHED sessionId=${deliveredSessionId} success=$success")
        onFinished(success)
    }
}
