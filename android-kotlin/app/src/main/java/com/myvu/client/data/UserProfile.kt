package com.myvu.client.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Stores detailed profile information for a user. Used to personalise AI responses.
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val profileId: String = UUID.randomUUID().toString(),
    var name: String = "",
    var avatarUri: String? = null,
    // JSON string with arbitrary preferences (e.g., language, units)
    var preferencesJson: String = "{}",
    // Comma‑separated tags like "weather,music,travel"
    var interestTags: String = "",
    var messageCount: Int = 0,
    var lastInteraction: Long = System.currentTimeMillis()
)
