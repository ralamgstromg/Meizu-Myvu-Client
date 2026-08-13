package com.myvu.client.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LogBusTest {

    @Before
    fun setUp() {
        LogBus.clear()
        LogBus.isEnabled = true
    }

    @Test
    fun testLoggingWhenEnabled() {
        LogBus.isEnabled = true
        LogBus.log("test line 1")
        assertEquals(1, LogBus.history().size)
        assertTrue(LogBus.history()[0].contains("test line 1"))
    }

    @Test
    fun testLoggingWhenDisabled() {
        LogBus.isEnabled = false
        assertFalse(LogBus.isEnabled)
        LogBus.log("should not be logged")
        LogBus.warn("should not be logged warn")
        LogBus.error("should not be logged error", null)
        assertEquals(0, LogBus.history().size)
    }

    @Test
    fun testDisablingClearsExistingLogs() {
        LogBus.log("existing line")
        assertEquals(1, LogBus.history().size)

        LogBus.isEnabled = false
        assertEquals(0, LogBus.history().size)
    }
}
