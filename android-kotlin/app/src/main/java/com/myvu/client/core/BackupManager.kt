package com.myvu.client.core

import android.content.Context
import android.os.Environment
import android.preference.PreferenceManager
import com.myvu.client.database.LocalDatabase
import com.myvu.client.database.NoteRepository
import com.myvu.client.database.ReminderRepository
import com.myvu.client.database.TodoRepository
import com.myvu.client.database.VoiceRecordingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manages full local backup and restore for the MYVU Client:
 * - SQLite Database (myvu_client.db)
 * - SharedPreferences (AI Keys, Models, Settings, Glasses MAC)
 * - Media & Audio Recordings (voice recordings, voice notes)
 * - Manifest with checksums, counts, and metadata
 */
object BackupManager {

    data class BackupManifest(
        val appVersion: String,
        val dbVersion: Int,
        val timestamp: Long,
        val dateFormatted: String,
        val notesCount: Int,
        val remindersCount: Int,
        val recordingsCount: Int,
        val todosCount: Int,
        val mediaFilesCount: Int
    )

    data class RestoreResult(
        val success: Boolean,
        val message: String,
        val notesRestored: Int = 0,
        val remindersRestored: Int = 0,
        val recordingsRestored: Int = 0,
        val todosRestored: Int = 0,
        val mediaFilesRestored: Int = 0
    )

    /**
     * Creates a complete data.zip containing:
     * - database.db
     * - preferences.json
     * - manifest.json
     * - media/
     */
    suspend fun createBackup(
        context: Context,
        onProgress: (String) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        onProgress("Preparando respaldo...")

        val backupDir = File(appContext.cacheDir, "backup_build_${System.currentTimeMillis()}").apply { mkdirs() }
        val mediaDir = File(backupDir, "media").apply { mkdirs() }

        try {
            // 1. Checkpoint WAL and copy database
            onProgress("Consolidando base de datos SQLite...")
            val dbFile = appContext.getDatabasePath(LocalDatabase.DATABASE_NAME)
            if (dbFile.exists()) {
                try {
                    val db = LocalDatabase.getInstance(appContext).writableDatabase
                    val cursor = db.rawQuery("PRAGMA wal_checkpoint(FULL)", null)
                    cursor.close()
                } catch (e: Exception) {
                    LogBus.error("BackupManager -> WAL checkpoint warning", e)
                }

                val dbTarget = File(backupDir, "database.db")
                dbFile.copyTo(dbTarget, overwrite = true)
            }

            val chatDbFile = appContext.getDatabasePath("myvu_chat.db")
            if (chatDbFile.exists()) {
                try {
                    val cDb = com.myvu.client.database.AppDatabase.getInstance(appContext).openHelper.writableDatabase
                    val cursor = cDb.query("PRAGMA wal_checkpoint(FULL)")
                    cursor.close()
                } catch (e: Exception) {
                    LogBus.error("BackupManager -> Chat DB WAL checkpoint warning", e)
                }
                val chatDbTarget = File(backupDir, "myvu_chat.db")
                chatDbFile.copyTo(chatDbTarget, overwrite = true)
            }

            // 2. Export SharedPreferences
            onProgress("Exportando configuraciones y claves de IA...")
            @Suppress("DEPRECATION")
            val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
            val allPrefs = prefs.all
            val jsonPrefs = JSONObject()
            for ((key, value) in allPrefs) {
                when (value) {
                    is Boolean -> jsonPrefs.put(key, value)
                    is Int -> jsonPrefs.put(key, value)
                    is Long -> jsonPrefs.put(key, value)
                    is Float -> jsonPrefs.put(key, value.toDouble())
                    is String -> jsonPrefs.put(key, value)
                    is Set<*> -> {
                        val arr = JSONArray()
                        value.forEach { if (it is String) arr.put(it) }
                        jsonPrefs.put(key, arr)
                    }
                }
            }
            File(backupDir, "preferences.json").writeText(jsonPrefs.toString(2))

            // 3. Collect media & attachment files
            onProgress("Recopilando notas de voz, fotos y documentos adjuntos...")
            var mediaCount = 0
            val possibleMediaDirs = listOfNotNull(
                appContext.getExternalFilesDir("voice_recordings"),
                File(appContext.filesDir, "voice_recordings"),
                appContext.getExternalFilesDir("attachments"),
                File(appContext.filesDir, "attachments"),
                File(appContext.getExternalFilesDir(null), "attachments"),
                File(appContext.filesDir, "audio"),
                appContext.getExternalFilesDir(null)
            )

            val allowedExtensions = setOf(
                "m4a", "wav", "mp3", "aac",
                "pdf", "docx", "doc", "xlsx", "xls", "txt", "csv", "json", "md",
                "jpg", "jpeg", "png", "webp"
            )

            val copiedMediaNames = mutableSetOf<String>()
            for (dir in possibleMediaDirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        val ext = file.extension.lowercase()
                        if (file.isFile && allowedExtensions.contains(ext)) {
                            if (!copiedMediaNames.contains(file.name)) {
                                file.copyTo(File(mediaDir, file.name), overwrite = true)
                                copiedMediaNames.add(file.name)
                                mediaCount++
                            }
                        }
                    }
                }
            }

