package com.myvu.client.service

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationFilterTest {

    private var currentTime: Long = 1_000_000L
    private lateinit var filter: NotificationFilter

    @Before
    fun setUp() {
        currentTime = 1_000_000L
        filter = NotificationFilter(
            dedupeWindowMs = 8_000L,
            maxPerWindow = 3,
            rateLimitWindowMs = 10_000L,
            clock = { currentTime }
        )
    }

    @Test
    fun isNoiseOrEmptyFiltersOngoingAndGroupSummary() {
        assertTrue(NotificationFilter.isNoiseOrEmpty(Notification.FLAG_ONGOING_EVENT, "Title", "Text"))
        assertTrue(NotificationFilter.isNoiseOrEmpty(Notification.FLAG_GROUP_SUMMARY, "Title", "Text"))
        assertTrue(NotificationFilter.isNoiseOrEmpty(0, "", ""))
        assertTrue(NotificationFilter.isNoiseOrEmpty(0, null, null))
        assertFalse(NotificationFilter.isNoiseOrEmpty(0, "Title", "Text"))
    }

    @Test
    fun truncateHandlesNullAndShortStrings() {
        assertEquals("", NotificationFilter.truncate(null))
        assertEquals("Hello", NotificationFilter.truncate("Hello", 10))
        assertEquals("Hel", NotificationFilter.truncate("Hello", 3))
    }

    @Test
    fun truncateBreaksAtWordBoundaryWhenPossible() {
        val input = "The quick brown fox jumps over the lazy dog"
        // maxLen 15 -> target length 12 -> "The quick br" -> last space at 9 -> "The quick..."
        val truncated = NotificationFilter.truncate(input, 15)
        assertEquals("The quick...", truncated)
    }

    @Test
    fun truncateHandlesNoSpacesInSub() {
        val input = "Supercalifragilisticexpialidocious"
        val truncated = NotificationFilter.truncate(input, 10)
        assertEquals("Superca...", truncated)
    }

    @Test
    fun deduplicatesIdenticalContentWithinWindow() {
        assertFalse(filter.isDuplicateContent("com.pkg", "Title", "Message"))
        assertTrue(filter.isDuplicateContent("com.pkg", "Title", "Message"))

        // Fast forward past dedupe window (8s)
        currentTime += 8_001L
        assertFalse(filter.isDuplicateContent("com.pkg", "Title", "Message"))
    }

    @Test
    fun rateLimitsExceedingWindowCount() {
        assertTrue(filter.allowRateLimit())
        assertTrue(filter.allowRateLimit())
        assertTrue(filter.allowRateLimit())
        assertFalse(filter.allowRateLimit()) // 4th in 10s window (max 3)

        // Fast forward past sliding window (10s)
        currentTime += 10_001L
        assertTrue(filter.allowRateLimit())
    }

    @Test
    fun resetClearsState() {
        filter.isDuplicateContent("com.pkg", "Title", "Message")
        assertTrue(filter.isDuplicateContent("com.pkg", "Title", "Message"))

        filter.reset()

        assertFalse(filter.isDuplicateContent("com.pkg", "Title", "Message"))
    }
}
