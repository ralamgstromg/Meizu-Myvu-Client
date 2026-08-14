package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiActionValidatorTest {

    @Test
    fun parsesAnswerAndWeatherAction() {
        val json = """
            {
              "answer": "Looking up weather for Barranquilla",
              "actions": [
                {
                  "type": "weather_query",
                  "arguments": {
                    "place": "Barranquilla"
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = GeminiActionValidator.parse(json)
        assertEquals("Looking up weather for Barranquilla", parsed.answer)
        assertEquals(1, parsed.actions.size)
        assertEquals("weather_query", parsed.actions[0].type)
        assertEquals("Barranquilla", parsed.actions[0].arguments["place"])
    }

    @Test
    fun rejectsUnknownActionWithoutExecutingIt() {
        val json = """
            {
              "answer": "Executing command",
              "actions": [
                {
                  "type": "send_raw_protocol",
                  "arguments": {
                    "payload": "code=122"
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = GeminiActionValidator.parse(json)
        assertEquals("Executing command", parsed.answer)
        assertTrue(parsed.actions.isEmpty())
    }

    @Test
    fun plainTextBecomesAnswerWithNoActions() {
        val text = "Hello, how can I help you today?"
        val parsed = GeminiActionValidator.parse(text)
        assertEquals("Hello, how can I help you today?", parsed.answer)
        assertTrue(parsed.actions.isEmpty())
    }

    @Test
    fun oversizedArgumentsAreRejected() {
        val longVal = "a".repeat(1001)
        val json = """
            {
              "answer": "Test",
              "actions": [
                {
                  "type": "web_search",
                  "arguments": {
                    "query": "$longVal"
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = GeminiActionValidator.parse(json)
        assertEquals("Test", parsed.answer)
        assertTrue(parsed.actions.isEmpty())
    }
}
