package com.myvu.client.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class BufferPoolTest {

    @Before
    public void setUp() {
        BufferPool.clear();
    }

    @Test
    public void testObtainReturnsBufferWithSufficientCapacity() {
        byte[] buf256 = BufferPool.obtain(100);
        assertNotNull(buf256);
        assertTrue(buf256.length >= 100);
        assertEquals(256, buf256.length);

        byte[] buf1024 = BufferPool.obtain(1000);
        assertNotNull(buf1024);
        assertTrue(buf1024.length >= 1000);
        assertEquals(1024, buf1024.length);

        byte[] buf4096 = BufferPool.obtain(4096);
        assertNotNull(buf4096);
        assertEquals(4096, buf4096.length);
    }

    @Test
    public void testRecycleAndReuse() {
        byte[] original = BufferPool.obtain(500); // gets 1024 bucket
        assertEquals(1024, original.length);

        BufferPool.recycle(original);

        byte[] reused = BufferPool.obtain(500);
        assertSame("Recycled buffer should be reused", original, reused);
    }

    @Test
    public void testCapacityLimits() {
        int max = BufferPool.getMaxPerBucket();
        List<byte[]> buffers = new ArrayList<>();

        // Obtain max + 5 buffers
        for (int i = 0; i < max + 5; i++) {
            buffers.add(BufferPool.obtain(256));
        }

        // Recycle all max + 5 buffers
        for (byte[] b : buffers) {
            BufferPool.recycle(b);
        }

        // Verify pool bucket size does not exceed max limit
        assertEquals(max, BufferPool.getPooledCount(256));
    }

    @Test
    public void testNonStandardOrOverMaxSizesNotPooled() {
        byte[] customBuf = new byte[333];
        BufferPool.recycle(customBuf);
        assertEquals(0, BufferPool.getPooledCount(333));

        byte[] hugeBuf = BufferPool.obtain(100000);
        assertEquals(100000, hugeBuf.length);
        BufferPool.recycle(hugeBuf);
        assertEquals(0, BufferPool.getPooledCount(100000));
    }

    @Test
    public void testClearEmptiesPool() {
        byte[] buf = BufferPool.obtain(256);
        BufferPool.recycle(buf);
        assertEquals(1, BufferPool.getPooledCount(256));

        BufferPool.clear();
        assertEquals(0, BufferPool.getPooledCount(256));

        byte[] newBuf = BufferPool.obtain(256);
        assertNotSame(buf, newBuf);
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int iterations = 1000;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < iterations; j++) {
                    byte[] b = BufferPool.obtain(200);
                    b[0] = (byte) (j & 0xFF);
                    BufferPool.recycle(b);
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assertTrue(BufferPool.getPooledCount(256) <= BufferPool.getMaxPerBucket());
    }
}
