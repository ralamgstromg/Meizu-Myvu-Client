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
                    "body TEXT NOT NULL, " +
                    "created_at INTEGER NOT NULL, " +
                    "updated_at INTEGER NOT NULL);"
        )

        db.execSQL(
            "CREATE TABLE reminders (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "body TEXT NOT NULL, " +
                    "trigger_at INTEGER NOT NULL, " +
                    "created_at INTEGER NOT NULL, " +
                    "state TEXT NOT NULL DEFAULT 'PENDING', " +
                    "alarm_request_code INTEGER NOT NULL);"
        )

        LogBus.log("LocalDatabase -> Created notes and reminders tables")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS notes;")
        db.execSQL("DROP TABLE IF EXISTS reminders;")
        onCreate(db)
    }

    companion object {
        const val DATABASE_NAME = "myvu_client.db"
        const val DATABASE_VERSION = 1

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
