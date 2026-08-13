package com.myvu.client.ai

import com.myvu.client.core.BufferPool
import com.myvu.client.core.LogBus
import com.myvu.client.protocol.Pb
import com.myvu.client.protocol.PbValue
import java.util.ArrayList
import java.util.TreeSet
import java.util.concurrent.atomic.AtomicInteger

/** Collects the glasses' microphone stream. */
class GlassesMicStream {

    class AudioFrame(
        private val buffer: ByteArray?,
        val length: Int
    ) {
        private val refCount = AtomicInteger(1)

        fun buffer(): ByteArray? = buffer

        fun retain() {
            refCount.incrementAndGet()
        }

        fun release() {
            if (refCount.decrementAndGet() == 0 && buffer != null) {
                BufferPool.recycle(buffer)
            }
        }

        fun copyBytes(): ByteArray {
            if (buffer == null || length <= 0) return ByteArray(0)
            val copy = ByteArray(length)
            System.arraycopy(buffer, 0, copy, 0, length)
            return copy
        }
    }

    private val packets = ArrayList<AudioFrame>()
    private var capturing = false
    @Volatile
    private var lastFrame: AudioFrame? = null
    private val justAdded = ArrayList<AudioFrame>()
    private var unknownSizeCount = 0
    private val observedSizes = TreeSet<Int>()
    private var rejected = 0
    private var structureLogged = false

    fun start() {
        for (frame in packets) {
            frame.release()
        }
        packets.clear()
        lastFrame = null
        justAdded.clear()
        unknownSizeCount = 0
        observedSizes.clear()
        capturing = true
    }

    fun stop() {
        capturing = false
    }

    fun isCapturing(): Boolean = capturing

    fun packetCount(): Int = packets.size

    fun offer(relayBody: ByteArray): Boolean {
        val field5 = extractAudio(relayBody)
        if (field5 == null) {
            rejected++
            return false
        }
        if (!capturing) return true

        justAdded.clear()
        var i = 0
        while (i + 2 <= field5.size) {
            val len = ((field5[i].toInt() and 0xFF) shl 8) or (field5[i + 1].toInt() and 0xFF)
            i += 2
            if (len <= 0 || i + len > field5.size) {
                unknownSizeCount++
                break
            }
            val poolBuf = BufferPool.obtain(len)
            System.arraycopy(field5, i, poolBuf, 0, len)
            val frame = AudioFrame(poolBuf, len)
            i += len

            if (packets.size >= MAX_PACKETS) {
                LogBus.warn("glasses mic buffer full ($MAX_PACKETS) -- stopping")
                frame.release()
                capturing = false
                break
            }
            observedSizes.add(frame.length)
            packets.add(frame)
            justAdded.add(frame)
        }
        if (justAdded.isNotEmpty()) lastFrame = justAdded[justAdded.size - 1]
        return true
    }

    fun justAddedFrames(): List<AudioFrame> = justAdded

    fun justAdded(): List<ByteArray> {
        val list = ArrayList<ByteArray>()
        for (frame in justAdded) {
            list.add(frame.copyBytes())
        }
        return list
    }

    fun lastPacket(): ByteArray? = lastFrame?.copyBytes()

    fun packets(): List<ByteArray> {
        val list = ArrayList<ByteArray>()
        for (frame in packets) {
            list.add(frame.copyBytes())
        }
        return list
    }

    fun unknownSizeCount(): Int = unknownSizeCount

    fun observedSizes(): Set<Int> = observedSizes

    fun rejectedCount(): Int = rejected

    private fun extractAudio(relayBody: ByteArray): ByteArray? {
        try {
            val fields = Pb.parse(relayBody)
            if (!structureLogged) {
                structureLogged = true
                val sb = StringBuilder("code:109 envelope fields:")
                for ((key, value) in fields) {
                    val v = value[0]
                    sb.append(' ').append(key).append('=')
                        .append(if (v.isVarint) "varint" else "${v.asBytes().size}B")
                }
                LogBus.log(sb.toString())
            }
            val audio = Pb.firstBytes(fields, FIELD_AUDIO, ByteArray(0))
            return if (audio.isNotEmpty()) audio else null
        } catch (e: Exception) {
            return null
        }
    }

    companion object {
        private val KNOWN_PACKET_SIZES = intArrayOf(40, 83, 120, 240)
        private const val FIELD_AUDIO = 5
        private const val MAX_PACKETS = 2000

        private fun isKnownSize(length: Int): Boolean {
            for (size in KNOWN_PACKET_SIZES) {
                if (size == length) return true
            }
            return false
        }
    }
}
