package com.myvu.client.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a single message in a chat session.
 * Stored in Room DB as `chat_message` table.
 */
@Entity(tableName = "chat_message")
data class ChatMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val timestamp: Long = System.currentTimeMillis(),
    // USER or AI
    val direction: String,
    // Text, voice, image, etc.
    val content: String,
    // Media type identifier (TEXT, VOICE, IMAGE)
    val mediaType: String = "TEXT",
    // Optional result of an action performed by the AI (e.g., app launched)
    val actionResult: String? = null
)
