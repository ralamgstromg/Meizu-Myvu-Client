package com.myvu.client.transport.ble

import com.myvu.client.protocol.Pb
import java.nio.ByteBuffer
import java.nio.ByteOrder

object BlePackets {
    // control types
    const val TYPE_CMD = 0
    const val TYPE_ACK = 1
    const val TYPE_SINGLE_CMD = 2
    const val TYPE_SINGLE_ACK = 3
    const val TYPE_MNG = 4
    const val TYPE_MNG_ACK = 5
    const val TYPE_FAST_CTR = 6
    const val TYPE_FAST_ACK = 7
    const val TYPE_MIX_CTR = 8
    const val TYPE_SINGLE_CMD_NO_ACK = 9

    // package types
    const val PKG_COMMON_DATA = 0
    const val PKG_STARRY_DATA = 16
    const val PKG_STARRY_DATA_INIT = 17

    // ACK status
    const val ACK_SUCCESS = 0
    const val ACK_READY = 1
    const val ACK_BUSY = 2
    const val ACK_TIMEOUT = 3
    const val ACK_CANCEL = 4
    const val ACK_SYNC = 5

    private fun le(size: Int): ByteBuffer =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    @JvmStatic
    fun dataPacket(seq: Int, payload: ByteArray): ByteArray {
        return Pb.concat(le(2).putShort(seq.toShort()).array(), payload)
    }

    private fun ctrLike(type: Int, frameCount: Int, pkgType: Int): ByteArray {
        return le(6).putShort(0.toShort())
            .put(type.toByte())
            .put(pkgType.toByte())
            .putShort(frameCount.toShort())
            .array()
    }

    @JvmStatic
    fun ctrPacket(frameCount: Int, pkgType: Int): ByteArray {
        return ctrLike(TYPE_CMD, frameCount, pkgType)
    }

    @JvmStatic
    fun fastCtrPacket(frameCount: Int, pkgType: Int): ByteArray {
        return ctrLike(TYPE_FAST_CTR, frameCount, pkgType)
    }

    @JvmStatic
    fun mixCtrPacket(frameCount: Int, pkgType: Int, firstChunk: ByteArray): ByteArray {
        return Pb.concat(ctrLike(TYPE_MIX_CTR, frameCount, pkgType), firstChunk)
    }

    private fun singleLike(type: Int, pkgType: Int, payload: ByteArray): ByteArray {
        val head = le(4).putShort(0.toShort()).put(type.toByte()).put(pkgType.toByte()).array()
        return Pb.concat(head, payload)
    }

    @JvmStatic
    fun singlePacket(pkgType: Int, payload: ByteArray): ByteArray {
        return singleLike(TYPE_SINGLE_CMD, pkgType, payload)
    }

    @JvmStatic
    fun singleNoAckPacket(pkgType: Int, payload: ByteArray): ByteArray {
        return singleLike(TYPE_SINGLE_CMD_NO_ACK, pkgType, payload)
    }

    @JvmStatic
    @JvmOverloads
    fun ackPacket(status: Int, lostSeqs: List<Int>? = null): ByteArray {
        var out = le(4).putShort(0.toShort()).put(TYPE_ACK.toByte()).put(status.toByte()).array()
        if (!lostSeqs.isNullOrEmpty()) {
            val bb = le(lostSeqs.size * 2)
            for (s in lostSeqs) {
                bb.putShort(s.toShort())
            }
            out = Pb.concat(out, bb.array())
        }
        return out
    }

    @JvmStatic
    fun fastAckPacket(status: Int): ByteArray {
        return le(4).putShort(0.toShort()).put(TYPE_FAST_ACK.toByte()).put(status.toByte()).array()
    }

    @JvmStatic
    fun singleAckPacket(status: Int): ByteArray {
        return le(4).putShort(0.toShort()).put(TYPE_SINGLE_ACK.toByte()).put(status.toByte()).array()
    }

    @JvmStatic
    fun parse(raw: ByteArray): BleParsedPacket {
        if (raw.size < 2) return BleParsedPacket(0)

        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val sn = bb.getShort(0).toInt() and 0xFFFF
        if (sn != 0) {
            val p = BleParsedPacket(sn)
            p.value = raw.copyOfRange(2, raw.size)
            return p
        }

        val p = BleParsedPacket(0)
        if (raw.size < 4) return p
        p.type = raw[2].toInt() and 0xFF
        p.command = raw[3].toInt() and 0xFF

        if (p.type == TYPE_MIX_CTR) {
            if (raw.size >= 6) {
                p.params.add(bb.getShort(4).toInt() and 0xFFFF)
                p.value = raw.copyOfRange(6, raw.size)
            }
            return p
        }

        var off = 4
        while (off + 2 <= raw.size) {
            p.params.add(bb.getShort(off).toInt() and 0xFFFF)
            off += 2
        }
        if (p.type == TYPE_SINGLE_CMD || p.type == TYPE_SINGLE_CMD_NO_ACK) {
            p.value = raw.copyOfRange(4, raw.size)
        }
        return p
    }
}
