package com.myvu.client.app

import com.myvu.client.app.feature.GlassGesture
import com.myvu.client.app.feature.TouchGestureManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.ArrayList

class InboundGestureTest {

    private data class GestureEvent(
        val gesture: GlassGesture,
        val code: Int,
        val name: String
    )

    private val receivedGestures = ArrayList<GestureEvent>()
    private lateinit var router: InboundRouter

    @Before
    fun setUp() {
        TouchGestureManager.resetDebounceForTesting()
        receivedGestures.clear()
        router = InboundRouter(object : InboundRouter.Sender {
            override fun send(actionJson: String, targetPkg: String, sourcePkg: String) {}
        })
        router.setTouchGestureListener { gestureType, rawCode, gestureName ->
            receivedGestures.add(GestureEvent(gestureType, rawCode, gestureName))
        }
    }

    @Test
    fun decodesSingleTouchGestureFromArray() {
        val json = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\"," +
                "\"value\":[{\"_event_type_\":\"action\",\"action_name\":\"touch_gesture\",\"action_value\":1}]}}"
        router.handle(json)

        assertEquals(1, receivedGestures.size)
        assertEquals(GlassGesture.TAP, receivedGestures[0].gesture)
        assertEquals(1, receivedGestures[0].code)
        assertEquals("touch_gesture", receivedGestures[0].name)
    }

    @Test
    fun decodesDoubleTapGesture() {
        val json = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\"," +
                "\"value\":[{\"_event_type_\":\"action\",\"action_name\":\"touch_gesture\",\"action_value\":2}]}}"
        router.handle(json)

        assertEquals(1, receivedGestures.size)
        assertEquals(GlassGesture.DOUBLE_TAP, receivedGestures[0].gesture)
        assertEquals(2, receivedGestures[0].code)
    }

    @Test
    fun decodesTripleTapAndLongPress() {
        val json = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\"," +
                "\"value\":[{\"action_name\":\"triple_tap\",\"action_value\":3}," +
                "{\"action_name\":\"long_press\",\"action_value\":4}]}}"
        router.handle(json)

        assertEquals(2, receivedGestures.size)
        assertEquals(GlassGesture.TRIPLE_TAP, receivedGestures[0].gesture)
        assertEquals(3, receivedGestures[0].code)
        assertEquals(GlassGesture.LONG_PRESS, receivedGestures[1].gesture)
        assertEquals(4, receivedGestures[1].code)
    }

    @Test
    fun decodesSwipeForwardAndBackward() {
        val json = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\"," +
                "\"value\":[{\"action_name\":\"swipe_forward\",\"action_value\":5}," +
                "{\"action_name\":\"swipe_backward\",\"action_value\":6}]}}"
        router.handle(json)

        assertEquals(2, receivedGestures.size)
        assertEquals(GlassGesture.SWIPE_FORWARD, receivedGestures[0].gesture)
        assertEquals(5, receivedGestures[0].code)
        assertEquals(GlassGesture.SWIPE_BACKWARD, receivedGestures[1].gesture)
        assertEquals(6, receivedGestures[1].code)
    }

    @Test
    fun decodesValueAsJsonEncodedString() {
        val json = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\"," +
                "\"value\":\"[{\\\"_event_type_\\\":\\\"action\\\",\\\"action_name\\\":\\\"touch_gesture\\\",\\\"action_value\\\":2}]\"}}"
        router.handle(json)

        assertEquals(1, receivedGestures.size)
        assertEquals(GlassGesture.DOUBLE_TAP, receivedGestures[0].gesture)
        assertEquals(2, receivedGestures[0].code)
    }

    @Test
    fun decodesDirectSyncGlassEventAction() {
        val json = "{\"action\":\"sync_glass_event\",\"value\":[{\"action_name\":\"touch_gesture\",\"action_value\":1}]}"
        router.handle(json)

        assertEquals(1, receivedGestures.size)
        assertEquals(GlassGesture.TAP, receivedGestures[0].gesture)
        assertEquals(1, receivedGestures[0].code)
    }

