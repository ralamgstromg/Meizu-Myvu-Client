package com.myvu.client.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class ReminderRepository {

    private final LocalDatabase dbHelper;

    public ReminderRepository(Context context) {
        this.dbHelper = LocalDatabase.getInstance(context);
    }

    public long insertReminder(String body, long triggerAt, int alarmRequestCode) {
        if (body == null || body.trim().isEmpty()) return -1;
        long now = System.currentTimeMillis();
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("body", body.trim());
        values.put("trigger_at", triggerAt);
        values.put("state", "PENDING");
        values.put("created_at", now);
        values.put("updated_at", now);
        values.put("snooze_count", 0);
        values.put("alarm_request_code", alarmRequestCode);
        return db.insert("reminders", null, values);
    }

    public List<Reminder> getPendingReminders() {
        List<Reminder> reminders = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("reminders",
                new String[]{"id", "body", "trigger_at", "state", "created_at", "updated_at", "fired_at", "snooze_count", "alarm_request_code"},
                "state = ?", new String[]{"PENDING"}, null, null, "trigger_at ASC")) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    reminders.add(mapRow(cursor));
                } while (cursor.moveToNext());
            }
        }
        return reminders;
    }

    public List<Reminder> getAllReminders() {
        List<Reminder> reminders = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("reminders",
                new String[]{"id", "body", "trigger_at", "state", "created_at", "updated_at", "fired_at", "snooze_count", "alarm_request_code"},
                null, null, null, null, "trigger_at DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    reminders.add(mapRow(cursor));
                } while (cursor.moveToNext());
            }
        }
        return reminders;
    }

    public Reminder getReminder(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("reminders",
                new String[]{"id", "body", "trigger_at", "state", "created_at", "updated_at", "fired_at", "snooze_count", "alarm_request_code"},
                "id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return mapRow(cursor);
            }
        }
        return null;
    }

    public boolean updateReminderState(long id, String state) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("state", state);
        values.put("updated_at", System.currentTimeMillis());
        if ("FIRED".equals(state)) {
            values.put("fired_at", System.currentTimeMillis());
        }
        return db.update("reminders", values, "id = ?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean snoozeReminder(long id, long newTriggerAt) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Reminder r = getReminder(id);
        if (r == null) return false;
        ContentValues values = new ContentValues();
        values.put("trigger_at", newTriggerAt);
        values.put("state", "PENDING");
        values.put("snooze_count", r.getSnoozeCount() + 1);
        values.put("updated_at", System.currentTimeMillis());
        return db.update("reminders", values, "id = ?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean deleteReminder(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete("reminders", "id = ?", new String[]{String.valueOf(id)}) > 0;
    }

    private Reminder mapRow(Cursor cursor) {
        return new Reminder(
                cursor.getLong(0),
                cursor.getString(1),
                cursor.getLong(2),
                cursor.getString(3),
                cursor.getLong(4),
                cursor.getLong(5),
                cursor.isNull(6) ? null : cursor.getLong(6),
                cursor.getInt(7),
                cursor.getInt(8)
        );
    }
}
