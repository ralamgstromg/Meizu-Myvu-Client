package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import java.text.Normalizer
import org.json.JSONObject

/**
 * Gate for side-effecting actions the model asks for (send a message, place a call).
 *
 * The model reads third-party content (unread messages, emails, web pages), so its
 * requests cannot be trusted as the user's intent: an incoming message can carry
 * instructions (indirect prompt injection). Under [ActionPolicy.CONFIRM_SENSITIVE]
 * a sensitive action is held here and only runs when the *user* confirms it on
 * their next utterance via [resolve]. Never call [resolve] with model output.
 */
object SensitiveActionGate {

    enum class Decision { ALLOW, CONFIRM, DENY }

    /** Skills (tool calling and `[SKILL: ...]` tags) that send or call on the user's behalf. */
    val SENSITIVE_SKILLS: Set<String> = setOf(
        "send-whatsapp", "send-telegram", "send-email", "send-sms", "call-contact", "voip-call"
    )

    /** Legacy JSON action types handled by [PhoneActionExecutor.executeAction]. */
    val SENSITIVE_ACTION_TYPES: Set<String> = setOf(
        "send_sms", "make_call", "call_whatsapp", "call_teams", "call_google_chat", "call_meet",
        "open_whatsapp", "open_telegram"
    )

    const val PENDING_TTL_MS = 60_000L
    const val BLOCKED_MESSAGE = "Acción bloqueada por la política de seguridad del asistente."
    const val CANCELLED_MESSAGE = "Acción cancelada."

    private val CONFIRM_PHRASES = setOf(
        "confirmar", "confirmo", "confirma", "si", "si envialo", "si enviar", "si llama", "si llamalo",
        "dale", "adelante", "hazlo", "envialo", "si hazlo", "si confirmo"
    )
    private val CANCEL_PHRASES = setOf("cancelar", "cancela", "cancelalo", "no", "no gracias", "no lo envies", "detente")

    private class Pending(val description: String, val action: suspend () -> String, val createdAt: Long)

    @Volatile private var pending: Pending? = null

    @JvmField
    internal var timeProvider: () -> Long = { System.currentTimeMillis() }

    fun isSensitiveSkill(skillId: String): Boolean = skillId in SENSITIVE_SKILLS

    fun isSensitiveActionType(type: String): Boolean = type in SENSITIVE_ACTION_TYPES

    fun decide(context: Context, sensitive: Boolean): Decision {
        if (!sensitive) return Decision.ALLOW
        return when (Prefs.actionPolicy(context)) {
            ActionPolicy.ALLOW_ALL -> Decision.ALLOW
            ActionPolicy.CONFIRM_SENSITIVE -> Decision.CONFIRM
            ActionPolicy.DENY_ALL -> Decision.DENY
        }
    }

    /** Holds [action] until the user confirms; returns the prompt to show/speak. */
    @Synchronized
    fun hold(description: String, action: suspend () -> String): String {
        pending = Pending(description, action, timeProvider())
        LogBus.log("SensitiveActionGate: holding '$description' until the user confirms")
        return confirmationPrompt(description)
    }

    fun hasPending(): Boolean = livePending() != null

    /**
     * Consumes a pending action with the user's utterance. Returns the action to run
     * on a confirmation, a no-op returning [CANCELLED_MESSAGE] on a cancellation, or
     * null when nothing is pending or the utterance is unrelated (which also drops
     * the pending action so it cannot fire on a later turn).
     */
    @Synchronized
    fun resolve(userText: String?): (suspend () -> String)? {
        val p = livePending()
        pending = null
        if (p == null || userText == null) return null
        val phrase = normalize(userText)
        return when (phrase) {
            in CONFIRM_PHRASES -> {
                LogBus.log("SensitiveActionGate: user confirmed '${p.description}'")
                p.action
            }
            in CANCEL_PHRASES -> {
                LogBus.log("SensitiveActionGate: user cancelled '${p.description}'")
                ({ CANCELLED_MESSAGE })
            }
            else -> {
                LogBus.log("SensitiveActionGate: dropped '${p.description}' (no confirmation)")
                null
            }
        }
    }

    /** Confirms the pending action through a physical gesture (e.g. glasses double tap). */
    @Synchronized
    fun confirmPending(): (suspend () -> String)? {
        val p = livePending()
        pending = null
        if (p != null) LogBus.log("SensitiveActionGate: user confirmed '${p.description}' by gesture")
        return p?.action
    }

    /** Discards the pending action through a physical gesture. Returns true if one was pending. */
    @Synchronized
    fun cancelPending(): Boolean {
        val p = livePending()
        pending = null
        if (p != null) LogBus.log("SensitiveActionGate: user cancelled '${p.description}' by gesture")
        return p != null
    }

    @Synchronized
    fun clear() {
        pending = null
        timeProvider = { System.currentTimeMillis() }
    }

    fun describeSkill(skillId: String, args: JSONObject): String {
        val parts = args.keys().asSequence()
            .map { key -> "$key: ${args.opt(key)}" }
            .joinToString(", ")
        return if (parts.isEmpty()) skillId else "$skillId ($parts)"
    }

    fun describeAction(type: String, arguments: Map<String, String>): String {
        val parts = arguments.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        return if (parts.isEmpty()) type else "$type ($parts)"
    }

    private fun confirmationPrompt(description: String): String =
        "Necesito tu confirmación para ejecutar: $description. Di «confirmar» o toca dos veces la patilla de las gafas para continuar; di «cancelar» o desliza hacia atrás para descartar."

    private fun livePending(): Pending? {
        val p = pending ?: return null
        return if (timeProvider() - p.createdAt <= PENDING_TTL_MS) p else null
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
