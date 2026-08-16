package com.myvu.client.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.myvu.client.core.LogBus

class LocalDatabase(context: Context) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE notes (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "type TEXT NOT NULL DEFAULT 'TEXT', " +
                    "title TEXT NOT NULL DEFAULT '', " +
                    "body TEXT NOT NULL, " +
                    "audio_path TEXT, " +
                    "duration_sec INTEGER NOT NULL DEFAULT 0, " +
                    "tags TEXT NOT NULL DEFAULT '', " +
                    "summary TEXT NOT NULL DEFAULT '', " +
                    "action_items TEXT NOT NULL DEFAULT '', " +
                    "mindmap_data TEXT NOT NULL DEFAULT '', " +
                    "attachments_json TEXT NOT NULL DEFAULT '[]', " +
                    "created_at INTEGER NOT NULL, " +
                    "updated_at INTEGER NOT NULL);"
        )

        db.execSQL(
            "CREATE TABLE reminders (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "title TEXT NOT NULL DEFAULT '', " +
                    "body TEXT NOT NULL, " +
                    "tags TEXT NOT NULL DEFAULT '', " +
                    "summary TEXT NOT NULL DEFAULT '', " +
                    "action_items TEXT NOT NULL DEFAULT '', " +
                    "mindmap_data TEXT NOT NULL DEFAULT '', " +
                    "attachments_json TEXT NOT NULL DEFAULT '[]', " +
                    "trigger_at INTEGER NOT NULL, " +
                    "created_at INTEGER NOT NULL, " +
                    "updated_at INTEGER NOT NULL DEFAULT 0, " +
                    "state TEXT NOT NULL DEFAULT 'PENDING', " +
                    "alarm_request_code INTEGER NOT NULL);"
        )

        db.execSQL(
            "CREATE TABLE todos (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "list_name TEXT NOT NULL DEFAULT 'General', " +
                    "title TEXT NOT NULL, " +
                    "completed INTEGER NOT NULL DEFAULT 0, " +
                    "tags TEXT NOT NULL DEFAULT '', " +
                    "created_at INTEGER NOT NULL, " +
                    "updated_at INTEGER NOT NULL);"
        )

        db.execSQL(
            "CREATE TABLE voice_recordings (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "title TEXT NOT NULL DEFAULT '', " +
                    "audio_path TEXT NOT NULL, " +
                    "duration_ms INTEGER NOT NULL DEFAULT 0, " +
                    "file_size_bytes INTEGER NOT NULL DEFAULT 0, " +
                    "raw_transcript TEXT NOT NULL DEFAULT '', " +
                    "diarized_transcript TEXT NOT NULL DEFAULT '', " +
                    "summary TEXT NOT NULL DEFAULT '', " +
                    "action_items TEXT NOT NULL DEFAULT '', " +
                    "mindmap_data TEXT NOT NULL DEFAULT '', " +
                    "tags TEXT NOT NULL DEFAULT '', " +
                    "category TEXT NOT NULL DEFAULT 'MEETING', " +
                    "status TEXT NOT NULL DEFAULT 'READY', " +
                    "attachments_json TEXT NOT NULL DEFAULT '[]', " +
                    "created_at INTEGER NOT NULL, " +
                    "updated_at INTEGER NOT NULL);"
        )

        LogBus.log("LocalDatabase -> Created notes, reminders, todos and voice_recordings tables (v7)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE notes ADD COLUMN type TEXT NOT NULL DEFAULT 'TEXT';")
                db.execSQL("ALTER TABLE notes ADD COLUMN title TEXT NOT NULL DEFAULT '';")
                db.execSQL("ALTER TABLE notes ADD COLUMN audio_path TEXT;")
                db.execSQL("ALTER TABLE notes ADD COLUMN duration_sec INTEGER NOT NULL DEFAULT 0;")
                db.execSQL("ALTER TABLE reminders ADD COLUMN title TEXT NOT NULL DEFAULT '';")
                db.execSQL("ALTER TABLE reminders ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0;")
                LogBus.log("LocalDatabase -> Migrated database from v$oldVersion to v$newVersion")
            } catch (e: Exception) {
                LogBus.error("LocalDatabase -> Migration failed, recreating tables", e)
                db.execSQL("DROP TABLE IF EXISTS notes;")
                db.execSQL("DROP TABLE IF EXISTS reminders;")
                db.execSQL("DROP TABLE IF EXISTS todos;")
                db.execSQL("DROP TABLE IF EXISTS voice_recordings;")
                onCreate(db)
                return
            }
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE notes ADD COLUMN tags TEXT DEFAULT ''")
                LogBus.log("LocalDatabase -> Migrated database to v3 (added tags column)")
            } catch (e: Exception) {
                LogBus.error("LocalDatabase -> Migration to v3 failed", e)
            }
        }
        if (oldVersion < 4) {
            try {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS todos (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "list_name TEXT NOT NULL DEFAULT 'General', " +
                            "title TEXT NOT NULL, " +
                            "completed INTEGER NOT NULL DEFAULT 0, " +
                            "tags TEXT NOT NULL DEFAULT '', " +
                            "created_at INTEGER NOT NULL, " +
                            "updated_at INTEGER NOT NULL);"
                )
                LogBus.log("LocalDatabase -> Migrated database to v4 (added todos table)")
            } catch (e: Exception) {
                LogBus.error("LocalDatabase -> Migration to v4 failed", e)
            }
        }
        if (oldVersion < 5) {
            try {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS voice_recordings (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "title TEXT NOT NULL DEFAULT '', " +
                            "audio_path TEXT NOT NULL, " +
                            "duration_ms INTEGER NOT NULL DEFAULT 0, " +
                            "file_size_bytes INTEGER NOT NULL DEFAULT 0, " +
                            "raw_transcript TEXT NOT NULL DEFAULT '', " +
                            "diarized_transcript TEXT NOT NULL DEFAULT '', " +
                            "summary TEXT NOT NULL DEFAULT '', " +
                            "action_items TEXT NOT NULL DEFAULT '', " +
                            "mindmap_data TEXT NOT NULL DEFAULT '', " +
                            "tags TEXT NOT NULL DEFAULT '', " +
                            "category TEXT NOT NULL DEFAULT 'MEETING', " +
                            "status TEXT NOT NULL DEFAULT 'READY', " +
                            "created_at INTEGER NOT NULL, " +
                            "updated_at INTEGER NOT NULL);"
                )
                LogBus.log("LocalDatabase -> Migrated database to v5 (added voice_recordings table)")
            } catch (e: Exception) {
                LogBus.error("LocalDatabase -> Migration to v5 failed", e)
            }
        }
        if (oldVersion < 6) {
            try {
                db.execSQL("ALTER TABLE notes ADD COLUMN summary TEXT NOT NULL DEFAULT '';")
                db.execSQL("ALTER TABLE notes ADD COLUMN action_items TEXT NOT NULL DEFAULT '';")
                db.execSQL("ALTER TABLE notes ADD COLUMN mindmap_data TEXT NOT NULL DEFAULT '';")

                db.execSQL("ALTER TABLE reminders ADD COLUMN tags TEXT NOT NULL DEFAULT '';")
                db.execSQL("ALTER TABLE reminders ADD COLUMN summary TEXT NOT NULL DEFAULT '';")
                db.execSQL("ALTER TABLE reminders ADD COLUMN action_items TEXT NOT NULL DEFAULT '';")
                db.execSQL("ALTER TABLE reminders ADD COLUMN mindmap_data TEXT NOT NULL DEFAULT '';")
                LogBus.log("LocalDatabase -> Migrated database to v6 (added AI summary, tasks, mindmap columns to notes & reminders)")
            } catch (e: Exception) {
                LogBus.error("LocalDatabase -> Migration to v6 failed", e)
            }
        }
        if (oldVersion < 7) {
            try {
                db.execSQL("ALTER TABLE notes ADD COLUMN attachments_json TEXT NOT NULL DEFAULT '[]';")
                db.execSQL("ALTER TABLE reminders ADD COLUMN attachments_json TEXT NOT NULL DEFAULT '[]';")
                db.execSQL("ALTER TABLE voice_recordings ADD COLUMN attachments_json TEXT NOT NULL DEFAULT '[]';")
                LogBus.log("LocalDatabase -> Migrated database to v7 (added attachments_json columns)")
            } catch (e: Exception) {
                LogBus.error("LocalDatabase -> Migration to v7 failed", e)
            }
        }
    }

    companion object {
        const val DATABASE_NAME = "myvu_client.db"
        const val DATABASE_VERSION = 7

        @Volatile
        private var instance: LocalDatabase? = null

        fun getInstance(context: Context): LocalDatabase {
            return instance ?: synchronized(this) {
                instance ?: LocalDatabase(context).also { instance = it }
            }
        }

        fun closeInstance() {
            synchronized(this) {
                try {
                    instance?.close()
                } catch (ignored: Exception) {}
                instance = null
            }
        }
    }
}
