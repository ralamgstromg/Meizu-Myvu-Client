package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResponseDeliveryTest {
    @Test
    fun textModeSendsOneAnswerAndEndsTurn() {
        val sent = mutableListOf<String>()
        var finished = false
        val delivery = AiResponseDelivery(
            sender = { sent += it },
            tts = FakeSpeaker(),
            isSessionActive = { it == "s1" },
            onFinished = { finished = true }
        )

        assertTrue(delivery.deliver(AiResponse("s1", "respuesta", false)))
        assertEquals(2, sent.size)
        assertTrue(finished)
    }

    @Test
    fun voiceOnlyOmitsVisualAnswerAndFinishesAfterSpeech() {
        val sent = mutableListOf<String>()
        val speaker = DelayedSpeaker()
        var finished = false
        val delivery = AiResponseDelivery(
            sender = { sent += it },
            tts = speaker,
            isSessionActive = { true },
            onFinished = { finished = true },
            mode = AiResponseMode.VOICE_ONLY
        )

        assertTrue(delivery.deliver(AiResponse("s1", "respuesta", true)))
        assertTrue(sent.none { it.contains("\"code\":122") })
        assertTrue(!finished)
        speaker.complete(true)
        assertTrue(finished)
    }

    @Test
    fun visualOnlySendsAnswerWithoutStartingSpeech() {
        val sent = mutableListOf<String>()
        val speaker = RecordingSpeaker()
        var finished = false
        val delivery = AiResponseDelivery(
            sender = { sent += it },
            tts = speaker,
            isSessionActive = { true },
            onFinished = { finished = true },
            mode = AiResponseMode.VISUAL_ONLY
        )

        assertTrue(delivery.deliver(AiResponse("s1", "respuesta", true)))
        assertTrue(sent.any { it.contains("\"code\":122") })
        assertTrue(sent.none { it.contains("\"code\":6") })
        assertEquals(0, speaker.calls)
        assertTrue(finished)
    }

    @Test
    fun voiceAndVisualSendsBothChannels() {
        val sent = mutableListOf<String>()
        val speaker = RecordingSpeaker()
        val delivery = AiResponseDelivery(
            sender = { sent += it },
            tts = speaker,
            isSessionActive = { true },
            onFinished = {},
            mode = AiResponseMode.VOICE_AND_VISUAL
        )

        assertTrue(delivery.deliver(AiResponse("s1", "respuesta", true)))
        assertTrue(sent.any { it.contains("\"code\":122") })
        assertTrue(sent.any { it.contains("\"code\":6") })
        assertEquals(1, speaker.calls)
    }

    @Test
    fun ttsCallbackCanBeDelayedUntilPlaybackCompletes() {
        val sent = mutableListOf<String>()
        var finished = false
        val speaker = DelayedSpeaker()
        val delivery = AiResponseDelivery(
            sender = { sent += it },
            tts = speaker,
            isSessionActive = { it == "s1" },
            onFinished = { finished = true }
        )

        assertTrue(delivery.deliver(AiResponse("s1", "respuesta", true)))
        assertTrue(!finished)
        assertEquals(2, sent.size)
        speaker.complete(true)
        assertTrue(finished)
        assertEquals(4, sent.size)
    }

    @Test
    fun staleResponseIsIgnored() {
        val sent = mutableListOf<String>()
        val delivery = AiResponseDelivery(
            sender = { sent += it },
            tts = FakeSpeaker(),
            isSessionActive = { false },
            onFinished = {}
        )

        assertTrue(!delivery.deliver(AiResponse("old", "respuesta", false)))
        assertTrue(sent.isEmpty())
    }

    private class FakeSpeaker : AiResponseDelivery.Speaker {
        override fun speak(text: String, callback: (Boolean) -> Unit) = callback(true)
        override fun stop() = Unit
    }

    private class RecordingSpeaker : AiResponseDelivery.Speaker {
        var calls = 0
        override fun speak(text: String, callback: (Boolean) -> Unit) {
            calls++
            callback(true)
        }
        override fun stop() = Unit
    }

    private class DelayedSpeaker : AiResponseDelivery.Speaker {
        private var callback: ((Boolean) -> Unit)? = null

        override fun speak(text: String, callback: (Boolean) -> Unit) {
            this.callback = callback
        }

        fun complete(success: Boolean) {
            val current = callback ?: error("missing TTS callback")
            callback = null
            current(success)
        }

        override fun stop() = Unit
    }
}
