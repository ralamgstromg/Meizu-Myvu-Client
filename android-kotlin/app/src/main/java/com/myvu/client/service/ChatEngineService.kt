package com.myvu.client.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.lifecycle.MutableLiveData
import com.myvu.client.ai.AiConversation
import com.myvu.client.core.LogBus
import com.myvu.client.data.ChatDao
import com.myvu.client.data.ChatMessage
import com.myvu.client.data.ChatSession
import com.myvu.client.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/**
 * Foreground service that mediates between the UI, the AI conversation, and the local Room DB.
 * It also parses @action tags in AI responses and forwards them to the existing PhoneActionExecutor.
 */
class ChatEngineService : Service() {
    private val binder = ChatEngineBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // LiveData that UI can observe for new messages (ordered by timestamp)
    val messagesLiveData = MutableLiveData<List<ChatMessage>>()

    // DB access – lazy because applicationContext isn't available at field-init time
    private val chatDao: ChatDao by lazy {
        AppDatabase.getInstance(applicationContext).chatDao()
    }

    // The AI conversation instance, created lazily to avoid context issues at init time
    private var aiConversation: AiConversation? = null

    // Holds the current session id; created lazily on first request
    @Volatile
    private var currentSessionId: String? = null

    // Job that collects message updates from Room
    private var observerJob: kotlinx.coroutines.Job? = null

    // Flag to check service status
    companion object {
        /** Protocol code used by AiProtocol for chat answers (CODE_CHAT_GPT_RESPONSE). */
        private const val CHAT_ANSWER_CODE = 122

        @Volatile
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        aiConversation?.shutdown()
        aiConversation = null
        observerJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * Ensure a ChatSession exists and return its id.
     */
    private suspend fun getOrCreateSessionId(): String {
        currentSessionId?.let { return it }
        // Try to fetch the latest session, otherwise create a new one
        val latest = chatDao.getLatestSession()
        val sessionId = latest?.sessionId ?: UUID.randomUUID().toString().also { newId ->
            val session = ChatSession(sessionId = newId)
            chatDao.insertSession(session)
        }
        currentSessionId = sessionId
        return sessionId
    }

    /**
     * Starts a new chat session (e.g. user taps "new chat").
     */
    fun newSession() {
        observerJob?.cancel()
        currentSessionId = null
        scope.launch {
            val sessionId = getOrCreateSessionId()
            startObserving(sessionId)
        }
    }

    /**
     * Returns (or lazily creates) the AiConversation bound to this service.
     * The Sender intercepts AI protocol messages to capture the AI answer text and persist it.
     */
    private fun getOrCreateAi(): AiConversation {
        aiConversation?.let { return it }
        val ai = AiConversation(applicationContext) { actionJson, _, _ ->
            // Intercept the AI protocol JSON to extract the chat answer text
            interceptAiProtocol(actionJson)
        }
        aiConversation = ai
        return ai
    }

    /**
     * Parses the AI protocol JSON action.
     * When the action is a chatAnswer (code 10020), extracts the answer text and stores
     * it as an AI ChatMessage in the Room database.
     */
    private fun interceptAiProtocol(actionJson: String) {
        try {
            val json = JSONObject(actionJson)
            val code = json.optInt("code", -1)
            if (code == CHAT_ANSWER_CODE) {
                val payload = json.optJSONObject("payload") ?: return
                val answerText = payload.optString("answer", "").trim()
                if (answerText.isBlank()) return

                val sessionId = currentSessionId ?: return
                val actionResult = parseActionTag(answerText)
                val aiMsg = ChatMessage(
                    sessionId = sessionId,
                    direction = "AI",
                    content = answerText,
                    mediaType = "TEXT",
                    actionResult = actionResult
                )
                scope.launch {
                    try {
                        chatDao.insertMessage(aiMsg)
                        LogBus.log("ChatEngine: AI response stored (${answerText.length} chars)")
                    } catch (e: Exception) {
                        LogBus.error("ChatEngine: failed to store AI response", e)
                    }
                }
            }
        } catch (e: Exception) {
            // Not a JSON we recognize -- ignore silently
            LogBus.trace("ChatEngine: non-JSON or unknown AI protocol action")
        }
    }

    /**
     * Public API: send a text message from UI.
     */
    fun sendText(text: String) {
        scope.launch {
            val sessionId = getOrCreateSessionId()
            val userMsg = ChatMessage(
                sessionId = sessionId,
                direction = "USER",
                content = text,
                mediaType = "TEXT"
            )
            chatDao.insertMessage(userMsg)

            // Start observing if not already doing so
            startObserving(sessionId)

            // Forward to AI — askText runs on main thread internally,
            // the response will arrive via the Sender callback in interceptAiProtocol
            val ai = getOrCreateAi()
            ai.askText(text)
        }
    }

    /**
     * Starts collecting message updates from Room for the given session.
     * Uses collectLatest so that rapid DB updates don't pile up.
     */
    private fun startObserving(sessionId: String) {
        // Avoid creating duplicate observers for the same session
        if (observerJob?.isActive == true) return
        observerJob = scope.launch {
            chatDao.getMessagesForSession(sessionId).collectLatest { list ->
                messagesLiveData.postValue(list)
            }
        }
    }

    /**
     * Simple parser that extracts a tag like "@action:open_app:com.spotify.music" from the AI text.
     * Returns the raw tag string (or null) for storage.
     */
    private fun parseActionTag(text: String?): String? {
        if (text == null) return null
        val regex = "@action:[^\\s]+".toRegex()
        return regex.find(text)?.value
    }

    // TODO: add sendVoice(File) and sendImage(Uri) – similar flow, omitted for brevity.

    inner class ChatEngineBinder : Binder() {
        fun getService(): ChatEngineService = this@ChatEngineService
    }
}
