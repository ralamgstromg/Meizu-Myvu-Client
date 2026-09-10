package com.myvu.client.transport

import com.myvu.client.transport.ble.BleHeartbeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleHeartbeatTest {

    private class MockScheduler : BleHeartbeat.Scheduler {
        var lastRunnable: Runnable? = null
        var lastDelayMs: Long = 0
        var callbackRemoved: Boolean = false

        override fun postDelayed(runnable: Runnable, delayMs: Long) {
            lastRunnable = runnable
            lastDelayMs = delayMs
        }

        override fun removeCallbacks(runnable: Runnable) {
            callbackRemoved = true
        }
    }

    private class MockTimeProvider(var time: Long) : BleHeartbeat.TimeProvider {
        override fun currentTimeMillis(): Long = time
    }

    @Test
    fun standardIntervalIsTwentySeconds() {
        val scheduler = MockScheduler()
        val timeProvider = MockTimeProvider(1000L)
        val heartbeat = BleHeartbeat(null, null, scheduler, timeProvider)

        assertEquals(BleHeartbeat.STANDARD_INTERVAL_MS, heartbeat.interval)
        assertEquals(20000L, heartbeat.interval)
    }

    @Test
    fun notifyDataActivitySwitchesToExtendedIntervalAndReschedules() {
        val scheduler = MockScheduler()
        val timeProvider = MockTimeProvider(5000L)
        val heartbeat = BleHeartbeat(null, null, scheduler, timeProvider)

        heartbeat.start()
        assertTrue(heartbeat.isRunning)
        assertEquals(20000L, scheduler.lastDelayMs)

        // Activity happens at t = 6000L
        timeProvider.time = 6000L
        heartbeat.notifyDataActivity()

        assertTrue(heartbeat.isDataActive)
        assertEquals(BleHeartbeat.EXTENDED_INTERVAL_MS, heartbeat.interval)
        assertEquals(25000L, heartbeat.interval)
        assertTrue(scheduler.callbackRemoved)
        assertEquals(25000L, scheduler.lastDelayMs)
    }
}
