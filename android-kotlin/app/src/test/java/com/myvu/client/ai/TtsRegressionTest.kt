package com.myvu.client.ai

import android.speech.tts.TextToSpeech
import com.myvu.client.core.TextToSpeechHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowTextToSpeech

/** Regressions: speech lost while the engine binds, queue overwrites, no retry after a failed init. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TtsRegressionTest {

    private val ctx get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        TextToSpeechHelper.shutdown()
        ShadowTextToSpeech.reset()
    }

    @After
    fun tearDown() = TextToSpeechHelper.shutdown()

    private fun lastEngine() = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())

    @Test
    fun ttsPlayerKeepsRequestsMadeWhileEngineIsBinding() {
        val player = TtsPlayer(ctx)
        player.init()
        var spoken: Boolean? = null
        player.speak("Reunión a las 15:30", TtsPlayer.Callback { spoken = it })

        // Engine finishes binding after the request arrived.
        lastEngine().onInitListener.onInit(TextToSpeech.SUCCESS)
        ShadowLooper.idleMainLooper()

        assertEquals("Reunión a las 3:30 de la tarde", lastEngine().lastSpokenText)
        // Before the fix the request was dropped and reported as failed (false).
        assertEquals(true, spoken)
    }

    @Test
    fun ttsPlayerRetriesWithNewEngineAfterFailedInit() {
        val player = TtsPlayer(ctx)
        player.speak("hola", null)
        val first = ShadowTextToSpeech.getLastTextToSpeechInstance()
        shadowOf(first).onInitListener.onInit(TextToSpeech.ERROR)
        ShadowLooper.idleMainLooper()

        player.speak("segundo intento", null)
        val second = ShadowTextToSpeech.getLastTextToSpeechInstance()
        assertNotSame(first, second)
        shadowOf(second).onInitListener.onInit(TextToSpeech.SUCCESS)

        assertEquals("segundo intento", shadowOf(second).lastSpokenText)
    }

    @Test
    fun helperSpeaksWithoutContextAfterAttachAndKeepsQueueOrder() {
        TextToSpeechHelper.attach(ctx)
        TextToSpeechHelper.speak("Te escucho")
        TextToSpeechHelper.speak("Generando tu resumen")

        lastEngine().onInitListener.onInit(TextToSpeech.SUCCESS)

        assertEquals(listOf("Te escucho", "Generando tu resumen"), lastEngine().spokenTextList)
        assertEquals(TextToSpeech.QUEUE_ADD, lastEngine().queueMode)
    }

    @Test
    fun helperRetriesAfterFailedInit() {
        TextToSpeechHelper.attach(ctx)
        TextToSpeechHelper.speak("uno")
        val first = ShadowTextToSpeech.getLastTextToSpeechInstance()
        shadowOf(first).onInitListener.onInit(TextToSpeech.ERROR)

        TextToSpeechHelper.speak("dos")
        val second = ShadowTextToSpeech.getLastTextToSpeechInstance()
        assertNotSame(first, second)
        shadowOf(second).onInitListener.onInit(TextToSpeech.SUCCESS)

        assertTrue(shadowOf(second).spokenTextList.contains("dos"))
    }
}
