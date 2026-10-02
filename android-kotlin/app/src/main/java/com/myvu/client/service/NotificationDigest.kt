package com.myvu.client.service

/**
 * Builds short, readable notification text for the HUD and for speech, limited to a
 * configurable number of words (see Prefs.notificationMaxWords).
 */
object NotificationDigest {

    const val DEFAULT_MAX_WORDS = 20
    const val MIN_WORDS = 5
    const val MAX_WORDS = 100

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
        val sender = clean(title)
        return when {
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

    private fun clean(text: String?): String =
        (text ?: "").replace(EMOJI, "").replace(WHITESPACE, " ").trim()
}
