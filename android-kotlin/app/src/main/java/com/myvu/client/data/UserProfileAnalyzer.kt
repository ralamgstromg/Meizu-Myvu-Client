package com.myvu.client.data

import android.content.Context
import com.myvu.client.database.AppDatabase
import com.myvu.client.core.LogBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * Manages chat history persistence and personalized user profile (name only, no history or interests in prompt).
 */
class UserProfileAnalyzer(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val DEFAULT_PROFILE_ID = "default_user_profile"

        @Volatile
        private var INSTANCE: UserProfileAnalyzer? = null

        fun getInstance(context: Context): UserProfileAnalyzer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserProfileAnalyzer(context).also { INSTANCE = it }
            }
        }
    }

    /**
     * Retrieves or creates the primary UserProfile.
     */
    suspend fun getOrCreateProfile(): UserProfile = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getInstance(appContext).chatDao()
        var profile = dao.getProfile(DEFAULT_PROFILE_ID) ?: dao.getDefaultProfile()
        if (profile == null) {
            profile = UserProfile(
                profileId = DEFAULT_PROFILE_ID,
                name = "Usuario",
                preferencesJson = "{\"language\":\"es\",\"tone\":\"concise\"}",
                interestTags = "",
                messageCount = 0,
                lastInteraction = System.currentTimeMillis()
            )
            dao.insertProfile(profile)
        }
        profile
    }

    /**
     * Saves a user message or AI response to Room DB for UI display purposes.
     */
    fun recordMessage(
        sessionId: String,
        direction: String,
        content: String,
        mediaType: String = "TEXT",
        actionResult: String? = null
    ) {
        if (content.isBlank()) return

        scope.launch {
            try {
                val dao = AppDatabase.getInstance(appContext).chatDao()
                
                // Ensure session exists
                val session = dao.getLatestSession()
                if (session == null || session.sessionId != sessionId) {
                    dao.insertSession(ChatSession(sessionId = sessionId, profileId = DEFAULT_PROFILE_ID))
                }

                val msg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    timestamp = System.currentTimeMillis(),
                    direction = direction,
                    content = content,
                    mediaType = mediaType,
                    actionResult = actionResult
                )
                dao.insertMessage(msg)

                if ("USER" == direction) {
                    updateProfileFromQuery(content)
                }
            } catch (e: Exception) {
                LogBus.error("UserProfileAnalyzer -> Error recording message", e)
            }
        }
    }

    /**
     * Updates profile interaction metadata (message count and last interaction time).
     */
    private suspend fun updateProfileFromQuery(query: String) = withContext(Dispatchers.IO) {
        try {
            val dao = AppDatabase.getInstance(appContext).chatDao()
            val profile = getOrCreateProfile()

            profile.messageCount += 1
            profile.lastInteraction = System.currentTimeMillis()
            dao.insertProfile(profile)
        } catch (e: Exception) {
            LogBus.error("UserProfileAnalyzer -> Error updating profile metadata", e)
        }
    }

    /**
     * Builds system prompt context derived exclusively from the user's name for personalized addressing.
     */
    suspend fun buildProfilePromptContext(): String = withContext(Dispatchers.IO) {
        try {
            val profile = getOrCreateProfile()
            if (profile.name.isNotBlank()) {
                "[Perfil del Usuario: El nombre del usuario es ${profile.name}. Dirígete a él de forma personalizada.]\n"
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * History context is disabled: returns empty string to ensure only current request is processed.
     */
    suspend fun buildRecentHistoryContext(limit: Int = 5): String = withContext(Dispatchers.IO) {
        ""
    }

    /**
     * Returns profile context containing ONLY the user's name. Bypasses conversation history.
     */
    suspend fun buildFullPromptContext(recentLimit: Int = 5): String = withContext(Dispatchers.IO) {
        buildProfilePromptContext()
    }

    /**
     * Updates profile details manually from Settings UI.
     */
    suspend fun saveProfile(name: String, interestTags: String = "", customInstructions: String = "") = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getInstance(appContext).chatDao()
        val profile = getOrCreateProfile()
        profile.name = name
        dao.insertProfile(profile)
    }
}
