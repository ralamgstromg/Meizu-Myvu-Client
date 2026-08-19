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
        var weatherSyncCalled = false
        var toggleMirrorCalled = false
        var mediaPlayPauseCalled = false
        var mediaNextCalled = false
        var mediaPrevCalled = false
        var openTeleprompterCalled = false
        var zenModeCalled = false
        var noneCalled = false

        override fun executeAiAssistant(code: Int) {
            aiCode = code
        }

        override fun executePhoneAssistant() {
            phoneAssistantCalled = true
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
        assertEquals(GestureAction.LAUNCH_PHONE_ASSISTANT, GestureAction.fromId("gemini"))
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
        assertEquals(GestureAction.MEDIA_PLAY_PAUSE, TouchGestureManager.getActionForGesture(null, GlassGesture.DOUBLE_TAP))
        assertEquals(GestureAction.LAUNCH_PHONE_ASSISTANT, TouchGestureManager.getActionForGesture(null, GlassGesture.TRIPLE_TAP))
        assertEquals(GestureAction.MEDIA_NEXT, TouchGestureManager.getActionForGesture(null, GlassGesture.SWIPE_FORWARD))
        assertEquals(GestureAction.MEDIA_PREV, TouchGestureManager.getActionForGesture(null, GlassGesture.SWIPE_BACKWARD))
        assertEquals(GestureAction.LAUNCH_LOCAL_AI, TouchGestureManager.getActionForGesture(null, GlassGesture.LONG_PRESS))
        assertEquals(GestureAction.LAUNCH_LOCAL_AI, TouchGestureManager.getActionForGesture(null, GlassGesture.UNKNOWN))
    }

    @Test
    fun dispatchesTripleTapToPhoneAssistantByDefault() {
        TouchGestureManager.handleGesture(null, GlassGesture.TRIPLE_TAP, 3, executor)
        assertTrue(executor.phoneAssistantCalled)
        assertFalse(executor.mediaPlayPauseCalled)
        assertEquals(null, executor.aiCode)
    }

    @Test
    fun dispatchesDoubleTapToMediaPlayPauseByDefault() {
        TouchGestureManager.handleGesture(null, GlassGesture.DOUBLE_TAP, 2, executor)
        assertTrue(executor.mediaPlayPauseCalled)
        assertFalse(executor.phoneAssistantCalled)
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
        assertFalse(executor.mediaPlayPauseCalled)
    }

    @Test
    fun debounceSuppressesRapidSuccessiveTriggers() {
        TouchGestureManager.handleGesture(null, GlassGesture.DOUBLE_TAP, 2, executor)
        assertTrue(executor.mediaPlayPauseCalled)

        // Immediate second event within debounce window should be ignored
        val secondExecutor = MockActionExecutor()
        TouchGestureManager.handleGesture(null, GlassGesture.DOUBLE_TAP, 2, secondExecutor)
        assertFalse(secondExecutor.mediaPlayPauseCalled)

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
}
