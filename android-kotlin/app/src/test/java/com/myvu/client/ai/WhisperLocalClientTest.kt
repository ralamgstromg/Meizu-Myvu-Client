package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperLocalClientTest {

    @Test
    fun defaultOptionHasValidConfig() {
        val option = WhisperLocalClient.DEFAULT_OPTION
        assertEquals("whisper-large-v3-turbo-i4", option.id)
        assertEquals("whisper_large_v3_turbo_30s_i4.tflite", option.fileName)
        assertTrue(option.downloadUrl.contains("huggingface.co/litert-community/whisper-large-v3-turbo"))
    }

    @Test
    fun findOptionReturnsValidModel() {
        val option = WhisperLocalClient.findOption("whisper-tiny-acft")
        assertEquals("whisper-tiny-acft", option.id)
        assertEquals("whisper-tiny-acft.tflite", option.fileName)
    }
}
