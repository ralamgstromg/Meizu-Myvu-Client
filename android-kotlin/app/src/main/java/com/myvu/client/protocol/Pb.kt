package com.myvu.client.protocol

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.HashMap

/**
 * Minimal protobuf writer + reader, ported from myvu_client/myvu/linkproto.py
 * (pb_bytes/pb_varint/pb_string/pb_parse). Not a general protobuf library --
 * only what this protocol needs.
 *
 * The reader is deliberately lenient in the same places the Python one is:
 * inbound data comes off a radio from a device we do not control, so a
 * malformed message must never take the connection down.
 */
object Pb {
    private fun varint(n: Long): ByteArray {
        var value = n
        val out = ByteArrayOutputStream()
        while (true) {
            val b = (value and 0x7FL).toInt()
            value = value ushr 7
            if (value != 0L) {
                out.write(b or 0x80)
            } else {
                out.write(b)
                return out.toByteArray()
            }
        }
    }

    private fun tag(field: Int, wire: Int): ByteArray {
        return varint((field.toLong() shl 3) or wire.toLong())
    }

    @JvmStatic
    fun bytes(field: Int, v: ByteArray): ByteArray {
        return concat(tag(field, 2), varint(v.size.toLong()), v)
    }

    @JvmStatic
    fun varintField(field: Int, v: Long): ByteArray {
        return concat(tag(field, 0), varint(v))
    }

    @JvmStatic
    fun string(field: Int, v: String): ByteArray {
        return bytes(field, v.toByteArray(StandardCharsets.UTF_8))
    }

    /** Convenience for the `a + b + c` byte-array concatenation. */
    @JvmStatic
    fun concat(vararg parts: ByteArray): ByteArray {
        var total = 0
        for (p in parts) total += p.size
        val out = ByteArray(total)
        var off = 0
        for (p in parts) {
            System.arraycopy(p, 0, out, off, p.size)
            off += p.size
        }
        return out
    }

    /** Parse into {fieldNumber: [values]}, mirroring linkproto.pb_parse. */
    @JvmStatic
    fun parse(data: ByteArray): Map<Int, List<PbValue>> {
        val out = HashMap<Int, MutableList<PbValue>>()
        var i = 0
        while (i < data.size) {
            val kv = readVarint(data, i) ?: break
            val key = kv[0]
            i = kv[1].toInt()
            val field = (key ushr 3).toInt()
            val wire = (key and 7L).toInt()

            val value: PbValue
            when (wire) {
                0 -> {
                    val r = readVarint(data, i) ?: return out
                    i = r[1].toInt()
                    value = PbValue.ofVarint(r[0])
                }
                2 -> {
                    val r = readVarint(data, i) ?: return out
                    val len = r[0].toInt()
                    i = r[1].toInt()
                    if (len < 0 || i + len > data.size) return out
                    value = PbValue.ofBytes(data.copyOfRange(i, i + len))
                    i += len
                }
                5 -> {
                    if (i + 4 > data.size) return out
                    value = PbValue.ofBytes(data.copyOfRange(i, i + 4))
                    i += 4
                }
                1 -> {
                    if (i + 8 > data.size) return out
                    value = PbValue.ofBytes(data.copyOfRange(i, i + 8))
                    i += 8
                }
                else -> return out
            }

            val list = out.getOrPut(field) { ArrayList(1) }
            list.add(value)
        }
        return out
    }

    /** Every value seen for field -- needed for repeated fields (e.g. mic audio chunks). */
    @JvmStatic
    fun all(f: Map<Int, List<PbValue>>, field: Int): List<PbValue> {
        return f[field] ?: emptyList()
    }

    @JvmStatic
    fun first(f: Map<Int, List<PbValue>>, field: Int): PbValue? {
        val v = f[field]
        return if (v.isNullOrEmpty()) null else v[0]
    }

    /** First length-delimited value for field, or def if absent/wrong type. */
    @JvmStatic
    fun firstBytes(f: Map<Int, List<PbValue>>, field: Int, def: ByteArray): ByteArray {
        val v = first(f, field)
        return if (v != null && !v.isVarint) v.asBytes() else def
    }

    /** First varint value for field, or def if absent/wrong type. */
    @JvmStatic
    fun firstVarint(f: Map<Int, List<PbValue>>, field: Int, def: Long): Long {
        val v = first(f, field)
        return if (v != null && v.isVarint) v.asVarint() else def
    }

    /** First length-delimited value decoded as UTF-8, or def if absent/wrong type. */
    @JvmStatic
    fun firstString(f: Map<Int, List<PbValue>>, field: Int, def: String?): String? {
        val v = first(f, field)
        return if (v != null && !v.isVarint) v.asString() else def
    }

    /** Returns {value, nextIndex}, or null if the buffer ends mid-varint. */
    private fun readVarint(data: ByteArray, start: Int): LongArray? {
        var shift = 0
        var result = 0L
        var i = start
        while (true) {
            if (i >= data.size || shift > 63) return null
            val b = data[i].toInt() and 0xFF
            i += 1
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) return longArrayOf(result, i.toLong())
            shift += 7
        }
    }
}
