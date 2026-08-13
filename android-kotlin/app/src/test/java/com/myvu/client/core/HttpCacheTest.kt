package com.myvu.client.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class HttpCacheTest {

    private lateinit var cache: HttpCache

    @Before
    fun setUp() {
        cache = HttpCache(1000) // 1000ms default TTL for testing
        HttpCache.getInstance().clear()
    }

    @Test
    fun testPutAndGet() {
        cache.put("https://api.example.com/data", "{\"status\":\"ok\"}")
        val cached = cache.get("https://api.example.com/data")
        assertNotNull(cached)
        assertEquals("{\"status\":\"ok\"}", cached)
    }

    @Test
    fun testGetNonExistentKey() {
        assertNull(cache.get("https://api.example.com/nonexistent"))
    }

    @Test
    fun testTtlExpirationWithSimulatedClock() {
        val now = 1000000L
        cache.put("key1", "val1", 500, now) // 500ms TTL starting at now

        assertEquals("val1", cache.get("key1", now))
        assertEquals("val1", cache.get("key1", now + 499))
        assertNull("Should expire at now + 500ms", cache.get("key1", now + 500))
        assertNull("Should remain null after expiry", cache.get("key1", now + 1000))
    }

    @Test
    fun testExpirationRemovesItem() {
        val now = 1000000L
        cache.put("key1", "val1", 100, now)
        assertEquals(1, cache.size())

        assertNull(cache.get("key1", now + 200))
        assertEquals(0, cache.size())
    }

    @Test
    fun testClear() {
        cache.put("k1", "v1")
        cache.put("k2", "v2")
        assertEquals(2, cache.size())

        cache.clear()
        assertEquals(0, cache.size())
        assertNull(cache.get("k1"))
        assertNull(cache.get("k2"))
    }

    @Test
    fun testRemove() {
        cache.put("k1", "v1")
        cache.put("k2", "v2")

        cache.remove("k1")
        assertNull(cache.get("k1"))
        assertEquals("v2", cache.get("k2"))
    }

    @Test
    fun testSingletonInstance() {
        val globalCache = HttpCache.getInstance()
        globalCache.put("globalKey", "globalVal")
        assertEquals("globalVal", HttpCache.getInstance().get("globalKey"))
    }

    @Test
    @Throws(InterruptedException::class)
    fun testThreadSafety() {
        val threads = 10
        val operationsPerThread = 500
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        for (i in 0 until threads) {
            val threadId = i
            executor.submit {
                try {
                    for (j in 0 until operationsPerThread) {
                        val key = "key_" + (j % 50)
                        val value = "val_" + threadId + "_" + j
                        cache.put(key, value, 5000)
                        cache.get(key)
                        if (j % 10 == 0) {
                            cache.remove(key)
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("All threads should complete", latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()
    }
}
