package com.myvu.client.app

import com.myvu.client.app.feature.GlassGesture
import com.myvu.client.app.feature.TouchGestureManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONObject

class PhysicalActionButtonConflictTest {

    private class RecordingActionExecutor : TouchGestureManager.ActionExecutor {
        val actions = mutableListOf<String>()

        override fun executeAiAssistant(code: Int) {
            actions.add("AI_$code")
        }

        override fun executeGeminiAssistant() {
            actions.add("GEMINI")
        }

        override fun executeGeminiLive() {
            actions.add("GEMINI_LIVE")
        }

        override fun executePhoneAssistant() {
            actions.add("PHONE_ASSISTANT")
        }

        override fun executeHudDashboard() {
            actions.add("HUD_DASHBOARD")
        }

        override fun executeVoiceAgentAura() {
            actions.add("VOICE_AURA")
        }

        override fun executeNone() {
            actions.add("NONE")
        }
    }

    private lateinit var executor: RecordingActionExecutor
    private lateinit var router: InboundRouter
    private val sentPackets = mutableListOf<String>()

    @Before
    fun setUp() {
        TouchGestureManager.resetDebounceForTesting()
        executor = RecordingActionExecutor()
        sentPackets.clear()
        router = InboundRouter { actionJson, _, _ ->
            sentPackets.add(actionJson)
        }
    }

    @Test
    fun physicalAiTriggerSuppressesTrailingTempleTouchEvent() {
        var simulatedTime = 1000L
        TouchGestureManager.timeProvider = { simulatedTime }

        var aiTriggered = 0
        router.setAiTriggerListener { code, _ ->
            if (code == 3) {
                aiTriggered++
            }
        }

        router.setTouchGestureListener { gestureType, rawCode, _, eventTime ->
            TouchGestureManager.handleGesture(null, gestureType, rawCode, executor, eventTime)
        }

        // 1. Glasses send Flyme XR AI button trigger (code 3)
        val aiTriggerJson = "{\"action\":\"ai_assistant\",\"code\":3,\"data\":{\"control\":1}}"
        router.handle(aiTriggerJson)

        assertEquals(1, aiTriggered)
        assertEquals(1000L, TouchGestureManager.lastPhysicalButtonTime)

        // 2. Firmware concurrently sends key_event 211 (temple double tap) within 200ms
        simulatedTime = 1200L
        val trailingKeyJson = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\",\"value\":[" +
                "{\"_action_value_\":\"key_event\",\"_event_attr_value_\":{\"down_or_up\":\"1\",\"key_code\":\"211\",\"key_event_sender\":4,\"key_event_time\":1789075058200},\"_event_id_\":\"key_event\"}" +
                "]}}"
        router.handle(trailingKeyJson)

        // Trailing key event MUST be suppressed -> zero actions executed by executor!
        assertEquals(0, executor.actions.size)
    }

    @Test
    fun actionButtonCode230TriggersHudDashboardOnlyAndDoesNotAccumulateTaps() {
        var simulatedTime = 2000L
        TouchGestureManager.timeProvider = { simulatedTime }

        router.setTouchGestureListener { gestureType, rawCode, _, eventTime ->
            TouchGestureManager.handleGesture(null, gestureType, rawCode, executor, eventTime)
        }

        // Action button press (code 230)
        val buttonPressJson = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\",\"value\":[" +
                "{\"_action_value_\":\"key_event\",\"_event_attr_value_\":{\"down_or_up\":\"1\",\"key_code\":\"230\",\"key_event_sender\":4,\"key_event_time\":1789075058000},\"_event_id_\":\"key_event\"}" +
                "]}}"
        router.handle(buttonPressJson)

        // Must execute HUD_DASHBOARD only
        assertEquals(listOf("HUD_DASHBOARD"), executor.actions)

        // Trailing tap within suppression window is ignored
        simulatedTime = 2300L
        val tapJson = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\",\"value\":[" +
                "{\"_action_value_\":\"key_event\",\"_event_attr_value_\":{\"down_or_up\":\"1\",\"key_code\":\"210\",\"key_event_sender\":4,\"key_event_time\":1789075058300},\"_event_id_\":\"key_event\"}" +
                "]}}"
        router.handle(tapJson)

        // Still only 1 action
        assertEquals(listOf("HUD_DASHBOARD"), executor.actions)
    }

    @Test
    fun templeTouchpadGesturesWorkNormallyOutsideSuppressionWindow() {
        var simulatedTime = 5000L
        TouchGestureManager.timeProvider = { simulatedTime }

        router.setTouchGestureListener { gestureType, rawCode, _, eventTime ->
            TouchGestureManager.handleGesture(null, gestureType, rawCode, executor, eventTime)
        }

        // Temple double tap (code 211) arrives without prior physical button press
        val doubleTapJson = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\",\"value\":[" +
                "{\"_action_value_\":\"key_event\",\"_event_attr_value_\":{\"down_or_up\":\"1\",\"key_code\":\"211\",\"key_event_sender\":4,\"key_event_time\":1789075058000},\"_event_id_\":\"key_event\"}" +
                "]}}"
        router.handle(doubleTapJson)

        // With null context, default double tap executes GEMINI
        assertEquals(listOf("GEMINI"), executor.actions)
    }

    @Test
    fun physicalButtonCode202WithDown200ConsolidatesAndTriggersHudDashboardOnly() {
        var simulatedTime = 6000L
        TouchGestureManager.timeProvider = { simulatedTime }

        router.setTouchGestureListener { gestureType, rawCode, _, eventTime ->
            TouchGestureManager.handleGesture(null, gestureType, rawCode, executor, eventTime)
        }

        // Real-world packet from glasses when physical button is pressed once:
        // [ { key_code: 200, sender: 2 }, { key_code: 202, sender: 2 } ]
        val realWorldButtonJson = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\",\"value\":[" +
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"key_event\",\"_event_attr_value_\":{\"key_code\":\"200\",\"down_or_up\":1,\"key_event_sender\":2,\"key_event_time\":1789267391000}}," +
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"key_event\",\"_event_attr_value_\":{\"key_code\":\"202\",\"down_or_up\":1,\"key_event_sender\":2,\"key_event_time\":1789267391000}}" +
                "]}}"
        router.handle(realWorldButtonJson)

        // 1. Code 200 (Down) MUST be consolidated and discarded
        // 2. Code 202 MUST resolve to ACTION_BUTTON and trigger HUD_DASHBOARD
        // 3. MUST NOT trigger PHONE_ASSISTANT or DOUBLE_TAP!
        assertEquals(listOf("HUD_DASHBOARD"), executor.actions)
    }
}
