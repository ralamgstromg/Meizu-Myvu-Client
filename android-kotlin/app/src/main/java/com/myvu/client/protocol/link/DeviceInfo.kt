package com.myvu.client.protocol.link

import com.myvu.client.protocol.Pb
import com.myvu.client.protocol.PbValue
import java.nio.charset.StandardCharsets

/**
 * DeviceInfo{1:btMac, 2:companyId, 3:categoryId, 4:modelId, 5:name, 6:battery,
 * 7:btStatus} -- the payload each side sends inside WRITE_SWITCH_INFO,
 * AES-encrypted with the ECDH-derived key.
 */
class DeviceInfo(
    @JvmField val btMac: String,
    @JvmField val companyId: String,
    @JvmField val categoryId: String,
    @JvmField val modelId: String,
    @JvmField val name: String,
    @JvmField val battery: Int,
    @JvmField val btStatus: Int
) {
    override fun toString(): String {
        return "$name ($btMac, battery $battery%, model $modelId)"
    }

    companion object {
        /** Zero-valued battery/btStatus are omitted from the wire, matching Python. */
        @JvmStatic
        fun build(
            btMac: String,
            companyId: String,
            categoryId: String,
            modelId: String,
            name: String,
            battery: Int,
            btStatus: Int
        ): ByteArray {
            var out = Pb.string(1, btMac)
            out = Pb.concat(out, Pb.string(2, companyId))
            out = Pb.concat(out, Pb.string(3, categoryId))
            out = Pb.concat(out, Pb.string(4, modelId))
            out = Pb.concat(out, Pb.bytes(5, name.toByteArray(StandardCharsets.UTF_8)))
            if (battery != 0) out = Pb.concat(out, Pb.varintField(6, battery.toLong()))
            if (btStatus != 0) out = Pb.concat(out, Pb.varintField(7, btStatus.toLong()))
            return out
        }

        @JvmStatic
        fun parse(raw: ByteArray): DeviceInfo {
            val f = Pb.parse(raw)
            return DeviceInfo(
                Pb.firstString(f, 1, "") ?: "",
                Pb.firstString(f, 2, "") ?: "",
                Pb.firstString(f, 3, "") ?: "",
                Pb.firstString(f, 4, "") ?: "",
                Pb.firstString(f, 5, "") ?: "",
                Pb.firstVarint(f, 6, 0).toInt(),
                Pb.firstVarint(f, 7, 0).toInt()
            )
        }
    }
}
