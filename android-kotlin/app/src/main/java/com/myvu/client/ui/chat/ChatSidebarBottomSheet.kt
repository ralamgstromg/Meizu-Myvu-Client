package com.myvu.client.ui.chat

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.myvu.client.R
import com.myvu.client.ai.AiProvider
import com.myvu.client.ai.PhoneActionExecutor
import com.myvu.client.ai.VoiceActionRouter
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.data.ChatMessage
import com.myvu.client.data.ChatSession
import com.myvu.client.data.UserProfileAnalyzer
import com.myvu.client.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Sidebar Chat Interface sheet accessible from anywhere in the app.
 * Provides history tracking, voice/text/image inputs, and mobile device control actions.
 */
class ChatSidebarBottomSheet : BottomSheetDialogFragment() {

    private lateinit var rvChatHistory: RecyclerView
    private lateinit var edtChatMessage: EditText
    private lateinit var btnSendChat: MaterialButton
    private lateinit var btnAttachImage: MaterialButton
    private lateinit var btnVoiceMic: MaterialButton
    private lateinit var btnCloseChatSidebar: MaterialButton
    private lateinit var progressChat: ProgressBar
    private lateinit var layImagePreview: LinearLayout
    private lateinit var imgAttachedPreview: ImageView
    private lateinit var btnRemoveAttachedImage: ImageButton

    private var currentSessionId: String = UUID.randomUUID().toString()
    private var attachedImageUri: Uri? = null

