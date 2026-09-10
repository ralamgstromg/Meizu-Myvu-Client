package com.myvu.client.app.feature

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProtocolTest {

    @Test
    fun testAssistantConfigDefaultsAreSafeAndBatteryFriendly() {
        val jsonStr = AiProtocol.assistantConfig()
        val json = JSONObject(jsonStr)

        assertEquals(AiProtocol.CODE_ASSISTANT_CONFIG, json.getInt("code"))
        val payload = json.getJSONObject("payload")

        // Continuous dialogue (active listening) MUST be disabled by default to save battery
        assertFalse("Continuous dialogue should be disabled by default", payload.getBoolean("isContinuousDialogueEnable"))

        // Wakeup / wake word MUST be disabled by default to save battery
        assertFalse("Low power wakeup should be disabled by default", payload.getBoolean("isLowPowerWakeupEnable"))
        assertFalse("Screen off wakeup should be disabled by default", payload.getBoolean("isLowPowerWakeupScreenOffEnable"))

        // Standard flags
        assertTrue(payload.getBoolean("isAsrResultScreenEnable"))
        assertTrue(payload.getBoolean("isChatGptCardDisplayEnable"))
        assertTrue(payload.getBoolean("isChatGptTTSPlayEnable"))
        assertTrue(payload.getBoolean("isNetworkAvailable"))
        assertFalse(payload.getBoolean("isWakeupVoiceRecording"))
        assertEquals(0, payload.getInt("ttsTimbreValue"))
    }

    @Test
    fun testAssistantConfigExplicitFlags() {
        val bothEnabled = JSONObject(AiProtocol.assistantConfig(lowPowerWakeupEnabled = true, continuousDialogueEnabled = true))
        val payloadBoth = bothEnabled.getJSONObject("payload")
        assertTrue(payloadBoth.getBoolean("isLowPowerWakeupEnable"))
        assertTrue(payloadBoth.getBoolean("isLowPowerWakeupScreenOffEnable"))
        assertTrue(payloadBoth.getBoolean("isContinuousDialogueEnable"))

        val onlyContinuous = JSONObject(AiProtocol.assistantConfig(lowPowerWakeupEnabled = false, continuousDialogueEnabled = true))
        val payloadContinuous = onlyContinuous.getJSONObject("payload")
        assertFalse(payloadContinuous.getBoolean("isLowPowerWakeupEnable"))
        assertTrue(payloadContinuous.getBoolean("isContinuousDialogueEnable"))

        val onlyWakeup = JSONObject(AiProtocol.assistantConfig(lowPowerWakeupEnabled = true, continuousDialogueEnabled = false))
        val payloadWakeup = onlyWakeup.getJSONObject("payload")
        assertTrue(payloadWakeup.getBoolean("isLowPowerWakeupEnable"))
        assertFalse(payloadWakeup.getBoolean("isContinuousDialogueEnable"))
    }
}