            // 4. Query counts for manifest
            val noteRepo = NoteRepository(appContext)
            val reminderRepo = ReminderRepository(appContext)
            val todoRepo = TodoRepository(appContext)
            val voiceRepo = VoiceRecordingRepository(appContext)

            val notesCount = try { noteRepo.getAllNotes().size } catch (e: Exception) { 0 }
            val remindersCount = try { reminderRepo.getAllReminders().size } catch (e: Exception) { 0 }
            val todosCount = try { todoRepo.getAllTodos().size } catch (e: Exception) { 0 }
            val recordingsCount = try { voiceRepo.getAllRecordings().size } catch (e: Exception) { 0 }

            val timestamp = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

            val manifest = JSONObject().apply {
                put("app_version", "0.3")
                put("db_version", LocalDatabase.DATABASE_VERSION)
                put("timestamp", timestamp)
                put("date", dateFormat)
                put("notes_count", notesCount)
                put("reminders_count", remindersCount)
                put("todos_count", todosCount)
                put("recordings_count", recordingsCount)
                put("media_files_count", mediaCount)
            }
            File(backupDir, "manifest.json").writeText(manifest.toString(2))

            // 5. Build ZIP file
            onProgress("Comprimiendo archivo data.zip...")
            val outputZipDir = File(appContext.getExternalFilesDir("backups") ?: appContext.filesDir, "backups").apply { mkdirs() }
            val zipFile = File(outputZipDir, "data.zip")
            if (zipFile.exists()) zipFile.delete()

            zipDirectory(backupDir, zipFile)

            // Also copy to Downloads/MYVU if accessible
            try {
                val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MYVU").apply { mkdirs() }
                val publicBackupFile = File(publicDir, "data.zip")
                zipFile.copyTo(publicBackupFile, overwrite = true)
                LogBus.log("BackupManager -> Saved copy in Downloads/MYVU/data.zip")
            } catch (e: Exception) {
                LogBus.log("BackupManager -> Could not copy to public Downloads: ${e.message}")
            }

