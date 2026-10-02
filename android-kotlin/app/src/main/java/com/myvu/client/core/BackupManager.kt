package com.myvu.client.core

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.preference.PreferenceManager
import android.provider.MediaStore
import com.myvu.client.core.backup.BackupArchive
import com.myvu.client.core.backup.PreferencesCodec
import com.myvu.client.database.AppDatabase
import com.myvu.client.database.LocalDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full local backup and restore for the MYVU Client.
 *
 * Archive format v2 (v1 archives are still restorable):
 * - `manifest.json`: format/app/db versions, counts and a SHA-256 for every other entry
 * - `database.db`, `myvu_chat.db`: SQLite databases after a WAL checkpoint
 * - `preferences.json`: typed SharedPreferences, credentials excluded (see [PreferencesCodec])
 * - `media/<dir>/<file>`: voice recordings and attachments, keyed by their source directory
 *
 * Restore validates the whole archive before touching live data. The SQLite
 * database file is swapped (SQLiteOpenHelper reopens lazily); the Room chat
 * database stays open and its tables are copied in one transaction, because
 * singletons hold its DAOs and a closed Room instance cannot be reopened. If
 * any step fails, the database and preferences are rolled back. The app must
 * be restarted afterwards so every cache reloads the restored data.
 */
object BackupManager {

    data class RestoreResult(
        val success: Boolean,
        val message: String,
        val notesRestored: Int = 0,
        val remindersRestored: Int = 0,
        val recordingsRestored: Int = 0,
        val todosRestored: Int = 0,
        val mediaFilesRestored: Int = 0,
        val requiresRestart: Boolean = false
    )

    const val FORMAT_VERSION = 2
    const val FILE_PREFIX = "myvu-backup-"
    const val KEEP_BACKUPS = 5

    private const val MANIFEST = "manifest.json"
    private const val PREFERENCES = "preferences.json"
    private const val MAIN_DB_ENTRY = "database.db"
    private const val CHAT_DB_NAME = "myvu_chat.db"
    private const val MEDIA_PREFIX = "media/"

    private val MEDIA_EXTENSIONS = setOf(
        "m4a", "wav", "mp3", "aac", "ogg", "opus",
        "pdf", "docx", "doc", "xlsx", "xls", "txt", "csv", "json", "md",
        "jpg", "jpeg", "png", "webp"
    )
    private val AUDIO_EXTENSIONS = setOf("m4a", "wav", "mp3", "aac", "ogg", "opus")

    // ------------------------------------------------------------------ create

