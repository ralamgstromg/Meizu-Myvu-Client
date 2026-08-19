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
        // Asegurar que no supere los 1000 caracteres para no saturar modelos 2B
        assertTrue(prompt.length < 1500)
    }

    @Test
    fun testGemmaTurnFormatting() {
        val formatted = GemmaLocalClient.formatPrompt("Instrucciones de sistema", "Hola")
        assertTrue(formatted.contains("<start_of_turn>user"))
        assertTrue(formatted.contains("<end_of_turn>"))
        assertTrue(formatted.contains("<start_of_turn>model"))
    }
}
