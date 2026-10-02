package com.myvu.client.core.errors

import com.myvu.client.app.CrashReporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AppErrorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun mapsThrowablesToUserFacingKinds() {
        assertTrue(AppError.from(SocketTimeoutException()) is AppError.Timeout)
        assertTrue(AppError.from(UnknownHostException()) is AppError.Network)
        assertTrue(AppError.from(IOException()) is AppError.Network)
        assertTrue(AppError.from(SecurityException()) is AppError.Permission)
        assertTrue(AppError.from(IllegalStateException()) is AppError.Unknown)
        assertTrue(AppError.Config("la clave de IA").userMessage.contains("la clave de IA"))
    }

    @Test
    fun attemptReturnsNullInsteadOfThrowing() {
        assertEquals(2, attempt("ok") { 1 + 1 })
        assertNull(attempt("boom") { throw IllegalStateException("x") })
    }

    @Test
    fun crashLogRotatesAndKeepsAtMostFiveFiles() {
        val dir = tmp.newFolder("logs")
        repeat(7) { i ->
            File(dir, "crash_log.txt").writeText("crash $i ".repeat(10))
            CrashReporter.rotateIfNeeded(dir, maxBytes = 10)
        }
        val files = dir.listFiles()!!.map { it.name }.sorted()
        assertEquals(listOf("crash_log.1.txt", "crash_log.2.txt", "crash_log.3.txt", "crash_log.4.txt"), files)
        assertTrue(File(dir, "crash_log.1.txt").readText().startsWith("crash 6"))
    }
}