    @Test
    fun ignoresUnrelatedEventTrackingActions() {
        val json = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"some_other_telemetry\",\"value\":[]}}"
        router.handle(json)

        assertTrue(receivedGestures.isEmpty())
    }

    @Test
    fun glassesEventHandlerWiresTouchGestureToManager() {
        var pageClosedCalled = false
        var wakeCalled = false
        var lastTriggerCode = -1

        val delegate = object : GlassesEventHandler.Delegate {
            override fun wakeRelay() { wakeCalled = true }
            override fun triggerAi(triggerCode: Int) { lastTriggerCode = triggerCode }
            override fun pageClosed() { pageClosedCalled = true }
            override fun refreshWeather() {}
            override fun updateBattery(battery: Int, isCharging: Boolean) {}
            override fun sendAction(actionJson: String) {}
        }

        val handler = GlassesEventHandler(null, router, delegate)
        TouchGestureManager.resetDebounceForTesting()
        val json = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\"," +
                "\"value\":[{\"action_name\":\"long_press\",\"action_value\":4}]}}"
        router.handle(json)

        assertEquals(4, lastTriggerCode)
    }

    @Test
    fun decodesFlymeArUnderscoredTelemetry() {
        val json = "{\"_action_name_\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\"," +
                "\"value\":[{\"_event_type_\":\"action\",\"_action_name_\":\"slip_forward\",\"_action_value_\":5}," +
                "{\"_event_type_\":\"action\",\"_event_name_\":\"slide_backward\",\"_event_value_\":6}]}}"
        router.handle(json)

        assertEquals(2, receivedGestures.size)
        assertEquals(GlassGesture.SWIPE_FORWARD, receivedGestures[0].gesture)
        assertEquals(5, receivedGestures[0].code)
        assertEquals(GlassGesture.SWIPE_BACKWARD, receivedGestures[1].gesture)
        assertEquals(6, receivedGestures[1].code)
    }

    @Test
    fun decodesTrackpadHardwareKeycodes() {
        val json = "{\"action\":\"sync_glass_event\",\"value\":[" +
                "{\"code\":19}," +
                "{\"code\":20}," +
                "{\"code\":21}," +
                "{\"code\":22}" +
                "]}"
        router.handle(json)

        assertEquals(4, receivedGestures.size)
        assertEquals(GlassGesture.SWIPE_FORWARD, receivedGestures[0].gesture)
        assertEquals(19, receivedGestures[0].code)
        assertEquals(GlassGesture.SWIPE_BACKWARD, receivedGestures[1].gesture)
        assertEquals(20, receivedGestures[1].code)
        assertEquals(GlassGesture.SWIPE_BACKWARD, receivedGestures[2].gesture)
        assertEquals(21, receivedGestures[2].code)
        assertEquals(GlassGesture.SWIPE_FORWARD, receivedGestures[3].gesture)
        assertEquals(22, receivedGestures[3].code)
    }

    @Test
    fun glassesEventHandlerRoutesAiTriggerCode3DirectlyToAi() {
        var lastTriggerCode = -1

        val delegate = object : GlassesEventHandler.Delegate {
            override fun wakeRelay() {}
            override fun triggerAi(triggerCode: Int) { lastTriggerCode = triggerCode }
            override fun pageClosed() {}
            override fun refreshWeather() {}
            override fun updateBattery(battery: Int, isCharging: Boolean) {}
            override fun sendAction(actionJson: String) {}
        }

        val handler = GlassesEventHandler(null, router, delegate)
        TouchGestureManager.resetDebounceForTesting()

        // code 3 hardware trigger from glasses physical button
        val json = "{\"code\":3,\"payload\":{\"control\":1}}"
        router.handle(json)

        // Must directly trigger AI model pipeline (STT -> Configured Model)
        assertEquals(3, lastTriggerCode)
    }

