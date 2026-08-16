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
        tags: String = "",
        summary: String = "",
        actionItems: String = "",
        mindmapData: String = "",
        attachmentsJson: String = "[]"
    ): Long {
        val now = System.currentTimeMillis()
        val note = Note(
            type = type,
            title = title,
            body = body,
            audioPath = audioPath,
            durationSec = durationSec,
            tags = tags,
            summary = summary,
            actionItems = actionItems,
            mindmapData = mindmapData,
            attachmentsJson = attachmentsJson,
            createdAt = now,
            updatedAt = now
        )
        return insert(note)
    }

    fun insert(note: Note): Long {
        return try {
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
                put("summary", note.summary)
                put("action_items", note.actionItems)
                put("mindmap_data", note.mindmapData)
                put("attachments_json", note.attachmentsJson)
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
            id
        } catch (e: Exception) {
            LogBus.error("NoteRepository -> insert failed", e)
            -1L
        }
    }

    fun update(note: Note): Boolean {
        return try {
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
                put("summary", note.summary)
                put("action_items", note.actionItems)
                put("mindmap_data", note.mindmapData)
                put("attachments_json", note.attachmentsJson)
                put("updated_at", now)
            }
            val rows = db.update("notes", values, "id = ?", arrayOf(note.id.toString()))
            val success = rows > 0
            if (success) {
                LogBus.log("NoteRepository -> Updated note #${note.id}")
            }
            success
        } catch (e: Exception) {
            LogBus.error("NoteRepository -> update failed", e)
            false
        }
    }

    fun updateAttachments(id: Long, attachmentsJson: String): Boolean {
        return try {
            val now = System.currentTimeMillis()
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("attachments_json", attachmentsJson)
                put("updated_at", now)
            }
            db.update("notes", values, "id = ?", arrayOf(id.toString())) > 0
        } catch (e: Exception) {
            LogBus.error("NoteRepository -> updateAttachments failed", e)
            false
        }
    }

    fun updateAiAnalysis(
        id: Long,
        summary: String,
        actionItems: String,
        mindmapData: String,
        tags: String? = null
    ): Boolean {
        return try {
            val now = System.currentTimeMillis()
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("summary", summary)
                put("action_items", actionItems)
                put("mindmap_data", mindmapData)
                if (!tags.isNullOrBlank()) {
                    put("tags", tags)
                }
                put("updated_at", now)
            }
            db.update("notes", values, "id = ?", arrayOf(id.toString())) > 0
        } catch (e: Exception) {
            LogBus.error("NoteRepository -> updateAiAnalysis failed", e)
            false
        }
    }

    fun getById(noteId: Long): Note? {
        return try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                "notes", null, "id = ?", arrayOf(noteId.toString()), null, null, null
            )
            cursor.use { c ->
                if (c.moveToFirst()) {
                    cursorToNote(c)
                } else null
            }
        } catch (e: Exception) {
            LogBus.error("NoteRepository -> getById failed for ID=$noteId", e)
            null
        }
    }

    fun getAll(): List<Note> = getAllNotes()

    fun getAllNotes(): List<Note> {
        val list = ArrayList<Note>()
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                "notes", null, null, null, null, null, "created_at DESC"
            )
            cursor.use { c ->
                while (c.moveToNext()) {
                    list.add(cursorToNote(c))
                }
            }
        } catch (e: Exception) {
            LogBus.error("NoteRepository -> getAllNotes failed", e)
        }
        return list
    }

    fun search(query: String, filter: String? = null): List<Note> {
        val list = ArrayList<Note>()
        try {
            val db = dbHelper.readableDatabase

            val selectionParts = mutableListOf<String>()
            val selectionArgs = mutableListOf<String>()

            val cleanQuery = query.trim()
            if (cleanQuery.isNotEmpty()) {
                val queryWithoutHash = if (cleanQuery.startsWith("#")) cleanQuery.substring(1) else cleanQuery
                selectionParts.add("(title LIKE ? OR body LIKE ? OR summary LIKE ? OR tags LIKE ? OR tags LIKE ? OR attachments_json LIKE ?)")
                selectionArgs.add("%$cleanQuery%")
                selectionArgs.add("%$cleanQuery%")
                selectionArgs.add("%$cleanQuery%")
                selectionArgs.add("%$cleanQuery%")
                selectionArgs.add("%$queryWithoutHash%")
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
        } catch (e: Exception) {
            LogBus.error("NoteRepository -> search failed", e)
        }
        return list
    }

    fun updateNote(id: Long, newBody: String, newTitle: String? = null, tags: String? = null): Boolean {
        return try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("body", newBody)
                if (newTitle != null) put("title", newTitle)
                if (tags != null) put("tags", tags)
                put("updated_at", System.currentTimeMillis())
            }
            val rows = db.update("notes", values, "id = ?", arrayOf(id.toString()))
            LogBus.log("NoteRepository -> Updated note #$id (rows: $rows)")
            rows > 0
        } catch (e: Exception) {
            LogBus.error("NoteRepository -> updateNote failed", e)
            false
        }
    }

    fun delete(noteId: Long): Boolean = deleteNote(noteId)

    fun deleteNote(id: Long): Boolean {
        return try {
            val db = dbHelper.writableDatabase
            val rows = db.delete("notes", "id = ?", arrayOf(id.toString()))
            val success = rows > 0
            if (success) {
                LogBus.log("NoteRepository -> Deleted note #$id")
            }
            success
        } catch (e: Exception) {
            LogBus.error("NoteRepository -> deleteNote failed", e)
            false
        }
    }

    fun deleteByTitle(titlePattern: String): Boolean {
        return try {
            val db = dbHelper.writableDatabase
            val rows = db.delete("notes", "title LIKE ? OR body LIKE ?", arrayOf("%$titlePattern%", "%$titlePattern%"))
            LogBus.log("NoteRepository -> deleteByTitle '$titlePattern' (rows: $rows)")
            rows > 0
        } catch (e: Exception) {
            LogBus.error("NoteRepository -> deleteByTitle failed", e)
            false
        }
    }

    fun getAllTags(): List<String> {
        val tagsSet = linkedSetOf<String>()
        try {
            val notes = getAllNotes()
            for (n in notes) {
                for (t in n.tagsList) {
                    if (t.isNotBlank()) tagsSet.add(t)
                }
            }
        } catch (e: Exception) {
            LogBus.error("NoteRepository -> getAllTags failed", e)
        }
        return tagsSet.toList()
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

        val summaryIdx = c.getColumnIndex("summary")
        val summary = if (summaryIdx != -1 && !c.isNull(summaryIdx)) c.getString(summaryIdx) else ""

        val actionItemsIdx = c.getColumnIndex("action_items")
        val actionItems = if (actionItemsIdx != -1 && !c.isNull(actionItemsIdx)) c.getString(actionItemsIdx) else ""

        val mindmapIdx = c.getColumnIndex("mindmap_data")
        val mindmapData = if (mindmapIdx != -1 && !c.isNull(mindmapIdx)) c.getString(mindmapIdx) else ""

        val attachmentsIdx = c.getColumnIndex("attachments_json")
        val attachmentsJson = if (attachmentsIdx != -1 && !c.isNull(attachmentsIdx)) c.getString(attachmentsIdx) else "[]"

        return Note(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            type = type,
            title = title,
            body = c.getString(c.getColumnIndexOrThrow("body")),
            audioPath = audioPath,
            durationSec = durationSec,
            tags = tags,
            summary = summary,
            actionItems = actionItems,
            mindmapData = mindmapData,
            attachmentsJson = attachmentsJson,
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
        )
    }
}
