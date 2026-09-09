package com.myvu.client.ai

import com.myvu.client.skills.Skill
import com.myvu.client.skills.SkillParameter
import com.myvu.client.skills.SkillToolConverter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallingIntegrationTest {

    @Test
    fun testToolDefinitionSerialization() {
        val params = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject().put("city", JSONObject().put("type", "string")))
            .put("required", JSONArray().put("city"))

        val tool = ToolDefinition(
            name = "weather_forecast",
            description = "Consulta el clima actual y pronóstico",
            parameters = params
        )

        val json = tool.toOpenAiJsonObject()
        assertEquals("function", json.getString("type"))
        val fn = json.getJSONObject("function")
        assertEquals("weather_forecast", fn.getString("name"))
        assertEquals("Consulta el clima actual y pronóstico", fn.getString("description"))
        assertEquals("object", fn.getJSONObject("parameters").getString("type"))
    }

    @Test
    fun testChatMessageSerialization() {
        val sysMsg = ChatMessage.system("Instrucción del sistema")
        assertEquals("system", sysMsg.toJsonObject().getString("role"))
        assertEquals("Instrucción del sistema", sysMsg.toJsonObject().getString("content"))

        val userMsg = ChatMessage.user("Hola Aura")
        assertEquals("user", userMsg.toJsonObject().getString("role"))

        val tc = ToolCall(id = "call_99", functionName = "call_contact", argumentsJson = "{\"contact_or_number\":\"Mama\"}")
        val assistantMsg = ChatMessage.assistant(content = null, toolCalls = listOf(tc))
        val assistantJson = assistantMsg.toJsonObject()
        assertEquals("assistant", assistantJson.getString("role"))
        val tcArray = assistantJson.getJSONArray("tool_calls")
        assertEquals(1, tcArray.length())
        assertEquals("call_99", tcArray.getJSONObject(0).getString("id"))

        val toolMsg = ChatMessage.tool(toolCallId = "call_99", content = "{\"status\":\"ok\"}")
        val toolJson = toolMsg.toJsonObject()
        assertEquals("tool", toolJson.getString("role"))
        assertEquals("call_99", toolJson.getString("tool_call_id"))
    }

    @Test
    fun testSkillToolConverter() {
        val skill = Skill(
            id = "send-whatsapp",
            name = "Enviar WhatsApp",
            description = "Envía un mensaje de WhatsApp a un contacto",
            parameters = mapOf(
                "contact" to SkillParameter("contact", "string", "Nombre del contacto", required = true),
                "message" to SkillParameter("message", "string", "Mensaje a enviar", required = true)
            )
        )

        val toolDef = SkillToolConverter.toToolDefinition(skill)
        assertEquals("send_whatsapp", toolDef.name)
        assertEquals("send-whatsapp", SkillToolConverter.toSkillId(toolDef.name))

        val toolJson = toolDef.toOpenAiJsonObject()
        val fn = toolJson.getJSONObject("function")
        val params = fn.getJSONObject("parameters")
        val props = params.getJSONObject("properties")
        assertTrue(props.has("contact"))
        assertTrue(props.has("message"))
        val req = params.getJSONArray("required")
        assertEquals(2, req.length())
    }

    @Test
    fun testLocalAiClientChatBodyConstructionAndResponseParsing() {
        val client = LocalAiClient(
            endpoint = "https://api.litellm.ai/v1/chat/completions",
            apiKey = "sk-test",
            model = "gemini-2.0-flash",
            systemPrompt = "System Aura"
        )

        val messages = listOf(
            ChatMessage.user("Llama a Carlos")
        )

        val tool = ToolDefinition(
            name = "call_contact",
            description = "Realiza llamada",
            parameters = JSONObject().put("type", "object")
        )

        val bodyStr = client.buildChatBody(messages = messages, tools = listOf(tool), jsonMode = false)
        val bodyJson = JSONObject(bodyStr)

        assertEquals("gemini-2.0-flash", bodyJson.getString("model"))
        assertEquals("auto", bodyJson.getString("tool_choice"))
        val toolsArr = bodyJson.getJSONArray("tools")
        assertEquals(1, toolsArr.length())

        val msgsArr = bodyJson.getJSONArray("messages")
        // System prompt was prepended automatically
        assertEquals(2, msgsArr.length())
        assertEquals("system", msgsArr.getJSONObject(0).getString("role"))
        assertEquals("user", msgsArr.getJSONObject(1).getString("role"))

        // Test parsing model response with tool_calls
        val sampleResponse = """
            {
              "id": "chatcmpl-123",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": null,
                    "tool_calls": [
                      {
                        "id": "call_abc123",
                        "type": "function",
                        "function": {
                          "name": "call_contact",
                          "arguments": "{\"contact_or_number\":\"Carlos\"}"
                        }
                      }
                    ]
                  },
                  "finish_reason": "tool_calls"
                }
              ]
            }
        """.trimIndent()

        val parsed = client.parseChatCompletion(sampleResponse)
        assertTrue(parsed.hasToolCalls)
        assertEquals(1, parsed.toolCalls.size)
        val call = parsed.toolCalls[0]
        assertEquals("call_abc123", call.id)
        assertEquals("call_contact", call.functionName)
        assertEquals("{\"contact_or_number\":\"Carlos\"}", call.argumentsJson)
    }
}
