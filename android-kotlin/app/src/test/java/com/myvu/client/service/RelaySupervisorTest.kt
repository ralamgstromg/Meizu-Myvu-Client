package com.myvu.client.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelaySupervisorTest {

    @Test
    fun testExponentialBackoffCalculation() {
        // Attempt 0 -> Initial disconnected delay (5000ms)
        assertEquals(5000L, RelaySupervisor.calculateBackoffDelay(0))

        // Attempt 1 -> 5000 * 2 = 10000ms
        assertEquals(10000L, RelaySupervisor.calculateBackoffDelay(1))

        // Attempt 2 -> 5000 * 4 = 20000ms
        assertEquals(20000L, RelaySupervisor.calculateBackoffDelay(2))

        // Attempt 3 -> 5000 * 8 = 40000ms
        assertEquals(40000L, RelaySupervisor.calculateBackoffDelay(3))

        // Attempt 4+ -> Capped at 60000ms
        assertEquals(60000L, RelaySupervisor.calculateBackoffDelay(4))
        assertEquals(60000L, RelaySupervisor.calculateBackoffDelay(10))
    }
}
