package com.myvu.client.nav

import java.util.HashMap
import java.util.Locale

class RouteCache {
    private val cache = HashMap<String, Route>()

    fun buildKey(startLat: Double, startLon: Double, destLat: Double, destLon: Double): String {
        return String.format(Locale.US, "%.3f,%.3f->%.3f,%.3f", startLat, startLon, destLat, destLon)
    }

    @Synchronized
    fun put(key: String?, route: Route?) {
        if (key == null || route == null) return
        if (cache.size >= MAX_CACHE_SIZE) {
            cache.clear()
        }
        cache[key] = route
    }

    @Synchronized
    fun get(key: String): Route? = cache[key]

    @Synchronized
    fun clear() {
        cache.clear()
    }

    companion object {
        private const val MAX_CACHE_SIZE = 10
    }
}
