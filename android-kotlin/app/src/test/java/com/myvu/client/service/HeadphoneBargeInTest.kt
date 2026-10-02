package com.myvu.client.service

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HeadphoneBargeInTest {

    private class FakeSpeech(var speaking: Boolean) : HeadphoneGestureManager.SpeechOutput {
        var stops = 0
        override fun isSpeaking() = speaking
        override fun stop() { stops++; speaking = false }
    }

    @Test
    fun tapWhileSpeakingOnlyStopsSpeech() {
        val manager = HeadphoneGestureManager.getInstance(RuntimeEnvironment.getApplication())
        val original = manager.speech
        val fake = FakeSpeech(speaking = true)
        manager.speech = fake
        try {
            assertTrue(manager.onHeadsetButtonEvent(KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.ACTION_DOWN))
            assertEquals(1, fake.stops)

            // Silent now: the next tap goes through normal tap counting, no extra stop.
            assertTrue(manager.onHeadsetButtonEvent(KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.ACTION_DOWN))
            assertEquals(1, fake.stops)
        } finally {
            manager.speech = original
        }
    }
}
