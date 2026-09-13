package com.myvu.client.service

import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelaySupervisorTest {

    @Test
    fun testExponentialBackoffCalculation() {
        // Attempt 0 -> Initial disconnected delay (2000ms)
        assertEquals(2000L, RelaySupervisor.calculateBackoffDelay(0))

        // Attempt 1 -> 2000 * 2 = 4000ms
        assertEquals(4000L, RelaySupervisor.calculateBackoffDelay(1))

        // Attempt 2 -> 2000 * 4 = 8000ms
        assertEquals(8000L, RelaySupervisor.calculateBackoffDelay(2))

        // Attempt 3 -> 2000 * 8 = 16000ms
        assertEquals(16000L, RelaySupervisor.calculateBackoffDelay(3))

        // Attempt 4 -> 2000 * 16 = 32000ms
        assertEquals(32000L, RelaySupervisor.calculateBackoffDelay(4))

        // Attempt 5+ -> Capped at 60000ms
        assertEquals(60000L, RelaySupervisor.calculateBackoffDelay(10))
    }

    @Test
    fun testStableRelayThresholdConstant() {
        assertEquals(30000L, RelaySupervisor.STABLE_RELAY_THRESHOLD_MS)
    }

    @Test
    fun testSppServerClosedSuspendsSupervisor() {
        val handler = Handler(Looper.getMainLooper())
        var connectCalls = 0
        val delegate = object : RelaySupervisor.Delegate {
            override fun isRelayConnected(): Boolean = false
            override fun canConnectRelay(): Boolean = true
            override fun connectRelay() { connectCalls++ }
        }
        val supervisor = RelaySupervisor(handler, delegate)
        supervisor.start()

        assertFalse(supervisor.isSppServerSuspended())

        supervisor.onSppServerClosed()
        assertTrue(supervisor.isSppServerSuspended())

        // onRelayLost while suspended must remain suspended and not connect
        supervisor.onRelayLost()
        assertTrue(supervisor.isSppServerSuspended())

        // Wake resets suspension and triggers immediate connect attempt
        supervisor.wake()
        assertFalse(supervisor.isSppServerSuspended())
        assertEquals(1, supervisor.getAttempt())
        assertEquals(1, connectCalls)

        supervisor.stop()
    }
}
