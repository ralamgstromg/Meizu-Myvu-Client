package com.myvu.client.protocol.link

import java.util.Locale

/** BleUtil.dealDeviceId and the MAC-string helpers around it. */
object DeviceId {
    /**
     * BleUtil.dealDeviceId: reverse the byte order AND bitwise-NOT each byte.
     * Verified against a real capture: dealDeviceId(7ca375d094f1) == 0e6b2f8a5c83.
     */
    @JvmStatic
    fun deal(identifier: ByteArray): ByteArray {
        val out = ByteArray(identifier.size)
        for (i in identifier.indices) {
            out[i] = (identifier[identifier.size - 1 - i].toInt().inv() and 0xFF).toByte()
        }
        return out
    }

    /** Utils.getBytesFromAddress("AA:BB:.."), tolerant of case and separators. */
    @JvmStatic
    fun macToBytes(mac: String): ByteArray {
        val clean = mac.replace(":", "").replace("-", "")
        if (clean.length != 12) {
            throw IllegalArgumentException("not a 6-byte MAC: $mac")
        }
        val out = ByteArray(6)
        for (i in 0 until 6) {
            out[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return out
    }

    /** Lowercase, separator-free MAC -- the form used as deviceId in the auth bean. */
    @JvmStatic
    fun macToHex(mac: String): String {
        return mac.replace(":", "").replace("-", "").lowercase(Locale.US)
    }
}
