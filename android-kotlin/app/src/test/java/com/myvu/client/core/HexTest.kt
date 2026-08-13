package com.myvu.client.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HexTest {

    @Test
    fun testEncodeAndDecode() {
        val original = byteArrayOf(0x00, 0x0F, 0x10, 0xAF.toByte(), 0xFF.toByte())
        val hexStr = Hex.encode(original)
        assertEquals("000f10afff", hexStr)

        val decoded = Hex.decode(hexStr)
        assertArrayEquals(original, decoded)
    }

    @Test
    fun testExtensionFunctions() {
        val bytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertEquals("deadbeef", bytes.toHexString())
        assertArrayEquals(bytes, "deadbeef".hexToByteArray())
    }

    @Test
    fun testDecodeWithWhitespace() {
        val input = "  de ad  be ef \n"
        val expected = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertArrayEquals(expected, Hex.decode(input))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDecodeOddLengthThrows() {
        Hex.decode("abc")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDecodeInvalidCharThrows() {
        Hex.decode("zz")
    }
}
