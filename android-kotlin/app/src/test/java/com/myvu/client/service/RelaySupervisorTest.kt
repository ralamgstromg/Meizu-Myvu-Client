package com.myvu.client.service

import android.os.Handler
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RelaySupervisorTest {

    private var isConnected = false
    private var canConnect = true
    private var connectCalls = 0

    private val delegate = object : RelaySupervisor.Delegate {
        override fun isRelayConnected(): Boolean = isConnected
        override fun canConnectRelay(): Boolean = canConnect
        override fun connectRelay() {
            connectCalls++
        }
    }

    private lateinit var supervisor: RelaySupervisor

    @Before
    fun setUp() {
        val handler = Handler()
        isConnected = false
        canConnect = true
        connectCalls = 0
        supervisor = RelaySupervisor(handler, delegate)
    }

    @Test
    fun backoffMsExposesConfiguredValue() {
        assertEquals(3000L, RelaySupervisor.backoffMs())
    }

    @Test
    fun startAndStopToggleStateWithoutCrashing() {
        supervisor.start()
        supervisor.stop()
    }

    @Test
    fun wakeResetsAttemptCountAndChecksRelay() {
        supervisor.start()
        supervisor.wake()
        assertEquals(1, connectCalls)
    }

    @Test
    fun wakeWhenRelayConnectedDoesNotTriggerConnect() {
        isConnected = true
        supervisor.start()
        supervisor.wake()
        assertEquals(0, connectCalls)
    }

    @Test
    fun wakeWhenCannotConnectDoesNotTriggerConnect() {
        canConnect = false
        supervisor.start()
        supervisor.wake()
        assertEquals(0, connectCalls)
    }
}
