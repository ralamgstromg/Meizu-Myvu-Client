package com.myvu.client.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class LocalDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "myvu_local.db";
    private static final int DATABASE_VERSION = 1;

    private static LocalDatabase instance;

    public static synchronized LocalDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new LocalDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private LocalDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "body TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL, " +
                "archived INTEGER NOT NULL DEFAULT 0" +
                ")");

        db.execSQL("CREATE TABLE reminders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "body TEXT NOT NULL, " +
                "trigger_at INTEGER NOT NULL, " +
                "state TEXT NOT NULL, " + // PENDING, FIRED, COMPLETED, CANCELLED, FAILED
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL, " +
                "fired_at INTEGER, " +
                "snooze_count INTEGER NOT NULL DEFAULT 0, " +
                "alarm_request_code INTEGER NOT NULL UNIQUE" +
                ")");

        db.execSQL("CREATE INDEX idx_reminders_state_trigger ON reminders(state, trigger_at)");
        db.execSQL("CREATE INDEX idx_notes_updated ON notes(updated_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // No upgrades in version 1
    }
}
