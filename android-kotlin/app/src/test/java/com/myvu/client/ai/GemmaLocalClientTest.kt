package com.myvu.client.ai

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GemmaLocalClientTest {

    @Test
    fun defaultOptionHasValidConfig() {
        val option = GemmaLocalClient.DEFAULT_OPTION
        assertEquals("gemma-2b-it-gpu-int4", option.id)
        assertEquals("gemma-2b-it-gpu-int4.bin", option.fileName)
        assertTrue(option.downloadUrl.contains("huggingface.co/google/gemma-2b-it-tflite"))
    }

    @Test
    fun defaultModelIdMatchesGemma2B() {
        assertEquals("gemma-2b-it-gpu-int4", GemmaLocalClient.GEMMA_2B_IT_GPU.id)
    }
}
