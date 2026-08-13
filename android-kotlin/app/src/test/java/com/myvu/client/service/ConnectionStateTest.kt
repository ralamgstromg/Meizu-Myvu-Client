package com.myvu.client.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ConnectionStateTest {

    @Test
    fun connectionStateHasAllRequiredValuesInOrder() {
        val values = ConnectionState.values()
        assertEquals(7, values.size)
        assertEquals(ConnectionState.IDLE, values[0])
        assertEquals(ConnectionState.BONDING, values[1])
        assertEquals(ConnectionState.CONNECTING, values[2])
        assertEquals(ConnectionState.PAIRING, values[3])
        assertEquals(ConnectionState.SESSION, values[4])
        assertEquals(ConnectionState.READY, values[5])
        assertEquals(ConnectionState.FAILED, values[6])
    }

    @Test
    fun valueOfReturnsCorrectEnumInstance() {
        assertEquals(ConnectionState.IDLE, ConnectionState.valueOf("IDLE"))
        assertEquals(ConnectionState.READY, ConnectionState.valueOf("READY"))
        assertEquals(ConnectionState.FAILED, ConnectionState.valueOf("FAILED"))
    }
}
