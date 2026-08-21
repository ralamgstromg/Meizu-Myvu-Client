package com.myvu.client.skills

import android.content.Context
import org.json.JSONObject

data class SkillParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = false
)

data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val parameters: Map<String, SkillParameter> = emptyMap(),
    val instructions: String = ""
)

data class SkillResult(
    val success: Boolean,
    val message: String,
    val payload: Any? = null
)

fun interface SkillHandler {
    suspend fun execute(context: Context, args: JSONObject): SkillResult
}
