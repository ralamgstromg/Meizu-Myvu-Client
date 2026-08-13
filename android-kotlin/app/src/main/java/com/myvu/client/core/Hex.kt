package com.myvu.client.core

/** Hex encode/decode. Pure Kotlin so it is usable from JVM unit tests. */
object Hex {
    private val DIGITS = "0123456789abcdef".toCharArray()

    @JvmStatic
    fun encode(data: ByteArray): String {
        return encode(data, 0, data.size)
    }

    @JvmStatic
    fun encode(data: ByteArray, off: Int, len: Int): String {
        val out = CharArray(len * 2)
        for (i in 0 until len) {
            val b = data[off + i].toInt() and 0xFF
            out[i * 2] = DIGITS[b ushr 4]
            out[i * 2 + 1] = DIGITS[b and 0x0F]
        }
        return String(out)
    }

    /** Decodes a hex string, ignoring whitespace. */
    @JvmStatic
    fun decode(hex: String): ByteArray {
        val clean = StringBuilder(hex.length)
        for (i in 0 until hex.length) {
            val c = hex[i]
            if (!Character.isWhitespace(c)) {
                clean.append(c)
            }
        }
        if (clean.length % 2 != 0) {
            throw IllegalArgumentException("hex string has odd length: ${clean.length}")
        }
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(clean[i * 2], 16)
            val lo = Character.digit(clean[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) {
                throw IllegalArgumentException("bad hex at offset ${i * 2}")
            }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}

fun ByteArray.toHexString(off: Int = 0, len: Int = size - off): String {
    return Hex.encode(this, off, len)
}

fun String.hexToByteArray(): ByteArray {
    return Hex.decode(this)
}