    private val chatAdapter = ChatAdapter()

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            setAttachedImage(uri)
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val uri = saveBitmapToCache(bitmap)
            if (uri != null) {
                setAttachedImage(uri)
            }
        }
    }

    private val sttLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                edtChatMessage.setText(spokenText)
                sendUserQuery(spokenText)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat_sidebar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvChatHistory = view.findViewById(R.id.rvChatHistory)
        edtChatMessage = view.findViewById(R.id.edtChatMessage)
        btnSendChat = view.findViewById(R.id.btnSendChat)
        btnAttachImage = view.findViewById(R.id.btnAttachImage)
        btnVoiceMic = view.findViewById(R.id.btnVoiceMic)
        btnCloseChatSidebar = view.findViewById(R.id.btnCloseChatSidebar)
        progressChat = view.findViewById(R.id.progressChat)
        layImagePreview = view.findViewById(R.id.layImagePreview)
        imgAttachedPreview = view.findViewById(R.id.imgAttachedPreview)
        btnRemoveAttachedImage = view.findViewById(R.id.btnRemoveAttachedImage)

        rvChatHistory.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        rvChatHistory.adapter = chatAdapter

        btnCloseChatSidebar.setOnClickListener { dismiss() }

        btnRemoveAttachedImage.setOnClickListener {
            attachedImageUri = null
            layImagePreview.visibility = View.GONE
        }

        btnAttachImage.setOnClickListener { showImageSourceDialog() }

        btnVoiceMic.setOnClickListener { launchVoiceStt() }

        btnSendChat.setOnClickListener {
            val text = edtChatMessage.text.toString().trim()
            if (text.isNotBlank() || attachedImageUri != null) {
                sendUserQuery(text)
            }
        }

        observeChatHistory()
    }

    private fun observeChatHistory() {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(requireContext()).chatDao()
            dao.getAllMessagesFlow().collectLatest { messages ->
                chatAdapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    rvChatHistory.smoothScrollToPosition(messages.size - 1)
                }
            }
        }
    }

    private fun setAttachedImage(uri: Uri) {
        attachedImageUri = uri
        imgAttachedPreview.setImageURI(uri)
        layImagePreview.visibility = View.VISIBLE
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri? {
        return try {
            val file = File(requireContext().cacheDir, "chat_attach_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            LogBus.error("ChatSidebar -> Error saving attached bitmap", e)
            null
        }
    }

    private fun showImageSourceDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Adjuntar Imagen")
            .setItems(arrayOf("Cámara", "Galería")) { _, which ->
                if (which == 0) {
                    cameraLauncher.launch(null)
                } else {
                    galleryLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun launchVoiceStt() {
        try {
            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Habla tu comando o consulta...")
            }
            sttLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Reconocimiento de voz no disponible en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendUserQuery(text: String) {
        val mediaType = if (attachedImageUri != null) "IMAGE" else "TEXT"
        val queryText = if (text.isBlank() && attachedImageUri != null) "[Imagen adjunta]" else text

        edtChatMessage.setText("")
        val imageUriString = attachedImageUri?.toString()
        attachedImageUri = null
        layImagePreview.visibility = View.GONE

        // Record User Query
        val analyzer = UserProfileAnalyzer.getInstance(requireContext())
        analyzer.recordMessage(currentSessionId, "USER", queryText, mediaType, imageUriString)

        progressChat.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val executor = PhoneActionExecutor(requireContext())
            val router = VoiceActionRouter(requireContext(), executor)

            val route = router.tryRoute(queryText)
            var responseText = ""
            var sourceName = "SIDEBAR_CHAT"

            if (route.handled) {
                responseText = route.responseText
                sourceName = route.source.name
            } else {
                val providerId = Prefs.aiProvider(requireContext())
                val provider = AiProvider.fromId(providerId)
                val apiKey = Prefs.aiApiKey(requireContext(), providerId)
                val model = Prefs.aiModel(requireContext(), providerId)
                val endpoint = Prefs.aiEndpoint(requireContext(), providerId)
                val prompt = Prefs.systemPrompt(requireContext())
                val client = provider.newClient(requireContext(), apiKey, model, endpoint, prompt)

                if (!client.isConfigured()) {
                    responseText = "El proveedor de IA (${provider.displayName}) no está configurado en Ajustes."
                    sourceName = "ERROR"
                } else {
                    try {
                        val profileContext = analyzer.buildProfilePromptContext()
                        val fullPrompt = profileContext + queryText
                        val rawAnswer = client.ask(fullPrompt)
                        val processed = executor.processAndExecute(rawAnswer)
                        responseText = if (processed.isNotBlank()) processed else (rawAnswer ?: "Respuesta vacía de la IA.")
                        sourceName = provider.displayName
                    } catch (e: Exception) {
                        LogBus.error("ChatSidebar -> Error querying AI", e)
                        responseText = "Error al consultar la IA: ${e.message}"
                        sourceName = "ERROR"
                    }
                }
            }

            analyzer.recordMessage(currentSessionId, "AI", responseText, "TEXT", sourceName)

            withContext(Dispatchers.Main) {
                progressChat.visibility = View.GONE
            }
        }
    }

    // RecyclerView Adapter for Chat Messages
    private class ChatAdapter : RecyclerView.Adapter<ChatViewHolder>() {
        private var items: List<ChatMessage> = emptyList()

        fun submitList(newList: List<ChatMessage>) {
            items = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
            return ChatViewHolder(v)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }

    private class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardMessage: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.cardMessage)
        private val txtMessageContent: TextView = itemView.findViewById(R.id.txtMessageContent)
        private val txtMessageTime: TextView = itemView.findViewById(R.id.txtMessageTime)
        private val txtMessageSource: TextView = itemView.findViewById(R.id.txtMessageSource)
        private val imgMessageAttached: ImageView = itemView.findViewById(R.id.imgMessageAttached)

        fun bind(msg: ChatMessage) {
            txtMessageContent.text = msg.content
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
            txtMessageTime.text = timeStr
            txtMessageSource.text = msg.actionResult ?: if (msg.direction == "USER") "Tú" else "IA"

            if ("USER" == msg.direction) {
                cardMessage.setCardBackgroundColor(itemView.context.getColor(R.color.obsidian_container_high))
                (itemView as LinearLayout).gravity = android.view.Gravity.END
            } else {
                cardMessage.setCardBackgroundColor(itemView.context.getColor(R.color.obsidian_container_low))
                (itemView as LinearLayout).gravity = android.view.Gravity.START
            }

            if (!msg.actionResult.isNullOrBlank() && msg.actionResult.startsWith("content://") || msg.actionResult?.startsWith("file://") == true) {
                imgMessageAttached.visibility = View.VISIBLE
                try {
                    imgMessageAttached.setImageURI(Uri.parse(msg.actionResult))
                } catch (_: Exception) {}
            } else {
                imgMessageAttached.visibility = View.GONE
            }
        }
    }
}
