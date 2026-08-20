package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgenticModuleIntegrationTest {

    @Test
    fun testDefaultSystemPromptIntegrity() {
        val prompt = AiClient.DEFAULT_SYSTEM_PROMPT
        assertTrue(prompt.contains("ACTION:NOTE_SEARCH"))
        assertTrue(prompt.contains("ACTION:REMINDER_SEARCH"))
        assertTrue(prompt.contains("ACTION:VOICE_RECORDING_SEARCH"))
        assertTrue(prompt.contains("ACTION:TODO_SEARCH"))
    }
}
