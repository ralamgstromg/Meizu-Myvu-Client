package com.myvu.client.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.ArrayList

class BufferPoolTest {

    @Before
    fun setUp() {
        BufferPool.clear()
    }

    @Test
    fun testObtainReturnsBufferWithSufficientCapacity() {
        val buf256 = BufferPool.obtain(100)
        assertNotNull(buf256)
        assertTrue(buf256.size >= 100)
        assertEquals(256, buf256.size)

        val buf1024 = BufferPool.obtain(1000)
        assertNotNull(buf1024)
        assertTrue(buf1024.size >= 1000)
        assertEquals(1024, buf1024.size)

        val buf4096 = BufferPool.obtain(4096)
        assertNotNull(buf4096)
        assertEquals(4096, buf4096.size)
    }

    @Test
    fun testRecycleAndReuse() {
        val original = BufferPool.obtain(500) // gets 1024 bucket
        assertEquals(1024, original.size)

        BufferPool.recycle(original)

        val reused = BufferPool.obtain(500)
        assertSame("Recycled buffer should be reused", original, reused)
    }

    @Test
    fun testCapacityLimits() {
        val max = BufferPool.getMaxPerBucket()
        val buffers = ArrayList<ByteArray>()

        // Obtain max + 5 buffers
        for (i in 0 until max + 5) {
            buffers.add(BufferPool.obtain(256))
        }

        // Recycle all max + 5 buffers
        for (b in buffers) {
            BufferPool.recycle(b)
        }

        // Verify pool bucket size does not exceed max limit
        assertEquals(max, BufferPool.getPooledCount(256))
    }

    @Test
    fun testNonStandardOrOverMaxSizesNotPooled() {
        val customBuf = ByteArray(333)
        BufferPool.recycle(customBuf)
        assertEquals(0, BufferPool.getPooledCount(333))

        val hugeBuf = BufferPool.obtain(100000)
        assertEquals(100000, hugeBuf.size)
        BufferPool.recycle(hugeBuf)
        assertEquals(0, BufferPool.getPooledCount(100000))
    }

    @Test
    fun testClearEmptiesPool() {
        val buf = BufferPool.obtain(256)
        BufferPool.recycle(buf)
        assertEquals(1, BufferPool.getPooledCount(256))

        BufferPool.clear()
        assertEquals(0, BufferPool.getPooledCount(256))

        val newBuf = BufferPool.obtain(256)
        assertNotSame(buf, newBuf)
    }

    @Test
    @Throws(InterruptedException::class)
    fun testConcurrentAccess() {
        val threadCount = 10
        val iterations = 1000
        val threads = arrayOfNulls<Thread>(threadCount)

        for (i in 0 until threadCount) {
            threads[i] = Thread {
                for (j in 0 until iterations) {
                    val b = BufferPool.obtain(200)
                    b[0] = (j and 0xFF).toByte()
                    BufferPool.recycle(b)
                }
            }
        }

        for (t in threads) t?.start()
        for (t in threads) t?.join()

        assertTrue(BufferPool.getPooledCount(256) <= BufferPool.getMaxPerBucket())
    }
}
