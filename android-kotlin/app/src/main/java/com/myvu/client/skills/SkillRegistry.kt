package com.myvu.client.skills

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.skills.handlers.AiVoiceRecorderHandler
import com.myvu.client.skills.handlers.CalendarEventsHandler
import com.myvu.client.skills.handlers.CallContactHandler
import com.myvu.client.skills.handlers.CreateNoteHandler
import com.myvu.client.skills.handlers.CreateReminderHandler
import com.myvu.client.skills.handlers.CurrencyConvertHandler
import com.myvu.client.skills.handlers.CurrencyRateHandler
import com.myvu.client.skills.handlers.DuckDuckGoSearchHandler
import com.myvu.client.skills.handlers.GoogleSearchHandler
import com.myvu.client.skills.handlers.NewsSearchHandler
import com.myvu.client.skills.handlers.SendEmailHandler
import com.myvu.client.skills.handlers.SendTelegramHandler
import com.myvu.client.skills.handlers.SendWhatsappHandler
import com.myvu.client.skills.handlers.HudNavigationHandler
import com.myvu.client.skills.handlers.UnreadEmailsSummaryHandler
import com.myvu.client.skills.handlers.UnreadNotificationsHandler
import com.myvu.client.skills.handlers.UnreadTelegramSummaryHandler
import com.myvu.client.skills.handlers.UnreadWhatsappSummaryHandler
import com.myvu.client.skills.handlers.WeatherForecastHandler
import com.myvu.client.skills.handlers.WikipediaSearchHandler
import com.myvu.client.skills.handlers.XTwitterSearchHandler
import com.myvu.client.skills.handlers.SmartOcrScannerHandler
import com.myvu.client.skills.handlers.WebPageSummarizerHandler
import com.myvu.client.skills.handlers.CodeCalculatorMathHandler
import com.myvu.client.skills.handlers.SmartAgendaPlannerHandler
import com.myvu.client.skills.handlers.SmartTranslateHudHandler
import com.myvu.client.skills.handlers.RagHistorySearchHandler
import com.myvu.client.skills.handlers.QuickAlarmTimerHandler

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
        registerHandler("google-search", GoogleSearchHandler())
        registerHandler("wikipedia-search", WikipediaSearchHandler())
        registerHandler("currency-rate", CurrencyRateHandler())
        registerHandler("currency-convert", CurrencyConvertHandler())
        registerHandler("weather-forecast", WeatherForecastHandler())
        registerHandler("create-note", CreateNoteHandler())
        registerHandler("create-reminder", CreateReminderHandler())
        registerHandler("ai-voice-recorder", AiVoiceRecorderHandler())
        registerHandler("calendar-events", CalendarEventsHandler())
        registerHandler("unread-notifications", UnreadNotificationsHandler())
        registerHandler("news-search", NewsSearchHandler())
        registerHandler("duckduckgo-search", DuckDuckGoSearchHandler())
        registerHandler("unread-emails-summary", UnreadEmailsSummaryHandler())
        registerHandler("unread-whatsapp-summary", UnreadWhatsappSummaryHandler())
        registerHandler("unread-telegram-summary", UnreadTelegramSummaryHandler())
        registerHandler("x-twitter-search", XTwitterSearchHandler())
        registerHandler("hud-navigation", HudNavigationHandler())

        // Productivity Skills
        registerHandler("smart-ocr-scanner", SmartOcrScannerHandler())
        registerHandler("web-page-summarizer", WebPageSummarizerHandler())
        registerHandler("code-calculator-math", CodeCalculatorMathHandler())
        registerHandler("smart-agenda-planner", SmartAgendaPlannerHandler())
        registerHandler("smart-translate-hud", SmartTranslateHudHandler())
        registerHandler("rag-history-search", RagHistorySearchHandler())
        registerHandler("quick-alarm-timer", QuickAlarmTimerHandler())

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
        sb.append("\n\n### Identidad y Habilidades Disponibles (Skills)\n")
        sb.append("Te llamas Aura. Responde siempre en Español con configuración regional de Colombia (es-CO, COP $).\n")
        sb.append("Tienes acceso a habilidades nativas en el dispositivo. Si el usuario te pide ejecutar una de estas acciones (o si es necesario para responder), debes responder INCLUYENDO una llamada a la habilidad con el siguiente formato exacto:\n")
        sb.append("[SKILL: id_habilidad {\"param1\": \"valor1\", ...}]\n\n")
        sb.append("Lista de habilidades activas:\n")

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
