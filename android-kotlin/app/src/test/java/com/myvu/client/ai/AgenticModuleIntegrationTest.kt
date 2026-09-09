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

    @Test
    fun testPruneToolsForQuery() {
        val dummyTools = listOf(
            ToolDefinition("google_search", "Search google", org.json.JSONObject()),
            ToolDefinition("wikipedia_search", "Search wikipedia", org.json.JSONObject()),
            ToolDefinition("duckduckgo_search", "Search ddg", org.json.JSONObject()),
            ToolDefinition("code_calculator_math", "Math calculator", org.json.JSONObject()),
            ToolDefinition("call_contact", "Call a phone contact", org.json.JSONObject()),
            ToolDefinition("send_whatsapp", "Send a whatsapp message", org.json.JSONObject()),
            ToolDefinition("quick_alarm_timer", "Set an alarm", org.json.JSONObject()),
            ToolDefinition("weather_forecast", "Check weather", org.json.JSONObject())
        )

        // General programming question should only keep general knowledge & math fallback tools
        val prunedGeneral = AgenticToolExecutor.pruneToolsForQuery(
            "cuál es la manera más rápida de buscar un ítem en python",
            dummyTools
        )
        val namesGeneral = prunedGeneral.map { it.name }
        assertTrue(namesGeneral.contains("google_search"))
        assertTrue(namesGeneral.contains("wikipedia_search"))
        assertTrue(namesGeneral.contains("duckduckgo_search"))
        assertTrue(namesGeneral.contains("code_calculator_math"))
        org.junit.Assert.assertFalse(namesGeneral.contains("call_contact"))
        org.junit.Assert.assertFalse(namesGeneral.contains("send_whatsapp"))
        org.junit.Assert.assertFalse(namesGeneral.contains("quick_alarm_timer"))

        // Phone call query should include call_contact
        val prunedCall = AgenticToolExecutor.pruneToolsForQuery("llama a mi hermano", dummyTools)
        assertTrue(prunedCall.any { it.name == "call_contact" })

        // Weather query should include weather_forecast
        val prunedWeather = AgenticToolExecutor.pruneToolsForQuery("cómo está el clima hoy", dummyTools)
        assertTrue(prunedWeather.any { it.name == "weather_forecast" })
    }
}

