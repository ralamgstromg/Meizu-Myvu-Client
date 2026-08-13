package com.myvu.client.core

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory HTTP response cache with configurable TTL (Time-To-Live).
 * Avoids redundant network requests for geocoding, route calculations, and weather queries.
 */
class HttpCache @JvmOverloads constructor(
    val defaultTtlMs: Long = DEFAULT_TTL_MS
) {

    companion object {
        const val DEFAULT_TTL_MS = 300_000L // 5 minutes

        private val INSTANCE: HttpCache by lazy { HttpCache() }

        @JvmStatic
        fun getInstance(): HttpCache = INSTANCE
    }

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    data class CacheEntry(
        val value: String,
        val expireAtMs: Long
    ) {
        fun isExpired(currentTimeMs: Long): Boolean {
            return currentTimeMs >= expireAtMs
        }
    }

    /** Put a key-value response into the cache with the default TTL. */
    fun put(key: String?, value: String?) {
        put(key, value, defaultTtlMs)
    }

    /** Put a key-value response into the cache with a custom TTL in milliseconds. */
    fun put(key: String?, value: String?, ttlMs: Long) {
        put(key, value, ttlMs, System.currentTimeMillis())
    }

    /** Put a key-value response into the cache with custom TTL and explicit current time (for testing). */
    fun put(key: String?, value: String?, ttlMs: Long, currentTimeMs: Long) {
        if (key == null || value == null) return
        val expireAt = currentTimeMs + ttlMs
        cache[key] = CacheEntry(value, expireAt)
    }

    /** Get cached value if present and not expired, returns null otherwise. */
    fun get(key: String?): String? {
        return get(key, System.currentTimeMillis())
    }

    /** Get cached value using explicit timestamp for clock simulation/testing. */
    fun get(key: String?, currentTimeMs: Long): String? {
        if (key == null) return null
        val entry = cache[key] ?: return null

        if (entry.isExpired(currentTimeMs)) {
            cache.remove(key, entry)
            return null
        }
        return entry.value
    }

    /** Removes a specific key from the cache. */
    fun remove(key: String?) {
        if (key != null) {
            cache.remove(key)
        }
    }

    /** Clears all cached responses. */
    fun clear() {
        cache.clear()
    }

    /** Returns current number of entries in the cache. */
    fun size(): Int {
        return cache.size
    }
}
