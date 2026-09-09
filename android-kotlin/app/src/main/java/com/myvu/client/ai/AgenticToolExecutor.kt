package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.skills.SkillRegistry
import com.myvu.client.skills.SkillToolConverter
import java.text.Normalizer
import org.json.JSONObject

/**
 * Result of an agentic ReAct loop execution.
 */
data class AgenticExecutionResult(
    val finalAnswer: String,
    val executedActions: List<ExecutedToolAction> = emptyList(),
    val totalTurns: Int = 1
)

data class ExecutedToolAction(
    val toolName: String,
    val skillId: String,
    val arguments: JSONObject,
    val success: Boolean,
    val feedback: String
)

/**
 * Multi-turn autonomous tool orchestrator (ReAct loop).
 * Executes tool calls emitted by Gemini via LiteLLM and feeds observations back into the context
 * until a final user-facing response is generated for the HUD / Chat.
 */
class AgenticToolExecutor(
    private val context: Context,
    private val aiClient: AiClient
) {

    suspend fun execute(
        userQuery: String,
        systemPrompt: String = Prefs.systemPrompt(context),
        conversationHistory: List<ChatMessage> = emptyList(),
        maxTurns: Int = 4,
        onToolAction: ((ExecutedToolAction) -> Unit)? = null
    ): AgenticExecutionResult {
        val allTools = SkillRegistry.getToolDefinitions()
        val tools = pruneToolsForQuery(userQuery, allTools)
        val messages = mutableListOf<ChatMessage>()

        // 1. System Prompt as first-class message
        if (systemPrompt.isNotBlank()) {
            val fullSys = systemPrompt + SkillRegistry.buildNativeToolsSystemPrompt()
            messages.add(ChatMessage.system(fullSys))
        }

        // 2. Prior conversation history
        messages.addAll(conversationHistory)

        // 3. Current User Question
        messages.add(ChatMessage.user(userQuery))

        val executedActions = mutableListOf<ExecutedToolAction>()
        var turns = 0
        var finalContent: String? = null

        while (turns < maxTurns) {
            turns++
            LogBus.log("AgenticToolExecutor: Turn $turns started (history size: ${messages.size}, available tools: ${tools.size})")

            val response: ChatCompletionResult = try {
                aiClient.chat(messages = messages, tools = tools, jsonMode = false)
            } catch (e: Exception) {
                LogBus.error("AgenticToolExecutor: Error in chat turn $turns", e)
                return AgenticExecutionResult(
                    finalAnswer = finalContent ?: "No pude procesar la consulta con el agente.",
                    executedActions = executedActions,
                    totalTurns = turns
                )
            }

            if (!response.hasToolCalls) {
                // Final answer reached
                finalContent = response.content
                LogBus.log("AgenticToolExecutor: Model produced final content on turn $turns (length: ${finalContent?.length ?: 0})")
                break
            }

            // Model requested one or more tool calls
            LogBus.log("AgenticToolExecutor: Turn $turns produced ${response.toolCalls.size} tool calls")

            // Append assistant message containing the tool calls
            messages.add(ChatMessage.assistant(content = response.content, toolCalls = response.toolCalls))

            for (toolCall in response.toolCalls) {
                val skillId = SkillToolConverter.toSkillId(toolCall.functionName)
                val handler = SkillRegistry.getHandler(skillId)

                val argsJson = try {
                    JSONObject(toolCall.argumentsJson)
                } catch (e: Exception) {
                    LogBus.warn("AgenticToolExecutor: Could not parse arguments for ${toolCall.functionName}: ${toolCall.argumentsJson}")
                    JSONObject()
                }

                val actionResult = if (handler != null) {
                    try {
                        LogBus.log("AgenticToolExecutor: Executing skill '$skillId' with args: $argsJson")
                        val res = handler.execute(context, argsJson)
                        ExecutedToolAction(
                            toolName = toolCall.functionName,
                            skillId = skillId,
                            arguments = argsJson,
                            success = res.success,
                            feedback = res.message
                        )
                    } catch (e: Exception) {
                        LogBus.error("AgenticToolExecutor: Error executing skill '$skillId'", e)
                        ExecutedToolAction(
                            toolName = toolCall.functionName,
                            skillId = skillId,
                            arguments = argsJson,
                            success = false,
                            feedback = "Error interno al ejecutar habilidad: ${e.message}"
                        )
                    }
                } else {
                    LogBus.warn("AgenticToolExecutor: No handler registered for skill '$skillId'")
                    ExecutedToolAction(
                        toolName = toolCall.functionName,
                        skillId = skillId,
                        arguments = argsJson,
                        success = false,
                        feedback = "La habilidad '$skillId' no está disponible en este dispositivo."
                    )
                }

                executedActions.add(actionResult)
                onToolAction?.invoke(actionResult)

                // Append observation back to LLM context as a tool role message
                val observationPayload = JSONObject()
                    .put("status", if (actionResult.success) "success" else "error")
                    .put("message", actionResult.feedback)
                    .toString()

                messages.add(ChatMessage.tool(toolCallId = toolCall.id, content = observationPayload))
            }
        }

        // If turn limit reached without final content, construct summary from actions
        val safeAnswer = if (!finalContent.isNullOrBlank()) {
            finalContent
        } else if (executedActions.isNotEmpty()) {
            val sb = java.lang.StringBuilder("Acciones realizadas:\n")
            for (act in executedActions) {
                val icon = if (act.success) "✅" else "⚠️"
                sb.append("$icon ${act.skillId}: ${act.feedback}\n")
            }
            sb.toString().trim()
        } else {
            "No se recibió respuesta del modelo."
        }

        return AgenticExecutionResult(
            finalAnswer = safeAnswer,
            executedActions = executedActions,
            totalTurns = turns
        )
    }

    companion object {
        fun pruneToolsForQuery(query: String, allTools: List<ToolDefinition>): List<ToolDefinition> {
            val q = Normalizer.normalize(query, Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .lowercase()
                .trim()

            val selectedToolNames = mutableSetOf<String>()

            // Always add general knowledge, web search and calculator fallback tools
            selectedToolNames.add("google_search")
            selectedToolNames.add("wikipedia_search")
            selectedToolNames.add("duckduckgo_search")
            selectedToolNames.add("code_calculator_math")

            // Communication: Calls
            if (q.contains("llama") || q.contains("marcar") || q.contains("contacto") || q.contains("telefono")) {
                selectedToolNames.add("call_contact")
            }

            // Communication: Messaging (WhatsApp, Telegram, Email)
            if (q.contains("whatsapp") || q.contains("mensaje") || q.contains("escribe") || q.contains("telegram") || q.contains("correo") || q.contains("email")) {
                selectedToolNames.add("send_whatsapp")
                selectedToolNames.add("send_telegram")
                selectedToolNames.add("send_email")
                selectedToolNames.add("unread_whatsapp_summary")
                selectedToolNames.add("unread_telegram_summary")
                selectedToolNames.add("unread_emails_summary")
            }

            // Alarms and Timers
            if (q.contains("alarma") || q.contains("temporizador") || q.contains("despiertame") || q.contains("cuenta regresiva")) {
                selectedToolNames.add("quick_alarm_timer")
            }

            // Notes and Reminders
            if (q.contains("nota") || q.contains("recordatorio") || q.contains("anota") || q.contains("recuerdame") || q.contains("apunta")) {
                selectedToolNames.add("create_note")
                selectedToolNames.add("create_reminder")
            }

            // Voice Recorder
            if (q.contains("graba") || q.contains("audio") || q.contains("dictado")) {
                selectedToolNames.add("ai_voice_recorder")
            }

            // Calendar and Agenda
            if (q.contains("reunion") || q.contains("agenda") || q.contains("calendario") || q.contains("evento") || q.contains("cita") || q.contains("compromiso")) {
                selectedToolNames.add("calendar_events")
                selectedToolNames.add("smart_agenda_planner")
            }

            // Weather
            if (q.contains("clima") || q.contains("temperatura") || q.contains("tiempo") || q.contains("pronostico") || q.contains("lluvia") || q.contains("llover")) {
                selectedToolNames.add("weather_forecast")
            }

            // Currency & Finance
            if (q.contains("dolar") || q.contains("euro") || q.contains("peso") || q.contains("moneda") || q.contains("tasa") || q.contains("cambio") || q.contains("cop") || q.contains("usd") || q.contains("eur")) {
                selectedToolNames.add("currency_rate")
                selectedToolNames.add("currency_convert")
            }

            // News / Social
            if (q.contains("noticia") || q.contains("titular") || q.contains("twitter") || q.contains("tweet") || q.contains(" x ")) {
                selectedToolNames.add("news_search")
                selectedToolNames.add("x_twitter_search")
            }

            // Navigation
            if (q.contains("navega") || q.contains("mapa") || q.contains("como llegar") || q.contains("ruta") || q.contains("direccion")) {
                selectedToolNames.add("hud_navigation")
            }

            // App opening
            if (q.contains("abre") || q.contains("abrir") || q.contains("lanza") || q.contains("aplicacion") || q.contains("app")) {
                selectedToolNames.add("open_app")
            }

            // Translation
            if (q.contains("traduce") || q.contains("traducir") || q.contains("traduccion") || q.contains("en ingles") || q.contains("en frances")) {
                selectedToolNames.add("smart_translate_hud")
            }

            // OCR / Camera
            if (q.contains("escanea") || q.contains("foto") || q.contains("imagen") || q.contains("texto en") || q.contains("lee la")) {
                selectedToolNames.add("smart_ocr_scanner")
            }

            // History / Memory
            if (q.contains("recuerdas") || q.contains("dijiste") || q.contains("historial") || q.contains("conversacion previa")) {
                selectedToolNames.add("rag_history_search")
            }

            // Web summarizer
            if (q.contains("pagina") || q.contains("link") || q.contains("url") || q.contains("web") || q.contains("articulo")) {
                selectedToolNames.add("web_page_summarizer")
            }

            // Notifications
            if (q.contains("notificacion") || q.contains("notificaciones") || q.contains("avisos")) {
                selectedToolNames.add("unread_notifications")
            }

            val filtered = allTools.filter { tool -> selectedToolNames.contains(tool.name) }
            return if (filtered.isNotEmpty()) filtered else allTools
        }
    }
}
