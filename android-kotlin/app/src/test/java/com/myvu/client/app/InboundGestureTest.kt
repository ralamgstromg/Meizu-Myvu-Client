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
                "\"value\":[{\"action_name\":\"touch_gesture\",\"action_value\":1}]}}"
        router.handle(json)

        assertEquals(1, lastTriggerCode)
    }
}
