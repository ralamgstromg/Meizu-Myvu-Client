package com.myvu.client.protocol

import com.myvu.client.core.Hex
import com.myvu.client.protocol.link.DeviceId
import com.myvu.client.protocol.link.DeviceInfo
import com.myvu.client.protocol.link.LinkCommands
import com.myvu.client.protocol.link.LinkProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkProtocolTest {

    @Test
    fun sppShortUuidIsDecodedLittleEndian() {
        assertEquals(
            "00009121-0000-1000-8000-00805f9b34fb",
            LinkProtocol.sppShortUuidToString(Hex.decode("21910000"))
        )
    }

    @Test
    fun sppShortUuidPadsToFourHexDigits() {
        assertEquals(
            "00000042-0000-1000-8000-00805f9b34fb",
            LinkProtocol.sppShortUuidToString(Hex.decode("42000000"))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun sppShortUuidRejectsShortPayload() {
        LinkProtocol.sppShortUuidToString(byteArrayOf(1, 2))
    }

    @Test
    fun linkProtocolRoundTrips() {
        val mac = Hex.decode("7ca375d094f1")
        val payload = byteArrayOf(9, 8, 7)

        val m = LinkProtocol.parse(
            LinkProtocol.build(mac, LinkCommands.CMD_SPP_SERVER_UUID_SYNC, payload)
        )

        assertArrayEquals(DeviceId.deal(mac), m.deviceId)
        assertEquals(LinkCommands.CMD_SPP_SERVER_UUID_SYNC, m.cmd)
        assertArrayEquals(payload, m.data)
    }

    @Test
    fun emptyDataFieldIsOmittedAndParsesBack() {
        val mac = Hex.decode("7ca375d094f1")
        val m = LinkProtocol.parse(LinkProtocol.build(mac, LinkCommands.CMD_INIT))
        assertEquals(LinkCommands.CMD_INIT, m.cmd)
        assertEquals(0, m.data.size)
    }

    @Test
    fun writeSwitchInfoOmitsZeroCode() {
        val info = byteArrayOf(1, 2, 3)
        assertArrayEquals(
            info,
            LinkProtocol.parseWriteSwitchInfo(LinkProtocol.writeSwitchInfo(info, 0))
        )
        assertArrayEquals(
            info,
            LinkProtocol.parseWriteSwitchInfo(LinkProtocol.writeSwitchInfo(info, 5))
        )
    }

    @Test
    fun deviceInfoRoundTrips() {
        val encoded = DeviceInfo.build(
            "AA:BB:CC:DD:EE:FF", "", "9999", "", "MyvuAndroid", 100, 0
        )
        val d = DeviceInfo.parse(encoded)

        assertEquals("AA:BB:CC:DD:EE:FF", d.btMac)
        assertEquals("9999", d.categoryId)
        assertEquals("MyvuAndroid", d.name)
        assertEquals(100, d.battery)
        assertEquals(0, d.btStatus)
    }

    @Test
    fun macHelpersAcceptCommonSeparators() {
        val expected = Hex.decode("2c6f4e00dc47")
        assertArrayEquals(expected, DeviceId.macToBytes("2C:6F:4E:00:DC:47"))
        assertArrayEquals(expected, DeviceId.macToBytes("2c-6f-4e-00-dc-47"))
        assertEquals("2c6f4e00dc47", DeviceId.macToHex("2C:6F:4E:00:DC:47"))
    }
}
