package com.myvu.client.ai

/**
 * Shortens an answer for the glasses HUD while the full text is spoken.
 * Keeps whole leading sentences up to [maxChars]; a first sentence that is
 * already too long is cut at a word boundary with an ellipsis.
 */
object HudSummary {

    const val DEFAULT_MAX_CHARS = 160

    private val SENTENCE_END = Regex("(?<=[.!?…])\\s+|\\n+")

    fun condense(text: String, maxChars: Int = DEFAULT_MAX_CHARS): String {
        val clean = text.trim()
        if (clean.length <= maxChars) return clean

        val sb = StringBuilder()
        for (sentence in clean.split(SENTENCE_END).map { it.trim() }.filter { it.isNotEmpty() }) {
            val next = if (sb.isEmpty()) sentence.length else sb.length + 1 + sentence.length
            if (next > maxChars) break
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(sentence)
        }
        if (sb.isNotEmpty()) return sb.toString()

        val cut = clean.take(maxChars - 1)
        val lastSpace = cut.lastIndexOf(' ')
        return (if (lastSpace > maxChars / 2) cut.substring(0, lastSpace) else cut).trimEnd(',', ';', ':', ' ') + "…"
    }

    /**
     * Splits [text] into HUD pages of at most [maxChars]: whole sentences when they fit,
     * word chunks for longer sentences. For sentence-shaped text the first page equals
     * [condense].
     */
    fun paginate(text: String, maxChars: Int = DEFAULT_MAX_CHARS): List<String> {
        val pages = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) pages.add(current.toString())
            current.setLength(0)
        }
        fun append(piece: String) {
            if (current.isNotEmpty() && current.length + 1 + piece.length > maxChars) flush()
            if (current.isNotEmpty()) current.append(' ')
            current.append(piece)
        }
        for (sentence in text.trim().split(SENTENCE_END).map { it.trim() }.filter { it.isNotEmpty() }) {
            if (sentence.length <= maxChars) {
                append(sentence)
            } else {
                flush()
                for (word in sentence.split(Regex("\\s+"))) append(word)
                flush()
            }
        }
        flush()
        return pages
    }
}
