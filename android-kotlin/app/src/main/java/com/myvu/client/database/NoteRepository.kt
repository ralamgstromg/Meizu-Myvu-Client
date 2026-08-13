package com.myvu.client.database

import android.content.ContentValues
import android.content.Context
import com.myvu.client.core.LogBus

class NoteRepository(context: Context) {

    private val dbHelper: LocalDatabase = LocalDatabase.getInstance(context)

    fun createNote(body: String): Long {
        val now = System.currentTimeMillis()
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("body", body)
            put("created_at", now)
            put("updated_at", now)
        }
        val id = db.insert("notes", null, values)
        if (id != -1L) {
            LogBus.log("NoteRepository -> Created note #$id")
        } else {
            LogBus.error("NoteRepository -> Failed to create note", null)
        }
        return id
    }

    fun getAllNotes(): List<Note> {
        val list = ArrayList<Note>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "notes", null, null, null, null, null, "created_at DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val note = Note(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    body = c.getString(c.getColumnIndexOrThrow("body")),
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                    updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
                )
                list.add(note)
            }
        }
        return list
    }

    fun deleteNote(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        val rows = db.delete("notes", "id = ?", arrayOf(id.toString()))
        val success = rows > 0
        if (success) {
            LogBus.log("NoteRepository -> Deleted note #$id")
        }
        return success
    }
}
