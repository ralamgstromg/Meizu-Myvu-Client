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
        assertEquals("gemma-4-e2b-it-int4", option.id)
        assertEquals("gemma-4-e2b-it-gpu-int4.task", option.fileName)
        assertTrue(option.downloadUrl.contains("huggingface.co"))
    }
}
