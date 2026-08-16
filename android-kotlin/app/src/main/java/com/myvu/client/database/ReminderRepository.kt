package com.myvu.client.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.myvu.client.core.LogBus
import kotlin.random.Random

class ReminderRepository(context: Context) {

    private val dbHelper: LocalDatabase = LocalDatabase.getInstance(context)

    fun createReminder(body: String, triggerAt: Long): Reminder? {
        return createReminder(title = "", body = body, triggerAt = triggerAt)
    }

    fun createReminder(
        title: String,
        body: String,
        triggerAt: Long,
        tags: String = "",
        summary: String = "",
        actionItems: String = "",
        mindmapData: String = "",
        attachmentsJson: String = "[]"
    ): Reminder? {
        val now = System.currentTimeMillis()
        val requestCode = Random.nextInt(10000, 999999)
        val reminder = Reminder(
            title = title,
            body = body,
            tags = tags,
            summary = summary,
            actionItems = actionItems,
            mindmapData = mindmapData,
            attachmentsJson = attachmentsJson,
            triggerAt = triggerAt,
            createdAt = now,
            updatedAt = now,
            state = "PENDING",
            alarmRequestCode = requestCode
        )
        val id = insert(reminder)
        return if (id != -1L) reminder else null
    }

    fun insert(reminder: Reminder): Long {
        return try {
            val now = if (reminder.createdAt == 0L) System.currentTimeMillis() else reminder.createdAt
            val updated = if (reminder.updatedAt == 0L) now else reminder.updatedAt
            if (reminder.alarmRequestCode == 0) {
                reminder.alarmRequestCode = Random.nextInt(10000, 999999)
            }

            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("title", reminder.title)
                put("body", reminder.body)
                put("tags", reminder.tags)
                put("summary", reminder.summary)
                put("action_items", reminder.actionItems)
                put("mindmap_data", reminder.mindmapData)
                put("attachments_json", reminder.attachmentsJson)
                put("trigger_at", reminder.triggerAt)
                put("created_at", now)
                put("updated_at", updated)
                put("state", reminder.state)
                put("alarm_request_code", reminder.alarmRequestCode)
            }

            val id = db.insert("reminders", null, values)
            if (id != -1L) {
                reminder.id = id
                LogBus.log("ReminderRepository -> Inserted reminder #$id triggerAt=${reminder.triggerAt}")
            } else {
                LogBus.error("ReminderRepository -> Failed to insert reminder", null)
            }
            id
        } catch (e: Exception) {
            LogBus.error("ReminderRepository -> insert failed", e)
            -1L
        }
    }

    fun update(reminder: Reminder): Boolean {
        return try {
            val now = System.currentTimeMillis()
            reminder.updatedAt = now
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("title", reminder.title)
                put("body", reminder.body)
                put("tags", reminder.tags)
                put("summary", reminder.summary)
                put("action_items", reminder.actionItems)
                put("mindmap_data", reminder.mindmapData)
                put("attachments_json", reminder.attachmentsJson)
                put("trigger_at", reminder.triggerAt)
                put("updated_at", now)
                put("state", reminder.state)
                put("alarm_request_code", reminder.alarmRequestCode)
            }
            val rows = db.update("reminders", values, "id = ?", arrayOf(reminder.id.toString()))
            val success = rows > 0
            if (success) {
                LogBus.log("ReminderRepository -> Updated reminder #${reminder.id}")
            }
            success
        } catch (e: Exception) {
            LogBus.error("ReminderRepository -> update failed", e)
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
            db.update("reminders", values, "id = ?", arrayOf(id.toString())) > 0
        } catch (e: Exception) {
            LogBus.error("ReminderRepository -> updateAttachments failed", e)
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
            db.update("reminders", values, "id = ?", arrayOf(id.toString())) > 0
        } catch (e: Exception) {
            LogBus.error("ReminderRepository -> updateAiAnalysis failed", e)
            false
        }
    }

    fun updateReminderState(id: Long, newState: String): Boolean {
        return try {
            val now = System.currentTimeMillis()
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("state", newState)
                put("updated_at", now)
            }
            val rows = db.update("reminders", values, "id = ?", arrayOf(id.toString()))
            val success = rows > 0
            if (success) {
                LogBus.log("ReminderRepository -> Updated reminder #$id state to $newState")
            }
            success
        } catch (e: Exception) {
            LogBus.error("ReminderRepository -> updateReminderState failed", e)
            false
        }
    }

    fun snoozeReminder(id: Long, newTriggerAt: Long): Boolean {
        return try {
            val now = System.currentTimeMillis()
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("trigger_at", newTriggerAt)
                put("state", "PENDING")
                put("updated_at", now)
            }
            val rows = db.update("reminders", values, "id = ?", arrayOf(id.toString()))
            val success = rows > 0
            if (success) {
                LogBus.log("ReminderRepository -> Snoozed reminder #$id newTriggerAt=$newTriggerAt")
            }
            success
        } catch (e: Exception) {
            LogBus.error("ReminderRepository -> snoozeReminder failed", e)
            false
        }
    }

    fun getById(reminderId: Long): Reminder? = getReminder(reminderId)

    fun getReminder(id: Long): Reminder? {
        return try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                "reminders", null, "id = ?", arrayOf(id.toString()), null, null, null
            )
            cursor.use { c ->
                if (c.moveToFirst()) {
                    cursorToReminder(c)
                } else null
            }
        } catch (e: Exception) {
            LogBus.error("ReminderRepository -> getReminder failed", e)
            null
        }
    }

    fun getPendingReminders(): List<Reminder> {
        val list = ArrayList<Reminder>()
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                "reminders", null, "state = ? OR state = ?", arrayOf("PENDING", "SNOOZED"), null, null, "trigger_at ASC"
            )
            cursor.use { c ->
                while (c.moveToNext()) {
                    list.add(cursorToReminder(c))
                }
            }
        } catch (e: Exception) {
            LogBus.error("ReminderRepository -> getPendingReminders failed", e)
        }
        return list
    }

    fun getAll(): List<Reminder> {
        val list = ArrayList<Reminder>()
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                "reminders", null, null, null, null, null, "trigger_at ASC"
            )
            cursor.use { c ->
                while (c.moveToNext()) {
                    list.add(cursorToReminder(c))
                }
            }
        } catch (e: Exception) {
            LogBus.error("ReminderRepository -> getAll failed", e)
        }
        return list
    }

    fun getAllReminders(): List<Reminder> = getAll()

    fun search(query: String, filter: String? = null): List<Reminder> {
        val list = ArrayList<Reminder>()
        try {
            val db = dbHelper.readableDatabase

            val selectionParts = mutableListOf<String>()
            val selectionArgs = mutableListOf<String>()

            val cleanQuery = query.trim()
            if (cleanQuery.isNotEmpty()) {
                val queryWithoutHash = if (cleanQuery.startsWith("#")) cleanQuery.substring(1) else cleanQuery
                selectionParts.add("(title LIKE ? OR body LIKE ? OR tags LIKE ? OR tags LIKE ? OR summary LIKE ? OR attachments_json LIKE ?)")
                selectionArgs.add("%$cleanQuery%")
                selectionArgs.add("%$cleanQuery%")
                selectionArgs.add("%$cleanQuery%")
                selectionArgs.add("%$queryWithoutHash%")
                selectionArgs.add("%$cleanQuery%")
                selectionArgs.add("%$cleanQuery%")
            }

            if (!filter.isNullOrBlank() && !filter.equals("ALL", ignoreCase = true) && !filter.equals("TODAS", ignoreCase = true)) {
                selectionParts.add("state = ?")
                selectionArgs.add(filter.uppercase())
            }

            val selection = if (selectionParts.isNotEmpty()) selectionParts.joinToString(" AND ") else null
            val args = if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null

            val cursor = db.query(
                "reminders", null, selection, args, null, null, "trigger_at ASC"
            )
            cursor.use { c ->
                while (c.moveToNext()) {
                    list.add(cursorToReminder(c))
                }
            }
        } catch (e: Exception) {
            LogBus.error("ReminderRepository -> search failed", e)
        }
        return list
    }

    fun delete(reminderId: Long): Boolean = deleteReminder(reminderId)

    fun deleteReminder(id: Long): Boolean {
        return try {
            val db = dbHelper.writableDatabase
            val rows = db.delete("reminders", "id = ?", arrayOf(id.toString()))
            val success = rows > 0
            if (success) {
                LogBus.log("ReminderRepository -> Deleted reminder #$id")
            }
            success
        } catch (e: Exception) {
            LogBus.error("ReminderRepository -> deleteReminder failed", e)
            false
        }
    }

    fun deleteByTitle(titlePattern: String): Boolean {
        return try {
            val db = dbHelper.writableDatabase
            val rows = db.delete("reminders", "title LIKE ? OR body LIKE ?", arrayOf("%$titlePattern%", "%$titlePattern%"))
            LogBus.log("ReminderRepository -> deleteByTitle '$titlePattern' (rows: $rows)")
            rows > 0
        } catch (e: Exception) {
            LogBus.error("ReminderRepository -> deleteByTitle failed", e)
            false
        }
    }

    fun updateReminder(
        id: Long,
        newBody: String,
        newTitle: String? = null,
        newTriggerAt: Long? = null,
        tags: String? = null
    ): Boolean {
        return try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("body", newBody)
                if (newTitle != null) put("title", newTitle)
                if (newTriggerAt != null) put("trigger_at", newTriggerAt)
                if (tags != null) put("tags", tags)
                put("updated_at", System.currentTimeMillis())
            }
            val rows = db.update("reminders", values, "id = ?", arrayOf(id.toString()))
            LogBus.log("ReminderRepository -> Updated reminder #$id (rows: $rows)")
            rows > 0
        } catch (e: Exception) {
            LogBus.error("ReminderRepository -> updateReminder failed", e)
            false
        }
    }

    fun getAllTags(): List<String> {
        val tagsSet = linkedSetOf<String>()
        try {
            val reminders = getAll()
            for (r in reminders) {
                for (t in r.tagsList) {
                    if (t.isNotBlank()) tagsSet.add(t)
                }
            }
        } catch (e: Exception) {
            LogBus.error("ReminderRepository -> getAllTags failed", e)
        }
        return tagsSet.toList()
    }

    private fun cursorToReminder(c: Cursor): Reminder {
        val titleIdx = c.getColumnIndex("title")
        val title = if (titleIdx != -1 && !c.isNull(titleIdx)) c.getString(titleIdx) else ""

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

        val updatedAtIdx = c.getColumnIndex("updated_at")
        val updatedAt = if (updatedAtIdx != -1 && !c.isNull(updatedAtIdx)) c.getLong(updatedAtIdx) else 0L

        return Reminder(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            title = title,
            body = c.getString(c.getColumnIndexOrThrow("body")),
            tags = tags,
            summary = summary,
            actionItems = actionItems,
            mindmapData = mindmapData,
            attachmentsJson = attachmentsJson,
            triggerAt = c.getLong(c.getColumnIndexOrThrow("trigger_at")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = updatedAt,
            state = c.getString(c.getColumnIndexOrThrow("state")),
            alarmRequestCode = c.getInt(c.getColumnIndexOrThrow("alarm_request_code"))
        )
    }
}
