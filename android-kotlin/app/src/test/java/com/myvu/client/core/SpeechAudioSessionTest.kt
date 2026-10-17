package com.myvu.client.core

import android.media.AudioManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeechAudioSessionTest {

    private val ctx get() = RuntimeEnvironment.getApplication()
    private val am get() = ctx.getSystemService(AudioManager::class.java)

    private fun music() = am.getStreamVolume(AudioManager.STREAM_MUSIC)

    @Before
    fun setUp() {
        shadowOf(am).setStreamMaxVolume(15)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 3, 0)
    }

    @After
    fun tearDown() {
        repeat(3) { SpeechAudioSession.release() }
        am.mode = AudioManager.MODE_NORMAL
    }

    @Test
    fun raisesMusicToBoostPercentWhileHeldAndRestoresOnRelease() {
        SpeechAudioSession.acquire(ctx, holdMs = 5_000)
        assertEquals(12, music()) // 80% of 15

        SpeechAudioSession.release()
        assertEquals(3, music())
        assertNotNull(shadowOf(am).lastAbandonedAudioFocusRequest)
    }

    @Test
    fun keepsBoostUntilLastOverlappingUtteranceEnds() {
        SpeechAudioSession.acquire(ctx, holdMs = 5_000)
        SpeechAudioSession.acquire(ctx, holdMs = 5_000)

        SpeechAudioSession.release()
        assertEquals(12, music())

        SpeechAudioSession.release()
        assertEquals(3, music())
    }

    @Test
    fun leavesVolumeAloneWhenUserChangedItDuringSpeech() {
        SpeechAudioSession.acquire(ctx, holdMs = 5_000)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 7, 0)

        SpeechAudioSession.release()
        assertEquals(7, music())
    }

    @Test
    fun skipsBoostWhenAudioFocusIsDenied() {
        shadowOf(am).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        SpeechAudioSession.acquire(ctx, holdMs = 5_000)
        assertEquals(3, music())

        SpeechAudioSession.release()
        assertEquals(3, music())
    }

    @Test
    fun doesNothingDuringCommunicationMode() {
        am.mode = AudioManager.MODE_IN_COMMUNICATION

        SpeechAudioSession.acquire(ctx, holdMs = 5_000)
        assertEquals(3, music())
        assertNull(shadowOf(am).lastAbandonedAudioFocusRequest)

        SpeechAudioSession.release()
        assertEquals(3, music())
    }

    @Test
    fun restoresLevelLeftRaisedByKilledProcess() {
        Prefs.setSpeechVolumeRecovery(ctx, savedLevel = 3, raisedLevel = 12)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 12, 0)

        SpeechAudioSession.restoreAfterCrash(ctx)

        assertEquals(3, music())
        assertEquals(-1, Prefs.speechVolumeSavedLevel(ctx))
    }

    @Test
    fun boostSwitchOffKeepsVolumeAndStillRequestsFocus() {
        Prefs.setNotifBoostEnabled(ctx, false)

        SpeechAudioSession.acquire(ctx, holdMs = 5_000)
        assertEquals(3, music())
        assertNotNull(shadowOf(am).lastAudioFocusRequest)

        SpeechAudioSession.release()
        assertEquals(3, music())
    }
}
