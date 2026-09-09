package com.myvu.client.skills

import com.myvu.client.ai.ToolDefinition
import org.json.JSONArray
import org.json.JSONObject

/**
 * Converts native [Skill] models into OpenAPI / JSON Schema [ToolDefinition] specifications
 * understood natively by LiteLLM, Gemini, and OpenAI-compatible endpoints.
 */
object SkillToolConverter {

    /**
     * Converts a single [Skill] into a [ToolDefinition].
     */
    fun toToolDefinition(skill: Skill): ToolDefinition {
        // OpenAI function names must match ^[a-zA-Z0-9_-]+$ with max 64 chars
        val toolName = normalizeToolName(skill.id)
        val description = skill.description.ifBlank { skill.name }

        val propertiesObj = JSONObject()
        val requiredList = JSONArray()

        for ((paramName, param) in skill.parameters) {
            val paramSchema = JSONObject()
            paramSchema.put("type", mapParameterType(param.type))
            paramSchema.put("description", param.description)

            propertiesObj.put(paramName, paramSchema)
            if (param.required) {
                requiredList.put(paramName)
            }
        }

        val parametersSchema = JSONObject()
        parametersSchema.put("type", "object")
        parametersSchema.put("properties", propertiesObj)
        if (requiredList.length() > 0) {
            parametersSchema.put("required", requiredList)
        }

        return ToolDefinition(
            name = toolName,
            description = description,
            parameters = parametersSchema
        )
    }

    /**
     * Converts all currently registered skills in [SkillRegistry] to tool definitions.
     */
    fun getRegisteredToolDefinitions(): List<ToolDefinition> {
        return SkillRegistry.getAllSkills().map { toToolDefinition(it) }
    }

    /**
     * Normalizes a skill id (e.g. "call-contact") to a function name (e.g. "call_contact").
     */
    fun normalizeToolName(skillId: String): String {
        return skillId.replace("-", "_").trim()
    }

    /**
     * Reverts a function name (e.g. "call_contact") back to the registered skill id (e.g. "call-contact").
     */
    fun toSkillId(functionName: String): String {
        val directMatch = SkillRegistry.getSkill(functionName)
        if (directMatch != null) return functionName

        val dashed = functionName.replace("_", "-")
        val dashedMatch = SkillRegistry.getSkill(dashed)
        if (dashedMatch != null) return dashed

        return dashed
    }

    private fun mapParameterType(rawType: String): String {
        return when (rawType.lowercase().trim()) {
            "int", "integer" -> "integer"
            "float", "double", "number" -> "number"
            "bool", "boolean" -> "boolean"
            "array", "list" -> "array"
            "object", "json" -> "object"
            else -> "string"
        }
    }
}
