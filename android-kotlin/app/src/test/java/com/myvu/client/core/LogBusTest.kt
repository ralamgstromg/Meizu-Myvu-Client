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

    @Test
    fun testStructuredLogEntriesAndSourceInference() {
        LogBus.log("Connecting to Meizu MYVU AR glasses")
        LogBus.bluetooth("Connected to Sony WH-1000XM4 headphones", deviceName = "Sony WH-1000XM4")
        LogBus.ai("Gemini generated response in 450ms")
        LogBus.phone("Battery level is at 85%")

        val entries = LogBus.entries()
        assertEquals(4, entries.size)

        assertEquals(DeviceSource.GLASSES, entries[0].source)
        assertTrue(entries[0].message.contains("MYVU"))

        assertEquals(DeviceSource.BLUETOOTH, entries[1].source)
        assertEquals("Sony WH-1000XM4", entries[1].deviceName)

        assertEquals(DeviceSource.AI, entries[2].source)
        assertTrue(entries[2].message.contains("Gemini"))

        assertEquals(DeviceSource.PHONE, entries[3].source)
    }

    @Test
    fun testEntryListenerReceivesStructuredEntries() {
        val received = mutableListOf<LogEntry>()
        val listener = LogBus.EntryListener { entry ->
            received.add(entry)
        }
        LogBus.addEntryListener(listener)

        LogBus.glasses("HUD brightness calibrated to 70%")
        assertEquals(1, received.size)
        assertEquals(DeviceSource.GLASSES, received[0].source)

        LogBus.removeEntryListener(listener)
        LogBus.glasses("Another glasses event")
        assertEquals(1, received.size)
    }

    @Test
    fun testErrorAndWarningEntriesWithThrowable() {
        val testException = IllegalStateException("BT GATT disconnected unexpectedly")
        LogBus.error("Glasses connection dropped", testException)
        LogBus.warn("Battery critically low: 5%")

        val entries = LogBus.entries()
        assertEquals(2, entries.size)

        val errorEntry = entries[0]
        assertEquals(android.util.Log.ERROR, errorEntry.level)
        assertEquals("Glasses connection dropped", errorEntry.message)
        assertEquals(testException, errorEntry.throwable)
        assertEquals(DeviceSource.GLASSES, errorEntry.source)

        val warnEntry = entries[1]
        assertEquals(android.util.Log.WARN, warnEntry.level)
        assertEquals("Battery critically low: 5%", warnEntry.message)
    }
}

