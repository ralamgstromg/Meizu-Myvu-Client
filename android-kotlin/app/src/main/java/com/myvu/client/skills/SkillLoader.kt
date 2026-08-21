package com.myvu.client.skills

import android.content.Context
import com.myvu.client.core.LogBus
import java.io.InputStreamReader

object SkillLoader {

    private const val ASSETS_SKILLS_PATH = "skills/built-in"

    fun loadSkillsFromAssets(context: Context): List<Skill> {
        val skills = mutableListOf<Skill>()
        try {
            val assetManager = context.assets
            val skillFolders = assetManager.list(ASSETS_SKILLS_PATH) ?: emptyArray()

            for (folder in skillFolders) {
                val skillFilePath = "$ASSETS_SKILLS_PATH/$folder/SKILL.md"
                try {
                    val inputStream = assetManager.open(skillFilePath)
                    val content = InputStreamReader(inputStream).use { it.readText() }
                    val skill = SkillParser.parse(content)
                    if (skill != null) {
                        skills.add(skill)
                        LogBus.log("SkillLoader: Loaded skill '${skill.id}' from $skillFilePath")
                    }
                } catch (e: Exception) {
                    LogBus.warn("SkillLoader: Failed to load $skillFilePath: ${e.message}")
                }
            }
        } catch (e: Exception) {
            LogBus.error("SkillLoader: Error listing skills assets", e)
        }
        return skills
    }
}
