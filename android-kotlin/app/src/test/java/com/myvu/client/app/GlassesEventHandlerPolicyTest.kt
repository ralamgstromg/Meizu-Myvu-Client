package com.myvu.client.app

import android.content.Context
import org.robolectric.RuntimeEnvironment
import com.myvu.client.app.feature.GlassGesture
import com.myvu.client.app.feature.TouchGestureManager
import com.myvu.client.core.Prefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterization tests for the glasses input policy owned by [GlassesEventHandler]:
 * AI button / wake word routing and the touchpad gesture executor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlassesEventHandlerPolicyTest {

    private class RecordingDelegate : GlassesEventHandler.Delegate {
        var wakeCount = 0
        val triggers = mutableListOf<Int>()
        var pageClosedCount = 0
        var weatherRefreshCount = 0
        val sentActions = mutableListOf<String>()

        override fun wakeRelay() { wakeCount++ }
        override fun triggerAi(triggerCode: Int) { triggers.add(triggerCode) }
        override fun pageClosed() { pageClosedCount++ }
        override fun refreshWeather() { weatherRefreshCount++ }
        override fun updateBattery(battery: Int, isCharging: Boolean) {}
        override fun sendAction(actionJson: String) { sentActions.add(actionJson) }
    }

    private lateinit var context: Context
    private lateinit var router: InboundRouter
    private lateinit var delegate: RecordingDelegate
    private lateinit var handler: GlassesEventHandler

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        TouchGestureManager.resetDebounceForTesting()
        router = InboundRouter { _, _, _ -> }
        delegate = RecordingDelegate()
        handler = GlassesEventHandler(context, router, delegate)
    }

    @After
    fun tearDown() {
        TouchGestureManager.resetDebounceForTesting()
        com.myvu.client.ai.SensitiveActionGate.clear()
    }

    private fun aiTrigger(code: Int, control: Int = 1) =
        "{\"action\":\"ai_assistant\",\"code\":$code,\"data\":{\"control\":$control}}"

    @Test
    fun aiButtonWithDefaultMappingWakesRelayAndTriggersAi() {
        router.handle(aiTrigger(3))

        assertEquals(1, delegate.wakeCount)
        assertEquals(listOf(3), delegate.triggers)
    }

    @Test
    fun aiButtonMappedToExternalAssistantDoesNotTriggerLocalAi() {
        for (mapping in listOf("LAUNCH_GEMINI", "LAUNCH_GEMINI_LIVE", "LAUNCH_PHONE_ASSISTANT")) {
            TouchGestureManager.resetDebounceForTesting()
            delegate.triggers.clear()
            delegate.wakeCount = 0
            Prefs.setGlassesActionButtonAction(context, mapping)

            router.handle(aiTrigger(3))

            assertEquals("wake for $mapping", 1, delegate.wakeCount)
            assertTrue("no local AI for $mapping", delegate.triggers.isEmpty())
        }
    }

    @Test
    fun wakeWordIgnoresActionButtonMapping() {
        Prefs.setGlassesActionButtonAction(context, "LAUNCH_GEMINI")

        router.handle(aiTrigger(7))

        assertEquals(1, delegate.wakeCount)
        assertEquals(listOf(7), delegate.triggers)
    }

    @Test
    fun aiButtonRightAfterPhysicalKeyEventIsSuppressed() {
        TouchGestureManager.lastPhysicalKeyEventTime = System.currentTimeMillis()

        router.handle(aiTrigger(3))

        assertEquals(0, delegate.wakeCount)
        assertTrue(delegate.triggers.isEmpty())
    }

    @Test
    fun buttonReleaseOnlyClosesPage() {
        router.handle(aiTrigger(3, control = 0))

        assertEquals(1, delegate.pageClosedCount)
        assertEquals(0, delegate.wakeCount)
        assertTrue(delegate.triggers.isEmpty())
    }

    @Test
    fun zenModeGestureTogglesPrefAndSendsSettingAndNotification() {
        Prefs.setTouchpadSwipeForwardAction(context, "zen_mode")
        val before = Prefs.zenModeEnabled(context)

        handler.handleGesture(GlassGesture.SWIPE_FORWARD)

        assertEquals(!before, Prefs.zenModeEnabled(context))
        assertEquals(2, delegate.sentActions.size)
    }

    @Test
    fun mirrorGestureTogglesPrefAndSendsNotification() {
        Prefs.setTouchpadSwipeBackwardAction(context, "toggle_mirror")
        val before = Prefs.mirrorEnabled(context)

        handler.handleGesture(GlassGesture.SWIPE_BACKWARD)

        assertEquals(!before, Prefs.mirrorEnabled(context))
        assertEquals(1, delegate.sentActions.size)
    }

    @Test
    fun weatherGestureRefreshesWeather() {
        Prefs.setTouchpadSwipeForwardAction(context, "weather_sync")

        handler.handleGesture(GlassGesture.SWIPE_FORWARD)

        assertEquals(1, delegate.weatherRefreshCount)
        assertFalse(delegate.sentActions.isEmpty())
    }

    @Test
    fun doubleTapConfirmsPendingSensitiveActionAndShowsResult() {
        val gate = com.myvu.client.ai.SensitiveActionGate
        val confirming = GlassesEventHandler(context, router, delegate, kotlinx.coroutines.Dispatchers.Unconfined)
        var sent = 0
        gate.hold("send-whatsapp (contact: Ana)") { sent++; "WhatsApp enviado" }

        confirming.handleGesture(GlassGesture.DOUBLE_TAP)

        assertEquals(1, sent)
        assertFalse(gate.hasPending())
        assertTrue(delegate.sentActions.any { it.contains("WhatsApp enviado") })
    }

    @Test
    fun backwardSwipeCancelsPendingSensitiveAction() {
        val gate = com.myvu.client.ai.SensitiveActionGate
        var sent = 0
        gate.hold("call-contact") { sent++; "Llamando" }

        handler.handleGesture(GlassGesture.SWIPE_BACKWARD)

        assertEquals(0, sent)
        assertFalse(gate.hasPending())
        assertTrue(delegate.sentActions.any { it.contains(com.myvu.client.ai.SensitiveActionGate.CANCELLED_MESSAGE) })
    }

    @Test
    fun gesturesKeepTheirMappingWhenNothingIsPending() {
        Prefs.setTouchpadSwipeBackwardAction(context, "weather_sync")

        handler.handleGesture(GlassGesture.SWIPE_BACKWARD)

        assertEquals(1, delegate.weatherRefreshCount)
    }
}
