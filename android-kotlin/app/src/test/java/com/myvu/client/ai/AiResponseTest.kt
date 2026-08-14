package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AiResponseTest {
    @Test
    fun responseTrimsWhitespaceAndPreservesSource() {
        val response = AiResponse("session-1", "  resultado  ", false, source = AiResponse.Source.WEATHER)
        assertEquals("resultado", response.normalizedText)
        assertEquals(AiResponse.Source.WEATHER, response.source)
    }

    @Test
    fun blankResponseIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AiResponse("session-1", "   ", false)
        }
    }
}
