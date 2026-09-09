package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.skills.SkillRegistry
import com.myvu.client.skills.SkillToolConverter
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
        val tools = SkillRegistry.getToolDefinitions()
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
}
