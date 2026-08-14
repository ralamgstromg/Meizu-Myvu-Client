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

    fun createReminder(title: String, body: String, triggerAt: Long): Reminder? {
        val now = System.currentTimeMillis()
        val requestCode = Random.nextInt(10000, 999999)
        val reminder = Reminder(
            title = title,
            body = body,
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
        val now = if (reminder.createdAt == 0L) System.currentTimeMillis() else reminder.createdAt
        val updated = if (reminder.updatedAt == 0L) now else reminder.updatedAt
        if (reminder.alarmRequestCode == 0) {
            reminder.alarmRequestCode = Random.nextInt(10000, 999999)
        }

        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("title", reminder.title)
            put("body", reminder.body)
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
        return id
    }

    fun update(reminder: Reminder): Boolean {
        val now = System.currentTimeMillis()
        reminder.updatedAt = now
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("title", reminder.title)
            put("body", reminder.body)
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
        return success
    }

    fun updateReminderState(id: Long, newState: String): Boolean {
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
        return success
    }

    fun snoozeReminder(id: Long, newTriggerAt: Long): Boolean {
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
        return success
    }

    fun getById(reminderId: Long): Reminder? = getReminder(reminderId)

    fun getReminder(id: Long): Reminder? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "reminders", null, "id = ?", arrayOf(id.toString()), null, null, null
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                return cursorToReminder(c)
            }
        }
        return null
    }

    fun getPendingReminders(): List<Reminder> {
        val list = ArrayList<Reminder>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "reminders", null, "state = ? OR state = ?", arrayOf("PENDING", "SNOOZED"), null, null, "trigger_at ASC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(cursorToReminder(c))
            }
        }
        return list
    }

    fun getAll(): List<Reminder> {
        val list = ArrayList<Reminder>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "reminders", null, null, null, null, null, "trigger_at ASC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(cursorToReminder(c))
            }
        }
        return list
    }

    fun search(query: String, filter: String? = null): List<Reminder> {
        val list = ArrayList<Reminder>()
        val db = dbHelper.readableDatabase

        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        val cleanQuery = query.trim()
        if (cleanQuery.isNotEmpty()) {
            selectionParts.add("(title LIKE ? OR body LIKE ?)")
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
        return list
    }

    fun delete(reminderId: Long): Boolean = deleteReminder(reminderId)

    fun deleteReminder(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        val rows = db.delete("reminders", "id = ?", arrayOf(id.toString()))
        val success = rows > 0
        if (success) {
            LogBus.log("ReminderRepository -> Deleted reminder #$id")
        }
        return success
    }

    fun deleteByTitle(titlePattern: String): Boolean {
        val db = dbHelper.writableDatabase
        val rows = db.delete("reminders", "title LIKE ? OR body LIKE ?", arrayOf("%$titlePattern%", "%$titlePattern%"))
        LogBus.log("ReminderRepository -> deleteByTitle '$titlePattern' (rows: $rows)")
        return rows > 0
    }

    fun updateReminder(id: Long, newBody: String, newTitle: String? = null, newTriggerAt: Long? = null): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("body", newBody)
            if (newTitle != null) put("title", newTitle)
            if (newTriggerAt != null) put("trigger_at", newTriggerAt)
            put("updated_at", System.currentTimeMillis())
        }
        val rows = db.update("reminders", values, "id = ?", arrayOf(id.toString()))
        LogBus.log("ReminderRepository -> Updated reminder #$id (rows: $rows)")
        return rows > 0
    }

    private fun cursorToReminder(c: Cursor): Reminder {
        val titleIdx = c.getColumnIndex("title")
        val title = if (titleIdx != -1 && !c.isNull(titleIdx)) c.getString(titleIdx) else ""

        val updatedAtIdx = c.getColumnIndex("updated_at")
        val updatedAt = if (updatedAtIdx != -1 && !c.isNull(updatedAtIdx)) c.getLong(updatedAtIdx) else 0L

        return Reminder(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            title = title,
            body = c.getString(c.getColumnIndexOrThrow("body")),
            triggerAt = c.getLong(c.getColumnIndexOrThrow("trigger_at")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = updatedAt,
            state = c.getString(c.getColumnIndexOrThrow("state")),
            alarmRequestCode = c.getInt(c.getColumnIndexOrThrow("alarm_request_code"))
        )
    }
}
