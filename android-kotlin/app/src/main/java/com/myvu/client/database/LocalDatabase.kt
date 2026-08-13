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
                    "created_at INTEGER NOT NULL, " +
                    "updated_at INTEGER NOT NULL);"
        )

        db.execSQL(
            "CREATE TABLE reminders (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "title TEXT NOT NULL DEFAULT '', " +
                    "body TEXT NOT NULL, " +
                    "trigger_at INTEGER NOT NULL, " +
                    "created_at INTEGER NOT NULL, " +
                    "updated_at INTEGER NOT NULL DEFAULT 0, " +
                    "state TEXT NOT NULL DEFAULT 'PENDING', " +
                    "alarm_request_code INTEGER NOT NULL);"
        )

        LogBus.log("LocalDatabase -> Created notes and reminders tables (v3)")
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
                onCreate(db)
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
    }

    companion object {
        const val DATABASE_NAME = "myvu_client.db"
        const val DATABASE_VERSION = 3

        @Volatile
        private var instance: LocalDatabase? = null

        @JvmStatic
        fun getInstance(context: Context): LocalDatabase {
            return instance ?: synchronized(this) {
                instance ?: LocalDatabase(context.applicationContext).also { instance = it }
            }
        }
    }
}
