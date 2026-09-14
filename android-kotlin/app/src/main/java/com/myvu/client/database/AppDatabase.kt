package com.myvu.client.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.myvu.client.data.ChatMessage
import com.myvu.client.data.ChatSession
import com.myvu.client.data.UserProfile
import com.myvu.client.data.ChatDao
import com.myvu.client.data.BluetoothDeviceEntity
import com.myvu.client.data.BluetoothDeviceDao

/**
 * Room database that holds chat history, user profile, and registered Bluetooth devices.
 */
@Database(
    entities = [ChatMessage::class, ChatSession::class, UserProfile::class, BluetoothDeviceEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun bluetoothDeviceDao(): BluetoothDeviceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "myvu_chat.db"
            ).fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