    /** Builds `myvu-backup-<timestamp>.zip`, keeps the last [KEEP_BACKUPS] and copies it to Downloads/MYVU. */
    suspend fun createBackup(
        context: Context,
        onProgress: (String) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val timestamp = System.currentTimeMillis()
        val outDir = backupDir(app)
        val name = FILE_PREFIX + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timestamp)) + ".zip"
        val partial = File(outDir, "$name.partial")
        val zipFile = File(outDir, name)

        onProgress("Consolidando bases de datos...")
        checkpointDatabases(app)

        try {
            FileOutputStream(partial).use { out ->
                BackupArchive.write(out) { zip ->
                    val mainDb = app.getDatabasePath(LocalDatabase.DATABASE_NAME)
                    if (mainDb.exists()) zip.putFile(MAIN_DB_ENTRY, mainDb)
                    val chatDb = chatDbFile(app)
                    if (chatDb.exists()) zip.putFile(CHAT_DB_NAME, chatDb)

                    onProgress("Exportando configuraciones (sin credenciales)...")
                    zip.putBytes(PREFERENCES, PreferencesCodec.export(defaultPrefs(app).all).toString(2).toByteArray())

                    onProgress("Recopilando grabaciones y adjuntos...")
                    var mediaCount = 0
                    for ((key, dir) in mediaDirs(app)) {
                        dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in MEDIA_EXTENSIONS }?.forEach {
                            zip.putFile("$MEDIA_PREFIX$key/${it.name}", it)
                            mediaCount++
                        }
                    }

                    onProgress("Generando manifiesto...")
                    val files = JSONObject()
                    zip.checksums.forEach { (path, sha) -> files.put(path, sha) }
                    val manifest = JSONObject()
                        .put("format_version", FORMAT_VERSION)
                        .put("app_version", appVersion(app))
                        .put("db_version", LocalDatabase.DATABASE_VERSION)
                        .put("timestamp", timestamp)
                        .put("date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp)))
                        .put("notes_count", countRows(app, "notes"))
                        .put("reminders_count", countRows(app, "reminders"))
                        .put("todos_count", countRows(app, "todos"))
                        .put("recordings_count", countRows(app, "voice_recordings"))
                        .put("media_files_count", mediaCount)
                        .put("files", files)
                    zip.putManifest(MANIFEST, manifest.toString(2).toByteArray())
                }
            }
            if (!partial.renameTo(zipFile)) throw IOException("No se pudo finalizar ${zipFile.name}")
        } finally {
            partial.delete()
        }

        rotateBackups(outDir)
        copyToDownloads(app, zipFile)
        onProgress("¡Respaldo local generado exitosamente!")
        LogBus.log("BackupManager -> Backup generated at: ${zipFile.absolutePath} (${zipFile.length() / 1024} KB)")
        zipFile
    }

    // ----------------------------------------------------------------- restore

    suspend fun restoreBackup(
        context: Context,
        zipStream: InputStream,
        onProgress: (String) -> Unit = {}
    ): RestoreResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val work = File(app.cacheDir, "restore_${System.currentTimeMillis()}")
        val staging = File(work, "staging").apply { mkdirs() }
        val rollbackDir = File(work, "rollback").apply { mkdirs() }

        try {
            onProgress("Extrayendo y verificando respaldo...")
            val extracted = BackupArchive.extract(zipStream, staging)
            val manifest = validate(staging, extracted)

            onProgress("Restaurando bases de datos...")
            val prefs = defaultPrefs(app)
            val prefsSnapshot = HashMap(prefs.all)
            LocalDatabase.closeInstance()
            val mainDb = app.getDatabasePath(LocalDatabase.DATABASE_NAME)
            val mainDbRollback = File(rollbackDir, LocalDatabase.DATABASE_NAME).takeIf { mainDb.exists() }
            mainDbRollback?.let { mainDb.copyTo(it, overwrite = true) }

            val mediaRestored = try {
                replaceDatabase(File(staging, MAIN_DB_ENTRY), mainDb)

                onProgress("Restaurando configuraciones...")
                File(staging, PREFERENCES).takeIf { it.exists() }?.let { file ->
                    val editor = prefs.edit()
                    PreferencesCodec.import(JSONObject(file.readText()), editor)
                    if (!editor.commit()) throw IOException("No se pudieron guardar las preferencias")
                }

                onProgress("Restaurando grabaciones y adjuntos...")
                val media = restoreMedia(app, staging, legacy = manifest.optInt("format_version", 1) < 2)

                // Last step: it commits atomically, so a failure here leaves the chat untouched.
                onProgress("Restaurando historial de chat...")
                File(staging, CHAT_DB_NAME).takeIf { it.exists() }?.let { copyRoomTables(app, it) }
                media
            } catch (e: Exception) {
                LogBus.error("BackupManager -> Restore failed while applying, rolling back", e)
                LocalDatabase.closeInstance()
                mainDbRollback?.let { replaceDatabase(it, mainDb) }
                prefs.edit().also { PreferencesCodec.restoreSnapshot(prefsSnapshot, it) }.commit()
                throw e
            }

            // Re-open to run onUpgrade for older backups.
            LocalDatabase.getInstance(app).writableDatabase
            onProgress("¡Restauración completada con éxito!")
            RestoreResult(
                success = true,
                message = "Restauración completada.",
                notesRestored = manifest.optInt("notes_count", 0),
                remindersRestored = manifest.optInt("reminders_count", 0),
                recordingsRestored = manifest.optInt("recordings_count", 0),
                todosRestored = manifest.optInt("todos_count", 0),
                mediaFilesRestored = mediaRestored,
                requiresRestart = true
            )
        } catch (e: Exception) {
            LogBus.error("BackupManager -> Restore failed", e)
            RestoreResult(success = false, message = "Error durante la restauración: ${e.message}")
        } finally {
            work.deleteRecursively()
        }
    }

    /** Checks manifest, versions and checksums. Throws before any live data is touched. */
    private fun validate(staging: File, extracted: Map<String, String>): JSONObject {
        val manifestFile = File(staging, MANIFEST)
        if (!manifestFile.exists()) throw IOException("El archivo no contiene un manifiesto válido de MYVU.")
        val manifest = JSONObject(manifestFile.readText())

        val format = manifest.optInt("format_version", 1)
        if (format > FORMAT_VERSION) {
            throw IOException("El respaldo fue creado por una versión más nueva de la app (formato $format).")
        }
        val dbVersion = manifest.optInt("db_version", 0)
        if (dbVersion > LocalDatabase.DATABASE_VERSION) {
            throw IOException("El respaldo usa una base de datos más nueva (v$dbVersion). Actualiza la app antes de restaurar.")
        }
        if (format >= 2) {
            val files = manifest.optJSONObject("files") ?: throw IOException("Manifiesto sin checksums.")
            for (path in files.keys()) {
                val actual = extracted[path] ?: throw IOException("Falta el archivo $path en el respaldo.")
                if (!actual.equals(files.getString(path), ignoreCase = true)) {
                    throw IOException("El archivo $path está dañado (checksum no coincide).")
                }
            }
        }
        return manifest
    }

    // ----------------------------------------------------------------- helpers

    /** Source directories for media, keyed by a stable name stored in the archive path. */
    private fun mediaDirs(app: Context): List<Pair<String, File>> {
        val candidates = listOf(
            "voice_recordings_ext" to app.getExternalFilesDir("voice_recordings"),
            "voice_recordings" to File(app.filesDir, "voice_recordings"),
            "attachments_ext" to app.getExternalFilesDir("attachments"),
            "attachments" to File(app.filesDir, "attachments"),
            "audio" to File(app.filesDir, "audio"),
            "ext_root" to app.getExternalFilesDir(null)
        )
        val seen = mutableSetOf<String>()
        return candidates.mapNotNull { (key, dir) ->
            dir?.takeIf { it.isDirectory && seen.add(it.canonicalPath) }?.let { key to it }
        }
    }

    private fun mediaTarget(app: Context, key: String): File? = when (key) {
        "voice_recordings_ext" -> app.getExternalFilesDir("voice_recordings") ?: File(app.filesDir, "voice_recordings")
        "voice_recordings" -> File(app.filesDir, "voice_recordings")
        "attachments_ext" -> app.getExternalFilesDir("attachments") ?: File(app.filesDir, "attachments")
        "attachments" -> File(app.filesDir, "attachments")
        "audio" -> File(app.filesDir, "audio")
        "ext_root" -> app.getExternalFilesDir(null) ?: app.filesDir
        else -> null
    }

    private fun restoreMedia(app: Context, staging: File, legacy: Boolean): Int {
        val mediaRoot = File(staging, MEDIA_PREFIX.trimEnd('/'))
        if (!mediaRoot.isDirectory) return 0
        var restored = 0
        if (legacy) {
            // v1 flattened every file into media/: route by extension like the old restore did.
            val voiceDir = mediaTarget(app, "voice_recordings_ext")!!
            val attachmentsDir = mediaTarget(app, "attachments_ext")!!
            mediaRoot.listFiles()?.filter { it.isFile }?.forEach { file ->
                val target = if (file.extension.lowercase() in AUDIO_EXTENSIONS) voiceDir else attachmentsDir
                target.mkdirs()
                file.copyTo(File(target, file.name), overwrite = true)
                restored++
            }
            return restored
        }
        mediaRoot.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val target = mediaTarget(app, dir.name)
            if (target == null) {
                LogBus.warn("BackupManager -> Skipping unknown media folder '${dir.name}'")
                return@forEach
            }
            target.mkdirs()
            dir.listFiles()?.filter { it.isFile }?.forEach { file ->
                file.copyTo(File(target, file.name), overwrite = true)
                restored++
            }
        }
        return restored
    }

    /** Flushes the WAL into the main file. Cursors are lazy: the PRAGMA only runs when the cursor is read. */
    private fun checkpointDatabases(app: Context) {
        try {
            if (app.getDatabasePath(LocalDatabase.DATABASE_NAME).exists()) {
                LocalDatabase.getInstance(app).writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            }
        } catch (e: Exception) {
            LogBus.warn("BackupManager -> WAL checkpoint failed: ${e.message}")
        }
        try {
            AppDatabase.getInstance(app).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        } catch (e: Exception) {
            LogBus.warn("BackupManager -> Chat DB WAL checkpoint failed: ${e.message}")
        }
    }

    /**
     * Replaces the rows of every Room table with those in [backupFile], inside one
     * transaction on the open connection (Room observers are notified). Columns are
     * matched by name, so a backup from an older schema fills the columns it has;
     * a missing NOT NULL column makes the insert fail and the transaction roll back.
     */
    private fun copyRoomTables(app: Context, backupFile: File) {
        val db = AppDatabase.getInstance(app).openHelper.writableDatabase
        db.execSQL("ATTACH DATABASE ? AS bk", arrayOf<Any>(backupFile.absolutePath))
        try {
            val tables = db.query(
                "SELECT name FROM bk.sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table', 'android_metadata')"
            ).use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() }

            db.beginTransaction()
            try {
                for (table in tables) {
                    val liveColumns = columns(db, "main", table)
                    if (liveColumns.isEmpty()) continue
                    val shared = columns(db, "bk", table).filter { it in liveColumns }.joinToString(",") { "`$it`" }
                    if (shared.isEmpty()) continue
                    db.execSQL("DELETE FROM main.`$table`")
                    db.execSQL("INSERT INTO main.`$table` ($shared) SELECT $shared FROM bk.`$table`")
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.execSQL("DETACH DATABASE bk")
        }
    }

    private fun columns(db: androidx.sqlite.db.SupportSQLiteDatabase, schema: String, table: String): List<String> =
        db.query("PRAGMA $schema.table_info(`$table`)").use { c ->
            val nameIndex = c.getColumnIndexOrThrow("name")
            generateSequence { if (c.moveToNext()) c.getString(nameIndex) else null }.toList()
        }

    /** Replaces [target] with [source] (if present) via copy + rename in the target directory. */
    private fun replaceDatabase(source: File, target: File) {
        if (!source.exists()) return
        val parent = target.parentFile ?: throw IOException("Ruta de base de datos inválida")
        parent.mkdirs()
        for (suffix in listOf("-wal", "-shm", "-journal")) File(parent, target.name + suffix).delete()
        val temp = File(parent, target.name + ".restoring")
        source.copyTo(temp, overwrite = true)
        if (target.exists() && !target.delete()) throw IOException("No se pudo reemplazar ${target.name}")
        if (!temp.renameTo(target)) throw IOException("No se pudo reemplazar ${target.name}")
    }

    /** File of the open Room database (the singleton may have been opened with another path). */
    private fun chatDbFile(app: Context): File =
        AppDatabase.getInstance(app).openHelper.writableDatabase.path?.let(::File) ?: app.getDatabasePath(CHAT_DB_NAME)

    private fun countRows(app: Context, table: String): Int = try {
        LocalDatabase.getInstance(app).readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    } catch (e: Exception) {
        0
    }

    private fun backupDir(app: Context): File =
        (app.getExternalFilesDir("backups") ?: File(app.filesDir, "backups")).apply { mkdirs() }

    private fun rotateBackups(dir: File) {
        dir.listFiles { f -> f.name.startsWith(FILE_PREFIX) && f.name.endsWith(".zip") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP_BACKUPS)
            ?.forEach { it.delete() }
    }

    private fun copyToDownloads(app: Context, zipFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, zipFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MYVU")
                }
                val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
                app.contentResolver.openOutputStream(uri)?.use { out -> zipFile.inputStream().use { it.copyTo(out) } }
            } else {
                @Suppress("DEPRECATION")
                val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MYVU").apply { mkdirs() }
                zipFile.copyTo(File(publicDir, zipFile.name), overwrite = true)
            }
            LogBus.log("BackupManager -> Saved copy in Downloads/MYVU/${zipFile.name}")
        } catch (e: Exception) {
            LogBus.warn("BackupManager -> Could not copy to public Downloads: ${e.message}")
        }
    }

    private fun appVersion(app: Context): String = try {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }

    @Suppress("DEPRECATION")
    private fun defaultPrefs(app: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(app)
}
