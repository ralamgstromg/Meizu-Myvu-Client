package com.myvu.client.app

import android.content.Context
import android.content.Intent
import android.os.Looper
import com.myvu.client.core.LogBus
import com.myvu.client.ui.ConnectActivity
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
 * 4. Gracefully recovers the app to ConnectActivity on critical main thread failures in production.
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
                        val rescueIntent = Intent(appCtx, ConnectActivity::class.java).apply {
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

    private fun saveCrashReportToFile(context: Context, report: String) {
        try {
            val dir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
            val logFile = File(dir, "crash_log.txt")
            FileWriter(logFile, true).use { writer ->
                writer.appendLine(report)
            }
        } catch (e: Exception) {
            // Ignore file logging errors
        }
    }
}
