package com.myvu.client.database

import android.content.ContentValues
import android.content.Context
import com.myvu.client.core.LogBus
import kotlin.random.Random

class ReminderRepository(context: Context) {

    private val dbHelper: LocalDatabase = LocalDatabase.getInstance(context)

    fun createReminder(body: String, triggerAt: Long): Reminder? {
        val now = System.currentTimeMillis()
        val requestCode = Random.nextInt(10000, 999999)

        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("body", body)
            put("trigger_at", triggerAt)
            put("created_at", now)
            put("state", "PENDING")
            put("alarm_request_code", requestCode)
        }

        val id = db.insert("reminders", null, values)
        if (id != -1L) {
            LogBus.log("ReminderRepository -> Created reminder #$id triggerAt=$triggerAt")
            return Reminder(
                id = id,
                body = body,
                triggerAt = triggerAt,
                createdAt = now,
                state = "PENDING",
                alarmRequestCode = requestCode
            )
        } else {
            LogBus.error("ReminderRepository -> Failed to insert reminder", null)
            return null
        }
    }

    fun getPendingReminders(): List<Reminder> {
        val list = ArrayList<Reminder>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "reminders", null, "state = ?", arrayOf("PENDING"), null, null, "trigger_at ASC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val reminder = Reminder(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    body = c.getString(c.getColumnIndexOrThrow("body")),
                    triggerAt = c.getLong(c.getColumnIndexOrThrow("trigger_at")),
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                    state = c.getString(c.getColumnIndexOrThrow("state")),
                    alarmRequestCode = c.getInt(c.getColumnIndexOrThrow("alarm_request_code"))
                )
                list.add(reminder)
            }
        }
        return list
    }

    fun getReminder(id: Long): Reminder? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "reminders", null, "id = ?", arrayOf(id.toString()), null, null, null
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                return Reminder(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    body = c.getString(c.getColumnIndexOrThrow("body")),
                    triggerAt = c.getLong(c.getColumnIndexOrThrow("trigger_at")),
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                    state = c.getString(c.getColumnIndexOrThrow("state")),
                    alarmRequestCode = c.getInt(c.getColumnIndexOrThrow("alarm_request_code"))
                )
            }
        }
        return null
    }

    fun updateReminderState(id: Long, newState: String): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("state", newState)
        }
        val rows = db.update("reminders", values, "id = ?", arrayOf(id.toString()))
        val success = rows > 0
        if (success) {
            LogBus.log("ReminderRepository -> Updated reminder #$id state to $newState")
        }
        return success
    }

    fun snoozeReminder(id: Long, newTriggerAt: Long): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("trigger_at", newTriggerAt)
            put("state", "PENDING")
        }
        val rows = db.update("reminders", values, "id = ?", arrayOf(id.toString()))
        val success = rows > 0
        if (success) {
            LogBus.log("ReminderRepository -> Snoozed reminder #$id newTriggerAt=$newTriggerAt")
        }
        return success
    }

    fun deleteReminder(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        val rows = db.delete("reminders", "id = ?", arrayOf(id.toString()))
        val success = rows > 0
        if (success) {
            LogBus.log("ReminderRepository -> Deleted reminder #$id")
        }
        return success
    }
}
