package com.myvu.client.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory HTTP response cache with configurable TTL (Time-To-Live).
 * Avoids redundant network requests for geocoding, route calculations, and weather queries.
 */
public class HttpCache {

    private static final HttpCache INSTANCE = new HttpCache();
    private static final long DEFAULT_TTL_MS = 300_000L; // 5 minutes

    private final long defaultTtlMs;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public static HttpCache getInstance() {
        return INSTANCE;
    }

    public HttpCache() {
        this(DEFAULT_TTL_MS);
    }

    public HttpCache(long defaultTtlMs) {
        this.defaultTtlMs = defaultTtlMs;
    }

    public static class CacheEntry {
        public final String value;
        public final long expireAtMs;

        public CacheEntry(String value, long expireAtMs) {
            this.value = value;
            this.expireAtMs = expireAtMs;
        }

        public boolean isExpired(long currentTimeMs) {
            return currentTimeMs >= expireAtMs;
        }
    }

    /** Put a key-value response into the cache with the default TTL. */
    public void put(String key, String value) {
        put(key, value, defaultTtlMs);
    }

    /** Put a key-value response into the cache with a custom TTL in milliseconds. */
    public void put(String key, String value, long ttlMs) {
        put(key, value, ttlMs, System.currentTimeMillis());
    }

    /** Put a key-value response into the cache with custom TTL and explicit current time (for testing). */
    public void put(String key, String value, long ttlMs, long currentTimeMs) {
        if (key == null || value == null) return;
        long expireAt = currentTimeMs + ttlMs;
        cache.put(key, new CacheEntry(value, expireAt));
    }

    /** Get cached value if present and not expired, returns null otherwise. */
    public String get(String key) {
        return get(key, System.currentTimeMillis());
    }

    /** Get cached value using explicit timestamp for clock simulation/testing. */
    public String get(String key, long currentTimeMs) {
        if (key == null) return null;
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;

        if (entry.isExpired(currentTimeMs)) {
            cache.remove(key, entry);
            return null;
        }
        return entry.value;
    }

    /** Removes a specific key from the cache. */
    public void remove(String key) {
        if (key != null) {
            cache.remove(key);
        }
    }

    /** Clears all cached responses. */
    public void clear() {
        cache.clear();
    }

    /** Returns current number of entries in the cache. */
    public int size() {
        return cache.size();
    }

    public long getDefaultTtlMs() {
        return defaultTtlMs;
    }
}