    @Test
    fun ignoresNonGestureSystemTelemetryEvents() {
        // Real-world telemetry packets from glasses that are NOT touch gestures
        val json = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\"," +
                "\"value\":[" +
                "{\"_event_type_\":\"action\",\"name\":\"suspend_stats\"}," +
                "{\"_event_type_\":\"action\",\"name\":\"iot_screen_status_change\"}," +
                "{\"_event_type_\":\"action\",\"name\":\"iot_sys_usages\"}," +
                "{\"_event_type_\":\"action\",\"name\":\"battery_stats\"}," +
                "{\"_event_type_\":\"action\",\"name\":\"iot_notification_reminder\"}," +
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"iot_voice_wakeup\"}," +
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"iot_voice_quit\"}," +
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"iot_a2dp_status_change\"}," +
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"audio_stats\"}," +
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"air_starrynet_bt\"}" +
                "]}}"
        router.handle(json)

        // None of these should be dispatched as touch gestures!
        assertTrue(receivedGestures.isEmpty())
    }

    @Test
    fun decodesTempleKeyEventsWithAllSendersAndKeyCodes() {
        val json = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\",\"value\":[" +
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"key_event\",\"_event_attr_value_\":{\"key_code\":\"210\",\"down_or_up\":1,\"key_event_sender\":1}}," + // Temple Tap (sender 1)
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"key_event\",\"_event_attr_value_\":{\"key_code\":\"211\",\"down_or_up\":1,\"key_event_sender\":1}}," + // Temple Double Tap (sender 1)
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"key_event\",\"_event_attr_value_\":{\"key_code\":\"212\",\"down_or_up\":1,\"key_event_sender\":1}}," + // Temple Long Press (sender 1)
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"key_event\",\"_event_attr_value_\":{\"key_code\":\"206\",\"down_or_up\":1,\"key_event_sender\":1}}," + // Temple Swipe Forward (sender 1)
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"key_event\",\"_event_attr_value_\":{\"key_code\":\"207\",\"down_or_up\":1,\"key_event_sender\":1}}," + // Temple Swipe Backward (sender 1)
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"key_event\",\"_event_attr_value_\":{\"key_code\":\"210\",\"down_or_up\":1,\"key_event_sender\":4}}," + // Phonepad Tap (sender 4)
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"key_event\",\"_event_attr_value_\":{\"key_code\":\"211\",\"down_or_up\":1,\"key_event_sender\":4}}," + // Phonepad Double Tap (sender 4)
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"key_event\",\"_event_attr_value_\":{\"key_code\":\"212\",\"down_or_up\":1,\"key_event_sender\":4}}," + // Phonepad Long Press (sender 4)
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"key_event\",\"_event_attr_value_\":{\"key_code\":\"200\",\"down_or_up\":1,\"key_event_sender\":2}}," + // Temple Tap (sender 2)
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"key_event\",\"_event_attr_value_\":{\"key_code\":\"201\",\"down_or_up\":1,\"key_event_sender\":2}}," + // Temple Swipe Forward (sender 2)
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"key_event\",\"_event_attr_value_\":{\"key_code\":\"202\",\"down_or_up\":1,\"key_event_sender\":2}}," + // Temple Double Tap (sender 2)
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"key_event\",\"_event_attr_value_\":{\"key_code\":\"237\",\"down_or_up\":1,\"key_event_sender\":2}}," + // Temple Swipe Backward (sender 2)
                "{\"_event_type_\":\"action_x\",\"_event_name_\":\"key_event\",\"_event_attr_value_\":{\"key_code\":\"210\",\"down_or_up\":0,\"key_event_sender\":1}}" +  // Release keyup - ignored
                "]}}"
        router.handle(json)

        assertEquals(12, receivedGestures.size)
        assertEquals(GlassGesture.TAP, receivedGestures[0].gesture)
        assertEquals(210, receivedGestures[0].code)
        assertEquals(GlassGesture.DOUBLE_TAP, receivedGestures[1].gesture)
        assertEquals(211, receivedGestures[1].code)
        assertEquals(GlassGesture.LONG_PRESS, receivedGestures[2].gesture)
        assertEquals(212, receivedGestures[2].code)
        assertEquals(GlassGesture.SWIPE_FORWARD, receivedGestures[3].gesture)
        assertEquals(206, receivedGestures[3].code)
        assertEquals(GlassGesture.SWIPE_BACKWARD, receivedGestures[4].gesture)
        assertEquals(207, receivedGestures[4].code)
        assertEquals(GlassGesture.TAP, receivedGestures[5].gesture)
        assertEquals(210, receivedGestures[5].code)
        assertEquals(GlassGesture.DOUBLE_TAP, receivedGestures[6].gesture)
        assertEquals(211, receivedGestures[6].code)
        assertEquals(GlassGesture.LONG_PRESS, receivedGestures[7].gesture)
        assertEquals(212, receivedGestures[7].code)
        assertEquals(GlassGesture.TAP, receivedGestures[8].gesture)
        assertEquals(200, receivedGestures[8].code)
        assertEquals(GlassGesture.SWIPE_FORWARD, receivedGestures[9].gesture)
        assertEquals(201, receivedGestures[9].code)
        assertEquals(GlassGesture.DOUBLE_TAP, receivedGestures[10].gesture)
        assertEquals(202, receivedGestures[10].code)
        assertEquals(GlassGesture.SWIPE_BACKWARD, receivedGestures[11].gesture)
        assertEquals(237, receivedGestures[11].code)
    }

