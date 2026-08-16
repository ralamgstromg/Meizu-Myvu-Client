package com.myvu.client.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.myvu.client.core.LogBus
import java.io.File

class VoiceRecordingRepository(private val context: Context) {

    private val dbHelper: LocalDatabase = LocalDatabase.getInstance(context)

    fun createRecording(recording: VoiceRecording): Long {
        return try {
            val now = if (recording.createdAt == 0L) System.currentTimeMillis() else recording.createdAt
            val updated = if (recording.updatedAt == 0L) now else recording.updatedAt
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("title", recording.title)
                put("audio_path", recording.audioPath)
                put("duration_ms", recording.durationMs)
                put("file_size_bytes", recording.fileSizeBytes)
                put("raw_transcript", recording.rawTranscript)
                put("diarized_transcript", recording.diarizedTranscript)
                put("summary", recording.summary)
                put("action_items", recording.actionItems)
                put("mindmap_data", recording.mindmapData)
                put("tags", recording.tags)
                put("category", recording.category)
                put("status", recording.status)
                put("attachments_json", recording.attachmentsJson)
                put("created_at", now)
                put("updated_at", updated)
            }
            val id = db.insert("voice_recordings", null, values)
            if (id != -1L) {
                recording.id = id
                LogBus.log("VoiceRecordingRepository -> Inserted recording #$id title='${recording.title}'")
            } else {
                LogBus.error("VoiceRecordingRepository -> Failed to insert recording", null)
            }
            id
        } catch (e: Exception) {
            LogBus.error("VoiceRecordingRepository -> createRecording failed", e)
            -1L
        }
    }

    fun getRecordingById(id: Long): VoiceRecording? {
        return try {
            val db = dbHelper.readableDatabase
            val cursor: Cursor = db.query(
                "voice_recordings",
                null,
                "id = ?",
                arrayOf(id.toString()),
                null,
                null,
                null
            )
            cursor.use {
                if (it.moveToFirst()) fromCursor(it) else null
            }
        } catch (e: Exception) {
            LogBus.error("VoiceRecordingRepository -> getRecordingById failed for ID=$id", e)
            null
        }
    }

    fun getAllRecordings(): List<VoiceRecording> {
        val list = mutableListOf<VoiceRecording>()
        try {
            val db = dbHelper.readableDatabase
            val cursor: Cursor = db.query(
                "voice_recordings",
                null,
                null,
                null,
                null,
                null,
                "created_at DESC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    list.add(fromCursor(it))
                }
            }
        } catch (e: Exception) {
            LogBus.error("VoiceRecordingRepository -> getAllRecordings failed", e)
        }
        return list
    }

    fun searchRecordings(
        query: String? = null,
        tag: String? = null,
        category: String? = null
    ): List<VoiceRecording> {
        val list = mutableListOf<VoiceRecording>()
        try {
            val db = dbHelper.readableDatabase

            val selectionParts = mutableListOf<String>()
            val selectionArgs = mutableListOf<String>()

            if (!query.isNullOrBlank()) {
                val pattern = "%${query.trim()}%"
                selectionParts.add("(title LIKE ? OR raw_transcript LIKE ? OR summary LIKE ? OR tags LIKE ? OR attachments_json LIKE ?)")
                selectionArgs.add(pattern)
                selectionArgs.add(pattern)
                selectionArgs.add(pattern)
                selectionArgs.add(pattern)
                selectionArgs.add(pattern)
            }

            if (!tag.isNullOrBlank() && tag != "ALL") {
                selectionParts.add("tags LIKE ?")
                selectionArgs.add("%${tag.trim()}%")
            }

            if (!category.isNullOrBlank() && category != "ALL") {
                selectionParts.add("category = ?")
                selectionArgs.add(category.trim())
            }

            val selection = if (selectionParts.isEmpty()) null else selectionParts.joinToString(" AND ")
            val args = if (selectionArgs.isEmpty()) null else selectionArgs.toTypedArray()

            val cursor: Cursor = db.query(
                "voice_recordings",
                null,
                selection,
                args,
                null,
                null,
                "created_at DESC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    list.add(fromCursor(it))
                }
            }
        } catch (e: Exception) {
            LogBus.error("VoiceRecordingRepository -> searchRecordings failed", e)
        }
        return list
    }

    fun updateRecording(recording: VoiceRecording): Boolean {
        return try {
            val now = System.currentTimeMillis()
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("title", recording.title)
                put("audio_path", recording.audioPath)
                put("duration_ms", recording.durationMs)
                put("file_size_bytes", recording.fileSizeBytes)
                put("raw_transcript", recording.rawTranscript)
                put("diarized_transcript", recording.diarizedTranscript)
                put("summary", recording.summary)
                put("action_items", recording.actionItems)
                put("mindmap_data", recording.mindmapData)
                put("tags", recording.tags)
                put("category", recording.category)
                put("status", recording.status)
                put("attachments_json", recording.attachmentsJson)
                put("updated_at", now)
            }
            val count = db.update("voice_recordings", values, "id = ?", arrayOf(recording.id.toString()))
            count > 0
        } catch (e: Exception) {
            LogBus.error("VoiceRecordingRepository -> updateRecording failed", e)
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
            db.update("voice_recordings", values, "id = ?", arrayOf(id.toString())) > 0
        } catch (e: Exception) {
            LogBus.error("VoiceRecordingRepository -> updateAttachments failed", e)
            false
        }
    }

    fun updateAiAnalysis(
        id: Long,
        rawTranscript: String,
        diarizedTranscript: String,
        summary: String,
        actionItems: String,
        mindmapData: String,
        tags: String? = null,
        status: String = VoiceRecording.STATUS_READY
    ): Boolean {
        return try {
            val now = System.currentTimeMillis()
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("raw_transcript", rawTranscript)
                put("diarized_transcript", diarizedTranscript)
                put("summary", summary)
                put("action_items", actionItems)
                put("mindmap_data", mindmapData)
                if (!tags.isNullOrBlank()) {
                    put("tags", tags)
                }
                put("status", status)
                put("updated_at", now)
            }
            val count = db.update("voice_recordings", values, "id = ?", arrayOf(id.toString()))
            count > 0
        } catch (e: Exception) {
            LogBus.error("VoiceRecordingRepository -> updateAiAnalysis failed", e)
            false
        }
    }

    fun updateStatus(id: Long, status: String): Boolean {
        return try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("status", status)
                put("updated_at", System.currentTimeMillis())
            }
            db.update("voice_recordings", values, "id = ?", arrayOf(id.toString())) > 0
        } catch (e: Exception) {
            LogBus.error("VoiceRecordingRepository -> updateStatus failed", e)
            false
        }
    }

    fun deleteRecording(id: Long, deleteAudioFile: Boolean = true): Boolean {
        return try {
            val recording = getRecordingById(id)
            if (recording != null && deleteAudioFile && recording.audioPath.isNotBlank()) {
                try {
                    val file = File(recording.audioPath)
                    if (file.exists()) {
                        file.delete()
                        LogBus.log("VoiceRecordingRepository -> Deleted audio file ${file.absolutePath}")
                    }
                } catch (e: Exception) {
                    LogBus.warn("VoiceRecordingRepository -> Failed to delete audio file: ${e.message}")
                }
            }
            val db = dbHelper.writableDatabase
            val count = db.delete("voice_recordings", "id = ?", arrayOf(id.toString()))
            count > 0
        } catch (e: Exception) {
            LogBus.error("VoiceRecordingRepository -> deleteRecording failed for ID=$id", e)
            false
        }
    }

    fun getAllTags(): List<String> {
        val tagsSet = linkedSetOf<String>()
        try {
            val recordings = getAllRecordings()
            for (rec in recordings) {
                for (t in rec.tagsList) {
                    if (t.isNotBlank()) tagsSet.add(t)
                }
            }
        } catch (e: Exception) {
            LogBus.error("VoiceRecordingRepository -> getAllTags failed", e)
        }
        return tagsSet.toList()
    }

    private fun fromCursor(cursor: Cursor): VoiceRecording {
        val attachmentsIdx = cursor.getColumnIndex("attachments_json")
        val attachmentsJson = if (attachmentsIdx != -1 && !cursor.isNull(attachmentsIdx)) cursor.getString(attachmentsIdx) else "[]"

        return VoiceRecording(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")) ?: "",
            audioPath = cursor.getString(cursor.getColumnIndexOrThrow("audio_path")) ?: "",
            durationMs = cursor.getLong(cursor.getColumnIndexOrThrow("duration_ms")),
            fileSizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow("file_size_bytes")),
            rawTranscript = cursor.getString(cursor.getColumnIndexOrThrow("raw_transcript")) ?: "",
            diarizedTranscript = cursor.getString(cursor.getColumnIndexOrThrow("diarized_transcript")) ?: "",
            summary = cursor.getString(cursor.getColumnIndexOrThrow("summary")) ?: "",
            actionItems = cursor.getString(cursor.getColumnIndexOrThrow("action_items")) ?: "",
            mindmapData = cursor.getString(cursor.getColumnIndexOrThrow("mindmap_data")) ?: "",
            tags = cursor.getString(cursor.getColumnIndexOrThrow("tags")) ?: "",
            category = cursor.getString(cursor.getColumnIndexOrThrow("category")) ?: VoiceRecording.CATEGORY_MEETING,
            status = cursor.getString(cursor.getColumnIndexOrThrow("status")) ?: VoiceRecording.STATUS_READY,
            attachmentsJson = attachmentsJson,
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
        )
    }
}
