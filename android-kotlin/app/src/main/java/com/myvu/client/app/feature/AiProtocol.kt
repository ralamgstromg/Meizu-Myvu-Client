package com.myvu.client.app.feature

import com.myvu.client.app.AppLayer
import org.json.JSONException
import org.json.JSONObject

/**
 * The AI-assistant wire protocol (com.xjsd.ai.assistant.protocol).
 */
object AiProtocol {
    const val PKG: String = AppLayer.PKG_AI

    // ---- codes (CmdCode.java) ----
    const val CODE_ASSISTANT_CONFIG: Int = 2    // phone -> glasses: capability flags
    const val CODE_START_VR_REQ: Int = 3        // glasses -> phone: button
    const val CODE_START_VR_RES: Int = 4        // phone -> glasses: session ack
    const val CODE_TTS_PLAY_RES: Int = 6        // phone -> glasses: play state
    const val CODE_VOICE_WAKEUP_VR_REQ: Int = 7 // glasses -> phone: wake word
    const val CODE_ASR_TRANS: Int = 101         // phone -> glasses: caption
    const val CODE_VUI: Int = 102               // phone -> glasses: open LLM scene
    const val CODE_VAD_EVENT: Int = 104         // phone -> glasses: speech bounds
    const val CODE_SYNC_VR_STATE: Int = 106     // phone -> glasses: VrState
    const val CODE_HOT_WORD_MANAGER: Int = 107  // phone -> glasses: end of turn
    const val CODE_RECORD_DATA_TRANS: Int = 109 // glasses -> phone: mic audio
    const val CODE_CHAT_GPT_RESPONSE: Int = 122 // phone -> glasses: answer text

    // ---- VrState values (protocol/VrState.java) ----
    const val VR_CLOSE: Int = 0
    const val VR_MULTI_WAKEUP: Int = 1
    const val VR_TTS_PLAY_START: Int = 3
    const val VR_TTS_PLAY_END: Int = 4
    const val VR_PROCESSION: Int = 7
    const val VR_LISTENING_TIMEOUT: Int = 8

    // ---- TTS play states ----
    const val PLAY_STATE_START: Int = 1
    const val PLAY_STATE_END: Int = 2

    /** The glasses' own listening timeout, armed by code:4. */
    const val TIMEOUT_LISTENING_MS: Long = 8000

    @JvmStatic
    @JvmOverloads
    fun assistantConfig(lowPowerWakeupEnabled: Boolean = false): String {
        try {
            return message(
                CODE_ASSISTANT_CONFIG, JSONObject()
                    .put("hasWakeupVoicePrint", false)
                    .put("isAsrResultScreenEnable", true)
                    .put("isChatGptCardDisplayEnable", true)
                    .put("isChatGptTTSPlayEnable", true)
                    .put("isContinuousDialogueEnable", true)
                    .put("isLowPowerWakeupEnable", lowPowerWakeupEnabled)
                    .put("isLowPowerWakeupScreenOffEnable", lowPowerWakeupEnabled)
                    .put("isNetworkAvailable", true)
                    .put("isWakeupVoiceRecording", false)
                    .put("ttsTimbreValue", 0)
            )
        } catch (e: JSONException) {
            throw IllegalStateException(e)
        }
    }

    private fun message(code: Int, payload: Any): String {
        try {
            return JSONObject().put("code", code).put("payload", payload).toString()
        } catch (e: JSONException) {
            throw IllegalStateException("AI payload could not be built", e)
        }
    }

    @JvmStatic
    fun sessionAck(sessionId: String): String {
        try {
            return message(
                CODE_START_VR_RES, JSONObject()
                    .put("hasNetwork", true)
                    .put("message", "唤醒成功")
                    .put("sessionId", sessionId)
                    .put("success", true)
            )
        } catch (e: JSONException) {
            throw IllegalStateException(e)
        }
    }

    @JvmStatic
    fun vadStart(sessionId: String): String = vadEvent(1, sessionId)

    @JvmStatic
    fun vadEnd(sessionId: String): String = vadEvent(2, sessionId)

    private fun vadEvent(type: Int, sessionId: String): String {
        try {
            return message(
                CODE_VAD_EVENT, JSONObject()
                    .put("type", type)
                    .put("sessionId", sessionId)
            )
        } catch (e: JSONException) {
            throw IllegalStateException(e)
        }
    }

    @JvmStatic
    fun asrResult(sessionId: String, text: String, isFinal: Boolean): String {
        try {
            return message(
                CODE_ASR_TRANS, JSONObject()
                    .put("id", sessionId)
                    .put("isOfflineResult", false)
                    .put("text", text)
                    .put("type", if (isFinal) 1 else 0)
            )
        } catch (e: JSONException) {
            throw IllegalStateException(e)
        }
    }

    @JvmStatic
    fun chatQuery(sessionId: String, query: String): String {
        try {
            val emptyUtterance = JSONObject()
                .put("id", "")
                .put("screen", "")
                .put("speech", "")
            return message(
                CODE_VUI, JSONObject()
                    .put("header", JSONObject()
                        .put("name", "default")
                        .put("namespace", "llm")
                        .put("specialCmdInChatGptScene", false))
                    .put("metadata", JSONObject().put("msgId", ""))
                    .put("payload", JSONObject()
                        .put("isSoundOpened", true)
                        .put("query", query)
                        .put("isNextRecorded", false)
                        .put("utterance", JSONObject()
                            .put("speech", "")
                            .put("screen", "")
                            .put("id", "")))
                    .put("source", 0)
                    .put("utterance", emptyUtterance)
                    .put("sessionId", sessionId)
            )
        } catch (e: JSONException) {
            throw IllegalStateException(e)
        }
    }

    @JvmStatic
    fun chatAnswer(sessionId: String, answer: String, baseStatus: Int): String {
        try {
            return message(
                CODE_CHAT_GPT_RESPONSE, JSONObject()
                    .put("answer", answer)
                    .put("base_status", baseStatus)
                    .put("isCmd", false)
                    .put("sessionId", sessionId)
                    .put("version_code", "")
            )
        } catch (e: JSONException) {
            throw IllegalStateException(e)
        }
    }

    @JvmStatic
    fun playState(state: Int): String {
        try {
            return message(
                CODE_TTS_PLAY_RES, JSONObject()
                    .put("id", "")
                    .put("isContinuous", false)
                    .put("isMulti", false)
                    .put("isWakeup", false)
                    .put("playState", state)
            )
        } catch (e: JSONException) {
            throw IllegalStateException(e)
        }
    }

    @JvmStatic
    fun vrState(state: Int): String = message(CODE_SYNC_VR_STATE, state)

    @JvmStatic
    fun endTurn(): String {
        try {
            return message(
                CODE_HOT_WORD_MANAGER, JSONObject()
                    .put("control", 4)
                    .put("isOffline", false)
            )
        } catch (e: JSONException) {
            throw IllegalStateException(e)
        }
    }
}
