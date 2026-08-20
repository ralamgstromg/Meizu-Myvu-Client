package com.myvu.client.ai

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class WhisperLocalClientTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun isConfiguredReturnsTrueAlways() {
        val client = WhisperLocalClient(context)
        assertTrue(client.isConfigured())
    }

    @Test
    fun transcribeReturnsEmptyStringOnEmptyAudio() {
        val client = WhisperLocalClient(context)
        val result = client.transcribe(ByteArray(0), 16000, 1, "es")
        assertEquals("", result)
    }

    @Test
    fun transcribeThrowsFallbackIOExceptionWhenConnectionRefused() {
        val client = WhisperLocalClient(context)

        try {
            client.transcribe(ByteArray(16000), 16000, 1, "es")
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Error en inferencia local Whisper") == true)
        }
    }
}
