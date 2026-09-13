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
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.navigation.NavigationView
import com.myvu.client.R
import com.myvu.client.ai.AiProvider
import com.myvu.client.ai.DailyBriefingService
import com.myvu.client.ai.PhoneActionExecutor
import com.myvu.client.ai.VoiceActionRouter
import com.myvu.client.core.EdgeToEdgeHelper
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.core.TextToSpeechHelper
import com.myvu.client.data.ChatMessage
import com.myvu.client.data.UserProfileAnalyzer
import com.myvu.client.database.AppDatabase
import com.myvu.client.database.VoiceRecording
import com.myvu.client.skills.SkillRegistry
import com.myvu.client.ui.ActivityLogActivity
import com.myvu.client.ui.ConnectActivity
import com.myvu.client.ui.DeviceManagementBottomSheet
import com.myvu.client.ui.GlassesSettingsActivity
import com.myvu.client.ui.HeadphoneSettingsActivity
import com.myvu.client.ui.NotesActivity
import com.myvu.client.ui.NotificationAppsActivity
import com.myvu.client.ui.SettingsActivity
import com.myvu.client.ui.TrackpadActivity
import com.myvu.client.ui.VoiceRecorderActivity
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
    private lateinit var chatDrawerLayout: DrawerLayout
    private lateinit var btnNavigationDrawer: ImageButton
    private lateinit var chatNavigationView: NavigationView
    private lateinit var btnQuickAiNotes: MaterialButton
    private lateinit var btnQuickRecordMeeting: MaterialButton
    private lateinit var btnQuickDailyBriefing: MaterialButton
    private lateinit var btnQuickTasks: MaterialButton
    private lateinit var btnQuickDevicesShortcut: MaterialButton
    private lateinit var btnDevices: MaterialButton
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
    private var speakNextResponse: Boolean = false

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
        com.myvu.client.core.LockScreenHelper.setupShowWhenLocked(this)
        setContentView(R.layout.activity_chat)

        if (intent?.getBooleanExtra(EXTRA_AUTO_START_STT, false) == true) {
            handleAutoStartStt()
        }

        chatDrawerLayout = findViewById(R.id.chatDrawerLayout)
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        btnNavigationDrawer = findViewById(R.id.btnNavigationDrawer)
        chatNavigationView = findViewById(R.id.chatNavigationView)
        btnQuickAiNotes = findViewById(R.id.btnQuickAiNotes)
        btnQuickRecordMeeting = findViewById(R.id.btnQuickRecordMeeting)
        btnQuickDailyBriefing = findViewById(R.id.btnQuickDailyBriefing)
        btnQuickTasks = findViewById(R.id.btnQuickTasks)
        btnQuickDevicesShortcut = findViewById(R.id.btnQuickDevicesShortcut)
        btnDevices = findViewById(R.id.btnDevices)
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

        // Navigation drawer trigger
        btnNavigationDrawer.setOnClickListener {
            chatDrawerLayout.openDrawer(GravityCompat.START)
        }

        // Handle back press to close drawer if open
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (chatDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                    chatDrawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Navigation Drawer Item Click Handler
        chatNavigationView.setNavigationItemSelectedListener { menuItem ->
            chatDrawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_chat_sidebar -> {
                    // Already in Chat
                }
                R.id.nav_notes -> {
                    startActivity(Intent(this, NotesActivity::class.java))
                }
                R.id.nav_voice_recorder -> {
                    startActivity(Intent(this, VoiceRecorderActivity::class.java))
                }
                R.id.nav_devices -> {
                    DeviceManagementBottomSheet.show(supportFragmentManager)
                }
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, ConnectActivity::class.java))
                }
                R.id.nav_ai_config -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                R.id.nav_trackpad -> {
                    startActivity(Intent(this, TrackpadActivity::class.java))
                }
                R.id.nav_notifications -> {
                    startActivity(Intent(this, NotificationAppsActivity::class.java))
                }
                R.id.nav_logs -> {
                    startActivity(Intent(this, ActivityLogActivity::class.java))
                }
                R.id.nav_total_disconnect -> {
                    com.myvu.client.core.TotalDisconnectHelper.performTotalDisconnect(this)
                }
            }
            true
        }

        // Quick AI Action Buttons
        btnQuickAiNotes.setOnClickListener {
            startActivity(Intent(this, NotesActivity::class.java))
        }

        btnQuickRecordMeeting.setOnClickListener {
            val intent = Intent(this, VoiceRecorderActivity::class.java).apply {
                putExtra("CATEGORY", VoiceRecording.CATEGORY_MEETING)
                putExtra("AUTO_START_RECORDING", true)
            }
            startActivity(intent)
        }

        btnQuickDailyBriefing.setOnClickListener {
            val briefingText = DailyBriefingService.generateBriefingText(this)
            TextToSpeechHelper.speak(briefingText)
            lifecycleScope.launch(Dispatchers.IO) {
                UserProfileAnalyzer.getInstance(this@ChatActivity).recordMessage(
                    currentSessionId,
                    "AI",
                    "☀️ **Resumen de tu Día (Briefing)**:\n\n$briefingText",
                    "TEXT",
                    "DAILY_BRIEFING"
                )
            }
        }

        btnQuickTasks.setOnClickListener {
            val intent = Intent(this, NotesActivity::class.java).apply {
                putExtra("PAGE", "REMINDERS")
            }
            startActivity(intent)
        }

        btnQuickDevicesShortcut.setOnClickListener {
            DeviceManagementBottomSheet.show(supportFragmentManager)
        }

        // Configure Edge-To-Edge for status bar / notch & navigation bar
        val cupertinoTabBar: View? = findViewById(R.id.cupertinoTabBar)
        EdgeToEdgeHelper.setupEdgeToEdge(this, topBar, bottomBar, rvChatHistory, cupertinoTabBar)

        rvChatHistory.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChatHistory.adapter = chatAdapter

        btnDevices.setOnClickListener {
            DeviceManagementBottomSheet.show(supportFragmentManager)
        }

        // Observe active Bluetooth device and update UI & Drawer Header
        val devManager = com.myvu.client.service.BluetoothDeviceManager.getInstance(this)
        devManager.syncPairedDevices()
        lifecycleScope.launch {
            devManager.activeDevice.collectLatest { dev ->
                val headerView = chatNavigationView.getHeaderView(0)
                val txtHeaderTitle = headerView?.findViewById<TextView>(R.id.txtHeaderTitle)
                val txtHeaderStatus = headerView?.findViewById<TextView>(R.id.txtHeaderStatus)

                if (dev != null && dev.isConnected) {
                    btnDevices.text = dev.name.take(12)
                    if (dev.deviceType == com.myvu.client.data.BluetoothDeviceType.HEADPHONES.name) {
                        btnDevices.setIconResource(R.drawable.ic_headphones)
                    } else if (dev.deviceType == com.myvu.client.data.BluetoothDeviceType.SMART_GLASSES.name) {
                        btnDevices.setIconResource(R.drawable.ic_glasses)
                    } else {
                        btnDevices.setIconResource(R.drawable.ic_bluetooth_device)
                    }
                    txtHeaderTitle?.text = dev.name
                    txtHeaderStatus?.text = "Conectado • ${dev.deviceType}"
                } else {
                    btnDevices.text = "Dispositivos"
                    btnDevices.setIconResource(R.drawable.ic_bluetooth_device)
                    txtHeaderTitle?.text = "Aura • Agente IA"
                    txtHeaderStatus?.text = "Sin dispositivo conectado"
                }
            }
        }

        // Dynamic Wearables Carousel Widgets Update via Room Database Flow
        lifecycleScope.launch {
            devManager.getAllDevicesFlow().collectLatest { devices ->
                val txtGlassesStatus: TextView? = findViewById(R.id.txtGlassesStatus)
                val txtGlassesBattery: TextView? = findViewById(R.id.txtGlassesBattery)
                val txtHeadphonesStatus: TextView? = findViewById(R.id.txtHeadphonesStatus)
                val txtHeadphonesBattery: TextView? = findViewById(R.id.txtHeadphonesBattery)
                val txtWearablesCount: TextView? = findViewById(R.id.txtWearablesCount)

                val glasses = devices.find { it.deviceType == com.myvu.client.data.BluetoothDeviceType.SMART_GLASSES.name }
                val headphones = devices.find { it.deviceType == com.myvu.client.data.BluetoothDeviceType.HEADPHONES.name }

                if (glasses != null && glasses.isConnected) {
                    txtGlassesStatus?.text = "Conectadas"
                    val liveBatt = glasses.batteryLevel 
                        ?: com.myvu.client.service.MyvuService.activeConnection()?.glassesInfo()?.battery?.takeIf { it in 0..100 }
                    txtGlassesBattery?.text = if (liveBatt != null) "$liveBatt%" else "--"
                } else {
                    txtGlassesStatus?.text = "Desconectadas"
                    txtGlassesBattery?.text = "--"
                }

                if (headphones != null && headphones.isConnected) {
                    txtHeadphonesStatus?.text = "Conectados"
                    val liveBatt = headphones.batteryLevel?.takeIf { it in 0..100 }
                    txtHeadphonesBattery?.text = if (liveBatt != null) "$liveBatt%" else "--"
                } else {
                    txtHeadphonesStatus?.text = "Desconectados"
                    txtHeadphonesBattery?.text = "--"
                }
                val totalConnected = devices.count { it.isConnected }
                txtWearablesCount?.text = "$totalConnected Activo(s)"
            }
        }

        // Theme Toggle (Light / Dark Mode)
        findViewById<ImageButton>(R.id.btnThemeToggle)?.let { btnTheme ->
            btnTheme.setOnClickListener {
                val current = Prefs.themeMode(this)
                val isCurrentlyDark = current == Prefs.THEME_MODE_DARK ||
                    (current == Prefs.THEME_MODE_SYSTEM && (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES)
                val next = if (isCurrentlyDark) Prefs.THEME_MODE_LIGHT else Prefs.THEME_MODE_DARK
                Prefs.setThemeMode(this, next)
                Toast.makeText(this, if (next == Prefs.THEME_MODE_DARK) "Modo Oscuro" else "Modo Claro", Toast.LENGTH_SHORT).show()
                recreate()
            }
        }

        // Wearable Widgets click actions
        findViewById<View>(R.id.cardWearableGlasses)?.setOnClickListener {
            lifecycleScope.launch {
                val devices = withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(this@ChatActivity).bluetoothDeviceDao().getAllDevices()
                }
                val glassesDev = devices.find { it.deviceType == com.myvu.client.data.BluetoothDeviceType.SMART_GLASSES.name }
                    ?: devices.find { it.name.contains("MYVU", ignoreCase = true) }
                val mac = glassesDev?.macAddress?.takeIf { it.isNotBlank() } ?: com.myvu.client.core.Prefs.targetMac(this@ChatActivity)
                GlassesSettingsActivity.start(
                    this@ChatActivity,
                    mac,
                    glassesDev?.name ?: "Gafas Meizu MYVU"
                )
            }
        }
        findViewById<View>(R.id.cardWearableHeadphones)?.setOnClickListener {
            lifecycleScope.launch {
                val devices = withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(this@ChatActivity).bluetoothDeviceDao().getAllDevices()
                }
                val headphonesDev = devices.find { it.deviceType == com.myvu.client.data.BluetoothDeviceType.HEADPHONES.name }
                HeadphoneSettingsActivity.start(this@ChatActivity, headphonesDev?.macAddress)
            }
        }
        findViewById<View>(R.id.cardWearablePair)?.setOnClickListener {
            DeviceManagementBottomSheet.show(supportFragmentManager)
        }

        // Cupertino 5-Tab Bar Click Handlers
        findViewById<View>(R.id.tabHub)?.setOnClickListener {
            if (chatAdapter.itemCount > 0) {
                rvChatHistory.smoothScrollToPosition(chatAdapter.itemCount - 1)
            }
        }
        findViewById<View>(R.id.tabDevices)?.setOnClickListener {
            DeviceManagementBottomSheet.show(supportFragmentManager)
        }
        findViewById<View>(R.id.tabArStudio)?.setOnClickListener {
            startActivity(Intent(this, TrackpadActivity::class.java))
        }
        findViewById<View>(R.id.tabTranslate)?.setOnClickListener {
            insertSkillTemplate("Traduce a español: ")
        }
        findViewById<View>(R.id.tabNotes)?.setOnClickListener {
            startActivity(Intent(this, NotesActivity::class.java))
        }

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

        // Configure Instant Send on IME Action & Keyboard Enter
        edtChatMessage.setOnEditorActionListener { _, actionId, event ->
            val isSendAction = actionId == EditorInfo.IME_ACTION_SEND ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_NULL
            val isEnterKey = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN

            if (isSendAction || isEnterKey) {
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
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                val text = edtChatMessage.text.toString().trim()
                if (text.isNotBlank() || attachedImageUri != null) {
                    sendUserQuery(text)
                }
                true
            } else {
                false
            }
        }

        // TextWatcher safety fallback: detect '\n' typed on any Android soft keyboard
        edtChatMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null && s.contains("\n")) {
                    val cleanText = s.toString().replace("\n", "").trim()
                    edtChatMessage.setText("")
                    if (cleanText.isNotBlank() || attachedImageUri != null) {
                        sendUserQuery(cleanText)
                    }
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.getBooleanExtra(EXTRA_AUTO_START_STT, false) == true) {
            handleAutoStartStt()
        }
    }

    override fun onResume() {
        super.onResume()
        com.myvu.client.service.BluetoothDeviceManager.getInstance(this).refreshAllDeviceBatteries()
    }

    private fun handleAutoStartStt() {
        speakNextResponse = true
        launchVoiceStt()
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
                        val rawAnswer = if (client.supportsToolCalling() && imageUriString.isNullOrBlank()) {
                            val agenticExecutor = com.myvu.client.ai.AgenticToolExecutor(this@ChatActivity, client)
                            val agenticResult = agenticExecutor.execute(
                                userQuery = fullPrompt,
                                systemPrompt = basePrompt
                            )
                            agenticResult.finalAnswer
                        } else if (!imageUriString.isNullOrBlank()) {
                            val imageBytes = contentResolver.openInputStream(Uri.parse(imageUriString))?.use { it.readBytes() }
                            if (imageBytes != null && imageBytes.isNotEmpty()) {
                                client.askWithImage(fullPrompt, imageBytes)
                            } else {
                                client.ask(fullPrompt)
                            }
                        } else {
                            client.ask(fullPrompt)
                        }

                        val processed = if (!client.supportsToolCalling()) {
                            val legacyProcessed = executor.processAndExecute(rawAnswer)
                            com.myvu.client.skills.SkillExecutor.processAndExecute(this@ChatActivity, legacyProcessed)
                        } else {
                            rawAnswer
                        }

                        responseText = if (processed.isNotBlank()) processed else (rawAnswer ?: "Respuesta vacía de la IA.")
                        sourceName = provider.displayName
                    } catch (e: Exception) {
                        LogBus.error("ChatActivity -> Error querying AI", e)
                        responseText = "Error al consultar la IA: ${e.message}"
                        sourceName = "ERROR"
                    }
                }
            }

            analyzer.recordMessage(currentSessionId, "AI", responseText, "TEXT", sourceName)

            if (speakNextResponse) {
                speakNextResponse = false
                TextToSpeechHelper.speak(responseText)
            }

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
        private val layMessageRoot: LinearLayout = itemView.findViewById(R.id.layMessageRoot)
        private val layMessageRow: LinearLayout = itemView.findViewById(R.id.layMessageRow)
        private val imgAiAvatar: ImageView = itemView.findViewById(R.id.imgAiAvatar)
        private val bubbleContainer: LinearLayout = itemView.findViewById(R.id.bubbleContainer)
        private val txtMessageContent: TextView = itemView.findViewById(R.id.txtMessageContent)
        private val txtMessageTime: TextView = itemView.findViewById(R.id.txtMessageTime)
        private val txtMessageSource: TextView = itemView.findViewById(R.id.txtMessageSource)
        private val imgMessageAttached: ImageView = itemView.findViewById(R.id.imgMessageAttached)

        fun bind(msg: ChatMessage) {
            val context = itemView.context
            txtMessageContent.text = msg.content
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
            txtMessageTime.text = timeStr
            txtMessageSource.text = msg.actionResult ?: if (msg.direction == "USER") "Tú" else "Companion AI"

            val isUser = "USER" == msg.direction
            if (isUser) {
                layMessageRoot.gravity = android.view.Gravity.END
                layMessageRow.gravity = android.view.Gravity.END
                imgAiAvatar.visibility = View.GONE
                bubbleContainer.setBackgroundResource(R.drawable.bg_ios_bubble_user)
                txtMessageContent.setTextColor(android.graphics.Color.WHITE)
                txtMessageTime.setTextColor(android.graphics.Color.parseColor("#B3FFFFFF"))
                txtMessageSource.setTextColor(android.graphics.Color.WHITE)
            } else {
                layMessageRoot.gravity = android.view.Gravity.START
                layMessageRow.gravity = android.view.Gravity.START
                imgAiAvatar.visibility = View.VISIBLE
                bubbleContainer.setBackgroundResource(R.drawable.bg_ios_bubble_ai)
                txtMessageContent.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.ios_label))
                txtMessageTime.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.ios_secondary_label))
                txtMessageSource.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.ios_blue))
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

    companion object {
        const val EXTRA_AUTO_START_STT: String = "EXTRA_AUTO_START_STT"
    }
}
