package com.myvu.client.app

import android.content.Context
import com.myvu.client.core.LogBus

/**
 * Global uncaught exception handler.
 * Surfaces crash details in LogBus before delegating to the system default handler.
 */
object CrashReporter {
    fun install(context: Context) {
        val system = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                LogBus.error(
                    "UNCAUGHT EXCEPTION on thread '${thread.name}': " +
                    "${throwable.javaClass.name}: ${throwable.message}",
                    throwable
                )
            } catch (ignored: Exception) {}
            system?.uncaughtException(thread, throwable)
        }
    }
}
