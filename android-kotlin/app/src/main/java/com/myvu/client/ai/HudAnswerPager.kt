package com.myvu.client.ai

import com.myvu.client.core.LogBus

/**
 * Keeps the full text of the last answer whose HUD view was condensed, so temple
 * swipes can page through it. Pages expire after [TTL_MS] or when a new answer opens.
 */
object HudAnswerPager {

    const val TTL_MS = 90_000L

    @JvmField
    internal var timeProvider: () -> Long = { System.currentTimeMillis() }

    private var pages: List<String> = emptyList()
    private var index = 0
    private var openedAt = 0L

    @Synchronized
    fun open(fullText: String) {
        pages = HudSummary.paginate(fullText)
        index = 0
        openedAt = timeProvider()
        LogBus.log("HudAnswerPager: ${pages.size} page(s) available")
    }

    @Synchronized
    fun isOpen(): Boolean = pages.size > 1 && timeProvider() - openedAt <= TTL_MS

    /** Next page labelled "n/total", or null at the end (which closes the pager). */
    @Synchronized
    fun next(): String? {
        if (!isOpen()) return null
        if (index + 1 >= pages.size) {
            close()
            return null
        }
        index++
        return current()
    }

    /** Previous page, or null when already on the first one. */
    @Synchronized
    fun previous(): String? {
        if (!isOpen() || index == 0) return null
        index--
        return current()
    }

    @Synchronized
    fun close() {
        pages = emptyList()
        index = 0
    }

    private fun current(): String = "(${index + 1}/${pages.size}) ${pages[index]}"
}
