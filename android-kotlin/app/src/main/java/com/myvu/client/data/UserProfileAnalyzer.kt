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
 * Manages chat history persistence and dynamic user profile enrichment based on queries.
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
                interestTags = "general,asistente",
                messageCount = 0,
                lastInteraction = System.currentTimeMillis()
            )
            dao.insertProfile(profile)
        }
        profile
    }

    /**
     * Saves a user message or AI response to Room DB and updates the user profile.
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
     * Analyzes query content to update user profile interests and interaction metadata.
     */
    private suspend fun updateProfileFromQuery(query: String) = withContext(Dispatchers.IO) {
        try {
            val dao = AppDatabase.getInstance(appContext).chatDao()
            val profile = getOrCreateProfile()

            profile.messageCount += 1
            profile.lastInteraction = System.currentTimeMillis()

            val currentTags = profile.interestTags.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toMutableSet()

            val lower = query.lowercase()
            if (lower.contains("clima") || lower.contains("tiempo") || lower.contains("temperatura")) currentTags.add("clima")
            if (lower.contains("música") || lower.contains("canción") || lower.contains("reproduc")) currentTags.add("música")
            if (lower.contains("teleprompter") || lower.contains("guion") || lower.contains("discurso")) currentTags.add("teleprompter")
            if (lower.contains("nota") || lower.contains("recordatorio") || lower.contains("tarea")) currentTags.add("productividad")
            if (lower.contains("notificación") || lower.contains("mensaje") || lower.contains("aviso")) currentTags.add("notificaciones")
            if (lower.contains("buscar") || lower.contains("google") || lower.contains("quién") || lower.contains("qué es")) currentTags.add("búsquedas")
            if (lower.contains("foto") || lower.contains("imagen") || lower.contains("cámara")) currentTags.add("visión_ia")

            profile.interestTags = currentTags.joinToString(",")
            dao.insertProfile(profile)
        } catch (e: Exception) {
            LogBus.error("UserProfileAnalyzer -> Error updating profile", e)
        }
    }

    /**
     * Builds system prompt context derived from user profile.
     */
    suspend fun buildProfilePromptContext(): String = withContext(Dispatchers.IO) {
        try {
            val profile = getOrCreateProfile()
            val sb = StringBuilder()
            if (profile.name.isNotBlank()) {
                sb.append("Nombre del usuario: ").append(profile.name).append(". ")
            }
            if (profile.interestTags.isNotBlank()) {
                sb.append("Intereses detectados del usuario: ").append(profile.interestTags).append(". ")
            }
            try {
                val json = JSONObject(profile.preferencesJson)
                if (json.has("customInstructions")) {
                    val custom = json.optString("customInstructions")
                    if (custom.isNotBlank()) {
                        sb.append("Instrucciones personalizadas del usuario: ").append(custom).append(". ")
                    }
                }
            } catch (_: Exception) {}

            if (sb.isNotEmpty()) "[Perfil del Usuario: ${sb.toString().trim()}]\n" else ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Updates profile details manually (from Settings UI).
     */
    suspend fun saveProfile(name: String, interestTags: String, customInstructions: String) = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getInstance(appContext).chatDao()
        val profile = getOrCreateProfile()
        profile.name = name
        profile.interestTags = interestTags

        val json = try {
            JSONObject(profile.preferencesJson)
        } catch (_: Exception) {
            JSONObject()
        }
        json.put("customInstructions", customInstructions)
        profile.preferencesJson = json.toString()

        dao.insertProfile(profile)
    }
}
