package com.myvu.client.protocol.link

import com.myvu.client.protocol.Pb
import com.myvu.client.protocol.PbValue
import java.util.Locale

/**
 * StarryNet LinkProtocol message builders/parsers, ported from
 * myvu_client/myvu/linkproto.py.
 *
 * These messages ride the BLE *internal* characteristic (0x2020) and carry the
 * ECDH bond plus the per-session SPP UUID sync.
 */
object LinkProtocol {
    private val EMPTY = ByteArray(0)

    // ------------------------------------------------------- LinkProtocol

    /** LinkProtocol{deviceId=dealDeviceId(identifier), cmd, data}. */
    @JvmStatic
    @JvmOverloads
    fun build(identifier: ByteArray, cmd: Int, data: ByteArray = EMPTY): ByteArray {
        var out = Pb.bytes(1, DeviceId.deal(identifier))
        out = Pb.concat(out, Pb.varintField(2, cmd.toLong()))
        if (data.isNotEmpty()) {
            out = Pb.concat(out, Pb.bytes(3, data))
        }
        return out
    }

    @JvmStatic
    fun parse(raw: ByteArray): LinkMessage {
        val f = Pb.parse(raw)
        return LinkMessage(
            Pb.firstBytes(f, 1, EMPTY),
            Pb.firstVarint(f, 2, 0).toInt(),
            Pb.firstBytes(f, 3, EMPTY)
        )
    }

    // -------------------------------------------------------- sub-messages

    /** WriteSwitchKey{1:key, 2:info}. */
    @JvmStatic
    fun writeSwitchKey(key: ByteArray, info: ByteArray): ByteArray {
        return Pb.concat(Pb.bytes(1, key), Pb.bytes(2, info))
    }

    /** Returns {key, info}. */
    @JvmStatic
    fun parseWriteSwitchKey(raw: ByteArray): Array<ByteArray> {
        val f = Pb.parse(raw)
        return arrayOf(Pb.firstBytes(f, 1, EMPTY), Pb.firstBytes(f, 2, EMPTY))
    }

    /** WriteSwitchInfo{1:code, 2:info}; code is omitted when zero, as in Python. */
    @JvmStatic
    fun writeSwitchInfo(info: ByteArray, code: Int): ByteArray {
        var out = EMPTY
        if (code != 0) out = Pb.concat(out, Pb.varintField(1, code.toLong()))
        return Pb.concat(out, Pb.bytes(2, info))
    }

    @JvmStatic
    fun parseWriteSwitchInfo(raw: ByteArray): ByteArray {
        return Pb.firstBytes(Pb.parse(raw), 2, EMPTY)
    }

    // ----------------------------------------------------------- SPP UUID

    /**
     * Decode a CMD_SPP_SERVER_UUID_SYNC payload into the full Bluetooth Base
     * UUID string, matching UUIDUtils.makeUUID(int) in the decompiled app.
     *
     * The 4-byte payload is LITTLE-endian -- confirmed empirically: a captured
     * payload of 21 91 00 00 only lands inside ByteUtils' expected range
     * (SecureRandom.nextInt(65535)) when read little-endian (0x9121 = 37153);
     * big-endian would give 0x21910000, far out of range.
     *
     * This is the opposite endianness to the TLV layer, so do not "unify" them.
     */
    @JvmStatic
    fun sppShortUuidToString(data: ByteArray?): String {
        if (data == null || data.size < 4) {
            throw IllegalArgumentException(
                "SPP UUID payload must be 4 bytes, got ${data?.size ?: 0}"
            )
        }
        val shortUuid = (data[0].toInt() and 0xFF) or
                ((data[1].toInt() and 0xFF) shl 8) or
                ((data[2].toInt() and 0xFF) shl 16) or
                ((data[3].toInt() and 0xFF) shl 24)
        return String.format(Locale.US, "0000%04x-0000-1000-8000-00805f9b34fb", shortUuid)
    }
}
