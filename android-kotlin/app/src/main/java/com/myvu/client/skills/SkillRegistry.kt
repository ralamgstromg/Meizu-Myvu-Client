package com.myvu.client.skills

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.skills.handlers.CallContactHandler
import com.myvu.client.skills.handlers.SendEmailHandler
import com.myvu.client.skills.handlers.SendTelegramHandler
import com.myvu.client.skills.handlers.SendWhatsappHandler

object SkillRegistry {

    private val loadedSkills = mutableMapOf<String, Skill>()
    private val handlers = mutableMapOf<String, SkillHandler>()

    fun initialize(context: Context) {
        loadedSkills.clear()
        handlers.clear()

        // 1. Register Built-in Kotlin Handlers
        registerHandler("call-contact", CallContactHandler())
        registerHandler("send-email", SendEmailHandler())
        registerHandler("send-whatsapp", SendWhatsappHandler())
        registerHandler("send-telegram", SendTelegramHandler())

        // 2. Load SKILL.md manifests from Assets
        val skills = SkillLoader.loadSkillsFromAssets(context)
        for (skill in skills) {
            loadedSkills[skill.id] = skill
        }
        LogBus.log("SkillRegistry: Initialized with ${loadedSkills.size} skills and ${handlers.size} handlers")
    }

    fun registerHandler(skillId: String, handler: SkillHandler) {
        handlers[skillId] = handler
    }

    fun getSkill(skillId: String): Skill? = loadedSkills[skillId]

    fun getHandler(skillId: String): SkillHandler? = handlers[skillId]

    fun getAllSkills(): List<Skill> = loadedSkills.values.toList()

    /**
     * Builds the system prompt addendum instructing the LLM on available skills and format.
     */
    fun buildSystemPromptAddendum(): String {
        if (loadedSkills.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("\n\n### Habilidades Disponibles (Skills)\n")
        sb.append("Tienes acceso a habilidades nativas en el dispositivo. Si el usuario te pide ejecutar una de estas acciones, debes responder INCLUYENDO una llamada a la habilidad con el siguiente formato exacto:\n")
        sb.append("[SKILL: id_habilidad {\"param1\": \"valor1\", ...}]\n\n")
        sb.append("Lista de habilidades:\n")

        for (skill in loadedSkills.values) {
            sb.append("- **${skill.id}**: ${skill.description}\n")
            if (skill.parameters.isNotEmpty()) {
                sb.append("  Parámetros:\n")
                for ((pName, pSpec) in skill.parameters) {
                    val req = if (pSpec.required) "(requerido)" else "(opcional)"
                    sb.append("   * `$pName` ${pSpec.type} $req: ${pSpec.description}\n")
                }
            }
        }
        sb.append("\nEjemplo: [SKILL: call-contact {\"contact_or_number\": \"Mama\"}]\n")
        return sb.toString()
    }
}
