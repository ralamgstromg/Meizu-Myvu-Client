package com.myvu.client.skills

import android.content.Context
import com.myvu.client.core.LogBus
import org.json.JSONObject

object SkillExecutor {

    private val SKILL_TAG_REGEX = Regex("\\[SKILL:\\s*([a-zA-Z0-9_-]+)\\s*(\\{.*?\\})?\\]")

    suspend fun processAndExecute(context: Context, llmResponse: String): String {
        if (llmResponse.isBlank()) return llmResponse

        val match = SKILL_TAG_REGEX.find(llmResponse) ?: return llmResponse

        val skillId = match.groupValues[1]
        val rawJsonArgs = match.groupValues.getOrNull(2) ?: "{}"

        val handler = SkillRegistry.getHandler(skillId)
        if (handler == null) {
            LogBus.warn("SkillExecutor: No handler registered for skill '$skillId'")
            return llmResponse
        }

        val jsonArgs = try {
            JSONObject(rawJsonArgs)
        } catch (e: Exception) {
            LogBus.error("SkillExecutor: Invalid JSON arguments for skill '$skillId': $rawJsonArgs", e)
            JSONObject()
        }

        LogBus.log("SkillExecutor: Executing skill '$skillId' with args: $jsonArgs")
        val result = handler.execute(context, jsonArgs)

        val userFeedback = if (result.success) {
            "⚡ [Acción Ejecutada: ${result.message}]"
        } else {
            "⚠️ [Error al ejecutar acción: ${result.message}]"
        }

        // Clean tag from output or append result feedback
        val cleanText = llmResponse.replace(match.value, "").trim()
        return if (cleanText.isEmpty()) userFeedback else "$cleanText\n\n$userFeedback"
    }
}