    @Test
    fun decodesKeyEventPacketsWithKeyCodes() {
        val json = "{\"action\":\"sync_glass_event\"," +
                "\"value\":[" +
                "{\"name\":\"key_event\",\"key_code\":23}," +
                "{\"name\":\"key_event\",\"key_code\":22}," +
                "{\"name\":\"key_event\",\"key_code\":21}," +
                "{\"name\":\"key_event\",\"key_code\":87}," +
                "{\"name\":\"key_event\",\"key_code\":88}," +
                "{\"name\":\"key_event\",\"key_code\":79}" +
                "]}"
        router.handle(json)

        assertEquals(6, receivedGestures.size)
        assertEquals(GlassGesture.TAP, receivedGestures[0].gesture)
        assertEquals(23, receivedGestures[0].code)
        assertEquals(GlassGesture.SWIPE_FORWARD, receivedGestures[1].gesture)
        assertEquals(22, receivedGestures[1].code)
        assertEquals(GlassGesture.SWIPE_BACKWARD, receivedGestures[2].gesture)
        assertEquals(21, receivedGestures[2].code)
        assertEquals(GlassGesture.SWIPE_FORWARD, receivedGestures[3].gesture)
        assertEquals(87, receivedGestures[3].code)
        assertEquals(GlassGesture.SWIPE_BACKWARD, receivedGestures[4].gesture)
        assertEquals(88, receivedGestures[4].code)
        assertEquals(GlassGesture.TAP, receivedGestures[5].gesture)
        assertEquals(79, receivedGestures[5].code)
    }

    @Test
    fun decodesPhonepadActionPackets() {
        receivedGestures.clear()
        router.handle("{\"action\":\"phonepad\",\"data\":{\"action\":\"click\"}}")
        assertEquals(1, receivedGestures.size)
        assertEquals(GlassGesture.TAP, receivedGestures[0].gesture)

        receivedGestures.clear()
        router.handle("{\"action\":\"phonepad\",\"data\":{\"action\":\"doubleClick\"}}")
        assertEquals(1, receivedGestures.size)
        assertEquals(GlassGesture.DOUBLE_TAP, receivedGestures[0].gesture)

        receivedGestures.clear()
        router.handle("{\"action\":\"phonepad\",\"data\":{\"action\":\"longPress\"}}")
        assertEquals(1, receivedGestures.size)
        assertEquals(GlassGesture.LONG_PRESS, receivedGestures[0].gesture)

        receivedGestures.clear()
        router.handle("{\"action\":\"phonepad\",\"data\":{\"action\":\"gestureMode\",\"actionType\":19}}")
        assertEquals(1, receivedGestures.size)
        assertEquals(GlassGesture.SWIPE_FORWARD, receivedGestures[0].gesture)
    }

