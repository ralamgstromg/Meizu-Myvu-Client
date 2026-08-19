package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaLocalClientTest {

    @Test
    fun defaultOptionHasValidConfig() {
        val option = GemmaLocalClient.DEFAULT_OPTION
        assertEquals("gemma-2b-it-gpu-int4", option.id)
        assertEquals("gemma-2b-it-gpu-int4.bin", option.fileName)
        assertTrue(option.downloadUrl.contains("huggingface.co/google/gemma-2b-it-tflite"))
    }

    @Test
    fun googleAiEdgeGalleryOptionsAreAvailable() {
        assertEquals("gemma-2b-it-gpu-int4", GemmaLocalClient.GEMMA_2B_IT_GPU.id)
        assertEquals("gemma-2b-it-cpu-int4", GemmaLocalClient.GEMMA_2B_IT_CPU.id)
        assertEquals("gemma-2-2b-it-gpu-int4", GemmaLocalClient.GEMMA_2_2B_IT_GPU.id)
        assertEquals("gemma-1.1-2b-it-gpu-int4", GemmaLocalClient.GEMMA_1_1_2B_IT_GPU.id)
    }

    @Test
    fun findOptionReturnsMatchingOrFallback() {
        val found = GemmaLocalClient.findOption("gemma-2-2b-it-gpu-int4")
        assertEquals("gemma-2-2b-it-gpu-int4.bin", found.fileName)

        val fallback = GemmaLocalClient.findOption("unknown_model_id")
        assertEquals(GemmaLocalClient.DEFAULT_OPTION.id, fallback.id)
    }
}
