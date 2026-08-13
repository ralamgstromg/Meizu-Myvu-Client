package com.myvu.client.transport.bt

import com.myvu.client.core.BufferPool
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FrameReassembler {

    private var buf: ByteArray = BufferPool.obtain(256)
    private var bufLen: Int = 0

    fun feed(data: ByteArray?): List<ByteArray> {
        if (data != null && data.isNotEmpty()) {
            val needed = bufLen + data.size
            if (needed > buf.size) {
                val old = buf
                buf = BufferPool.obtain(needed)
                if (bufLen > 0) {
                    System.arraycopy(old, 0, buf, 0, bufLen)
                }
                BufferPool.recycle(old)
            }
            System.arraycopy(data, 0, buf, bufLen, data.size)
            bufLen = needed
        }

        val out = mutableListOf<ByteArray>()
        while (true) {
            val idx = indexOfMagic(buf, bufLen)
            if (idx < 0) {
                if (bufLen > RfcommFraming.MAGIC.size) {
                    val keep = RfcommFraming.MAGIC.size
                    System.arraycopy(buf, bufLen - keep, buf, 0, keep)
                    bufLen = keep
                }
                break
            }
            if (idx > 0) {
                val remaining = bufLen - idx
                System.arraycopy(buf, idx, buf, 0, remaining)
                bufLen = remaining
            }
            if (bufLen < HEADER) break

            val length = ByteBuffer.wrap(buf, 4, 4).order(ByteOrder.BIG_ENDIAN).int

            if (length < MIN_FRAME || length > MAX_FRAME) {
                val skip = RfcommFraming.MAGIC.size
                val remaining = bufLen - skip
                System.arraycopy(buf, skip, buf, 0, remaining)
                bufLen = remaining
                continue
            }

            val total = HEADER + length
            if (bufLen < total) break

            val frame = ByteArray(length - MIN_FRAME)
            System.arraycopy(buf, HEADER + MIN_FRAME, frame, 0, frame.size)
            out.add(frame)

            val remaining = bufLen - total
            System.arraycopy(buf, total, buf, 0, remaining)
            bufLen = remaining
        }
        return out
    }

    fun reset() {
        if (buf.isNotEmpty()) {
            BufferPool.recycle(buf)
            buf = BufferPool.obtain(256)
        }
        bufLen = 0
    }

    companion object {
        private const val HEADER = 8
        private val MIN_FRAME = RfcommFraming.PREFIX.size
        const val MAX_FRAME = 64 * 1024

        private fun indexOfMagic(data: ByteArray, length: Int): Int {
            val magic = RfcommFraming.MAGIC
            if (length < magic.size) return -1
            outer@ for (i in 0..length - magic.size) {
                for (j in magic.indices) {
                    if (data[i + j] != magic[j]) continue@outer
                }
                return i
            }
            return -1
        }
    }
}
