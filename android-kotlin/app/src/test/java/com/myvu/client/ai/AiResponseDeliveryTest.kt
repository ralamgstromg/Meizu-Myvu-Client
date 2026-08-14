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
