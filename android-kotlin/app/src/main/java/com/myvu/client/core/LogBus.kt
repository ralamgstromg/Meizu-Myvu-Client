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
 * Supported device categories/sources for unified activity logging.
 */
enum class DeviceSource(val displayName: String, val tagKey: String) {
    ALL("Todos", "all"),
    GLASSES("Gafas MYVU", "glasses"),
    BLUETOOTH("Dispositivos BT", "bluetooth"),
    PHONE("Teléfono / Sistema", "phone"),
    AI("Asistente IA", "ai")
}

/**
 * Structured log record containing metadata for rich filtering and presentation.
 */
data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val source: DeviceSource,
    val level: Int,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
    val deviceName: String? = null,
    val formattedLine: String = ""
)

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

    fun interface EntryListener {
        fun onEntry(entry: LogEntry)
    }

    data class LogMessage(
        val level: Int,
        val message: String,
        val throwable: Throwable? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val BUFFER: Deque<String> = ArrayDeque(CAPACITY)
    private val ENTRIES: Deque<LogEntry> = ArrayDeque(CAPACITY)
    private val LISTENERS = CopyOnWriteArrayList<Listener>()
    private val ENTRY_LISTENERS = CopyOnWriteArrayList<EntryListener>()
    private val STAMP = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var sequenceCounter = 0L

    private val _logFlow = MutableSharedFlow<LogMessage>(extraBufferCapacity = 64)
    val logFlow: SharedFlow<LogMessage> = _logFlow.asSharedFlow()

    private val _entryFlow = MutableSharedFlow<LogEntry>(extraBufferCapacity = 128)
    val entryFlow: SharedFlow<LogEntry> = _entryFlow.asSharedFlow()

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
    @JvmOverloads
    fun log(
        msg: String,
        source: DeviceSource? = null,
        tag: String = TAG,
        deviceName: String? = null
    ) {
        if (!isEnabled) return
        androidLog(Log.INFO, tag, msg, null)
        val finalSource = source ?: inferDeviceSource(msg, tag)
        emit(Log.INFO, stamp() + "  " + msg, msg, null, finalSource, tag, deviceName)
    }

    @JvmStatic
    @JvmOverloads
    fun warn(
        msg: String,
        source: DeviceSource? = null,
        tag: String = TAG,
        deviceName: String? = null
    ) {
        if (!isEnabled) return
        androidLog(Log.WARN, tag, msg, null)
        val finalSource = source ?: inferDeviceSource(msg, tag)
        emit(Log.WARN, stamp() + "  !! " + msg, msg, null, finalSource, tag, deviceName)
    }

    @JvmStatic
    @JvmOverloads
    fun error(
        msg: String,
        t: Throwable? = null,
        source: DeviceSource? = null,
        tag: String = TAG,
        deviceName: String? = null
    ) {
        if (!isEnabled) return
        androidLog(Log.ERROR, tag, msg, t)
        val detail = if (t == null) msg else "$msg: ${t.javaClass.simpleName}: ${t.message}"
        val finalSource = source ?: inferDeviceSource(msg, tag)
        emit(Log.ERROR, stamp() + "  !! " + detail, msg, t, finalSource, tag, deviceName)
    }

    @JvmStatic
    fun glasses(msg: String, level: Int = Log.INFO) {
        when (level) {
            Log.WARN -> warn(msg, source = DeviceSource.GLASSES)
            Log.ERROR -> error(msg, null, source = DeviceSource.GLASSES)
            else -> log(msg, source = DeviceSource.GLASSES)
        }
    }

    @JvmStatic
    fun bluetooth(msg: String, deviceName: String? = null, level: Int = Log.INFO) {
        when (level) {
            Log.WARN -> warn(msg, source = DeviceSource.BLUETOOTH, deviceName = deviceName)
            Log.ERROR -> error(msg, null, source = DeviceSource.BLUETOOTH, deviceName = deviceName)
            else -> log(msg, source = DeviceSource.BLUETOOTH, deviceName = deviceName)
        }
    }

    @JvmStatic
    fun phone(msg: String, level: Int = Log.INFO) {
        when (level) {
            Log.WARN -> warn(msg, source = DeviceSource.PHONE)
            Log.ERROR -> error(msg, null, source = DeviceSource.PHONE)
            else -> log(msg, source = DeviceSource.PHONE)
        }
    }

    @JvmStatic
    fun ai(msg: String, level: Int = Log.INFO) {
        when (level) {
            Log.WARN -> warn(msg, source = DeviceSource.AI)
            Log.ERROR -> error(msg, null, source = DeviceSource.AI)
            else -> log(msg, source = DeviceSource.AI)
        }
    }

    /** Verbose frame-level detail: goes to logcat only, never the on-screen buffer. */
    @JvmStatic
    fun trace(msg: String) {
        if (!isEnabled) return
        androidLog(Log.DEBUG, TAG, msg, null)
    }

    private fun inferDeviceSource(msg: String, tag: String): DeviceSource {
        val lower = (msg + " " + tag).lowercase(Locale.ROOT)
        return when {
            lower.contains("glasses") || lower.contains("lens") || lower.contains("myvu") ||
                lower.contains("temple") || lower.contains("touchpad") || lower.contains("hud") ||
                lower.contains("flyme") || lower.contains("gesture") || lower.contains("rfcomm") ||
                lower.contains("action_btn") || lower.contains("action_button") ||
                lower.contains("glass_event") || lower.contains("turn icon") ||
                lower.contains("send to lens") -> DeviceSource.GLASSES

            lower.contains("bluetooth") || lower.contains("headphone") || lower.contains("headset") ||
                lower.contains("a2dp") || lower.contains("audio device") || lower.contains("bt device") ||
                lower.contains("ble") || lower.contains("sco") || lower.contains("bond") ||
                lower.contains("airpods") || lower.contains("galaxy buds") ||
                (lower.contains("disconnect") && lower.contains("device")) -> DeviceSource.BLUETOOTH

            lower.contains("gemini") || lower.contains("aura") || lower.contains("assistant") ||
                lower.contains("chat") || lower.contains("llm") || lower.contains("stt") ||
                lower.contains("tts") || lower.contains("transcrib") || lower.contains("whisper") ||
                lower.contains("groq") || lower.contains("openai") || lower.contains("ai prompt") ||
                lower.contains("speech") -> DeviceSource.AI

            else -> DeviceSource.PHONE
        }
    }

    private fun androidLog(level: Int, tag: String, msg: String, t: Throwable?) {
        try {
            when (level) {
                Log.WARN -> Log.w(tag, msg)
                Log.ERROR -> Log.e(tag, msg, t)
                Log.DEBUG -> Log.d(tag, msg)
                else -> Log.i(tag, msg)
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

    private fun emit(
        level: Int,
        line: String,
        rawMsg: String,
        t: Throwable?,
        source: DeviceSource,
        tag: String,
        deviceName: String?
    ) {
        val now = System.currentTimeMillis()
        val entry: LogEntry
        synchronized(BUFFER) {
            sequenceCounter++
            entry = LogEntry(
                id = sequenceCounter,
                timestamp = now,
                source = source,
                level = level,
                tag = tag,
                message = rawMsg,
                throwable = t,
                deviceName = deviceName,
                formattedLine = line
            )
            if (BUFFER.size >= CAPACITY) BUFFER.removeFirst()
            BUFFER.addLast(line)

            if (ENTRIES.size >= CAPACITY) ENTRIES.removeFirst()
            ENTRIES.addLast(entry)
        }

        _logFlow.tryEmit(LogMessage(level, rawMsg, t, now))
        _entryFlow.tryEmit(entry)

        if (LISTENERS.isEmpty() && ENTRY_LISTENERS.isEmpty()) return

        val dispatch = Runnable {
            for (l in LISTENERS) {
                l.onLine(line)
            }
            for (el in ENTRY_LISTENERS) {
                el.onEntry(entry)
            }
        }
        val handler = MAIN
        if (handler != null) {
            handler.post(dispatch)
        } else {
            dispatch.run() // no looper: deliver inline
        }
    }

    /** Returns the buffered history formatted lines so a newly attached screen can catch up. */
    @JvmStatic
    fun history(): List<String> {
        synchronized(BUFFER) {
            return ArrayList(BUFFER)
        }
    }

    /** Returns the structured history records. */
    @JvmStatic
    fun entries(): List<LogEntry> {
        synchronized(ENTRIES) {
            return ArrayList(ENTRIES)
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
    fun addEntryListener(l: EntryListener) {
        ENTRY_LISTENERS.addIfAbsent(l)
    }

    @JvmStatic
    fun removeEntryListener(l: EntryListener) {
        ENTRY_LISTENERS.remove(l)
    }

    @JvmStatic
    fun clear() {
        synchronized(BUFFER) {
            BUFFER.clear()
            ENTRIES.clear()
        }
    }
}

