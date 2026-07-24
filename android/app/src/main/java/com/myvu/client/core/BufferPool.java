package com.myvu.client.core;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe pool of byte arrays for memory reuse in packet reassembly
 * and audio decoding.
 */
public class BufferPool {

    /** Standard size buckets in bytes. */
    public static final int[] BUCKET_SIZES = {256, 1024, 4096, 16384, 65536};
    
    public static final int DEFAULT_MAX_PER_BUCKET = 16;

    private static final Map<Integer, ConcurrentLinkedQueue<byte[]>> POOLS = new ConcurrentHashMap<>();

    static {
        for (int size : BUCKET_SIZES) {
            POOLS.put(size, new ConcurrentLinkedQueue<>());
        }
    }

    private BufferPool() {
        // Utility class
    }

    /**
     * Obtains a byte array with capacity of at least {@code minCapacity}.
     * If a pooled buffer of suitable bucket size is available, it will be reused;
     * otherwise, a new byte array is allocated.
     * The returned buffer is cleared (zeroed out).
     */
    public static byte[] obtain(int minCapacity) {
        int bucketSize = findBucketSize(minCapacity);
        if (bucketSize < 0) {
            return new byte[minCapacity];
        }

        ConcurrentLinkedQueue<byte[]> queue = POOLS.get(bucketSize);
        if (queue != null) {
            byte[] buf = queue.poll();
            if (buf != null) {
                Arrays.fill(buf, (byte) 0);
                return buf;
            }
        }
        return new byte[bucketSize];
    }

    /**
     * Recycles a buffer back to the pool if its length matches a standard size bucket
     * and the bucket capacity limit has not been reached.
     */
    public static void recycle(byte[] buf) {
        if (buf == null) return;

        int length = buf.length;
        ConcurrentLinkedQueue<byte[]> queue = POOLS.get(length);
        if (queue != null && queue.size() < DEFAULT_MAX_PER_BUCKET) {
            queue.offer(buf);
        }
    }

    /**
     * Returns the maximum number of buffers stored per size bucket.
     */
    public static int getMaxPerBucket() {
        return DEFAULT_MAX_PER_BUCKET;
    }

    /**
     * Returns the current number of pooled buffers in the specified bucket size.
     */
    public static int getPooledCount(int bucketSize) {
        ConcurrentLinkedQueue<byte[]> queue = POOLS.get(bucketSize);
        return queue != null ? queue.size() : 0;
    }

    /**
     * Clears all pooled buffers.
     */
    public static void clear() {
        for (ConcurrentLinkedQueue<byte[]> queue : POOLS.values()) {
            queue.clear();
        }
    }

    private static int findBucketSize(int minCapacity) {
        for (int size : BUCKET_SIZES) {
            if (size >= minCapacity) {
                return size;
            }
        }
        return -1;
    }
}
