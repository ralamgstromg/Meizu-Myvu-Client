package com.myvu.client.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a chat session grouping multiple messages.
 */
@Entity(tableName = "chat_session")
data class ChatSession(
    @PrimaryKey val sessionId: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    // Reference to user profile (optional)
    var profileId: String? = null
)
