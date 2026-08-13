package com.myvu.client.protocol

import java.nio.charset.StandardCharsets

/**
 * One decoded protobuf field value.
 *
 * Python's linkproto.pb_parse puts heterogeneous types in one dict (ints for
 * varints, bytes for length-delimited). Java/Kotlin needs a tagged union instead.
 */
class PbValue private constructor(
    private val varint: Long,
    private val bytes: ByteArray?,
    val isVarint: Boolean
) {
    fun asVarint(): Long {
        check(isVarint) { "field is length-delimited, not a varint" }
        return varint
    }

    fun asBytes(): ByteArray {
        check(!isVarint) { "field is a varint, not length-delimited" }
        return bytes!!
    }

    fun asString(): String {
        return String(asBytes(), StandardCharsets.UTF_8)
    }

    override fun toString(): String {
        return if (isVarint) "varint:$varint" else "bytes[${bytes?.size ?: 0}]"
    }

    companion object {
        @JvmStatic
        fun ofVarint(v: Long): PbValue = PbValue(v, null, true)

        @JvmStatic
        fun ofBytes(v: ByteArray): PbValue = PbValue(0L, v, false)
    }
}