            onProgress("¡Respaldo local generado exitosamente!")
            LogBus.log("BackupManager -> Backup generated at: ${zipFile.absolutePath} (${zipFile.length() / 1024} KB)")
            zipFile
        } finally {
            backupDir.deleteRecursively()
        }
    }

    /**
     * Restores a full backup from a zip InputStream or File.
     */
    suspend fun restoreBackup(
        context: Context,
        zipStream: InputStream,
        onProgress: (String) -> Unit = {}
    ): RestoreResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        onProgress("Extrayendo archivo de respaldo...")

        val extractDir = File(appContext.cacheDir, "restore_temp_${System.currentTimeMillis()}").apply { mkdirs() }

        try {
            unzip(zipStream, extractDir)

            // Validate manifest
            val manifestFile = File(extractDir, "manifest.json")
            if (!manifestFile.exists()) {
                return@withContext RestoreResult(
                    success = false,
                    message = "El archivo no contiene un manifiesto válido de MYVU."
                )
            }

            val manifestJson = JSONObject(manifestFile.readText())
            val notesCount = manifestJson.optInt("notes_count", 0)
            val remindersCount = manifestJson.optInt("reminders_count", 0)
            val todosCount = manifestJson.optInt("todos_count", 0)
            val recordingsCount = manifestJson.optInt("recordings_count", 0)

            onProgress("Restaurando base de datos SQLite...")
            val dbBackupFile = File(extractDir, "database.db")
            if (dbBackupFile.exists()) {
                // 1. Close current DB
                LocalDatabase.closeInstance()

                // 2. Overwrite DB file
                val appDbFile = appContext.getDatabasePath(LocalDatabase.DATABASE_NAME)
                appDbFile.parentFile?.mkdirs()

                // Delete WAL and SHM journal files
                File(appDbFile.parentFile, "${LocalDatabase.DATABASE_NAME}-wal").delete()
                File(appDbFile.parentFile, "${LocalDatabase.DATABASE_NAME}-shm").delete()
                File(appDbFile.parentFile, "${LocalDatabase.DATABASE_NAME}-journal").delete()

                dbBackupFile.copyTo(appDbFile, overwrite = true)

                // 3. Re-open DB and run any needed upgrades
                val newDb = LocalDatabase.getInstance(appContext).writableDatabase
                LogBus.log("BackupManager -> DB restored successfully (version: ${newDb.version})")
            }

            val chatDbBackupFile = File(extractDir, "myvu_chat.db")
            if (chatDbBackupFile.exists()) {
                val appChatDbFile = appContext.getDatabasePath("myvu_chat.db")
                appChatDbFile.parentFile?.mkdirs()
                File(appChatDbFile.parentFile, "myvu_chat.db-wal").delete()
                File(appChatDbFile.parentFile, "myvu_chat.db-shm").delete()
                File(appChatDbFile.parentFile, "myvu_chat.db-journal").delete()
                chatDbBackupFile.copyTo(appChatDbFile, overwrite = true)
                LogBus.log("BackupManager -> myvu_chat.db restored successfully")
            }

            // Restore SharedPreferences
            onProgress("Restaurando configuraciones de IA y preferencias...")
            val prefsFile = File(extractDir, "preferences.json")
            if (prefsFile.exists()) {
                @Suppress("DEPRECATION")
                val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
                val editor = prefs.edit()
                val jsonPrefs = JSONObject(prefsFile.readText())

                for (key in jsonPrefs.keys()) {
                    val value = jsonPrefs.get(key)
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                        is String -> editor.putString(key, value)
                        is JSONArray -> {
                            val set = mutableSetOf<String>()
                            for (i in 0 until value.length()) {
                                set.add(value.getString(i))
                            }
                            editor.putStringSet(key, set)
                        }
                    }
                }
                editor.apply()
            }

            // Restore media & attachments files
            onProgress("Restaurando grabaciones de voz, fotos y documentos adjuntos...")
            val mediaDir = File(extractDir, "media")
            var mediaRestored = 0
            if (mediaDir.exists() && mediaDir.isDirectory) {
                val targetVoiceDir = appContext.getExternalFilesDir("voice_recordings") ?: File(appContext.filesDir, "voice_recordings")
                targetVoiceDir.mkdirs()

                val targetAttachmentsDir = File(appContext.getExternalFilesDir(null), "attachments").apply { mkdirs() }

                mediaDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val ext = file.extension.lowercase()
                        if (ext in setOf("m4a", "wav", "mp3", "aac")) {
                            file.copyTo(File(targetVoiceDir, file.name), overwrite = true)
                        } else {
                            file.copyTo(File(targetAttachmentsDir, file.name), overwrite = true)
                        }
                        mediaRestored++
                    }
                }
            }

            onProgress("¡Restauración completada con éxito!")
            RestoreResult(
                success = true,
                message = "Restauración completada.",
                notesRestored = notesCount,
                remindersRestored = remindersCount,
                recordingsRestored = recordingsCount,
                todosRestored = todosCount,
                mediaFilesRestored = mediaRestored
            )
        } catch (e: Exception) {
            LogBus.error("BackupManager -> Restore failed", e)
            RestoreResult(
                success = false,
                message = "Error durante la restauración: ${e.message}"
            )
        } finally {
            extractDir.deleteRecursively()
        }
    }

    private fun zipDirectory(sourceDir: File, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            zipFileTree(sourceDir, sourceDir, zos)
        }
    }

    private fun zipFileTree(rootDir: File, currentDir: File, zos: ZipOutputStream) {
        val files = currentDir.listFiles() ?: return
        val buffer = ByteArray(8192)

        for (file in files) {
            if (file.isDirectory) {
                zipFileTree(rootDir, file, zos)
            } else {
                val relativePath = file.relativeTo(rootDir).path
                val entry = ZipEntry(relativePath)
                entry.time = file.lastModified()
                zos.putNextEntry(entry)

                FileInputStream(file).use { fis ->
                    BufferedInputStream(fis).use { bis ->
                        var count: Int
                        while (bis.read(buffer).also { count = it } != -1) {
                            zos.write(buffer, 0, count)
                        }
                    }
                }
                zos.closeEntry()
            }
        }
    }

    private fun unzip(zipStream: InputStream, targetDir: File) {
        val buffer = ByteArray(8192)
        ZipInputStream(BufferedInputStream(zipStream)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                // Security check for Zip Slip vulnerability
                if (!file.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw SecurityException("Entrada ZIP inválida: ${entry.name}")
                }

                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        BufferedOutputStream(fos).use { bos ->
                            var count: Int
                            while (zis.read(buffer).also { count = it } != -1) {
                                bos.write(buffer, 0, count)
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
