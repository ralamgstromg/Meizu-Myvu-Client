package com.myvu.client.transport.bt

import com.myvu.client.protocol.Pb
import java.nio.ByteBuffer
import java.nio.ByteOrder

object RfcommFraming {
    @JvmField
    val MAGIC: ByteArray = byteArrayOf(0xea.toByte(), 0xca.toByte(), 0x93.toByte(), 0x53.toByte())

    @JvmField
    val PREFIX: ByteArray = byteArrayOf(0x00, 0x02)

    @Deprecated("Retained only for pre-BLE harness flow")
    const val DEFAULT_CHANNEL: Int = 13

    @JvmStatic
    fun encodeFrame(payload: ByteArray): ByteArray {
        val body = Pb.concat(PREFIX, payload)
        val lenBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(body.size).array()
        return Pb.concat(MAGIC, lenBuf, body)
    }
}
