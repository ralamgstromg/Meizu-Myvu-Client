package com.myvu.client.ui.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.myvu.client.R
import com.myvu.client.ai.AiProvider
import com.myvu.client.ai.PhoneActionExecutor
import com.myvu.client.ai.VoiceActionRouter
import com.myvu.client.core.EdgeToEdgeHelper
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.data.ChatMessage
import com.myvu.client.data.UserProfileAnalyzer
import com.myvu.client.database.AppDatabase
import com.myvu.client.skills.SkillRegistry
import com.myvu.client.ui.SettingsActivity
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
 * Full-Screen Chat Activity supporting history tracking, quick skill access toolbar,
 * instant Enter sending, voice/text/image inputs, and mobile device control actions.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private lateinit var btnBackChat: ImageButton
    private lateinit var btnChatSettings: ImageButton
    private lateinit var rvChatHistory: RecyclerView
    private lateinit var edtChatMessage: EditText
    private lateinit var btnSendChat: MaterialButton
    private lateinit var btnAttachImage: MaterialButton
    private lateinit var btnVoiceMic: MaterialButton
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        btnBackChat = findViewById(R.id.btnBackChat)
        btnChatSettings = findViewById(R.id.btnChatSettings)
        rvChatHistory = findViewById(R.id.rvChatHistory)
        edtChatMessage = findViewById(R.id.edtChatMessage)
        btnSendChat = findViewById(R.id.btnSendChat)
        btnAttachImage = findViewById(R.id.btnAttachImage)
        btnVoiceMic = findViewById(R.id.btnVoiceMic)
        progressChat = findViewById(R.id.progressChat)
        layImagePreview = findViewById(R.id.layImagePreview)
        imgAttachedPreview = findViewById(R.id.imgAttachedPreview)
        btnRemoveAttachedImage = findViewById(R.id.btnRemoveAttachedImage)

        // Configure Edge-To-Edge for status bar / notch & navigation bar
        EdgeToEdgeHelper.setupEdgeToEdge(this, topBar, bottomBar, rvChatHistory)

        rvChatHistory.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChatHistory.adapter = chatAdapter

        btnBackChat.setOnClickListener { finish() }

        btnChatSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

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

        // Configure Instant Send on IME Action (Virtual Keyboard Intro)
        edtChatMessage.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN && !event.isShiftPressed)) {
                val text = edtChatMessage.text.toString().trim()
                if (text.isNotBlank() || attachedImageUri != null) {
                    sendUserQuery(text)
                }
                true
            } else {
                false
            }
        }

        // Configure Hardware Keyboard Enter Key
        edtChatMessage.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN && !event.isShiftPressed) {
                val text = edtChatMessage.text.toString().trim()
                if (text.isNotBlank() || attachedImageUri != null) {
                    sendUserQuery(text)
                }
                true
            } else {
                false
            }
        }

        setupQuickSkillsBar()
        observeChatHistory()
    }

    private fun setupQuickSkillsBar() {
        val btnAllSkills: MaterialButton = findViewById(R.id.btnAllSkills)
        val chipCall: Chip = findViewById(R.id.chipCall)
        val chipWhatsapp: Chip = findViewById(R.id.chipWhatsapp)
        val chipEmail: Chip = findViewById(R.id.chipEmail)
        val chipTelegram: Chip = findViewById(R.id.chipTelegram)
        val chipGoogle: Chip = findViewById(R.id.chipGoogle)
        val chipWiki: Chip = findViewById(R.id.chipWiki)
        val chipWeather: Chip = findViewById(R.id.chipWeather)
        val chipCurrency: Chip = findViewById(R.id.chipCurrency)
        val chipNote: Chip = findViewById(R.id.chipNote)
        val chipReminder: Chip = findViewById(R.id.chipReminder)
        val chipRecorder: Chip = findViewById(R.id.chipRecorder)

        btnAllSkills.setOnClickListener { showSkillsDialog() }
        chipCall.setOnClickListener { insertSkillTemplate("Llamar a ") }
        chipWhatsapp.setOnClickListener { insertSkillTemplate("Enviar whatsapp a ") }
        chipEmail.setOnClickListener { insertSkillTemplate("Enviar email a ") }
        chipTelegram.setOnClickListener { insertSkillTemplate("Enviar telegram a ") }
        chipGoogle.setOnClickListener { insertSkillTemplate("Buscar en Google ") }
        chipWiki.setOnClickListener { insertSkillTemplate("Buscar en Wikipedia ") }
        chipWeather.setOnClickListener { insertSkillTemplate("Clima en ") }
        chipCurrency.setOnClickListener { insertSkillTemplate("Convertir 100 USD a COP") }
        chipNote.setOnClickListener { insertSkillTemplate("Crear nota con titulo: ") }
        chipReminder.setOnClickListener { insertSkillTemplate("Recordar en 30 minutos: ") }
        chipRecorder.setOnClickListener { insertSkillTemplate("Iniciar grabacion de voz IA") }
    }

    private fun insertSkillTemplate(template: String) {
        edtChatMessage.setText(template)
        edtChatMessage.setSelection(template.length)
        edtChatMessage.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.showSoftInput(edtChatMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showSkillsDialog() {
        val skills = SkillRegistry.getAllSkills()
        if (skills.isEmpty()) {
            Toast.makeText(this, "No hay habilidades registradas", Toast.LENGTH_SHORT).show()
            return
        }
        val names = skills.map { "⚡ ${it.name}\n${it.description}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Habilidades Disponibles (Skills)")
            .setItems(names) { _, which ->
                val selectedSkill = skills[which]
                insertSkillTemplate("Usa la habilidad ${selectedSkill.id} para ")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun observeChatHistory() {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@ChatActivity).chatDao()
            dao.getAllMessagesFlow().collectLatest { messages ->
                if (!isFinishing && !isDestroyed) {
                    chatAdapter.submitList(messages)
                    if (messages.isNotEmpty()) {
                        rvChatHistory.smoothScrollToPosition(messages.size - 1)
                    }
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
            val file = File(cacheDir, "chat_attach_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            LogBus.error("ChatActivity -> Error saving attached bitmap", e)
            null
        }
    }

    private fun showImageSourceDialog() {
        AlertDialog.Builder(this)
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
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Habla tu consulta o comando...")
            }
            sttLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Reconocimiento de voz no disponible en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendUserQuery(text: String) {
        val mediaType = if (attachedImageUri != null) "IMAGE" else "TEXT"
        val queryText = if (text.isBlank() && attachedImageUri != null) "[Imagen adjunta]" else text

        edtChatMessage.setText("")
        val imageUriString = attachedImageUri?.toString()
        attachedImageUri = null
        layImagePreview.visibility = View.GONE

        val analyzer = UserProfileAnalyzer.getInstance(this)
        analyzer.recordMessage(currentSessionId, "USER", queryText, mediaType, imageUriString)

        progressChat.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val executor = PhoneActionExecutor(this@ChatActivity)
            val router = VoiceActionRouter(this@ChatActivity, executor)

            val route = router.tryRoute(queryText)
            var responseText = ""
            var sourceName = "CHAT_UI"

            if (route.handled) {
                responseText = route.responseText
                sourceName = route.source.name
            } else {
                val providerId = Prefs.aiProvider(this@ChatActivity)
                val provider = AiProvider.fromId(providerId)
                val apiKey = Prefs.aiApiKey(this@ChatActivity, providerId)
                val model = Prefs.aiModel(this@ChatActivity, providerId)
                val endpoint = Prefs.aiEndpoint(this@ChatActivity, providerId)
                val basePrompt = Prefs.systemPrompt(this@ChatActivity)
                val prompt = basePrompt + SkillRegistry.buildSystemPromptAddendum()
                val client = provider.newClient(this@ChatActivity, apiKey, model, endpoint, prompt)

                if (!client.isConfigured()) {
                    responseText = "El proveedor de IA (${provider.displayName}) no está configurado en Ajustes."
                    sourceName = "ERROR"
                } else {
                    try {
                        val profileContext = analyzer.buildProfilePromptContext()
                        val fullPrompt = profileContext + queryText
                        val rawAnswer = if (!imageUriString.isNullOrBlank()) {
                            val imageBytes = contentResolver.openInputStream(Uri.parse(imageUriString))?.use { it.readBytes() }
                            if (imageBytes != null && imageBytes.isNotEmpty()) {
                                client.askWithImage(fullPrompt, imageBytes)
                            } else {
                                client.ask(fullPrompt)
                            }
                        } else {
                            client.ask(fullPrompt)
                        }
                        val processed = executor.processAndExecute(rawAnswer)
                        val skillProcessed = com.myvu.client.skills.SkillExecutor.processAndExecute(this@ChatActivity, processed)
                        responseText = if (skillProcessed.isNotBlank()) skillProcessed else (rawAnswer ?: "Respuesta vacía de la IA.")
                        sourceName = provider.displayName
                    } catch (e: Exception) {
                        LogBus.error("ChatActivity -> Error querying AI", e)
                        responseText = "Error al consultar la IA: ${e.message}"
                        sourceName = "ERROR"
                    }
                }
            }

            analyzer.recordMessage(currentSessionId, "AI", responseText, "TEXT", sourceName)

            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    progressChat.visibility = View.GONE
                }
            }
        }
    }

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

            if (!msg.actionResult.isNullOrBlank() && (msg.actionResult.startsWith("content://") || msg.actionResult.startsWith("file://"))) {
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
