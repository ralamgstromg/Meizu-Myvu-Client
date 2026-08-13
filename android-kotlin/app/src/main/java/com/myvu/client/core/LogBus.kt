package com.myvu.client.core

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Date
import java.util.Deque
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A process-wide log ring buffer with listeners and SharedFlow support.
 *
 * The connection runs on background threads while the UI comes and goes, so
 * log lines are buffered here and replayed when a screen attaches. Listeners
 * are always invoked on the main thread.
 *
 * Every Android touchpoint is guarded: android.jar's classes are non-functional
 * stubs under JVM unit tests, and without these guards merely logging would
 * throw ExceptionInInitializerError and make every pure-logic class that logs
 * untestable off-device.
 */
object LogBus {
    const val TAG = "myvu"
    private const val CAPACITY = 2000

    fun interface Listener {
        fun onLine(line: String)
    }

    data class LogMessage(
        val level: Int,
        val message: String,
        val throwable: Throwable? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val BUFFER: Deque<String> = ArrayDeque(CAPACITY)
    private val LISTENERS = CopyOnWriteArrayList<Listener>()
    private val STAMP = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _logFlow = MutableSharedFlow<LogMessage>(extraBufferCapacity = 64)
    val logFlow: SharedFlow<LogMessage> = _logFlow.asSharedFlow()

    /** Null when there is no Android runtime (i.e. under JVM unit tests). */
    private val MAIN: Handler? by lazy { createMainHandler() }

    private fun createMainHandler(): Handler? {
        return try {
            val looper = Looper.getMainLooper()
            if (looper != null) Handler(looper) else null
        } catch (ignored: Throwable) {
            null // no Android runtime
        }
    }

    @Volatile
    @JvmStatic
    var isEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                clear()
            }
        }

    @JvmStatic
    fun log(msg: String) {
        if (!isEnabled) return
        androidLog(Log.INFO, msg, null)
        emit(Log.INFO, stamp() + "  " + msg, msg, null)
    }

    @JvmStatic
    fun warn(msg: String) {
        if (!isEnabled) return
        androidLog(Log.WARN, msg, null)
        emit(Log.WARN, stamp() + "  !! " + msg, msg, null)
    }

    @JvmStatic
    @JvmOverloads
    fun error(msg: String, t: Throwable? = null) {
        if (!isEnabled) return
        androidLog(Log.ERROR, msg, t)
        val detail = if (t == null) msg else "$msg: ${t.javaClass.simpleName}: ${t.message}"
        emit(Log.ERROR, stamp() + "  !! " + detail, msg, t)
    }

    /** Verbose frame-level detail: goes to logcat only, never the on-screen buffer. */
    @JvmStatic
    fun trace(msg: String) {
        if (!isEnabled) return
        androidLog(Log.DEBUG, msg, null)
    }

    private fun androidLog(level: Int, msg: String, t: Throwable?) {
        try {
            when (level) {
                Log.WARN -> Log.w(TAG, msg)
                Log.ERROR -> Log.e(TAG, msg, t)
                Log.DEBUG -> Log.d(TAG, msg)
                else -> Log.i(TAG, msg)
            }
        } catch (ignored: Throwable) {
            // Stubbed android.util.Log under unit tests.
        }
    }

    private fun stamp(): String {
        synchronized(STAMP) {
            return STAMP.format(Date())
        }
    }

    private fun emit(level: Int, line: String, rawMsg: String, t: Throwable?) {
        synchronized(BUFFER) {
            if (BUFFER.size >= CAPACITY) BUFFER.removeFirst()
            BUFFER.addLast(line)
        }
        _logFlow.tryEmit(LogMessage(level, rawMsg, t))

        if (LISTENERS.isEmpty()) return

        val dispatch = Runnable {
            for (l in LISTENERS) {
                l.onLine(line)
            }
        }
        val handler = MAIN
        if (handler != null) {
            handler.post(dispatch)
        } else {
            dispatch.run() // no looper: deliver inline
        }
    }

    /** Returns the buffered history so a newly attached screen can catch up. */
    @JvmStatic
    fun history(): List<String> {
        synchronized(BUFFER) {
            return ArrayList(BUFFER)
        }
    }

    @JvmStatic
    fun addListener(l: Listener) {
        LISTENERS.addIfAbsent(l)
    }

    @JvmStatic
    fun removeListener(l: Listener) {
        LISTENERS.remove(l)
    }

    @JvmStatic
    fun clear() {
        synchronized(BUFFER) {
            BUFFER.clear()
        }
    }
}
