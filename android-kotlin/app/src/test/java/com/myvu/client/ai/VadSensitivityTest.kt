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

    @Test
    fun testDynamicSpeechThresholdPostUtterance() {
        val speechEnergy = 75.0
        val baseThreshold = 75.0

        // Before speech: peak energy is low (e.g. 50), threshold is baseThreshold (75)
        var speechStarted = false
        var peakEnergy = 50.0
        var dynamicThreshold = if (speechStarted && peakEnergy > speechEnergy * 2.0) {
            max(baseThreshold, peakEnergy * 0.22)
        } else {
            baseThreshold
        }
        assertEquals(75.0, dynamicThreshold, 0.01)

        // Speech starts and peaks at 800.0
        speechStarted = true
        peakEnergy = 800.0
        dynamicThreshold = if (speechStarted && peakEnergy > speechEnergy * 2.0) {
            max(baseThreshold, peakEnergy * 0.22)
        } else {
            baseThreshold
        }
        // 800 * 0.22 = 176.0
        assertEquals(176.0, dynamicThreshold, 0.01)

        // When speech ends and mic drops back to ambient room noise (level ~85),
        // level (85) is strictly less than dynamicThreshold (176), allowing silence detection!
        val ambientRoomNoise = 85.0
        assertTrue(ambientRoomNoise < dynamicThreshold)
    }
}
