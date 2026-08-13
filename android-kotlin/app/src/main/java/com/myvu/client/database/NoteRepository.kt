package com.myvu.client.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.myvu.client.core.LogBus

class NoteRepository(context: Context) {

    private val dbHelper: LocalDatabase = LocalDatabase.getInstance(context)

    fun createNote(body: String): Long {
        return insert(Note(title = "", body = body))
    }

    fun createNote(
        title: String,
        body: String,
        type: String = "TEXT",
        audioPath: String? = null,
        durationSec: Int = 0,
        tags: String = ""
    ): Long {
        val now = System.currentTimeMillis()
        val note = Note(
            type = type,
            title = title,
            body = body,
            audioPath = audioPath,
            durationSec = durationSec,
            tags = tags,
            createdAt = now,
            updatedAt = now
        )
        return insert(note)
    }

    fun insert(note: Note): Long {
        val now = if (note.createdAt == 0L) System.currentTimeMillis() else note.createdAt
        val updated = if (note.updatedAt == 0L) now else note.updatedAt
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("type", note.type)
            put("title", note.title)
            put("body", note.body)
            put("audio_path", note.audioPath)
            put("duration_sec", note.durationSec)
            put("tags", note.tags)
            put("created_at", now)
            put("updated_at", updated)
        }
        val id = db.insert("notes", null, values)
        if (id != -1L) {
            note.id = id
            LogBus.log("NoteRepository -> Inserted note #$id type=${note.type}")
        } else {
            LogBus.error("NoteRepository -> Failed to insert note", null)
        }
        return id
    }

    fun update(note: Note): Boolean {
        val now = System.currentTimeMillis()
        note.updatedAt = now
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("type", note.type)
            put("title", note.title)
            put("body", note.body)
            put("audio_path", note.audioPath)
            put("duration_sec", note.durationSec)
            put("tags", note.tags)
            put("updated_at", now)
        }
        val rows = db.update("notes", values, "id = ?", arrayOf(note.id.toString()))
        val success = rows > 0
        if (success) {
            LogBus.log("NoteRepository -> Updated note #${note.id}")
        }
        return success
    }

    fun getById(noteId: Long): Note? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "notes", null, "id = ?", arrayOf(noteId.toString()), null, null, null
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                return cursorToNote(c)
            }
        }
        return null
    }

    fun getAll(): List<Note> = getAllNotes()

    fun getAllNotes(): List<Note> {
        val list = ArrayList<Note>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "notes", null, null, null, null, null, "created_at DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(cursorToNote(c))
            }
        }
        return list
    }

    fun search(query: String, filter: String? = null): List<Note> {
        val list = ArrayList<Note>()
        val db = dbHelper.readableDatabase

        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        val cleanQuery = query.trim()
        if (cleanQuery.isNotEmpty()) {
            selectionParts.add("(title LIKE ? OR body LIKE ? OR tags LIKE ?)")
            selectionArgs.add("%$cleanQuery%")
            selectionArgs.add("%$cleanQuery%")
            selectionArgs.add("%$cleanQuery%")
        }

        if (!filter.isNullOrBlank() && !filter.equals("ALL", ignoreCase = true) && !filter.equals("TODAS", ignoreCase = true)) {
            selectionParts.add("type = ?")
            selectionArgs.add(filter.uppercase())
        }

        val selection = if (selectionParts.isNotEmpty()) selectionParts.joinToString(" AND ") else null
        val args = if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null

        val cursor = db.query(
            "notes", null, selection, args, null, null, "updated_at DESC, created_at DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(cursorToNote(c))
            }
        }
        return list
    }

    fun delete(noteId: Long): Boolean = deleteNote(noteId)

    fun deleteNote(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        val rows = db.delete("notes", "id = ?", arrayOf(id.toString()))
        val success = rows > 0
        if (success) {
            LogBus.log("NoteRepository -> Deleted note #$id")
        }
        return success
    }

    private fun cursorToNote(c: Cursor): Note {
        val audioPathIdx = c.getColumnIndex("audio_path")
        val audioPath = if (audioPathIdx != -1 && !c.isNull(audioPathIdx)) c.getString(audioPathIdx) else null

        val typeIdx = c.getColumnIndex("type")
        val type = if (typeIdx != -1 && !c.isNull(typeIdx)) c.getString(typeIdx) else "TEXT"

        val titleIdx = c.getColumnIndex("title")
        val title = if (titleIdx != -1 && !c.isNull(titleIdx)) c.getString(titleIdx) else ""

        val durationIdx = c.getColumnIndex("duration_sec")
        val durationSec = if (durationIdx != -1 && !c.isNull(durationIdx)) c.getInt(durationIdx) else 0

        val tagsIdx = c.getColumnIndex("tags")
        val tags = if (tagsIdx != -1 && !c.isNull(tagsIdx)) c.getString(tagsIdx) else ""

        return Note(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            type = type,
            title = title,
            body = c.getString(c.getColumnIndexOrThrow("body")),
            audioPath = audioPath,
            durationSec = durationSec,
            tags = tags,
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
        )
    }
}
