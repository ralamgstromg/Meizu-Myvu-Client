package com.myvu.client.skills

import com.myvu.client.core.LogBus
import java.io.BufferedReader
import java.io.StringReader

object SkillParser {

    fun parse(markdownContent: String): Skill? {
        if (markdownContent.isBlank()) return null

        val reader = BufferedReader(StringReader(markdownContent))
        var line = reader.readLine()

        // Check if starts with YAML frontmatter separator
        if (line == null || line.trim() != "---") {
            LogBus.warn("SkillParser: Missing frontmatter start '---'")
            return null
        }

        var id = ""
        var name = ""
        var description = ""
        val parameters = mutableMapOf<String, SkillParameter>()
        var inParametersBlock = false
        var currentParamName = ""

        val instructionsBuilder = StringBuilder()
        var frontmatterEnded = false

        while (true) {
            line = reader.readLine() ?: break

            if (!frontmatterEnded) {
                if (line.trim() == "---") {
                    frontmatterEnded = true
                    continue
                }

                val trimmed = line.trim()
                if (trimmed.startsWith("id:")) {
                    id = trimmed.substringAfter("id:").trim()
                    inParametersBlock = false
                } else if (trimmed.startsWith("name:")) {
                    name = trimmed.substringAfter("name:").trim()
                    inParametersBlock = false
                } else if (trimmed.startsWith("description:")) {
                    description = trimmed.substringAfter("description:").trim()
                    inParametersBlock = false
                } else if (trimmed.startsWith("parameters:")) {
                    inParametersBlock = true
                } else if (inParametersBlock && line.startsWith("  ")) {
                    // Parameter line, e.g.  to: { type: string, description: "...", required: true }
                    val paramLine = line.trim()
                    if (paramLine.contains(":")) {
                        val paramName = paramLine.substringBefore(":").trim()
                        val paramBody = paramLine.substringAfter(":").trim()
                        val param = parseParameterBody(paramName, paramBody)
                        if (param != null) {
                            parameters[paramName] = param
                        }
                    }
                }
            } else {
                instructionsBuilder.append(line).append("\n")
            }
        }

        if (id.isEmpty()) return null

        return Skill(
            id = id,
            name = name.ifEmpty { id },
            description = description,
            parameters = parameters,
            instructions = instructionsBuilder.toString().trim()
        )
    }

    private fun parseParameterBody(name: String, body: String): SkillParameter? {
        // Example body: { type: string, description: "...", required: true }
        var type = "string"
        var description = ""
        var required = false

        if (body.startsWith("{") && body.endsWith("}")) {
            val content = body.substring(1, body.length - 1)
            val parts = content.split(",")
            for (part in parts) {
                val kv = part.split(":", limit = 2)
                if (kv.size == 2) {
                    val key = kv[0].trim().lowercase()
                    var value = kv[1].trim()
                    if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
                        value = value.substring(1, value.length - 1)
                    }
                    when (key) {
                        "type" -> type = value
                        "description" -> description = value
                        "required" -> required = value.lowercase() == "true"
                    }
                }
            }
        }
        return SkillParameter(name = name, type = type, description = description, required = required)
    }
}
