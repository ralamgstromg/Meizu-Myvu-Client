package com.myvu.client.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
