package com.myvu.client.transport

import com.myvu.client.transport.ble.BleHeartbeat
import com.myvu.client.transport.ble.BleMessageChannel
import com.myvu.client.transport.ble.BlePackets
import com.myvu.client.transport.ble.BleParsedPacket
import com.myvu.client.transport.ble.BleReassembler
import com.myvu.client.transport.ble.Uuids
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BleTransportTest {

    @Test
    fun testUuidsFormatting() {
        val u = Uuids.make(3025)
        assertEquals("00000bd1-0000-1000-8000-00805f9b34fb", u.toString().lowercase())
        assertEquals(Uuids.SERVICE, u)

        assertNotNull(Uuids.AIR_INTERNAL)
        assertNotNull(Uuids.AIR_EXTERNAL)
        assertNotNull(Uuids.AIR_URGENT)
        assertNotNull(Uuids.CCCD)
        assertEquals(2, Uuids.CHANNEL_SETS.size)
    }

    @Test
    fun testBlePacketsDataPacketEncodingAndParsing() {
        val seq = 5
        val payload = byteArrayOf(0x10, 0x20, 0x30)
        val dataPkg = BlePackets.dataPacket(seq, payload)

        // LE sequence number (2 bytes) + payload
        assertEquals(5, dataPkg.size)
        val bb = ByteBuffer.wrap(dataPkg).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(5, bb.getShort(0).toInt() and 0xFFFF)

        // Parse data packet
        val parsed = BlePackets.parse(dataPkg)
        assertTrue(parsed.isData)
        assertEquals(5, parsed.sn)
        assertArrayEquals(payload, parsed.value)
    }

    @Test
    fun testBlePacketsControlPackets() {
        // Single packet
        val payload = byteArrayOf(0x01, 0x02)
        val single = BlePackets.singlePacket(BlePackets.PKG_STARRY_DATA, payload)
        val pSingle = BlePackets.parse(single)

        assertFalse(pSingle.isData)
        assertEquals(0, pSingle.sn)
        assertEquals(BlePackets.TYPE_SINGLE_CMD, pSingle.type)
        assertEquals(BlePackets.PKG_STARRY_DATA, pSingle.command)
        assertArrayEquals(payload, pSingle.value)

        // SingleNoAck packet
        val singleNoAck = BlePackets.singleNoAckPacket(BlePackets.PKG_COMMON_DATA, payload)
        val pNoAck = BlePackets.parse(singleNoAck)
        assertEquals(BlePackets.TYPE_SINGLE_CMD_NO_ACK, pNoAck.type)
        assertEquals(BlePackets.PKG_COMMON_DATA, pNoAck.command)

        // CTR packet
        val ctr = BlePackets.ctrPacket(3, BlePackets.PKG_COMMON_DATA)
        val pCtr = BlePackets.parse(ctr)
        assertEquals(BlePackets.TYPE_CMD, pCtr.type)
        assertEquals(BlePackets.PKG_COMMON_DATA, pCtr.command)
        assertEquals(1, pCtr.params.size)
        assertEquals(3, pCtr.frameCount())

        // Fast CTR packet
        val fastCtr = BlePackets.fastCtrPacket(4, BlePackets.PKG_STARRY_DATA_INIT)
        val pFastCtr = BlePackets.parse(fastCtr)
        assertEquals(BlePackets.TYPE_FAST_CTR, pFastCtr.type)
        assertEquals(4, pFastCtr.frameCount())

        // ACK packet with lost sequences
        val ack = BlePackets.ackPacket(BlePackets.ACK_READY, listOf(2, 4))
        val pAck = BlePackets.parse(ack)
        assertEquals(BlePackets.TYPE_ACK, pAck.type)
        assertEquals(BlePackets.ACK_READY, pAck.ackStatus())
        assertEquals(listOf(2, 4), pAck.params)
    }

    @Test
    fun testBleReassemblerMultiFrame() {
        val reassembler = BleReassembler()
        assertFalse(reassembler.isActive)

        val header = byteArrayOf(0x0A, 0x0B)
        reassembler.start(3, BlePackets.PKG_COMMON_DATA, header)
        assertTrue(reassembler.isActive)
        assertEquals(3, reassembler.frameCount)

        val frag1 = byteArrayOf(0x11, 0x12)
        val frag2 = byteArrayOf(0x21, 0x22)
        val frag3 = byteArrayOf(0x31, 0x32)

        // Feed fragments out of order (2, then 1, then 3)
        assertNull(reassembler.add(2, frag2))
        assertNull(reassembler.add(1, frag1))

        val complete = reassembler.add(3, frag3)
        assertNotNull(complete)
        assertFalse(reassembler.isActive)

        // Expected: header + frag1 + frag2 + frag3
        val expected = header + frag1 + frag2 + frag3
        assertArrayEquals(expected, complete)
    }

    @Test
    fun testBleMessageChannelSendFast() {
        val writtenPackets = mutableListOf<ByteArray>()
        val writer = BleMessageChannel.Writer { packet -> writtenPackets.add(packet) }

        val channel = BleMessageChannel("test", writer)
        channel.setDmtu(20)

        // Payload of 45 bytes -> Math.max(1, (45 + 20 - 1)/20) = 3 frames
        val payload = ByteArray(45) { it.toByte() }
        channel.sendFast(payload, BlePackets.PKG_STARRY_DATA_INIT)

        // Expected 4 packets: 1 FastCTR packet + 3 data fragments
        assertEquals(4, writtenPackets.size)

        // First packet is FastCTR
        val ctrParsed = BlePackets.parse(writtenPackets[0])
        assertEquals(BlePackets.TYPE_FAST_CTR, ctrParsed.type)
        assertEquals(3, ctrParsed.frameCount())

        // Next 3 are data fragments
        val f1 = BlePackets.parse(writtenPackets[1])
        assertEquals(1, f1.sn)
        assertEquals(20, f1.value.size)

        val f2 = BlePackets.parse(writtenPackets[2])
        assertEquals(2, f2.sn)
        assertEquals(20, f2.value.size)

        val f3 = BlePackets.parse(writtenPackets[3])
        assertEquals(3, f3.sn)
        assertEquals(5, f3.value.size)
    }

    @Test
    fun testBleMessageChannelFeedInbound() {
        val writtenPackets = mutableListOf<ByteArray>()
        val writer = BleMessageChannel.Writer { packet -> writtenPackets.add(packet) }

        var receivedPkgType = -1
        var receivedPayload: ByteArray? = null

        val channel = BleMessageChannel("test", writer, null) { pkgType, payload ->
            receivedPkgType = pkgType
            receivedPayload = payload
        }

        val testPayload = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val singleCmd = BlePackets.singlePacket(BlePackets.PKG_COMMON_DATA, testPayload)

        channel.feed(singleCmd)

        // Should receive message callback
        assertEquals(BlePackets.PKG_COMMON_DATA, receivedPkgType)
        assertArrayEquals(testPayload, receivedPayload)

        // Should automatically send singleAckPacket(ACK_SUCCESS) back
        assertEquals(1, writtenPackets.size)
        val ackParsed = BlePackets.parse(writtenPackets[0])
        assertEquals(BlePackets.TYPE_SINGLE_ACK, ackParsed.type)
        assertEquals(BlePackets.ACK_SUCCESS, ackParsed.ackStatus())
    }

    @Test
    fun testBleHeartbeatAdaptiveDutyCycle() {
        var currentTime = 100000L
        val timeProvider = BleHeartbeat.TimeProvider { currentTime }

        val heartbeat = BleHeartbeat(null, null, null, timeProvider)

        // Standard interval initially
        assertEquals(BleHeartbeat.STANDARD_INTERVAL_MS, heartbeat.interval)
        assertFalse(heartbeat.isDataActive)

        // Notify activity
        heartbeat.notifyDataActivity()
        assertTrue(heartbeat.isDataActive)
        assertEquals(BleHeartbeat.EXTENDED_INTERVAL_MS, heartbeat.interval)

        // Fast forward 10 seconds (less than 15s ACTIVE_DATA_TIMEOUT_MS)
        currentTime += 10000L
        assertTrue(heartbeat.isDataActive)

        // Fast forward past 15 seconds
        currentTime += 6000L
        assertFalse(heartbeat.isDataActive)
        assertEquals(BleHeartbeat.STANDARD_INTERVAL_MS, heartbeat.interval)
    }
}
