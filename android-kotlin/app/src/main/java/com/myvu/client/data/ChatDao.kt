package com.myvu.client.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for chat-related tables.
 */
@Dao
interface ChatDao {
    // ---- ChatMessage ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("SELECT * FROM chat_message WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_message ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_message ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<ChatMessage>

    @Query("SELECT * FROM chat_message ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ChatMessage>

    @Query("DELETE FROM chat_message")
    suspend fun deleteAllMessages()

    // ---- ChatSession ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession)

    @Query("SELECT * FROM chat_session ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestSession(): ChatSession?

    @Query("SELECT * FROM chat_session ORDER BY startTime DESC")
    suspend fun getAllSessions(): List<ChatSession>

    @Query("DELETE FROM chat_session")
    suspend fun deleteAllSessions()

    // ---- UserProfile ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfile)

    @Query("SELECT * FROM user_profile WHERE profileId = :profileId")
    suspend fun getProfile(profileId: String): UserProfile?

    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun getDefaultProfile(): UserProfile?
}
