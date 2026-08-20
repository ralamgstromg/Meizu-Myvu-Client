package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavAudioEncoderTest {

    @Test
    fun encodePcmToWavGeneratesValidRiffHeader() {
        val dummyPcm = ByteArray(1600) { 0.toByte() }
        val wav = WavAudioEncoder.encodePcmToWav(dummyPcm, sampleRate = 16000, channels = 1)

        assertEquals(44 + 1600, wav.size)
        // Header RIFF
        assertEquals('R'.code.toByte(), wav[0])
        assertEquals('I'.code.toByte(), wav[1])
        assertEquals('F'.code.toByte(), wav[2])
        assertEquals('F'.code.toByte(), wav[3])

        // WAVE
        assertEquals('W'.code.toByte(), wav[8])
        assertEquals('A'.code.toByte(), wav[9])
        assertEquals('V'.code.toByte(), wav[10])
        assertEquals('E'.code.toByte(), wav[11])
    }
}
