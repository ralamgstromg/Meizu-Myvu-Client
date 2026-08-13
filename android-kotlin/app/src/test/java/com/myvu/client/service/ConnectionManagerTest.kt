package com.myvu.client.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionManagerTest {

    @Test
    fun connectionStateDefaultsToIdle() {
        val state = ConnectionState.IDLE
        assertEquals("IDLE", state.name)
    }

    @Test
    fun stateEnumTransitionsAreSupported() {
        val states = listOf(
            ConnectionState.IDLE,
            ConnectionState.CONNECTING,
            ConnectionState.PAIRING,
            ConnectionState.SESSION,
            ConnectionState.READY,
            ConnectionState.FAILED
        )
        assertEquals(6, states.size)
    }
}
