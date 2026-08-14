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
        assertEquals("gemma-4-e2b-it-litert-lm", option.id)
        assertEquals("gemma-4-E2B-it.litertlm", option.fileName)
        assertTrue(option.downloadUrl.contains("huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"))
    }
}
