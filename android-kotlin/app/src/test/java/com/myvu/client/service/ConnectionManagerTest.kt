package com.myvu.client.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionManagerTest {

    @Test
    fun connectionStateDefaultsToIdle() {
        val state = ConnectionState.IDLE
        assertEquals("IDLE", state.name)
    }

    @Test
    fun allConnectionStatesArePresent() {
        val allStates = ConnectionState.values()
        assertEquals(7, allStates.size)
        val names = allStates.map { it.name }
        assertEquals(
            listOf("IDLE", "BONDING", "CONNECTING", "PAIRING", "SESSION", "READY", "FAILED"),
            names
        )
    }

    @Test
    fun stateEnumTransitionsAreSupported() {
        val states = listOf(
            ConnectionState.IDLE,
            ConnectionState.BONDING,
            ConnectionState.CONNECTING,
            ConnectionState.PAIRING,
            ConnectionState.SESSION,
            ConnectionState.READY,
            ConnectionState.FAILED
        )
        assertEquals(7, states.size)
    }

    @Test
    fun stateFlowEmitsUpdatedStates() = runBlocking {
        val flow = MutableStateFlow(ConnectionState.IDLE)

        flow.value = ConnectionState.CONNECTING
        assertEquals(ConnectionState.CONNECTING, flow.value)

        flow.value = ConnectionState.READY
        assertEquals(ConnectionState.READY, flow.value)
    }

    @Test
    fun stateFlowEmitsFullLifecycleTransitions() = runTest {
        val flow = MutableStateFlow(ConnectionState.IDLE)
        val emitted = mutableListOf<ConnectionState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { emitted.add(it) }
        }

        val lifecycle = listOf(
            ConnectionState.BONDING,
            ConnectionState.CONNECTING,
            ConnectionState.PAIRING,
            ConnectionState.SESSION,
            ConnectionState.READY,
            ConnectionState.FAILED,
            ConnectionState.IDLE
        )

        lifecycle.forEach { flow.value = it }

        assertEquals(
            listOf(ConnectionState.IDLE) + lifecycle,
            emitted
        )
        job.cancel()
    }

    @Test
    fun listenerAndStateFlowSynchronousNotification() = runTest {
        val flow = MutableStateFlow(ConnectionState.IDLE)
        val listenerStates = mutableListOf<ConnectionState>()
        val listener = ConnectionManager.Listener { s ->
            listenerStates.add(s)
        }

        fun updateState(s: ConnectionState) {
            flow.value = s
            listener.onStateChanged(s)
        }

        updateState(ConnectionState.CONNECTING)
        updateState(ConnectionState.PAIRING)
        updateState(ConnectionState.SESSION)
        updateState(ConnectionState.READY)

        assertEquals(ConnectionState.READY, flow.value)
        assertEquals(
            listOf(
                ConnectionState.CONNECTING,
                ConnectionState.PAIRING,
                ConnectionState.SESSION,
                ConnectionState.READY
            ),
            listenerStates
        )
    }
}
