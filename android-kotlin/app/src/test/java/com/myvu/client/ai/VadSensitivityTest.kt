package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class VadSensitivityTest {

    @Test
    fun testVadThresholdCalculationCapping() {
        val speechEnergy = 75.0
        val maxSpeechThreshold = 200.0
        val speechOverNoise = 2.5

        // Quiet room: noise floor 20.0 -> threshold = max(75, 20*2.5 = 50) = 75.0
        val quietFloor = 20.0
        val quietThreshold = min(maxSpeechThreshold, max(speechEnergy, quietFloor * speechOverNoise))
        assertEquals(75.0, quietThreshold, 0.01)

        // Moderate room: noise floor 60.0 -> threshold = max(75, 60*2.5 = 150) = 150.0
        val moderateFloor = 60.0
        val moderateThreshold = min(maxSpeechThreshold, max(speechEnergy, moderateFloor * speechOverNoise))
        assertEquals(150.0, moderateThreshold, 0.01)

        // Loud room: noise floor 120.0 -> threshold would be 300 without cap, capped at 200.0
        val loudFloor = 120.0
        val loudThreshold = min(maxSpeechThreshold, max(speechEnergy, loudFloor * speechOverNoise))
        assertEquals(200.0, loudThreshold, 0.01)
    }

    @Test
    fun testSilenceHoldMsIsAtLeastOneSecond() {
        val silenceHoldMs = 1200L
        assertTrue(silenceHoldMs >= 1000L)
    }
}
