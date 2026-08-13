package com.myvu.client.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.StandardCharsets

class TlvBoxTest {

    @Test
    fun tlvIntsAreBigEndian() {
        val ser = TlvBox().putInt(TlvTags.MSG_ID, 1).serialize()
        assertEquals(TlvTags.MSG_ID.toByte(), ser[0])
        assertEquals(0.toByte(), ser[1])
        assertEquals(4.toByte(), ser[2])
        assertArrayEquals(byteArrayOf(0, 0, 0, 1), ser.copyOfRange(3, 7))
    }

    @Test
    fun tlvPreservesInsertionOrder() {
        val ser = TlvBox()
            .putByte(100, 3)
            .putInt(101, 7)
            .putByte(103, 1)
            .serialize()
        assertEquals(100, ser[0].toInt() and 0xFF)
        assertEquals(101, ser[4].toInt() and 0xFF)
        assertEquals(103, ser[11].toInt() and 0xFF)
    }

    @Test
    fun tlvRoundTrips() {
        val body = "payload".toByteArray(StandardCharsets.UTF_8)
        val parsed = TlvBox.parse(
            TlvBox()
                .putByte(TlvTags.MSG_TYPE, MsgType.SEND)
                .putInt(TlvTags.MSG_ID, 42)
                .putBytes(TlvTags.MSG_BODY, body)
                .serialize()
        )

        assertEquals(MsgType.SEND, parsed.getByte(TlvTags.MSG_TYPE))
        assertEquals(42, parsed.getInt(TlvTags.MSG_ID))
        assertArrayEquals(body, parsed.getBytes(TlvTags.MSG_BODY))
        assertNull("absent tags return null so callers can default", parsed.getInt(TlvTags.ERROR_CODE))
    }

    @Test
    fun relayFrameRoundTrips() {
        val body = "{\"action\":\"notification\"}".toByteArray(StandardCharsets.UTF_8)
        val frame = Relay.buildFrame(Relay.DEFAULT_CATEGORY, MsgType.SEND, 1, 1, 1, body)

        val m = Relay.parseFrame(frame)
        assertNotNull(m)
        assertEquals(Relay.DEFAULT_CATEGORY, m!!.category)
        assertEquals(MsgType.SEND, m.msgType)
        assertEquals(1, m.msgId)
        assertEquals(1, m.needCallback)
        assertArrayEquals(body, m.msgBody)
    }

    @Test
    fun nonRelayBuffersParseToNull() {
        assertNull(Relay.parseFrame(ByteArray(0)))
        assertNull(Relay.parseFrame(byteArrayOf(0x02, 0x00))) // wrong frame prefix
    }

    @Test
    fun sequencerStartsAtOne() {
        val seq = RelaySequencer()
        assertEquals(0, seq.outId)

        val first = Relay.parseFrame(seq.dataFrame(byteArrayOf(1)))
        assertNotNull(first)
        assertEquals(1, first!!.msgId)

        val second = Relay.parseFrame(seq.dataFrame(byteArrayOf(2)))
        assertNotNull(second)
        assertEquals(2, second!!.msgId)
    }

    @Test
    fun ackFrameEchoesPeerIdAndCategory() {
        val inbound = Relay.parseFrame(
            Relay.buildFrame(5, MsgType.SEND, 99, 1, 1, byteArrayOf(7))
        )
        assertNotNull(inbound)

        val ack = Relay.parseFrame(RelaySequencer().ackFrame(inbound!!))
        assertNotNull(ack)
        assertEquals(MsgType.SEND_SUCCESS, ack!!.msgType)
        assertEquals(99, ack.msgId)
        assertEquals("ACK must be sent in the peer's category", 5, ack.category)
    }
}
