package com.myvu.client.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HttpCacheTest {

    private HttpCache cache;

    @Before
    public void setUp() {
        cache = new HttpCache(1000); // 1000ms default TTL for testing
        HttpCache.getInstance().clear();
    }

    @Test
    public void testPutAndGet() {
        cache.put("https://api.example.com/data", "{\"status\":\"ok\"}");
        String cached = cache.get("https://api.example.com/data");
        assertNotNull(cached);
        assertEquals("{\"status\":\"ok\"}", cached);
    }

    @Test
    public void testGetNonExistentKey() {
        assertNull(cache.get("https://api.example.com/nonexistent"));
    }

    @Test
    public void testTtlExpirationWithSimulatedClock() {
        long now = 1000000L;
        cache.put("key1", "val1", 500, now); // 500ms TTL starting at now

        assertEquals("val1", cache.get("key1", now));
        assertEquals("val1", cache.get("key1", now + 499));
        assertNull("Should expire at now + 500ms", cache.get("key1", now + 500));
        assertNull("Should remain null after expiry", cache.get("key1", now + 1000));
    }

    @Test
    public void testExpirationRemovesItem() {
        long now = 1000000L;
        cache.put("key1", "val1", 100, now);
        assertEquals(1, cache.size());

        assertNull(cache.get("key1", now + 200));
        assertEquals(0, cache.size());
    }

    @Test
    public void testClear() {
        cache.put("k1", "v1");
        cache.put("k2", "v2");
        assertEquals(2, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
        assertNull(cache.get("k1"));
        assertNull(cache.get("k2"));
    }

    @Test
    public void testRemove() {
        cache.put("k1", "v1");
        cache.put("k2", "v2");

        cache.remove("k1");
        assertNull(cache.get("k1"));
        assertEquals("v2", cache.get("k2"));
    }

    @Test
    public void testSingletonInstance() {
        HttpCache globalCache = HttpCache.getInstance();
        globalCache.put("globalKey", "globalVal");
        assertEquals("globalVal", HttpCache.getInstance().get("globalKey"));
    }

    @Test
    public void testThreadSafety() throws InterruptedException {
        int threads = 10;
        int operationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key_" + (j % 50);
                        String value = "val_" + threadId + "_" + j;
                        cache.put(key, value, 5000);
                        cache.get(key);
                        if (j % 10 == 0) {
                            cache.remove(key);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue("All threads should complete", latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
    }
}
