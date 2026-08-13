package com.myvu.client.transport

import com.myvu.client.transport.bt.BtTransport
import com.myvu.client.transport.bt.FrameReassembler
import com.myvu.client.transport.bt.RfcommFraming
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BtTransportTest {

    @Test
    fun testRfcommFramingConstants() {
        assertArrayEquals(
            byteArrayOf(0xea.toByte(), 0xca.toByte(), 0x93.toByte(), 0x53.toByte()),
            RfcommFraming.MAGIC
        )
        assertArrayEquals(byteArrayOf(0x00, 0x02), RfcommFraming.PREFIX)
        assertEquals(13, RfcommFraming.DEFAULT_CHANNEL)
    }

    @Test
    fun testRfcommEncodeFrame() {
        val payload = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val frame = RfcommFraming.encodeFrame(payload)

        // Structure: MAGIC (4B) + LEN (4B BE) + PREFIX (2B) + PAYLOAD (4B) = 14B total
        assertEquals(14, frame.size)

        // Check magic
        val magic = frame.copyOfRange(0, 4)
        assertArrayEquals(RfcommFraming.MAGIC, magic)

        // Check length field (body length = PREFIX (2) + payload (4) = 6)
        val len = ByteBuffer.wrap(frame, 4, 4).order(ByteOrder.BIG_ENDIAN).int
        assertEquals(6, len)

        // Check prefix
        val prefix = frame.copyOfRange(8, 10)
        assertArrayEquals(RfcommFraming.PREFIX, prefix)

        // Check payload
        val extractedPayload = frame.copyOfRange(10, 14)
        assertArrayEquals(payload, extractedPayload)
    }

    @Test
    fun testFrameReassemblerSingleFrame() {
        val reassembler = FrameReassembler()
        val payload = "Hello Glasses!".toByteArray(Charsets.UTF_8)
        val frame = RfcommFraming.encodeFrame(payload)

        val result = reassembler.feed(frame)
        assertEquals(1, result.size)
        assertArrayEquals(payload, result[0])
    }

    @Test
    fun testFrameReassemblerChunkedInput() {
        val reassembler = FrameReassembler()
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val frame = RfcommFraming.encodeFrame(payload)

        // Feed byte by byte
        var accumulated: List<ByteArray> = emptyList()
        for (b in frame) {
            val res = reassembler.feed(byteArrayOf(b))
            if (res.isNotEmpty()) {
                accumulated = res
            }
        }

        assertEquals(1, accumulated.size)
        assertArrayEquals(payload, accumulated[0])
    }

    @Test
    fun testFrameReassemblerMultipleFramesInSingleChunk() {
        val reassembler = FrameReassembler()
        val p1 = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        val p2 = byteArrayOf(0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte())

        val f1 = RfcommFraming.encodeFrame(p1)
        val f2 = RfcommFraming.encodeFrame(p2)
        val combined = f1 + f2

        val result = reassembler.feed(combined)
        assertEquals(2, result.size)
        assertArrayEquals(p1, result[0])
        assertArrayEquals(p2, result[1])
    }

    @Test
    fun testFrameReassemblerCorruptMagicResync() {
        val reassembler = FrameReassembler()
        val payload = byteArrayOf(0x42)
        val validFrame = RfcommFraming.encodeFrame(payload)

        val junk = byteArrayOf(0x00, 0x11, 0x22, 0xEA.toByte(), 0xCA.toByte(), 0x00)
        val input = junk + validFrame

        val result = reassembler.feed(input)
        assertEquals(1, result.size)
        assertArrayEquals(payload, result[0])
    }

    @Test
    fun testFrameReassemblerInvalidLengthSafety() {
        val reassembler = FrameReassembler()

        // Magic + invalid length (e.g. negative or exceeding MAX_FRAME)
        val badLenHeader = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .put(RfcommFraming.MAGIC)
            .putInt(FrameReassembler.MAX_FRAME + 100)
            .array()

        val validPayload = byteArrayOf(0x77)
        val validFrame = RfcommFraming.encodeFrame(validPayload)

        val result = reassembler.feed(badLenHeader + validFrame)
        assertEquals(1, result.size)
        assertArrayEquals(validPayload, result[0])
    }

    @Test
    fun testFrameReassemblerReset() {
        val reassembler = FrameReassembler()
        val payload = byteArrayOf(0x01, 0x02)
        val frame = RfcommFraming.encodeFrame(payload)

        // Feed half of the frame
        reassembler.feed(frame.copyOfRange(0, 5))
        reassembler.reset()

        // Now feed complete new frame
        val result = reassembler.feed(frame)
        assertEquals(1, result.size)
        assertArrayEquals(payload, result[0])
    }

    @Test
    fun testBtTransportDefaults() {
        assertNotNull(BtTransport.DEFAULT_SPP_UUID)
        assertEquals("00001101-0000-1000-8000-00805f9b34fb", BtTransport.DEFAULT_SPP_UUID.toString().lowercase())
    }
}
