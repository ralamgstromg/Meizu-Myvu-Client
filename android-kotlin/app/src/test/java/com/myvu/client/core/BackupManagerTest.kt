package com.myvu.client.core

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.myvu.client.core.backup.BackupArchive
import com.myvu.client.database.LocalDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var voiceDir: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        @Suppress("DEPRECATION")
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().commit()
        voiceDir = context.getExternalFilesDir("voice_recordings")!!.apply { mkdirs() }
        LocalDatabase.getInstance(context).writableDatabase
            .execSQL("INSERT INTO notes (title, body, created_at, updated_at) VALUES ('t', 'c', 1, 1)")
    }

    @After
    fun tearDown() {
        LocalDatabase.closeInstance()
    }

    private fun backup(): File = runBlocking { BackupManager.createBackup(context) }

    private fun restore(zip: File) = runBlocking { zip.inputStream().use { BackupManager.restoreBackup(context, it) } }

    private fun readEntry(zip: File, name: String): String =
        ZipFile(zip).use { z -> z.getInputStream(z.getEntry(name)).bufferedReader().readText() }

    /** Rebuilds [zip] replacing one entry's bytes (keeps the original manifest). */
    private fun tamper(zip: File, entryName: String, bytes: ByteArray): File {
        val out = File(zip.parentFile, "tampered-${System.nanoTime()}.zip")
        ZipFile(zip).use { src ->
            ZipOutputStream(out.outputStream()).use { dst ->
                for (e in src.entries()) {
                    dst.putNextEntry(ZipEntry(e.name))
                    if (e.name == entryName) dst.write(bytes) else src.getInputStream(e).use { it.copyTo(dst) }
                    dst.closeEntry()
                }
            }
        }
        return out
    }

    @Test
    fun roundTripRestoresTypedPreferencesAndMedia() {
        prefs.edit()
            .putString("weather_place", "Bogotá")
            .putLong("small_long", 5L)
            .putInt("volume", 7)
            .putBoolean("mirror_notifications", true)
            .putStringSet("mirror_allowed_packages", setOf("a", "b"))
            .commit()
        File(voiceDir, "nota.m4a").writeText("audio")

        val zip = backup()
        assertTrue(zip.name.startsWith(BackupManager.FILE_PREFIX))

        prefs.edit().clear().commit()
        File(voiceDir, "nota.m4a").delete()

        val result = restore(zip)

        assertTrue(result.message, result.success)
        assertTrue(result.requiresRestart)
        assertEquals(1, result.notesRestored)
        assertEquals("Bogotá", prefs.getString("weather_place", null))
        assertEquals(5L, prefs.getLong("small_long", 0L))
        assertEquals(7, prefs.getInt("volume", 0))
        assertTrue(prefs.getBoolean("mirror_notifications", false))
        assertEquals(setOf("a", "b"), prefs.getStringSet("mirror_allowed_packages", null))
        assertEquals("audio", File(voiceDir, "nota.m4a").readText())
    }

    @Test
    fun chatHistoryIsRestoredWhileRoomStaysOpen() = runBlocking {
        val dao = com.myvu.client.database.AppDatabase.getInstance(context).chatDao()
        dao.deleteAllMessages()
        dao.insertMessage(com.myvu.client.data.ChatMessage(sessionId = "s1", direction = "USER", content = "hola", mediaType = "TEXT"))
        val zip = backup()
        dao.deleteAllMessages()
        dao.insertMessage(com.myvu.client.data.ChatMessage(sessionId = "s2", direction = "USER", content = "otro", mediaType = "TEXT"))

        val result = restore(zip)

        assertTrue(result.message, result.success)
        // Same DAO instance keeps working: Room was never closed.
        assertEquals(listOf("hola"), dao.getAllMessages().map { it.content })
    }

    @Test
    fun credentialsAreNotExported() {
        prefs.edit()
            .putString("gdrive_refresh_token", "SECRET-R")
            .putString("gdrive_client_secret", "SECRET-C")
            .putString("openai_api_key", "SECRET-K")
            .putString("weather_place", "Lima")
            .commit()

        val exported = readEntry(backup(), "preferences.json")

        assertFalse(exported.contains("SECRET"))
        assertTrue(exported.contains("Lima"))
    }

    @Test
    fun corruptedEntryIsRejectedWithoutTouchingLiveData() {
        prefs.edit().putString("weather_place", "Quito").commit()
        val zip = backup()
        prefs.edit().putString("weather_place", "Cali").commit()

        val result = restore(tamper(zip, "preferences.json", "{}".toByteArray()))

        assertFalse(result.success)
        assertTrue(result.message.contains("checksum"))
        assertEquals("Cali", prefs.getString("weather_place", null))
    }

    @Test
    fun newerDatabaseVersionIsRejected() {
        val zip = backup()
        val manifest = JSONObject(readEntry(zip, "manifest.json"))
            .put("db_version", LocalDatabase.DATABASE_VERSION + 1)

        val result = restore(tamper(zip, "manifest.json", manifest.toString().toByteArray()))

        assertFalse(result.success)
        assertTrue(result.message.contains("más nueva"))
    }

    @Test(expected = SecurityException::class)
    fun zipSlipEntryIsRejected() {
        val bytes = ByteArrayOutputStream().also { buf ->
            ZipOutputStream(buf).use { z ->
                z.putNextEntry(ZipEntry("../evil.txt"))
                z.write("x".toByteArray())
                z.closeEntry()
            }
        }.toByteArray()
        val target = File(context.cacheDir, "slip").apply { mkdirs() }

        BackupArchive.extract(bytes.inputStream(), target)
    }

    @Test
    fun legacyV1BackupIsStillRestorable() {
        val legacy = File(context.cacheDir, "data.zip")
        ZipOutputStream(legacy.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json"))
            z.write("""{"app_version":"0.3","db_version":7,"notes_count":0}""".toByteArray())
            z.putNextEntry(ZipEntry("preferences.json"))
            z.write("""{"weather_place":"Medellín","volume":4}""".toByteArray())
            z.putNextEntry(ZipEntry("media/old.wav"))
            z.write("wav".toByteArray())
        }

        val result = restore(legacy)

        assertTrue(result.message, result.success)
        assertEquals("Medellín", prefs.getString("weather_place", null))
        assertEquals(4, prefs.getInt("volume", 0))
        assertEquals("wav", File(voiceDir, "old.wav").readText())
    }

    @Test
    fun failureWhileApplyingRollsBackDatabaseAndPreferences() {
        val zip = backup()
        LocalDatabase.getInstance(context).writableDatabase
            .execSQL("INSERT INTO notes (title, body, created_at, updated_at) VALUES ('t2', 'c2', 2, 2)")
        prefs.edit().putString("weather_place", "Cusco").commit()

        // Valid checksum, but preferences.json is not JSON: validation passes, applying fails.
        val badPrefs = "not json".toByteArray()
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(badPrefs).joinToString("") { "%02x".format(it) }
        val manifest = JSONObject(readEntry(zip, "manifest.json"))
        manifest.getJSONObject("files").put("preferences.json", sha)
        val broken = tamper(tamper(zip, "preferences.json", badPrefs), "manifest.json", manifest.toString().toByteArray())

        val result = restore(broken)

        assertFalse(result.success)
        assertEquals("Cusco", prefs.getString("weather_place", null))
        val notes = LocalDatabase.getInstance(context).readableDatabase
            .rawQuery("SELECT COUNT(*) FROM notes", null).use { it.moveToFirst(); it.getInt(0) }
        assertEquals(2, notes)
    }

    @Test
    fun onlyTheNewestBackupsAreKept() {
        val dir = backup().parentFile!!
        for (i in 1..BackupManager.KEEP_BACKUPS + 2) {
            File(dir, "${BackupManager.FILE_PREFIX}20000101-00000$i.zip").writeText("old")
        }

        backup()

        val kept = dir.listFiles { f -> f.name.startsWith(BackupManager.FILE_PREFIX) }!!
        assertEquals(BackupManager.KEEP_BACKUPS, kept.size)
    }
}
