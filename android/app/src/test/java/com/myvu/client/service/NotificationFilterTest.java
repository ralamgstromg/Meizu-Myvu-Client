package com.myvu.client.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Notification;

import org.junit.Before;
import org.junit.Test;

public class NotificationFilterTest {

    private TestClock clock;
    private NotificationFilter filter;

    private static class TestClock implements NotificationFilter.Clock {
        private long currentTime = 1000000L;

        @Override
        public long currentTimeMillis() {
            return currentTime;
        }

        public void advanceMs(long ms) {
            currentTime += ms;
        }
    }

    @Before
    public void setUp() {
        clock = new TestClock();
        filter = new NotificationFilter(3000L, 10, 10000L, clock);
    }

    @Test
    public void contentDeduplicationWithinWindow() {
        String title = "WhatsApp";
        String text = "Hello, how are you doing today?";

        // First attempt: should not be duplicate
        assertFalse(filter.isDuplicateContent(title, text));

        // Immediate repeat within 3-second window: should be duplicate
        clock.advanceMs(1000);
        assertTrue(filter.isDuplicateContent(title, text));

        // Different message within 3-second window: should not be duplicate
        assertFalse(filter.isDuplicateContent(title, "Different message content"));

        // Advance time past 3-second window (3001 ms from original or last insertion)
        clock.advanceMs(3001);
        assertFalse(filter.isDuplicateContent(title, text));
    }

    @Test
    public void smartTextTruncationWordBoundaries() {
        // Short text under 120 chars
        String shortText = "Short message that is less than 120 characters.";
        assertEquals(shortText, NotificationFilter.truncate(shortText));

        // Exact 120 chars text
        StringBuilder exact120 = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            exact120.append("1234567890");
        }
        assertEquals(120, exact120.length());
        assertEquals(exact120.toString(), NotificationFilter.truncate(exact120.toString()));

        // Over 120 chars with clean word boundary truncation
        String longTextWithWords = "This is a very long notification message that exceeds the maximum length of one hundred and twenty characters allowed for display on the smart glasses lens.";
        assertTrue(longTextWithWords.length() > 120);

        String truncated = NotificationFilter.truncate(longTextWithWords);
        assertTrue(truncated.length() <= 120);
        assertTrue(truncated.endsWith("..."));
        // Check clean word boundary (no partial words before "...")
        String bodyBeforeEllipsis = truncated.substring(0, truncated.length() - 3);
        assertFalse(bodyBeforeEllipsis.endsWith(" "));

        // Over 120 chars without spaces (single long string)
        StringBuilder noSpaces = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            noSpaces.append("a");
        }
        String truncatedNoSpaces = NotificationFilter.truncate(noSpaces.toString());
        assertEquals(120, truncatedNoSpaces.length());
        assertTrue(truncatedNoSpaces.endsWith("..."));
    }

    @Test
    public void filteringNoiseFlagsAndEmptyContent() {
        // Ongoing event flag (FLAG_ONGOING_EVENT = 2)
        assertTrue(NotificationFilter.isNoiseOrEmpty(Notification.FLAG_ONGOING_EVENT, "Title", "Text"));

        // Group summary flag (FLAG_GROUP_SUMMARY = 512)
        assertTrue(NotificationFilter.isNoiseOrEmpty(Notification.FLAG_GROUP_SUMMARY, "Title", "Text"));

        // Empty content (both title and text null or whitespace)
        assertTrue(NotificationFilter.isNoiseOrEmpty(0, "", ""));
        assertTrue(NotificationFilter.isNoiseOrEmpty(0, null, null));
        assertTrue(NotificationFilter.isNoiseOrEmpty(0, "   ", "  "));

        // Normal notification (valid flag 0 and non-empty content)
        assertFalse(NotificationFilter.isNoiseOrEmpty(0, "Title", "Text"));
        assertFalse(NotificationFilter.isNoiseOrEmpty(0, "Title", ""));
        assertFalse(NotificationFilter.isNoiseOrEmpty(0, "", "Text"));
    }

    @Test
    public void rateLimitingSlidingWindow() {
        // Max 10 per window
        for (int i = 0; i < 10; i++) {
            assertTrue("Notification " + i + " should be allowed", filter.allowRateLimit());
        }

        // 11th notification within window should be rejected
        assertFalse("11th notification within window should be rate-limited", filter.allowRateLimit());

        // Advance 5 seconds (still within 10-second window from start)
        clock.advanceMs(5000);
        assertFalse("Should still be rate-limited at 5s", filter.allowRateLimit());

        // Advance past 10-second window (e.g. +6000 ms -> total 11000 ms)
        clock.advanceMs(6000);
        assertTrue("Notification should be allowed after sliding window expires", filter.allowRateLimit());
    }
}
