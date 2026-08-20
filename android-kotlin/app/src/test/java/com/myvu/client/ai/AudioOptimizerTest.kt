package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class AudioOptimizerTest {

    @Test
    fun testOptimize48kTo16kReducesSampleCount3ToOne() {
        // Create 1 second of 48kHz 16-bit PCM (48000 samples = 96000 bytes)
        val sampleRate = 48000
        val numSamples = 48000
        val pcm48k = ByteArray(numSamples * 2)

        for (i in 0 until numSamples) {
            val freq = 440.0 // 440 Hz sine wave
            val sampleVal = (15000 * sin(2.0 * Math.PI * freq * i / sampleRate)).toInt().toShort()
            pcm48k[i * 2] = (sampleVal.toInt() and 0xFF).toByte()
            pcm48k[i * 2 + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }

        val optimized = AudioOptimizer.optimize48kTo16k(pcm48k)
        val outSamples = optimized.size / 2

        // The active speech region should be approximately ~16000 samples (1/3 decimation) plus padding bounds
        assertTrue(outSamples > 0)
        assertTrue(outSamples <= 16000 + 3200)
    }

    @Test
    fun testEmptyAudioHandling() {
        val empty = ByteArray(0)
        val result = AudioOptimizer.optimize48kTo16k(empty)
        assertEquals(0, result.size)
    }
}
