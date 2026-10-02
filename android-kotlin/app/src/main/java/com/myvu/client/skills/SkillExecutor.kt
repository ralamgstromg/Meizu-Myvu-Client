package com.myvu.client.skills

import android.content.Context
import com.myvu.client.ai.SensitiveActionGate
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

        val cleanText = llmResponse.replace(match.value, "").trim()
        val sensitive = SensitiveActionGate.isSensitiveSkill(skillId)
        when (SensitiveActionGate.decide(context, sensitive)) {
            SensitiveActionGate.Decision.CONFIRM -> {
                val prompt = SensitiveActionGate.hold(SensitiveActionGate.describeSkill(skillId, jsonArgs)) {
                    handler.execute(context, jsonArgs).message
                }
                return if (cleanText.isEmpty()) prompt else "$cleanText $prompt"
            }
            SensitiveActionGate.Decision.DENY -> {
                LogBus.warn("SkillExecutor: Blocked sensitive skill '$skillId' by action policy")
                val blocked = SensitiveActionGate.BLOCKED_MESSAGE
                return if (cleanText.isEmpty()) blocked else "$cleanText $blocked"
            }
            SensitiveActionGate.Decision.ALLOW -> Unit
        }

        LogBus.log("SkillExecutor: Executing skill '$skillId' with args: $jsonArgs")
        val result = handler.execute(context, jsonArgs)

        val userFeedback = if (result.success) {
            result.message
        } else {
            "Error al ejecutar acción: ${result.message}"
        }

        // Clean tag from output or append result feedback
        return if (cleanText.isEmpty()) userFeedback else "$cleanText $userFeedback"
    }
}
