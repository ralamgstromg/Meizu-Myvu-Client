package com.myvu.client.service;

import android.app.Notification;
import android.service.notification.StatusBarNotification;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Thread-safe filtering, deduplication, smart text truncation, and rate-limiting engine
 * for notifications before mirroring to the Myvu smart glasses.
 */
public class NotificationFilter {

    public static final int DEFAULT_MAX_TEXT_LEN = 120;
    public static final long DEFAULT_DEDUPE_WINDOW_MS = 3_000L; // 3 seconds
    public static final int DEFAULT_MAX_PER_WINDOW = 10;
    public static final long DEFAULT_RATE_LIMIT_WINDOW_MS = 10_000L; // 10 seconds

    private final long dedupeWindowMs;
    private final int maxPerWindow;
    private final long rateLimitWindowMs;
    private final Clock clock;

    private final Map<String, Long> recentContentHashes = new HashMap<>();
    private final Deque<Long> rateLimitTimestamps = new ArrayDeque<>();

    public interface Clock {
        long currentTimeMillis();
    }

    public NotificationFilter() {
        this(DEFAULT_DEDUPE_WINDOW_MS, DEFAULT_MAX_PER_WINDOW, DEFAULT_RATE_LIMIT_WINDOW_MS, System::currentTimeMillis);
    }

    public NotificationFilter(long dedupeWindowMs, int maxPerWindow, long rateLimitWindowMs, Clock clock) {
        this.dedupeWindowMs = dedupeWindowMs;
        this.maxPerWindow = maxPerWindow;
        this.rateLimitWindowMs = rateLimitWindowMs;
        this.clock = clock != null ? clock : System::currentTimeMillis;
    }

    /**
     * Checks whether a notification should be filtered due to noise flags
     * (FLAG_ONGOING_EVENT, FLAG_GROUP_SUMMARY) or empty content.
     */
    public static boolean isNoiseOrEmpty(int flags, String title, String text) {
        if ((flags & Notification.FLAG_ONGOING_EVENT) != 0) {
            return true;
        }
        if ((flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return true;
        }
        boolean titleEmpty = title == null || title.trim().isEmpty();
        boolean textEmpty = text == null || text.trim().isEmpty();
        return titleEmpty && textEmpty;
    }

    /**
     * Checks if a StatusBarNotification contains noise flags or empty content.
     */
    public static boolean isNoiseOrEmpty(StatusBarNotification sbn, String title, String text) {
        if (sbn == null) return true;
        Notification n = sbn.getNotification();
        if (n == null) return true;
        return isNoiseOrEmpty(n.flags, title, text);
    }

    /**
     * Smart text truncation to maximum 120 characters with clean word boundaries.
     */
    public static String truncate(String text) {
        return truncate(text, DEFAULT_MAX_TEXT_LEN);
    }

    /**
     * Truncates text to maxLen characters with clean word boundaries and trailing "...".
     */
    public static String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        if (maxLen <= 3) {
            return text.substring(0, maxLen);
        }
        int targetLen = maxLen - 3;
        String sub = text.substring(0, targetLen);
        int lastSpace = -1;
        for (int i = sub.length() - 1; i >= 0; i--) {
            if (Character.isWhitespace(sub.charAt(i))) {
                lastSpace = i;
                break;
            }
        }
        if (lastSpace > 0) {
            return sub.substring(0, lastSpace).replaceAll("\\s+$", "") + "...";
        } else {
            return sub + "...";
        }
    }

    /**
     * Thread-safe content-based deduplication.
     * Evaluates identical (title + text) content within the deduplication window.
     */
    public synchronized boolean isDuplicateContent(String title, String text) {
        return isDuplicateContent("", title, text);
    }

    /**
     * Thread-safe content-based deduplication with package context.
     */
    public synchronized boolean isDuplicateContent(String pkg, String title, String text) {
        long now = clock.currentTimeMillis();
        String contentKey = (pkg != null ? pkg : "") + "|" + (title != null ? title : "") + "|" + (text != null ? text : "");

        // Cleanup expired entries
        Iterator<Map.Entry<String, Long>> iterator = recentContentHashes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > dedupeWindowMs) {
                iterator.remove();
            }
        }

        Long lastSeen = recentContentHashes.get(contentKey);
        if (lastSeen != null && (now - lastSeen) <= dedupeWindowMs) {
            return true;
        }

        recentContentHashes.put(contentKey, now);
        return false;
    }

    /**
     * Thread-safe sliding window rate-limiting check.
     * Returns true if allowed, false if rate limit reached.
     */
    public synchronized boolean allowRateLimit() {
        long now = clock.currentTimeMillis();

        // Expire older timestamps outside sliding window
        while (!rateLimitTimestamps.isEmpty() && (now - rateLimitTimestamps.peekFirst()) > rateLimitWindowMs) {
            rateLimitTimestamps.removeFirst();
        }

        if (rateLimitTimestamps.size() >= maxPerWindow) {
            return false;
        }

        rateLimitTimestamps.addLast(now);
        return true;
    }

    /**
     * Resets internal state caches.
     */
    public synchronized void reset() {
        recentContentHashes.clear();
        rateLimitTimestamps.clear();
    }
}
