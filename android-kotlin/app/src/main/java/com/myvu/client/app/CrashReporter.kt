package com.myvu.client.app

import android.content.Context
import android.os.Looper
import com.myvu.client.core.LogBus

/**
 * Global uncaught exception handler.
 * Surfaces crash details in LogBus.
 * Protects background worker threads from crashing the main process while
 * gracefully delegating main-thread crashes to the system handler.
 */
object CrashReporter {
    fun install(context: Context) {
        val system = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val isMain = (thread == Looper.getMainLooper().thread)
                LogBus.error(
                    "UNCAUGHT EXCEPTION [mainThread=$isMain, thread='${thread.name}']: " +
                    "${throwable.javaClass.name}: ${throwable.message}",
                    throwable
                )

                // If exception occurs on a background worker or async task, absorb after logging to prevent dropping the BLE connection
                if (!isMain) {
                    LogBus.warn("CrashReporter: Prevented process termination from background thread '${thread.name}'")
                    return@setDefaultUncaughtExceptionHandler
                }
            } catch (ignored: Exception) {}
            system?.uncaughtException(thread, throwable)
        }
    }
}
