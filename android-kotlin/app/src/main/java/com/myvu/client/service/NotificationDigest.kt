package com.myvu.client.service

/**
 * Builds short, readable notification text for the HUD and for speech, limited to a
 * configurable number of words (see Prefs.notificationMaxWords).
 */
object NotificationDigest {

    const val DEFAULT_MAX_WORDS = 20
    const val MIN_WORDS = 5
    const val MAX_WORDS = 100

    private val MESSENGERS = listOf("whatsapp", "telegram", "signal", "messenger")

    private val EMOJI = Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\uFE0F\\u200D]")
    private val WHITESPACE = Regex("\\s+")

    /** Keeps the first [maxWords] words of [text], adding "…" when it was cut. */
    fun limitWords(text: String?, maxWords: Int): String {
        val words = clean(text).split(' ').filter { it.isNotEmpty() }
        if (words.size <= maxWords) return words.joinToString(" ")
        return words.take(maxWords).joinToString(" ").trimEnd(',', ';', ':', '.') + "…"
    }

    /**
     * Spoken form: "Mensaje de Ana en WhatsApp: llego tarde…". The title of a messaging
     * notification is usually the sender; without a title the app name is the source.
     */
    fun spoken(app: String, title: String?, text: String?, maxWords: Int): String {
        val body = limitWords(text, maxWords)
        val isMessenger = MESSENGERS.any { app.contains(it, ignoreCase = true) }
        val (group, sender) = if (isMessenger) splitGroupTitle(clean(title)) else null to clean(title)
        return when {
            group != null && body.isNotEmpty() -> "Mensaje de $sender en el grupo $group de $app: $body"
            sender.isNotEmpty() && body.isNotEmpty() -> "Mensaje de $sender en $app: $body"
            sender.isNotEmpty() -> "Notificación de $app: ${limitWords(sender, maxWords)}"
            body.isNotEmpty() -> "Notificación de $app: $body"
            else -> "Nueva notificación de $app"
        }
    }

    /** HUD form: title is the sender (or app), body is limited to [maxWords]. */
    fun hud(app: String, title: String?, text: String?, maxWords: Int): Pair<String, String> {
        val sender = clean(title).ifEmpty { app }
        return limitWords(sender, 6) to limitWords(text, maxWords)
    }

    /**
     * WhatsApp/Telegram group notifications use "Group: Sender" titles, and senders not in
     * the contacts are prefixed with "~". Returns (group or null, sender).
     */
    internal fun splitGroupTitle(title: String): Pair<String?, String> {
        val parts = title.split(": ", limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            return parts[0].trim() to parts[1].trim().removePrefix("~").trim()
        }
        return null to title.removePrefix("~").trim()
    }

    private fun clean(text: String?): String =
        (text ?: "").replace(EMOJI, "").replace(WHITESPACE, " ").trim()
}
