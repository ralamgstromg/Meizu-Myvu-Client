package com.myvu.client.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ResourceSafetyTest {

    @Test
    fun testStreamClosureSafety() {
        val data = "test data stream".toByteArray()
        var closed = false

        val input = object : ByteArrayInputStream(data) {
            override fun close() {
                super.close()
                closed = true
            }
        }

        input.use { stream ->
            val buf = ByteArray(1024)
            stream.read(buf)
        }

        assertTrue(closed)
    }

    @Test
    fun testOutputStreamSafety() {
        var closed = false
        val output = object : ByteArrayOutputStream() {
            override fun close() {
                super.close()
                closed = true
            }
        }

        output.use { stream ->
            stream.write("hello".toByteArray())
        }

        assertTrue(closed)
    }
}
