package com.myvu.client.protocol

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedHashMap

/**
 * Faithful port of myvu_client/myvu/tlv.py (TlvBox codec).
 * Wire format (big-endian): concatenation of [tag:1][length:2][value].
 */
class TlvBox {
    @JvmField
    val values: LinkedHashMap<Int, ByteArray> = LinkedHashMap()

    fun putBytes(tag: Int, v: ByteArray): TlvBox {
        values[tag] = v
        return this
    }

    fun putByte(tag: Int, v: Int): TlvBox {
        values[tag] = byteArrayOf((v and 0xFF).toByte())
        return this
    }

    fun putInt(tag: Int, v: Int): TlvBox {
        val bb = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        bb.putInt(v)
        values[tag] = bb.array()
        return this
    }

    fun putBox(tag: Int, box: TlvBox): TlvBox {
        values[tag] = box.serialize()
        return this
    }

    fun getBytes(tag: Int): ByteArray? = values[tag]

    /** Returns null when the tag is absent, so callers can supply a default. */
    fun getByte(tag: Int): Int? {
        val v = values[tag]
        if (v == null || v.isEmpty()) return null
        return v[0].toInt() and 0xFF
    }

    fun getInt(tag: Int): Int? {
        val v = values[tag]
        if (v == null || v.size != 4) return null
        return ByteBuffer.wrap(v).order(ByteOrder.BIG_ENDIAN).int
    }

    fun serialize(): ByteArray {
        val out = ByteArrayOutputStream()
        for ((tag, v) in values) {
            out.write(tag and 0xFF)
            out.write((v.size shr 8) and 0xFF)
            out.write(v.size and 0xFF)
            try {
                out.write(v)
            } catch (impossible: IOException) {
                throw AssertionError(impossible)
            }
        }
        return out.toByteArray()
    }

    companion object {
        @JvmStatic
        fun parse(data: ByteArray): TlvBox {
            val box = TlvBox()
            var i = 0
            val n = data.size
            while (i + 3 <= n) {
                val tag = data[i].toInt() and 0xFF
                val length = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i + 2].toInt() and 0xFF)
                i += 3
                if (i + length > n) break
                box.values[tag] = data.copyOfRange(i, i + length)
                i += length
            }
            return box
        }
    }
}
