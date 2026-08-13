package com.myvu.client.service

import android.app.Notification
import android.service.notification.StatusBarNotification
import java.util.ArrayDeque
import java.util.Deque
import java.util.HashMap

/**
 * Thread-safe filtering, deduplication, smart text truncation, and rate-limiting engine
 * for notifications before mirroring to the Myvu smart glasses.
 */
class NotificationFilter @JvmOverloads constructor(
    private val dedupeWindowMs: Long = DEFAULT_DEDUPE_WINDOW_MS,
    private val maxPerWindow: Int = DEFAULT_MAX_PER_WINDOW,
    private val rateLimitWindowMs: Long = DEFAULT_RATE_LIMIT_WINDOW_MS,
    clock: Clock? = null
) {
    fun interface Clock {
        fun currentTimeMillis(): Long
    }

    private val clock: Clock = clock ?: Clock { System.currentTimeMillis() }
    private val recentContentHashes: MutableMap<String, Long> = HashMap()
    private val rateLimitTimestamps: Deque<Long> = ArrayDeque()

    /**
     * Thread-safe content-based deduplication.
     * Evaluates identical (title + text) content within the deduplication window.
     */
    @Synchronized
    fun isDuplicateContent(title: String?, text: String?): Boolean {
        return isDuplicateContent("", title, text)
    }

    /**
     * Thread-safe content-based deduplication with package context.
     */
    @Synchronized
    fun isDuplicateContent(pkg: String?, title: String?, text: String?): Boolean {
        val now = clock.currentTimeMillis()
        val contentKey = "${pkg ?: ""}|${title ?: ""}|${text ?: ""}"

        // Cleanup expired entries
        val iterator = recentContentHashes.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > dedupeWindowMs) {
                iterator.remove()
            }
        }

        val lastSeen = recentContentHashes[contentKey]
        if (lastSeen != null && (now - lastSeen) <= dedupeWindowMs) {
            return true
        }

        recentContentHashes[contentKey] = now
        return false
    }

    /**
     * Thread-safe sliding window rate-limiting check.
     * Returns true if allowed, false if rate limit reached.
     */
    @Synchronized
    fun allowRateLimit(): Boolean {
        val now = clock.currentTimeMillis()

        // Expire older timestamps outside sliding window
        while (!rateLimitTimestamps.isEmpty() && (now - rateLimitTimestamps.peekFirst()!!) > rateLimitWindowMs) {
            rateLimitTimestamps.removeFirst()
        }

        if (rateLimitTimestamps.size >= maxPerWindow) {
            return false
        }

        rateLimitTimestamps.addLast(now)
        return true
    }

    /**
     * Resets internal state caches.
     */
    @Synchronized
    fun reset() {
        recentContentHashes.clear()
        rateLimitTimestamps.clear()
    }

    companion object {
        const val DEFAULT_MAX_TEXT_LEN: Int = 120
        const val DEFAULT_DEDUPE_WINDOW_MS: Long = 8_000L // 8 seconds
        const val DEFAULT_MAX_PER_WINDOW: Int = 10
        const val DEFAULT_RATE_LIMIT_WINDOW_MS: Long = 10_000L // 10 seconds

        /**
         * Checks whether a notification should be filtered due to noise flags
         * (FLAG_ONGOING_EVENT, FLAG_GROUP_SUMMARY) or empty content.
         */
        @JvmStatic
        fun isNoiseOrEmpty(flags: Int, title: String?, text: String?): Boolean {
            if ((flags and Notification.FLAG_ONGOING_EVENT) != 0) {
                return true
            }
            if ((flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
                return true
            }
            val titleEmpty = title.isNullOrBlank()
            val textEmpty = text.isNullOrBlank()
            return titleEmpty && textEmpty
        }

        /**
         * Checks if a StatusBarNotification contains noise flags or empty content.
         */
        @JvmStatic
        fun isNoiseOrEmpty(sbn: StatusBarNotification?, title: String?, text: String?): Boolean {
            if (sbn == null) return true
            val n = sbn.notification ?: return true
            return isNoiseOrEmpty(n.flags, title, text)
        }

        /**
         * Truncates text to maxLen characters with clean word boundaries and trailing "...".
         */
        @JvmStatic
        @JvmOverloads
        fun truncate(text: String?, maxLen: Int = DEFAULT_MAX_TEXT_LEN): String {
            if (text == null) return ""
            if (text.length <= maxLen) return text
            if (maxLen <= 3) {
                return text.substring(0, maxLen)
            }
            val targetLen = maxLen - 3
            val sub = text.substring(0, targetLen)
            var lastSpace = -1
            for (i in sub.length - 1 downTo 0) {
                if (Character.isWhitespace(sub[i])) {
                    lastSpace = i
                    break
                }
            }
            return if (lastSpace > 0) {
                sub.substring(0, lastSpace).replace(Regex("\\s+$"), "") + "..."
            } else {
                "$sub..."
            }
        }
    }
}
