package com.myvu.client.app

import com.myvu.client.app.feature.GestureAction
import com.myvu.client.app.feature.GlassGesture
import com.myvu.client.app.feature.TouchGestureManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TouchGestureManagerTest {

    private class MockActionExecutor : TouchGestureManager.ActionExecutor {
        var aiCode: Int? = null
        var phoneAssistantCalled = false
        var geminiAssistantCalled = false
        var geminiLiveCalled = false
        var launchAppPackage: String? = null
        var weatherSyncCalled = false
        var toggleMirrorCalled = false
        var mediaPlayPauseCalled = false
        var mediaNextCalled = false
        var mediaPrevCalled = false
        var openTeleprompterCalled = false
        var zenModeCalled = false
        var hudDashboardCalled = false
        var voiceAgentAuraCalled = false
        var noneCalled = false

        override fun executeAiAssistant(code: Int) {
            aiCode = code
        }

        override fun executeGeminiAssistant() {
            geminiAssistantCalled = true
        }

        override fun executeGeminiLive() {
            geminiLiveCalled = true
        }

        override fun executePhoneAssistant() {
            phoneAssistantCalled = true
        }

        override fun executeLaunchApp(packageName: String) {
            launchAppPackage = packageName
        }

        override fun executeWeatherSync() {
            weatherSyncCalled = true
        }

        override fun executeToggleMirror() {
            toggleMirrorCalled = true
        }

        override fun executeMediaPlayPause() {
            mediaPlayPauseCalled = true
        }

        override fun executeMediaNext() {
            mediaNextCalled = true
        }

        override fun executeMediaPrevious() {
            mediaPrevCalled = true
        }

        override fun executeOpenTeleprompter() {
            openTeleprompterCalled = true
        }

        override fun executeZenMode() {
            zenModeCalled = true
        }

        override fun executeHudDashboard() {
            hudDashboardCalled = true
        }

        override fun executeVoiceAgentAura() {
            voiceAgentAuraCalled = true
        }

        override fun executeNone() {
            noneCalled = true
        }
    }

    private lateinit var executor: MockActionExecutor

    @Before
    fun setUp() {
        TouchGestureManager.resetDebounceForTesting()
        executor = MockActionExecutor()
    }

    @Test
    fun parsesAllGestureActionIdsCorrectly() {
        assertEquals(GestureAction.NONE, GestureAction.fromId("none"))
        assertEquals(GestureAction.LAUNCH_PHONE_ASSISTANT, GestureAction.fromId("phone_assistant"))
        assertEquals(GestureAction.LAUNCH_GEMINI, GestureAction.fromId("gemini"))
        assertEquals(GestureAction.LAUNCH_GEMINI, GestureAction.fromId("launch_gemini"))
        assertEquals(GestureAction.LAUNCH_GEMINI_LIVE, GestureAction.fromId("gemini_live"))
        assertEquals(GestureAction.LAUNCH_GEMINI_LIVE, GestureAction.fromId("launch_gemini_live"))
        assertEquals(GestureAction.LAUNCH_GEMINI_LIVE, GestureAction.fromId("live"))
        assertEquals(GestureAction.LAUNCH_PHONE_ASSISTANT, GestureAction.fromId("google_assistant"))
        assertEquals(GestureAction.LAUNCH_LOCAL_AI, GestureAction.fromId("ai_assistant"))
        assertEquals(GestureAction.LAUNCH_LOCAL_AI, GestureAction.fromId("local_ai"))
        assertEquals(GestureAction.MEDIA_PLAY_PAUSE, GestureAction.fromId("media_play_pause"))
        assertEquals(GestureAction.MEDIA_NEXT, GestureAction.fromId("media_next"))
        assertEquals(GestureAction.MEDIA_PREV, GestureAction.fromId("media_prev"))
        assertEquals(GestureAction.MEDIA_PREV, GestureAction.fromId("media_previous"))
        assertEquals(GestureAction.WEATHER_SYNC, GestureAction.fromId("weather_sync"))
        assertEquals(GestureAction.TOGGLE_MIRROR, GestureAction.fromId("toggle_mirror"))
        assertEquals(GestureAction.OPEN_TELEPROMPTER, GestureAction.fromId("open_teleprompter"))
        assertEquals(GestureAction.ZEN_MODE, GestureAction.fromId("zen_mode"))
    }

    @Test
    fun unknownActionIdFallsBackToNone() {
        assertEquals(GestureAction.NONE, GestureAction.fromId("non_existent_action"))
        assertEquals(GestureAction.NONE, GestureAction.fromId(null))
        assertEquals(GestureAction.NONE, GestureAction.fromId(""))
    }

    @Test
    fun defaultActionsForGesturesAreAccurate() {
        assertEquals(GestureAction.NONE, TouchGestureManager.getActionForGesture(null, GlassGesture.TAP))
        assertEquals(GestureAction.LAUNCH_GEMINI, TouchGestureManager.getActionForGesture(null, GlassGesture.DOUBLE_TAP))
        assertEquals(GestureAction.LAUNCH_GEMINI, TouchGestureManager.getActionForGesture(null, GlassGesture.TRIPLE_TAP))
        assertEquals(GestureAction.MEDIA_NEXT, TouchGestureManager.getActionForGesture(null, GlassGesture.SWIPE_FORWARD))
        assertEquals(GestureAction.MEDIA_PREV, TouchGestureManager.getActionForGesture(null, GlassGesture.SWIPE_BACKWARD))
        assertEquals(GestureAction.LAUNCH_LOCAL_AI, TouchGestureManager.getActionForGesture(null, GlassGesture.LONG_PRESS))
        assertEquals(GestureAction.NONE, TouchGestureManager.getActionForGesture(null, GlassGesture.UNKNOWN))
    }

    @Test
    fun unknownGestureDoesNotExecuteAnyAction() {
        TouchGestureManager.handleGesture(null, GlassGesture.UNKNOWN, -1, executor)
        assertFalse(executor.phoneAssistantCalled)
        assertFalse(executor.geminiAssistantCalled)
        assertFalse(executor.mediaPlayPauseCalled)
        assertFalse(executor.noneCalled)
        assertEquals(null, executor.aiCode)
    }

    @Test
    fun dispatchesTripleTapToGeminiAssistantByDefault() {
        TouchGestureManager.handleGesture(null, GlassGesture.TRIPLE_TAP, 3, executor)
        assertTrue(executor.geminiAssistantCalled)
        assertFalse(executor.mediaPlayPauseCalled)
        assertEquals(null, executor.aiCode)
    }

    @Test
    fun resolvesAppActionAndExtractsPackageName() {
        val appActionId = GestureAction.makeAppActionId("com.spotify.music")
        assertEquals("app:com.spotify.music", appActionId)
        assertTrue(GestureAction.isAppAction(appActionId))
        assertEquals("com.spotify.music", GestureAction.getAppPackage(appActionId))
        assertEquals(GestureAction.LAUNCH_APP, GestureAction.fromId(appActionId))

        assertFalse(GestureAction.isAppAction("launch_gemini"))
        assertEquals(null, GestureAction.getAppPackage("launch_gemini"))
    }

    @Test
    fun dispatchesDoubleTapToGeminiByDefault() {
        TouchGestureManager.handleGesture(null, GlassGesture.DOUBLE_TAP, 2, executor)
        assertTrue(executor.geminiAssistantCalled)
        assertFalse(executor.mediaPlayPauseCalled)
    }

    @Test
    fun dispatchesSwipeForwardToMediaNextByDefault() {
        TouchGestureManager.handleGesture(null, GlassGesture.SWIPE_FORWARD, 5, executor)
        assertTrue(executor.mediaNextCalled)
        assertFalse(executor.mediaPrevCalled)
    }

    @Test
    fun dispatchesSwipeBackwardToMediaPrevByDefault() {
        TouchGestureManager.handleGesture(null, GlassGesture.SWIPE_BACKWARD, 6, executor)
        assertTrue(executor.mediaPrevCalled)
        assertFalse(executor.mediaNextCalled)
    }

    @Test
    fun dispatchesLongPressToAiAssistantByDefault() {
        TouchGestureManager.handleGesture(null, GlassGesture.LONG_PRESS, 4, executor)
        assertEquals(4, executor.aiCode)
    }

    @Test
    fun dispatchesTapToNoneByDefault() {
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 1, executor)
        assertTrue(executor.noneCalled)
        assertFalse(executor.geminiAssistantCalled)
    }

    @Test
    fun debounceSuppressesRapidSuccessiveTriggers() {
        TouchGestureManager.handleGesture(null, GlassGesture.DOUBLE_TAP, 2, executor)
        assertTrue(executor.geminiAssistantCalled)

        // Immediate second event within debounce window should be ignored
        val secondExecutor = MockActionExecutor()
        TouchGestureManager.handleGesture(null, GlassGesture.DOUBLE_TAP, 2, secondExecutor)
        assertFalse(secondExecutor.geminiAssistantCalled)

        // After reset, event passes through
        TouchGestureManager.resetDebounceForTesting()
        val thirdExecutor = MockActionExecutor()
        TouchGestureManager.handleGesture(null, GlassGesture.SWIPE_FORWARD, 5, thirdExecutor)
        assertTrue(thirdExecutor.mediaNextCalled)
    }

    @Test
    fun handleTriggerOverloadMapsRawCodeToGesture() {
        TouchGestureManager.handleTrigger(null, 5, executor) // code 5 = SWIPE_FORWARD
        assertTrue(executor.mediaNextCalled)
    }

    @Test
    fun allGestureActionsHaveNonEmptyDisplayNames() {
        for (action in GestureAction.entries) {
            assertTrue(action.id.isNotEmpty())
            assertTrue(action.displayName.isNotEmpty())
        }
    }

    @Test
    fun twoTapsWithinWindowSynthesizeDoubleTap() {
        var simulatedTime = 1000L
        TouchGestureManager.timeProvider = { simulatedTime }

        // Tap 1 at t=1000ms: TAP action is NONE by default
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 1, executor)
        assertTrue(executor.noneCalled)
        assertFalse(executor.geminiAssistantCalled)

        // Reset executor flags
        val secondExecutor = MockActionExecutor()

        // Tap 2 at t=1180ms (+180ms, within 40..700ms window)
        // DOUBLE_TAP action is LAUNCH_GEMINI by default
        simulatedTime = 1180L
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 1, secondExecutor)
        assertTrue(secondExecutor.geminiAssistantCalled)
        assertFalse(secondExecutor.noneCalled)
    }

    @Test
    fun twoTapsAt600msSynthesizeDoubleTap() {
        var simulatedTime = 1000L
        TouchGestureManager.timeProvider = { simulatedTime }

        // Tap 1 at t=1000ms
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 1, executor)
        assertTrue(executor.noneCalled)

        // Tap 2 at t=1600ms (+600ms, within 40..700ms window)
        val secondExecutor = MockActionExecutor()
        simulatedTime = 1600L
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 1, secondExecutor)
        assertTrue(secondExecutor.geminiAssistantCalled)
    }

    @Test
    fun gestureWithActionNoneDoesNotDebounceSubsequentTap() {
        var simulatedTime = 2000L
        TouchGestureManager.timeProvider = { simulatedTime }

        // A gesture with action NONE (e.g. TAP or unconfigured swipe)
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 1, executor)
        assertTrue(executor.noneCalled)

        // 10ms later (simulatedTime = 2010L, well under 350ms debounce)
        simulatedTime = 2010L
        val secondExecutor = MockActionExecutor()
        // Hardware double tap arrives
        TouchGestureManager.handleGesture(null, GlassGesture.DOUBLE_TAP, 2, secondExecutor)
        // Must NOT be blocked by the previous NONE action!
        assertTrue(secondExecutor.geminiAssistantCalled)
    }

    @Test
    fun contactBounceUnder40msDoesNotResetTapAccumulator() {
        var simulatedTime = 5000L
        TouchGestureManager.timeProvider = { simulatedTime }

        // Tap 1 at t=5000ms
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 1, executor)
        assertTrue(executor.noneCalled)

        // Electrical contact bounce at t=5005ms (+5ms, < 40ms)
        simulatedTime = 5005L
        val bounceExecutor = MockActionExecutor()
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 1, bounceExecutor)
        assertFalse(bounceExecutor.noneCalled)

        // Tap 2 at t=5200ms (+200ms from the original tap at 5000ms)
        simulatedTime = 5200L
        val secondExecutor = MockActionExecutor()
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 1, secondExecutor)
        // Must successfully synthesize DOUBLE_TAP because the 5ms bounce did not corrupt lastTapTime
        assertTrue(secondExecutor.geminiAssistantCalled)
    }

    @Test
    fun twoTapsAt750msSynthesizeDoubleTapInExtendedWindow() {
        var simulatedTime = 1000L
        TouchGestureManager.timeProvider = { simulatedTime }

        // Tap 1 at t=1000ms
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 1, executor)
        assertTrue(executor.noneCalled)

        // Tap 2 at t=1750ms (+750ms, within new 30..1100ms window)
        val secondExecutor = MockActionExecutor()
        simulatedTime = 1750L
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 1, secondExecutor)
        assertTrue(secondExecutor.geminiAssistantCalled)
    }

    @Test
    fun twoTapsAt950msSynthesizeDoubleTapInExtendedWindow() {
        var simulatedTime = 1000L
        TouchGestureManager.timeProvider = { simulatedTime }

        // Tap 1 at t=1000ms
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 1, executor)
        assertTrue(executor.noneCalled)

        // Tap 2 at t=1950ms (+950ms, within extended 30..1100ms window)
        val secondExecutor = MockActionExecutor()
        simulatedTime = 1950L
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 1, secondExecutor)
        assertTrue(secondExecutor.geminiAssistantCalled)
    }

    @Test
    fun twoTapsWithHardwareEventTimeSynthesizeDoubleTapEvenIfNetworkDelayed() {
        var simulatedTime = 1000L
        TouchGestureManager.timeProvider = { simulatedTime }

        // Hardware tap 1 occurred at 1789080160000L, arrived at t=1000ms
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 210, executor, eventTime = 1789080160000L)
        assertTrue(executor.noneCalled)

        // Hardware tap 2 occurred at 1789080160800L (+800ms on glasses),
        // but due to Bluetooth suspend wakeup delay, arrived at t=4500ms (+3500ms on phone!)
        val secondExecutor = MockActionExecutor()
        simulatedTime = 4500L
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 210, secondExecutor, eventTime = 1789080160800L)

        // Must succeed because hardware interval was 800ms!
        assertTrue(secondExecutor.geminiAssistantCalled)
    }

    @Test
    fun threeTapsSynthesizeTripleTapAcrossPackets() {
        var simulatedTime = 1000L
        TouchGestureManager.timeProvider = { simulatedTime }

        // Tap 1 at t=1000ms
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 1, executor)
        assertTrue(executor.noneCalled)

        // Tap 2 at t=1200ms (+200ms)
        val secondExecutor = MockActionExecutor()
        simulatedTime = 1200L
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 1, secondExecutor)
        assertTrue(secondExecutor.geminiAssistantCalled)

        // Tap 3 at t=1450ms (+250ms from tap 2)
        val thirdExecutor = MockActionExecutor()
        simulatedTime = 1450L
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 1, thirdExecutor)
        // With default null context, TRIPLE_TAP triggers LAUNCH_GEMINI
        assertTrue(thirdExecutor.geminiAssistantCalled)
    }

    @Test
    fun launchGeminiAssistantAndPhoneAssistantAreNullSafe() {
        // Must handle null context gracefully without throwing
        TouchGestureManager.launchGeminiAssistant(null, isLive = false)
        TouchGestureManager.launchGeminiAssistant(null, isLive = true)
        TouchGestureManager.launchPhoneAssistant(null)
    }

    @Test
    fun lockScreenHelperReLockMethodsAreSafe() {
        // Must handle cancellation and locking without throwing exceptions
        com.myvu.client.core.LockScreenHelper.cancelScheduledReLock()
    }

    @Test
    fun actionButtonCode230ExecutesHudDashboardAndDoesNotSynthesizeTap() {
        var simulatedTime = 1000L
        TouchGestureManager.timeProvider = { simulatedTime }

        // Code 230 arrives: physical action button press
        TouchGestureManager.handleGesture(null, GlassGesture.ACTION_BUTTON, 230, executor)
        assertTrue(executor.hudDashboardCalled)
        assertFalse(executor.noneCalled)
        assertFalse(executor.geminiAssistantCalled)

        // If a temple tap arrives 200ms later, it must be suppressed by physical button suppression window
        val secondExecutor = MockActionExecutor()
        simulatedTime = 1200L
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 210, secondExecutor)
        assertFalse(secondExecutor.geminiAssistantCalled)
        assertFalse(secondExecutor.noneCalled)
    }

    @Test
    fun physicalButtonPressedSuppressesSubsequentTempleGestures() {
        var simulatedTime = 1000L
        TouchGestureManager.timeProvider = { simulatedTime }

        // External notification of physical button pressed (e.g. from AI trigger code 3)
        TouchGestureManager.notifyPhysicalButtonPressed(null)

        // Temple tap arrives 300ms later: MUST BE SUPPRESSED!
        simulatedTime = 1300L
        TouchGestureManager.handleGesture(null, GlassGesture.TAP, 210, executor)
        assertFalse(executor.geminiAssistantCalled)
        assertFalse(executor.noneCalled)

        // Temple double tap arrives 600ms later: MUST BE SUPPRESSED!
        val secondExecutor = MockActionExecutor()
        simulatedTime = 1600L
        TouchGestureManager.handleGesture(null, GlassGesture.DOUBLE_TAP, 212, secondExecutor)
        assertFalse(secondExecutor.geminiAssistantCalled)
        assertFalse(secondExecutor.phoneAssistantCalled)
    }

    @Test
    fun templeGesturesWorkAfterSuppressionWindowExpires() {
        var simulatedTime = 1000L
        TouchGestureManager.timeProvider = { simulatedTime }

        TouchGestureManager.notifyPhysicalButtonPressed(null)

        // Temple gesture arrives 1300ms later (> 1200ms suppression window)
        simulatedTime = 2300L
        TouchGestureManager.handleGesture(null, GlassGesture.DOUBLE_TAP, 212, executor)
        assertTrue(executor.geminiAssistantCalled)
    }
}

