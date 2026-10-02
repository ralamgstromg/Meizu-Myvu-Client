package com.myvu.client.search

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.myvu.client.core.LogBus
import com.myvu.client.database.AppDatabase
import com.myvu.client.database.LocalDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

/**
 * Full-text search over everything the agent stores: notes, meeting recordings
 * (title, transcript, summary, action items), reminders, tasks and chat history.
 *
 * The index lives in its own database (`myvu_search.db`): it is derived data, so
 * it is rebuilt from the source tables whenever their content signature (row
 * counts and last update) changes, and it is not part of backups. Text is
 * indexed lowercase and without accents, so "reunion" finds "Reunión".
 */
object SearchIndex {

    enum class Kind(val label: String) { NOTE("Nota"), RECORDING("Reunión"), REMINDER("Recordatorio"), TODO("Tarea"), CHAT("Chat") }

    data class Hit(val kind: Kind, val refId: String, val title: String, val snippet: String, val date: Long, val score: Int)

    private class Helper(context: Context) : SQLiteOpenHelper(context, "myvu_search.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE VIRTUAL TABLE docs USING fts4(kind, ref_id, date, title, body, norm_title, norm_body)")
            db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS docs")
            db.execSQL("DROP TABLE IF EXISTS meta")
            onCreate(db)
        }
    }

    @Volatile
    private var helper: Helper? = null

    private fun db(context: Context): SQLiteDatabase =
        (helper ?: synchronized(this) { helper ?: Helper(context.applicationContext).also { helper = it } }).writableDatabase

    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase()

    /** Searches all content; every term must match (prefix match). */
    suspend fun search(context: Context, query: String, kinds: Set<Kind> = Kind.values().toSet(), limit: Int = 50): List<Hit> =
        withContext(Dispatchers.IO) {
            val terms = normalize(query).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }
            if (terms.isEmpty()) return@withContext emptyList()
            refreshIfStale(context)

            val match = terms.joinToString(" ") { "$it*" }
            val hits = mutableListOf<Hit>()
            db(context).rawQuery(
                "SELECT kind, ref_id, date, title, body, norm_title, norm_body FROM docs WHERE docs MATCH ?",
                arrayOf(match)
            ).use { c ->
                while (c.moveToNext()) {
                    val kind = runCatching { Kind.valueOf(c.getString(0)) }.getOrNull() ?: continue
                    if (kind !in kinds) continue
                    val normTitle = c.getString(5)
                    val normBody = c.getString(6)
                    val score = terms.sumOf { t -> 3 * count(normTitle, t) + count(normBody, t) }
                    hits.add(Hit(kind, c.getString(1), c.getString(3), snippet(c.getString(4), normBody, terms.first()), c.getLong(2), score))
                }
            }
            hits.sortedWith(compareByDescending<Hit> { it.score }.thenByDescending { it.date }).take(limit)
        }

    /** Forces a rebuild on the next search (e.g. after a backup restore). */
    fun invalidate(context: Context) {
        db(context).delete("meta", null, null)
    }

    private fun count(haystack: String, term: String): Int {
        var n = 0
        var i = haystack.indexOf(term)
        while (i >= 0) { n++; i = haystack.indexOf(term, i + term.length) }
        return n
    }

    /** ~120 chars of the original text around the first match (normalized text keeps NFC offsets). */
    internal fun snippet(body: String, normBody: String, term: String, maxChars: Int = 120): String {
        if (body.isBlank()) return ""
        val idx = normBody.indexOf(term).takeIf { it >= 0 && normBody.length == body.length } ?: 0
        val start = (idx - maxChars / 3).coerceAtLeast(0)
        val end = (start + maxChars).coerceAtMost(body.length)
        val core = body.substring(start, end).replace(Regex("\\s+"), " ").trim()
        return (if (start > 0) "…" else "") + core + (if (end < body.length) "…" else "")
    }

    // ------------------------------------------------------------------ build

    private suspend fun refreshIfStale(context: Context) {
        val signature = signature(context)
        val db = db(context)
        val current = db.rawQuery("SELECT value FROM meta WHERE key = 'signature'", null).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        if (current == signature) return
        rebuild(context, db)
        db.insertWithOnConflict("meta", null, ContentValues().apply {
            put("key", "signature"); put("value", signature)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private suspend fun signature(context: Context): String {
        val local = LocalDatabase.getInstance(context).readableDatabase
        val parts = listOf(
            "notes" to "updated_at", "reminders" to "updated_at", "todos" to "updated_at", "voice_recordings" to "updated_at"
        ).map { (table, col) ->
            local.rawQuery("SELECT COUNT(*), COALESCE(MAX($col), 0) FROM $table", null).use { c ->
                c.moveToFirst(); "${c.getLong(0)}:${c.getLong(1)}"
            }
        }
        val chat = AppDatabase.getInstance(context).chatDao().getRecentMessages(1).firstOrNull()?.timestamp ?: 0L
        val chatCount = AppDatabase.getInstance(context).openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM chat_message").use { c -> c.moveToFirst(); c.getLong(0) }
        return (parts + "$chatCount:$chat").joinToString("|")
    }

    private suspend fun rebuild(context: Context, db: SQLiteDatabase) {
        val started = System.currentTimeMillis()
        val local = LocalDatabase.getInstance(context).readableDatabase
        val chatMessages = AppDatabase.getInstance(context).chatDao().getAllMessages()
        db.beginTransaction()
        try {
            db.delete("docs", null, null)
            fun add(kind: Kind, id: Any, date: Long, title: String, body: String) {
                db.insert("docs", null, ContentValues().apply {
                    put("kind", kind.name); put("ref_id", id.toString()); put("date", date)
                    put("title", title); put("body", body)
                    put("norm_title", normalize(title)); put("norm_body", normalize(body))
                })
            }
            fun each(sql: String, row: (android.database.Cursor) -> Unit) =
                local.rawQuery(sql, null).use { c -> while (c.moveToNext()) row(c) }

            each("SELECT id, title, body, summary, action_items, tags, updated_at FROM notes") { c ->
                add(Kind.NOTE, c.getLong(0), c.getLong(6), c.getString(1).ifBlank { c.getString(2).take(40) },
                    listOf(c.getString(2), c.getString(3), c.getString(4), c.getString(5)).filter { it.isNotBlank() }.joinToString("\n"))
            }
            each("SELECT id, title, raw_transcript, summary, action_items, tags, created_at FROM voice_recordings") { c ->
                add(Kind.RECORDING, c.getLong(0), c.getLong(6), c.getString(1).ifBlank { "Grabación" },
                    listOf(c.getString(3), c.getString(4), c.getString(2), c.getString(5)).filter { it.isNotBlank() }.joinToString("\n"))
            }
            each("SELECT id, title, body, summary, tags, trigger_at FROM reminders") { c ->
                add(Kind.REMINDER, c.getLong(0), c.getLong(5), c.getString(1).ifBlank { c.getString(2).take(40) },
                    listOf(c.getString(2), c.getString(3), c.getString(4)).filter { it.isNotBlank() }.joinToString("\n"))
            }
            each("SELECT id, title, list_name, tags, updated_at FROM todos") { c ->
                add(Kind.TODO, c.getLong(0), c.getLong(4), c.getString(1), listOf(c.getString(2), c.getString(3)).joinToString(" "))
            }
            chatMessages.forEach { m ->
                add(Kind.CHAT, m.sessionId, m.timestamp, if (m.direction == "USER") "Tú" else "Asistente", m.content)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        LogBus.log("SearchIndex -> rebuilt in ${System.currentTimeMillis() - started} ms")
    }
}
