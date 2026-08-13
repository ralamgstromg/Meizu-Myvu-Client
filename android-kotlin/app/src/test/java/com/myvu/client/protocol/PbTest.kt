package com.myvu.client.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PbTest {

    @Test
    fun varintAndBytesRoundTrip() {
        val msg = Pb.concat(
            Pb.varintField(1, 300),
            Pb.string(2, "hello"),
            Pb.varintField(3, 0)
        )

        val f = Pb.parse(msg)
        assertEquals(300L, Pb.firstVarint(f, 1, -1))
        assertEquals("hello", Pb.firstString(f, 2, null))
        assertEquals(0L, Pb.firstVarint(f, 3, -1))
    }

    @Test
    fun repeatedFieldsAreAllRetained() {
        val msg = Pb.concat(
            Pb.bytes(5, byteArrayOf(1)),
            Pb.bytes(5, byteArrayOf(2)),
            Pb.bytes(5, byteArrayOf(3))
        )

        val all = Pb.all(Pb.parse(msg), 5)
        assertEquals(3, all.size)
        assertArrayEquals(byteArrayOf(2), all[1].asBytes())
    }

    @Test
    fun largeVarintsSurvive() {
        val big = 1739000000000L
        assertEquals(big, Pb.firstVarint(Pb.parse(Pb.varintField(12, big)), 12, -1))
    }

    @Test
    fun fixed32And64AreKeptAsRawBytesInsteadOfThrowing() {
        val fixed32 = Pb.concat(byteArrayOf(((1 shl 3) or 5).toByte()), byteArrayOf(1, 2, 3, 4))
        assertEquals(4, Pb.firstBytes(Pb.parse(fixed32), 1, ByteArray(0)).size)

        val fixed64 = Pb.concat(
            byteArrayOf(((2 shl 3) or 1).toByte()),
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        )
        assertEquals(8, Pb.firstBytes(Pb.parse(fixed64), 2, ByteArray(0)).size)
    }

    @Test
    fun groupWireTypesStopTheParseCleanly() {
        val msg = Pb.concat(
            Pb.string(1, "kept"),
            byteArrayOf(((2 shl 3) or 3).toByte()),
            Pb.string(4, "dropped")
        )

        val f = Pb.parse(msg)
        assertEquals("kept", Pb.firstString(f, 1, null))
        assertNull("fields after a group marker are not decoded", Pb.first(f, 4))
    }

    @Test
    fun truncatedLengthDelimitedFieldDoesNotOverrun() {
        val msg = Pb.concat(
            byteArrayOf(((1 shl 3) or 2).toByte(), 200.toByte()),
            byteArrayOf(1, 2, 3)
        )
        assertTrue(Pb.parse(msg).isEmpty())
    }

    @Test
    fun truncatedVarintDoesNotOverrun() {
        assertTrue(Pb.parse(byteArrayOf(((1 shl 3) or 0).toByte(), 0x80.toByte())).isEmpty())
    }

    @Test
    fun runawayContinuationBitsTerminate() {
        val evil = ByteArray(32)
        evil[0] = ((1 shl 3) or 0).toByte()
        for (i in 1 until evil.size) evil[i] = 0x80.toByte()
        assertTrue(Pb.parse(evil).isEmpty())
    }

    @Test
    fun emptyInputYieldsEmptyMap() {
        assertTrue(Pb.parse(ByteArray(0)).isEmpty())
    }

    @Test
    fun accessorsFallBackWhenTypeMismatches() {
        val f = Pb.parse(Pb.varintField(1, 5))
        assertArrayEquals(ByteArray(0), Pb.firstBytes(f, 1, ByteArray(0)))
        assertEquals(-1L, Pb.firstVarint(f, 99, -1))
    }
}