    @Test
    fun filtersParasiticSwipeWhenSimultaneousTapOccursInBatch() {
        // Exact payload from log line 361: finger landing produces micro-swipe (206) and tap (210) at same timestamp
        val json = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\",\"value\":[" +
                "{\"_action_value_\":\"key_event\",\"_event_attr_value_\":{\"down_or_up\":\"1\",\"key_code\":\"206\",\"key_event_sender\":2,\"key_event_time\":7133096},\"_event_id_\":\"key_event\"}," +
                "{\"_action_value_\":\"key_event\",\"_event_attr_value_\":{\"down_or_up\":\"1\",\"key_code\":\"210\",\"key_event_sender\":2,\"key_event_time\":7133096},\"_event_id_\":\"key_event\"}" +
                "]}}"
        router.handle(json)

        // The parasitic swipe forward (206) at the exact same key_event_time must be filtered out
        assertEquals(1, receivedGestures.size)
        assertEquals(GlassGesture.TAP, receivedGestures[0].gesture)
        assertEquals(210, receivedGestures[0].code)
    }

    @Test
    fun filtersDuplicateBounceTapsWithIdenticalTimestampInBatch() {
        val json = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\",\"value\":[" +
                "{\"_action_value_\":\"key_event\",\"_event_attr_value_\":{\"down_or_up\":\"1\",\"key_code\":\"210\",\"key_event_sender\":1,\"key_event_time\":1789075058000},\"_event_id_\":\"key_event\"}," +
                "{\"_action_value_\":\"key_event\",\"_event_attr_value_\":{\"down_or_up\":\"1\",\"key_code\":\"210\",\"key_event_sender\":1,\"key_event_time\":1789075058000},\"_event_id_\":\"key_event\"}" +
                "]}}"
        router.handle(json)

        assertEquals(1, receivedGestures.size)
        assertEquals(GlassGesture.TAP, receivedGestures[0].gesture)
        assertEquals(210, receivedGestures[0].code)
    }

    @Test
    fun consolidatesSender2TouchDownAndConfirmPairToSingleTap() {
        val json = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\",\"value\":[" +
                "{\"_action_value_\":\"key_event\",\"_event_attr_value_\":{\"down_or_up\":\"1\",\"key_code\":\"200\",\"key_event_sender\":2,\"key_event_time\":1789075095000},\"_event_id_\":\"key_event\"}," +
                "{\"_action_value_\":\"key_event\",\"_event_attr_value_\":{\"down_or_up\":\"1\",\"key_code\":\"203\",\"key_event_sender\":2,\"key_event_time\":1789075096000},\"_event_id_\":\"key_event\"}" +
                "]}}"
        router.handle(json)

        assertEquals(1, receivedGestures.size)
        assertEquals(GlassGesture.TAP, receivedGestures[0].gesture)
        assertEquals(200, receivedGestures[0].code)
    }

    @Test
    fun synthesizesDoubleTapWhenTwoTapsInSameBatchWithinTimeWindow() {
        val json = "{\"action\":\"event_tracking\",\"data\":{\"action\":\"sync_glass_event\",\"value\":[" +
                "{\"_action_value_\":\"key_event\",\"_event_attr_value_\":{\"down_or_up\":\"1\",\"key_code\":\"210\",\"key_event_sender\":1,\"key_event_time\":1789075058000},\"_event_id_\":\"key_event\"}," +
                "{\"_action_value_\":\"key_event\",\"_event_attr_value_\":{\"down_or_up\":\"1\",\"key_code\":\"210\",\"key_event_sender\":1,\"key_event_time\":1789075058200},\"_event_id_\":\"key_event\"}" +
                "]}}"
        router.handle(json)

        assertEquals(1, receivedGestures.size)
        assertEquals(GlassGesture.DOUBLE_TAP, receivedGestures[0].gesture)
    }
}
