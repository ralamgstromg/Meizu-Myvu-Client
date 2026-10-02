package com.myvu.client.app

import android.content.Context
import android.content.Intent
import android.os.Looper
import com.myvu.client.core.LogBus
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global uncaught exception handler and crash shield.
 * 1. Traps unexpected exceptions on both background threads and main UI thread.
 * 2. Writes full diagnostic crash report to local storage and LogBus.
 * 3. Prevents background worker crashes from terminating BLE / App services.
 * 4. Gracefully recovers the app to HomeActivity on critical main thread failures in production.
 */
object CrashReporter {

    private var isHandlingCrash = false
    var enableGracefulRescueInProduction = true

    fun install(context: Context) {
        val appCtx = try { context.applicationContext } catch (_: Throwable) { null }
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            var isMain = false
            try {
                isMain = try {
                    val mainLooper = Looper.getMainLooper()
                    mainLooper != null && thread == mainLooper.thread
                } catch (_: Throwable) {
                    false
                }

                val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()

                val crashLog = """
                    ================ CRASH REPORT ================
                    Time: $timeStamp
                    Thread: ${thread.name} (ID: ${thread.id}, isMain: $isMain)
                    Exception: ${throwable.javaClass.name}
                    Message: ${throwable.message}
                    ---------------- STACK TRACE -----------------
                    $stackTrace
                    ==============================================
                """.trimIndent()

                LogBus.error(
                    "UNCAUGHT EXCEPTION [mainThread=$isMain, thread='${thread.name}']: " +
                    "${throwable.javaClass.name}: ${throwable.message}",
                    throwable
                )

                if (appCtx != null) {
                    saveCrashReportToFile(appCtx, crashLog)
                }

                // If exception occurs on a background worker or async coroutine, absorb safely
                if (!isMain && appCtx != null) {
                    LogBus.warn("CrashReporter: Safely absorbed crash on background thread '${thread.name}' to preserve app stability")
                    try {
                        com.myvu.client.core.ServiceKeepAliveHelper.ensureServiceRunning(appCtx)
                    } catch (e: Throwable) {
                        LogBus.error("CrashReporter: Failed to re-ensure service running", e)
                    }
                    return@setDefaultUncaughtExceptionHandler
                }

                // If on main thread in production and not in recursive crash loop, perform graceful rescue
                if (isMain && appCtx != null && enableGracefulRescueInProduction && !isHandlingCrash) {
                    isHandlingCrash = true
                    LogBus.error("CrashReporter: Initiating graceful rescue on main thread crash...", null)

                    try {
                        val rescueIntent = Intent(appCtx, com.myvu.client.ui.home.HomeActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            putExtra("EXTRA_RESCUED_FROM_CRASH", true)
                        }
                        appCtx.startActivity(rescueIntent)
                        android.os.Process.killProcess(android.os.Process.myPid())
                        System.exit(10)
                        return@setDefaultUncaughtExceptionHandler
                    } catch (e: Exception) {
                        LogBus.error("CrashReporter: Failed graceful restart", e)
                    }
                }
            } catch (ignored: Throwable) {
                // Ignore any internal crash reporter exception
            }

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    const val MAX_LOG_BYTES = 1L * 1024 * 1024
    const val KEEP_LOG_FILES = 5

    /** Crash log files, newest first: crash_log.txt, crash_log.1.txt, ... */
    fun logFiles(context: Context): List<File> {
        val dir = logDir(context)
        return (listOf(File(dir, "crash_log.txt")) + (1 until KEEP_LOG_FILES).map { File(dir, "crash_log.$it.txt") })
            .filter { it.exists() }
    }

    private fun logDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "logs").apply { mkdirs() }

    /** Shifts crash_log.txt -> .1 -> .2 ... once it exceeds [maxBytes]; drops the oldest. */
    internal fun rotateIfNeeded(dir: File, maxBytes: Long = MAX_LOG_BYTES) {
        val current = File(dir, "crash_log.txt")
        if (!current.exists() || current.length() < maxBytes) return
        File(dir, "crash_log.${KEEP_LOG_FILES - 1}.txt").delete()
        for (i in KEEP_LOG_FILES - 2 downTo 1) {
            File(dir, "crash_log.$i.txt").takeIf { it.exists() }?.renameTo(File(dir, "crash_log.${i + 1}.txt"))
        }
        current.renameTo(File(dir, "crash_log.1.txt"))
    }

    private fun saveCrashReportToFile(context: Context, report: String) {
        try {
            val dir = logDir(context)
            rotateIfNeeded(dir)
            FileWriter(File(dir, "crash_log.txt"), true).use { writer ->
                writer.appendLine(report)
            }
        } catch (e: Exception) {
            // LogBus may be the thing failing here: use the platform log directly.
            android.util.Log.w("CrashReporter", "Could not write crash log: ${e.message}")
        }
    }
}
