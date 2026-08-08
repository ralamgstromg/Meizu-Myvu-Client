package com.myvu.client.nav;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory cache for OSRM routes to allow offline navigation recovery when network drops.
 */
public class RouteCache {
    private static final int MAX_CACHE_SIZE = 10;
    private final Map<String, Route> cache = new HashMap<>();

    public String buildKey(double startLat, double startLon, double destLat, double destLon) {
        return String.format("%.3f,%.3f->%.3f,%.3f", startLat, startLon, destLat, destLon);
    }

    public synchronized void put(String key, Route route) {
        if (key == null || route == null) return;
        if (cache.size() >= MAX_CACHE_SIZE) {
            cache.clear(); // Simple flush if limit reached
        }
        cache.put(key, route);
    }

    public synchronized Route get(String key) {
        return cache.get(key);
    }

    public synchronized void clear() {
        cache.clear();
    }
}
