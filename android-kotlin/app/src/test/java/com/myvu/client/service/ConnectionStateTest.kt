package com.myvu.client.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun stateFlowEmitsSequentialTransitions() = runTest {
        val stateFlow = MutableStateFlow(ConnectionState.IDLE)
        val collected = mutableListOf<ConnectionState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            stateFlow.collect { collected.add(it) }
        }

        stateFlow.value = ConnectionState.CONNECTING
        stateFlow.value = ConnectionState.PAIRING
        stateFlow.value = ConnectionState.SESSION
        stateFlow.value = ConnectionState.READY

        assertEquals(
            listOf(
                ConnectionState.IDLE,
                ConnectionState.CONNECTING,
                ConnectionState.PAIRING,
                ConnectionState.SESSION,
                ConnectionState.READY
            ),
            collected
        )
        job.cancel()
    }
}
