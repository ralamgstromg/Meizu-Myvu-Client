package com.myvu.client.app

import com.myvu.client.core.LogBus
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class CrashReporterTest {

    @Test
    fun testCrashReporterInterception() {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        val delegateCalled = AtomicBoolean(false)
        val dummyOriginal = Thread.UncaughtExceptionHandler { _, _ ->
            delegateCalled.set(true)
        }

        try {
            Thread.setDefaultUncaughtExceptionHandler(dummyOriginal)

            val logLines = mutableListOf<String>()
            val listener = LogBus.Listener { line -> logLines.add(line) }
            LogBus.addListener(listener)

            try {
                val dummyContext = object : android.content.ContextWrapper(null) {}
                CrashReporter.install(dummyContext)

                val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
                val testThrowable = RuntimeException("Test crash message")
                currentHandler?.uncaughtException(Thread.currentThread(), testThrowable)

                assertTrue("Delegate handler should have been called", delegateCalled.get())
                assertTrue(
                    "LogBus should contain error entry",
                    logLines.any { it.contains("UNCAUGHT EXCEPTION") && it.contains("Test crash message") }
                )
            } finally {
                LogBus.removeListener(listener)
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        }
    }
}
