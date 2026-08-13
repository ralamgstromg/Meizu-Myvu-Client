package com.myvu.client.core

import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Thread-safe pool of byte arrays for memory reuse in packet reassembly
 * and audio decoding.
 */
object BufferPool {

    /** Standard size buckets in bytes. */
    @JvmField
    val BUCKET_SIZES = intArrayOf(256, 1024, 4096, 16384, 65536)

    const val DEFAULT_MAX_PER_BUCKET = 16

    private val POOLS = ConcurrentHashMap<Int, ConcurrentLinkedQueue<ByteArray>>()

    init {
        for (size in BUCKET_SIZES) {
            POOLS[size] = ConcurrentLinkedQueue()
        }
    }

    /**
     * Obtains a byte array with capacity of at least [minCapacity].
     * If a pooled buffer of suitable bucket size is available, it will be reused;
     * otherwise, a new byte array is allocated.
     * The returned buffer is cleared (zeroed out).
     */
    @JvmStatic
    fun obtain(minCapacity: Int): ByteArray {
        val bucketSize = findBucketSize(minCapacity)
        if (bucketSize < 0) {
            return ByteArray(minCapacity)
        }

        val queue = POOLS[bucketSize]
        if (queue != null) {
            val buf = queue.poll()
            if (buf != null) {
                Arrays.fill(buf, 0.toByte())
                return buf
            }
        }
        return ByteArray(bucketSize)
    }

    /**
     * Recycles a buffer back to the pool if its length matches a standard size bucket
     * and the bucket capacity limit has not been reached.
     */
    @JvmStatic
    fun recycle(buf: ByteArray?) {
        if (buf == null) return

        val length = buf.size
        val queue = POOLS[length]
        if (queue != null && queue.size < DEFAULT_MAX_PER_BUCKET) {
            queue.offer(buf)
        }
    }

    /**
     * Returns the maximum number of buffers stored per size bucket.
     */
    @JvmStatic
    fun getMaxPerBucket(): Int {
        return DEFAULT_MAX_PER_BUCKET
    }

    /**
     * Returns the current number of pooled buffers in the specified bucket size.
     */
    @JvmStatic
    fun getPooledCount(bucketSize: Int): Int {
        val queue = POOLS[bucketSize]
        return queue?.size ?: 0
    }

    /**
     * Clears all pooled buffers.
     */
    @JvmStatic
    fun clear() {
        for (queue in POOLS.values) {
            queue.clear()
        }
    }

    private fun findBucketSize(minCapacity: Int): Int {
        for (size in BUCKET_SIZES) {
            if (size >= minCapacity) {
                return size
            }
        }
        return -1
    }
}
