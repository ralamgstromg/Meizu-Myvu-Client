package com.myvu.client.core

/**
 * Text the phone spoke in the last two minutes. The glasses ask the phone to read the same answer or
 * notification again (caller com.tts.assistant / com.tts.notification); checking this record turns those
 * echoes into silence instead of a second reading.
 */
object RecentSpeech {

    private const val WINDOW_MS = 120_000L
    private const val MAX_ENTRIES = 16
    /** Shorter texts only match exactly, so a short "ok" never swallows a long answer. */
    private const val MIN_CONTAINS_LENGTH = 24

    private val lock = Any()
    private val entries = ArrayDeque<Pair<Long, String>>()

    /** Test seam for the time window. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    fun remember(text: String) {
        val key = normalize(text)
        if (key.isEmpty()) return
        synchronized(lock) {
            val now = clock()
            entries.removeAll { now - it.first > WINDOW_MS }
            entries.addLast(now to key)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
    }

    /** True when [text] equals, or contains / is contained in, something spoken within the window. */
    fun wasSpokenRecently(text: String): Boolean {
        val key = normalize(text)
        if (key.isEmpty()) return false
        synchronized(lock) {
            val now = clock()
            return entries.any { (at, spoken) ->
                now - at <= WINDOW_MS && (
                    spoken == key ||
                        (minOf(spoken.length, key.length) >= MIN_CONTAINS_LENGTH &&
                            (spoken.contains(key) || key.contains(spoken)))
                    )
            }
        }
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
