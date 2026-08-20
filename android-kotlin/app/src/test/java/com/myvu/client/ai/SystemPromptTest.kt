package com.myvu.client.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptTest {
    @Test
    fun testDefaultSystemPromptIsConciseAndContainsActionTags() {
        val prompt = AiClient.DEFAULT_SYSTEM_PROMPT
        assertTrue(prompt.contains("MEIZU MYVU"))
        assertTrue(prompt.contains("ACTION:SEARCH="))
        assertTrue(prompt.contains("ACTION:CALL="))
        assertTrue(prompt.contains("ACTION:WHATSAPP="))
        // Asegurar que se mantenga en tamaño compacto para modelos locales 2B/7B
        assertTrue(prompt.length < 2500)
    }
}
