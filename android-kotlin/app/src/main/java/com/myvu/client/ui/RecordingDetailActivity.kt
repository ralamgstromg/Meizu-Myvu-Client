package com.myvu.client.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import com.myvu.client.R
import com.myvu.client.ai.MeetingAiProcessor
import com.myvu.client.core.DocumentExtractor
import com.myvu.client.core.EdgeToEdgeHelper
import com.myvu.client.core.LogBus
import com.myvu.client.core.MindMapVisualizerHelper
import com.myvu.client.core.setMarkdown
import com.myvu.client.database.Attachment
import com.myvu.client.database.AttachmentType
import com.myvu.client.database.TodoRepository
import com.myvu.client.database.VoiceRecording
import com.myvu.client.database.VoiceRecordingRepository
import com.myvu.client.recorder.AudioPlayerManager
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RecordingDetailActivity : AppCompatActivity() {

    private var recordingId: Long = -1L
    private var recording: VoiceRecording? = null
    private lateinit var repository: VoiceRecordingRepository
    private lateinit var aiProcessor: MeetingAiProcessor
    private val playerManager = AudioPlayerManager()

    // Attachments
    private var pendingPhotoFile: File? = null
    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingPhotoFile != null && pendingPhotoFile!!.exists() && pendingPhotoFile!!.length() > 0) {
            lifecycleScope.launch {
                val att = DocumentExtractor.processPhotoFile(this@RecordingDetailActivity, pendingPhotoFile!!)
                addAttachment(att)
            }
        }
    }

    private val pickDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                try {
                    val att = DocumentExtractor.processUriAttachment(this@RecordingDetailActivity, uri)
                    addAttachment(att)
                } catch (e: Exception) {
                    Toast.makeText(this@RecordingDetailActivity, "Error al procesar archivo: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Views
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvDetailTitle: TextView
    private lateinit var tvDetailDate: TextView
    private lateinit var tvDetailTags: TextView
    private lateinit var playerSeekBar: SeekBar
    private lateinit var tvCurrentPosition: TextView
    private lateinit var tvTotalDuration: TextView
    private lateinit var btnMainPlayPause: MaterialButton
    private lateinit var btnRewind5s: MaterialButton
    private lateinit var btnForward5s: MaterialButton
    private lateinit var btnSpeedToggle: MaterialButton
    private lateinit var tabsDetail: TabLayout

    // Processing banner
    private lateinit var layProcessingBanner: LinearLayout
    private lateinit var tvProcessingStatus: TextView

    // Tab Views
    private lateinit var viewTabTranscript: View
    private lateinit var viewTabSummary: View
    private lateinit var viewTabTasks: View
    private lateinit var viewTabMindmap: View
    private lateinit var viewTabQa: View

    // Content Views
    private lateinit var layDiarizedContainer: LinearLayout
    private lateinit var tvRawTranscript: TextView
    private lateinit var btnRecordingTakePhoto: MaterialButton
    private lateinit var btnRecordingAttachFile: MaterialButton
    private lateinit var layRecordingAttachmentsSection: LinearLayout
    private lateinit var tvRecordingAttachmentsHeader: TextView
    private lateinit var layRecordingAttachmentsList: LinearLayout
    private lateinit var tvSummaryContent: TextView
    private lateinit var btnAddNewManualTask: MaterialButton
    private lateinit var btnExportAllTasksToTodo: MaterialButton
    private lateinit var layMeetingTasksContainer: LinearLayout

    // Mind Map Views
    private lateinit var wvInteractiveMindMap: WebView
    private lateinit var scrollMindmapText: ScrollView
    private lateinit var tvMindmapContent: TextView
    private lateinit var btnToggleMindMapView: MaterialButton
    private var isMindMapGraphMode = true

    // QA Chat
    private lateinit var layQaChatMessages: LinearLayout
    private lateinit var scrollQaChat: ScrollView
    private lateinit var etQaQuestion: EditText
    private lateinit var btnSendQaQuestion: MaterialButton

    private var currentSpeedIndex = 0
    private val speedOptions = floatArrayOf(1.0f, 1.25f, 1.5f, 2.0f)
    private val speedLabels = arrayOf("1.0x", "1.25x", "1.5x", "2.0x")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_recording_detail)

            recordingId = intent.getLongExtra(EXTRA_RECORDING_ID, -1L)
            if (recordingId == -1L) {
                Toast.makeText(this, "ID de grabación inválido", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            repository = VoiceRecordingRepository(this)
            aiProcessor = MeetingAiProcessor(this)

            bindViews()
            EdgeToEdgeHelper.setupEdgeToEdge(this, toolbar)
            setupPlayer()
            setupTabs()
            setupTopBarActions()
            setupAttachmentsUi()
            setupMindMapToggle()
            setupQaChat()

            loadRecordingData()

            val autoProcess = intent.getBooleanExtra(EXTRA_AUTO_PROCESS, false)
            if (autoProcess || (recording?.summary.isNullOrBlank() && recording?.rawTranscript.isNullOrBlank())) {
                startAiProcessing()
            }
        } catch (e: Throwable) {
            LogBus.error("RecordingDetailActivity: Fatal error in onCreate", e)
            Toast.makeText(this, "Error al abrir la grabación: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun bindViews() {
        toolbar = findViewById(R.id.toolbarDetail)
        tvDetailTitle = findViewById(R.id.tvDetailTitle)
        tvDetailDate = findViewById(R.id.tvDetailDate)
        tvDetailTags = findViewById(R.id.tvDetailTags)

        playerSeekBar = findViewById(R.id.seekBarAudio)
        tvCurrentPosition = findViewById(R.id.tvCurrentPosition)
        tvTotalDuration = findViewById(R.id.tvTotalDuration)
        btnMainPlayPause = findViewById(R.id.btnMainPlayPause)
        btnRewind5s = findViewById(R.id.btnRewind5s)
        btnForward5s = findViewById(R.id.btnForward5s)
        btnSpeedToggle = findViewById(R.id.btnSpeedToggle)
        tabsDetail = findViewById(R.id.tabLayoutDetail)

        layProcessingBanner = findViewById(R.id.layProcessingBanner)
        tvProcessingStatus = findViewById(R.id.tvProcessingStatus)

        viewTabTranscript = findViewById(R.id.viewTabTranscript)
        viewTabSummary = findViewById(R.id.viewTabSummary)
        viewTabTasks = findViewById(R.id.viewTabTasks)
        viewTabMindmap = findViewById(R.id.viewTabMindmap)
        viewTabQa = findViewById(R.id.viewTabQa)

        layDiarizedContainer = findViewById(R.id.layDiarizedContainer)
        tvRawTranscript = findViewById(R.id.tvRawTranscript)
        btnRecordingTakePhoto = findViewById(R.id.btnRecordingTakePhoto)
        btnRecordingAttachFile = findViewById(R.id.btnRecordingAttachFile)
        layRecordingAttachmentsSection = findViewById(R.id.layRecordingAttachmentsSection)
        tvRecordingAttachmentsHeader = findViewById(R.id.tvRecordingAttachmentsHeader)
        layRecordingAttachmentsList = findViewById(R.id.layRecordingAttachmentsList)

        tvSummaryContent = findViewById(R.id.tvSummaryContent)
        btnAddNewManualTask = findViewById(R.id.btnAddNewManualTask)
        btnExportAllTasksToTodo = findViewById(R.id.btnExportAllTasksToTodo)
        layMeetingTasksContainer = findViewById(R.id.layMeetingTasksContainer)

        wvInteractiveMindMap = findViewById(R.id.wvInteractiveMindMap)
        scrollMindmapText = findViewById(R.id.scrollMindmapText)
        tvMindmapContent = findViewById(R.id.tvMindmapContent)
        btnToggleMindMapView = findViewById(R.id.btnToggleMindMapView)

        scrollQaChat = findViewById(R.id.scrollQaChat)
        layQaChatMessages = findViewById(R.id.layQaChatMessages)
        etQaQuestion = findViewById(R.id.etQaQuestion)
        btnSendQaQuestion = findViewById(R.id.btnSendQaQuestion)
    }

    private fun loadRecordingData() {
        try {
            recording = repository.getRecordingById(recordingId)
            val rec = recording ?: run {
                Toast.makeText(this, "Grabación no encontrada", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            val displayTitle = rec.title.ifBlank { "Grabación #${rec.id}" }
            toolbar.title = displayTitle
            tvDetailTitle.text = displayTitle
            toolbar.subtitle = "${rec.category} • ${rec.formattedDuration()}"
            tvDetailDate.text = rec.formattedDate()
            tvDetailTags.text = if (rec.tags.isNotBlank()) "🏷️ ${rec.tags}" else "🏷️ Sin tags"
            tvTotalDuration.text = rec.formattedDuration()

            // Populate Transcript
            populateTranscript(rec)

            // Populate Summary with Markdown
            val summaryText = if (rec.summary.isNotBlank()) {
                rec.summary
            } else {
                "*Aún no se ha generado un resumen.*\n\nToca el botón ✨ arriba a la derecha para procesar la grabación con IA."
            }
            tvSummaryContent.setMarkdown(summaryText)

            // Populate Tasks
            populateTasks(rec)

            // Populate Mind Map
            loadMindMap(rec)

            // Populate Attachments
            renderAttachments()
        } catch (e: Exception) {
            LogBus.error("RecordingDetailActivity: Error loading recording data", e)
        }
    }

    private fun setupAttachmentsUi() {
        btnRecordingTakePhoto.setOnClickListener {
            try {
                val photosDir = File(getExternalFilesDir(null), "attachments").apply { mkdirs() }
                val photoFile = File(photosDir, "IMG_${System.currentTimeMillis()}.jpg")
                pendingPhotoFile = photoFile
                val photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
                takePhotoLauncher.launch(photoUri)
            } catch (e: Exception) {
                Toast.makeText(this, "Error al abrir cámara: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnRecordingAttachFile.setOnClickListener {
            val mimeTypes = arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "text/plain",
                "text/markdown",
                "text/csv",
                "application/json",
                "image/*"
            )
            pickDocumentLauncher.launch(mimeTypes)
        }
    }

    private fun addAttachment(attachment: Attachment) {
        val rec = recording ?: return
        val currentList = rec.getAttachments().toMutableList()
        currentList.add(attachment)
        rec.attachmentsJson = Attachment.listToJson(currentList)
        repository.updateAttachments(rec.id, rec.attachmentsJson)
        Toast.makeText(this, "Adjunto añadido: ${attachment.fileName}", Toast.LENGTH_SHORT).show()
        renderAttachments()
    }

    private fun removeAttachment(attachment: Attachment) {
        val rec = recording ?: return
        AlertDialog.Builder(this)
            .setTitle("Eliminar Adjunto")
            .setMessage("¿Deseas eliminar '${attachment.fileName}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                val currentList = rec.getAttachments().toMutableList()
                currentList.removeAll { it.id == attachment.id }
                rec.attachmentsJson = Attachment.listToJson(currentList)
                repository.updateAttachments(rec.id, rec.attachmentsJson)
                renderAttachments()
                Toast.makeText(this, "Adjunto eliminado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun renderAttachments() {
        val rec = recording ?: return
        val attachments = rec.getAttachments()
        if (attachments.isEmpty()) {
            layRecordingAttachmentsSection.visibility = View.GONE
            return
        }
        layRecordingAttachmentsSection.visibility = View.VISIBLE
        tvRecordingAttachmentsHeader.text = "📎 Archivos y Fotos Adjuntas (${attachments.size}):"
        layRecordingAttachmentsList.removeAllViews()

        for (att in attachments) {
            val cardView = layoutInflater.inflate(R.layout.item_attachment_card, layRecordingAttachmentsList, false)
            val tvIcon = cardView.findViewById<TextView>(R.id.tvAttachmentIcon)
            val ivThumb = cardView.findViewById<ImageView>(R.id.ivAttachmentThumb)
            val tvName = cardView.findViewById<TextView>(R.id.tvAttachmentName)
            val tvSize = cardView.findViewById<TextView>(R.id.tvAttachmentSize)
            val btnRemove = cardView.findViewById<ImageButton>(R.id.btnRemoveAttachment)

            tvName.text = att.fileName
            val sizeKb = att.fileSizeBytes / 1024
            tvSize.text = "$sizeKb KB • ${att.fileType.name}"

            when (att.fileType) {
                AttachmentType.IMAGE -> {
                    tvIcon.visibility = View.GONE
                    ivThumb.visibility = View.VISIBLE
                    if (att.thumbnailPath != null && File(att.thumbnailPath).exists()) {
                        ivThumb.setImageURI(Uri.fromFile(File(att.thumbnailPath)))
                    } else if (File(att.filePath).exists()) {
                        ivThumb.setImageURI(Uri.fromFile(File(att.filePath)))
                    }
                }
                AttachmentType.PDF -> {
                    if (att.thumbnailPath != null && File(att.thumbnailPath).exists()) {
                        tvIcon.visibility = View.GONE
                        ivThumb.visibility = View.VISIBLE
                        ivThumb.setImageURI(Uri.fromFile(File(att.thumbnailPath)))
                    } else {
                        tvIcon.text = "📄"
                        tvIcon.visibility = View.VISIBLE
                        ivThumb.visibility = View.GONE
                    }
                }
                AttachmentType.WORD -> {
                    tvIcon.text = "📘"
                    tvIcon.visibility = View.VISIBLE
                    ivThumb.visibility = View.GONE
                }
                AttachmentType.EXCEL -> {
                    tvIcon.text = "📊"
                    tvIcon.visibility = View.VISIBLE
                    ivThumb.visibility = View.GONE
                }
                AttachmentType.TEXT -> {
                    tvIcon.text = "📝"
                    tvIcon.visibility = View.VISIBLE
                    ivThumb.visibility = View.GONE
                }
                AttachmentType.OTHER -> {
                    tvIcon.text = "📎"
                    tvIcon.visibility = View.VISIBLE
                    ivThumb.visibility = View.GONE
                }
            }

            btnRemove.setOnClickListener {
                removeAttachment(att)
            }

            cardView.setOnClickListener {
                showAttachmentDetailsDialog(att)
            }

            layRecordingAttachmentsList.addView(cardView)
        }
    }

    private fun showAttachmentDetailsDialog(att: Attachment) {
        val snippet = att.extractedText.ifBlank { "(Sin texto extraído o formato binario)" }
        val sizeKb = att.fileSizeBytes / 1024

        val msg = "📁 Archivo: ${att.fileName}\n" +
                "📦 Tipo: ${att.fileType.name} ($sizeKb KB)\n\n" +
                "🔍 Contenido extraído para IA:\n" +
                (if (snippet.length > 800) snippet.substring(0, 800) + "...\n[Texto completo disponible para IA]" else snippet)

        AlertDialog.Builder(this)
            .setTitle("Detalle del Adjunto")
            .setMessage(msg)
            .setPositiveButton("Preguntar en Chat IA") { _, _ ->
                tabsDetail.getTabAt(4)?.select()
                etQaQuestion.setText("Explica o resume el archivo ${att.fileName}")
            }
            .setNeutralButton("Eliminar") { _, _ ->
                removeAttachment(att)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun setupTopBarActions() {
        toolbar.setNavigationOnClickListener { finish() }

        // Click to rename title
        findViewById<View>(R.id.btnEditRecordingTitle)?.setOnClickListener { showEditTitleDialog() }
        tvDetailTitle.setOnClickListener { showEditTitleDialog() }

        findViewById<View>(R.id.btnReAnalyzeAi)?.setOnClickListener {
            startAiProcessing()
        }

        findViewById<View>(R.id.btnSendToGlasses)?.setOnClickListener {
            sendSummaryToGlasses()
        }

        findViewById<View>(R.id.btnShareRecording)?.setOnClickListener {
            shareRecordingSummary()
        }

        findViewById<View>(R.id.btnDeleteRecording)?.setOnClickListener {
            confirmDelete()
        }
    }

    private fun showEditTitleDialog() {
        val rec = recording ?: return
        val input = EditText(this).apply {
            setText(rec.title)
            setSelection(rec.title.length)
            setHint("Nombre de la grabación...")
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_obsidian))
            setHintTextColor(ContextCompat.getColor(context, R.color.outline_obsidian))
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Renombrar Grabación")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotBlank()) {
                    rec.title = newTitle
                    repository.updateRecording(rec)
                    toolbar.title = newTitle
                    tvDetailTitle.text = newTitle
                    Toast.makeText(this, "Título actualizado", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun populateTranscript(rec: VoiceRecording) {
        layDiarizedContainer.removeAllViews()

        if (rec.diarizedTranscript.isNotBlank()) {
            try {
                val array = JSONArray(rec.diarizedTranscript)
                if (array.length() > 0) {
                    val inflater = LayoutInflater.from(this)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val speaker = obj.optString("speaker", "Hablante")
                        val text = obj.optString("text", "")
                        val time = obj.optString("time", "")

                        val itemView = inflater.inflate(R.layout.item_diarized_utterance, layDiarizedContainer, false)
                        val tvSpeaker: TextView = itemView.findViewById(R.id.tvSpeakerName)
                        val tvTime: TextView = itemView.findViewById(R.id.tvSpeakerTime)
                        val tvText: TextView = itemView.findViewById(R.id.tvSpeakerText)

                        tvSpeaker.text = "👤 $speaker"
                        tvTime.text = time
                        tvText.setMarkdown(text)

                        layDiarizedContainer.addView(itemView)
                    }
                    return
                }
            } catch (e: Exception) {
                LogBus.warn("RecordingDetailActivity: Error parsing diarization JSON: ${e.message}")
            }
        }

        // Fallback to raw text with markdown
        val rawTv = TextView(this).apply {
            setMarkdown(if (rec.rawTranscript.isNotBlank()) rec.rawTranscript else "*Sin transcripción disponible.*")
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_obsidian))
            textSize = 15f
            setLineSpacing(0f, 1.3f)
            setTextIsSelectable(true)
            setPadding(0, 8, 0, 0)
        }
        layDiarizedContainer.addView(rawTv)
    }

    // ==========================================
    // TASK CRUD (Create, Read, Update, Delete)
    // ==========================================
    private fun populateTasks(rec: VoiceRecording) {
        layMeetingTasksContainer.removeAllViews()
        val json = rec.actionItems

        btnAddNewManualTask.setOnClickListener {
            showTaskEditDialog(null, -1)
        }

        if (json.isBlank() || json == "[]") {
            val emptyTv = TextView(this).apply {
                text = "No hay tareas registradas. Toca '+ Nueva Tarea' para crear una o ejecuta el análisis con IA."
                setTextColor(ContextCompat.getColor(context, R.color.outline_obsidian))
                setPadding(0, 16, 0, 0)
                textSize = 14f
            }
            layMeetingTasksContainer.addView(emptyTv)
            btnExportAllTasksToTodo.visibility = View.GONE
            return
        }

        btnExportAllTasksToTodo.visibility = View.VISIBLE
        btnExportAllTasksToTodo.setOnClickListener {
            val count = aiProcessor.exportActionItemsToTodos(rec.actionItems)
            Toast.makeText(this, "✅ $count tareas exportadas a Mis Tareas", Toast.LENGTH_SHORT).show()
        }

        try {
            val array = JSONArray(json)
            val inflater = LayoutInflater.from(this)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val task = obj.optString("task", "")
                val owner = obj.optString("owner", "")
                val deadline = obj.optString("deadline", "")
                val completed = obj.optBoolean("completed", false)

                val itemView = inflater.inflate(R.layout.item_meeting_task, layMeetingTasksContainer, false)
                val cbTask: CheckBox = itemView.findViewById(R.id.cbMeetingTask)
                val tvTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
                val tvOwner: TextView = itemView.findViewById(R.id.tvTaskOwner)
                val tvDeadline: TextView = itemView.findViewById(R.id.tvTaskDeadline)
                val btnEdit: MaterialButton = itemView.findViewById(R.id.btnEditSingleTask)
                val btnAddSingle: MaterialButton = itemView.findViewById(R.id.btnExportSingleTask)
                val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDeleteSingleTask)

                tvTitle.text = task
                cbTask.isChecked = completed
                if (completed) {
                    tvTitle.paint.isStrikeThruText = true
                    tvTitle.alpha = 0.5f
                } else {
                    tvTitle.paint.isStrikeThruText = false
                    tvTitle.alpha = 1.0f
                }

                // Checkbox toggle state
                cbTask.setOnCheckedChangeListener { _, isChecked ->
                    obj.put("completed", isChecked)
                    saveTasksArray(array)
                    if (isChecked) {
                        tvTitle.paint.isStrikeThruText = true
                        tvTitle.alpha = 0.5f
                    } else {
                        tvTitle.paint.isStrikeThruText = false
                        tvTitle.alpha = 1.0f
                    }
                }

                if (owner.isNotBlank()) {
                    tvOwner.text = "👤 $owner"
                    tvOwner.visibility = View.VISIBLE
                } else {
                    tvOwner.visibility = View.GONE
                }

                if (deadline.isNotBlank()) {
                    tvDeadline.text = "📅 $deadline"
                    tvDeadline.visibility = View.VISIBLE
                } else {
                    tvDeadline.visibility = View.GONE
                }

                btnEdit.setOnClickListener {
                    showTaskEditDialog(obj, i)
                }

                btnAddSingle.setOnClickListener {
                    val todoRepo = TodoRepository(this)
                    todoRepo.createTodo(listName = "Reuniones", title = task, tags = "reunion,voz")
                    Toast.makeText(this, "Tarea añadida a To-Do", Toast.LENGTH_SHORT).show()
                }

                btnDelete.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Eliminar Tarea")
                        .setMessage("¿Eliminar '$task'?")
                        .setPositiveButton("Eliminar") { _, _ ->
                            array.remove(i)
                            saveTasksArray(array)
                            recording?.let { populateTasks(it) }
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }

                layMeetingTasksContainer.addView(itemView)
            }
        } catch (e: Exception) {
            LogBus.error("RecordingDetailActivity: Error parsing tasks JSON", e)
        }
    }

    private fun showTaskEditDialog(existingObj: JSONObject?, index: Int) {
        val isNew = (existingObj == null)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_task, null, false)
        val etTitle = dialogView.findViewById<EditText>(R.id.etDialogTaskTitle)
        val etOwner = dialogView.findViewById<EditText>(R.id.etDialogTaskOwner)
        val etDeadline = dialogView.findViewById<EditText>(R.id.etDialogTaskDeadline)

        if (existingObj != null) {
            etTitle.setText(existingObj.optString("task", ""))
            etOwner.setText(existingObj.optString("owner", ""))
            etDeadline.setText(existingObj.optString("deadline", ""))
        }

        AlertDialog.Builder(this)
            .setTitle(if (isNew) "Nueva Tarea" else "Editar Tarea")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val taskTitle = etTitle.text.toString().trim()
                if (taskTitle.isBlank()) return@setPositiveButton

                val rec = recording ?: return@setPositiveButton
                val array = try {
                    if (rec.actionItems.isNotBlank()) JSONArray(rec.actionItems) else JSONArray()
                } catch (_: Exception) { JSONArray() }

                val targetObj = existingObj ?: JSONObject().apply {
                    put("completed", false)
                }
                targetObj.put("task", taskTitle)
                targetObj.put("owner", etOwner.text.toString().trim())
                targetObj.put("deadline", etDeadline.text.toString().trim())

                if (isNew) {
                    array.put(targetObj)
                } else {
                    array.put(index, targetObj)
                }

                saveTasksArray(array)
                populateTasks(rec)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun saveTasksArray(array: JSONArray) {
        val rec = recording ?: return
        rec.actionItems = array.toString()
        repository.updateRecording(rec)
    }

    // ==========================================
    // MIND MAP INTERACTIVE WEBVIEW & OUTLINE
    // ==========================================
    private fun setupMindMapToggle() {
        wvInteractiveMindMap.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        btnToggleMindMapView.setOnClickListener {
            isMindMapGraphMode = !isMindMapGraphMode
            if (isMindMapGraphMode) {
                btnToggleMindMapView.text = "📝 Esquema"
                wvInteractiveMindMap.visibility = View.VISIBLE
                scrollMindmapText.visibility = View.GONE
            } else {
                btnToggleMindMapView.text = "🎨 Gráfico"
                wvInteractiveMindMap.visibility = View.GONE
                scrollMindmapText.visibility = View.VISIBLE
            }
        }
    }

    private fun loadMindMap(rec: VoiceRecording) {
        val mindmapData = rec.mindmapData
        if (mindmapData.isBlank()) {
            tvMindmapContent.text = "Mapa mental no disponible todavía. Procesa la grabación con IA."
            return
        }

        tvMindmapContent.text = mindmapData

        try {
            val html = MindMapVisualizerHelper.buildMindMapHtml(rec.title.ifBlank { "Reunión" }, mindmapData)
            wvInteractiveMindMap.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            LogBus.error("RecordingDetailActivity: Error loading mindmap HTML", e)
        }
    }

    private fun setupPlayer() {
        playerManager.listener = object : AudioPlayerManager.Listener {
            override fun onPlaybackStarted(path: String, durationMs: Int) {
                runOnUiThread {
                    btnMainPlayPause.setIconResource(R.drawable.ic_pause)
                    playerSeekBar.max = durationMs
                }
            }

            override fun onPlaybackProgress(currentMs: Int, durationMs: Int) {
                runOnUiThread {
                    playerSeekBar.progress = currentMs
                    val totalSecs = currentMs / 1000
                    val mins = totalSecs / 60
                    val secs = totalSecs % 60
                    tvCurrentPosition.text = String.format("%02d:%02d", mins, secs)
                }
            }

            override fun onPlaybackPaused() {
                runOnUiThread {
                    btnMainPlayPause.setIconResource(R.drawable.ic_play_arrow)
                }
            }

            override fun onPlaybackResumed() {
                runOnUiThread {
                    btnMainPlayPause.setIconResource(R.drawable.ic_pause)
                }
            }

            override fun onPlaybackStopped() {
                runOnUiThread {
                    btnMainPlayPause.setIconResource(R.drawable.ic_play_arrow)
                    playerSeekBar.progress = 0
                    tvCurrentPosition.text = "00:00"
                }
            }

            override fun onPlaybackCompleted() {
                runOnUiThread {
                    btnMainPlayPause.setIconResource(R.drawable.ic_play_arrow)
                    playerSeekBar.progress = 0
                    tvCurrentPosition.text = "00:00"
                }
            }

            override fun onPlaybackError(message: String) {
                runOnUiThread {
                    Toast.makeText(this@RecordingDetailActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnMainPlayPause.setOnClickListener {
            val rec = recording ?: return@setOnClickListener
            if (rec.audioPath.isNotBlank()) {
                playerManager.togglePlayPause(rec.audioPath)
            } else {
                Toast.makeText(this, "Ruta de audio no disponible", Toast.LENGTH_SHORT).show()
            }
        }

        playerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    playerManager.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnRewind5s.setOnClickListener {
            val target = (playerSeekBar.progress - 5000).coerceAtLeast(0)
            playerManager.seekTo(target)
        }

        btnForward5s.setOnClickListener {
            val target = (playerSeekBar.progress + 5000).coerceAtMost(playerSeekBar.max)
            playerManager.seekTo(target)
        }

        btnSpeedToggle.setOnClickListener {
            currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
            val speed = speedOptions[currentSpeedIndex]
            btnSpeedToggle.text = speedLabels[currentSpeedIndex]
            playerManager.setSpeed(speed)
        }
    }

    private fun setupTabs() {
        tabsDetail.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                switchTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun switchTab(position: Int) {
        viewTabTranscript.visibility = if (position == 0) View.VISIBLE else View.GONE
        viewTabSummary.visibility = if (position == 1) View.VISIBLE else View.GONE
        viewTabTasks.visibility = if (position == 2) View.VISIBLE else View.GONE
        viewTabMindmap.visibility = if (position == 3) View.VISIBLE else View.GONE
        viewTabQa.visibility = if (position == 4) View.VISIBLE else View.GONE
    }

    private fun startAiProcessing() {
        layProcessingBanner.visibility = View.VISIBLE
        tvProcessingStatus.text = "Iniciando análisis con IA..."

        aiProcessor.processFullMeeting(
            recordingId = recordingId,
            onProgress = { stage ->
                runOnUiThread { tvProcessingStatus.text = stage }
            },
            callback = { result ->
                runOnUiThread {
                    layProcessingBanner.visibility = View.GONE
                    result.onSuccess { updated ->
                        recording = updated
                        loadRecordingData()
                        Toast.makeText(this@RecordingDetailActivity, "✨ Análisis IA completado con éxito", Toast.LENGTH_SHORT).show()
                    }.onFailure { error ->
                        Toast.makeText(this@RecordingDetailActivity, "Error en IA: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // ==========================================
    // AI CHAT WITH MARKDOWN & QUICK SUGGESTIONS
    // ==========================================
    private fun setupQaChat() {
        findViewById<Chip>(R.id.chipQuickSummary)?.setOnClickListener {
            sendChatMessage("¿Puedes darme un resumen ejecutivo en 3 puntos clave?")
        }
        findViewById<Chip>(R.id.chipQuickAgreements)?.setOnClickListener {
            sendChatMessage("¿Cuáles fueron los principales acuerdos y decisiones tomadas?")
        }
        findViewById<Chip>(R.id.chipQuickTasks)?.setOnClickListener {
            sendChatMessage("¿Qué tareas y compromisos quedaron asignados y a quiénes?")
        }
        findViewById<Chip>(R.id.chipQuickPending)?.setOnClickListener {
            sendChatMessage("¿Qué preguntas, dudas o temas quedaron abiertos sin resolver?")
        }

        btnSendQaQuestion.setOnClickListener {
            val q = etQaQuestion.text?.toString()?.trim()
            if (!q.isNullOrBlank()) {
                sendChatMessage(q)
            }
        }
    }

    private fun sendChatMessage(question: String) {
        val rec = recording ?: return
        etQaQuestion.setText("")

        // Add user bubble
        addChatBubble("🧑‍💻 Tú", question, isUser = true)

        // Query AI
        aiProcessor.askQuestionAboutRecording(rec, question) { result ->
            runOnUiThread {
                result.onSuccess { answer ->
                    addChatBubble("✨ Asistente IA", answer, isUser = false)
                }.onFailure { error ->
                    addChatBubble("⚠️ Error", "No pude procesar la consulta: ${error.message}", isUser = false)
                }
            }
        }
    }

    private fun addChatBubble(sender: String, message: String, isUser: Boolean) {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius = 24f
            strokeWidth = 2
            setStrokeColor(ContextCompat.getColor(context, if (isUser) R.color.cyber_teal else R.color.outline_variant_obsidian))
            setCardBackgroundColor(ContextCompat.getColor(context, if (isUser) R.color.obsidian_container_high else R.color.obsidian_container_low))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            layoutParams = lp
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val tvSender = TextView(this).apply {
            text = sender
            setTextColor(ContextCompat.getColor(context, if (isUser) R.color.cyber_teal else R.color.cyber_teal_light))
            textSize = 12f
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        topRow.addView(tvSender)

        // Copy button for AI responses
        if (!isUser) {
            val btnCopy = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                setIconResource(R.drawable.ic_share_cyber)
                setIconTintResource(R.color.on_surface_variant_obsidian)
                text = "Copiar"
                textSize = 11f
                setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant_obsidian))
                setOnClickListener {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("AI Response", message)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this@RecordingDetailActivity, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                }
            }
            topRow.addView(btnCopy)
        }

        val tvMsg = TextView(this).apply {
            setMarkdown(message)
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_obsidian))
            textSize = 14f
            setLineSpacing(0f, 1.35f)
            setTextIsSelectable(true)
            setPadding(0, 6, 0, 0)
        }

        inner.addView(topRow)
        inner.addView(tvMsg)
        card.addView(inner)
        layQaChatMessages.addView(card)

        scrollQaChat.post {
            scrollQaChat.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun sendSummaryToGlasses() {
        val rec = recording ?: return
        val summary = rec.summary.ifBlank { rec.rawTranscript }
        if (summary.isBlank()) {
            Toast.makeText(this, "No hay resumen para enviar a las gafas", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent("com.myvu.client.ACTION_TELEPROMPTER").apply {
            putExtra("text", summary.take(400))
        }
        sendBroadcast(intent)
        Toast.makeText(this, "👓 Resumen enviado a las gafas MEIZU MYVU", Toast.LENGTH_SHORT).show()
    }

    private fun shareRecordingSummary() {
        val rec = recording ?: return
        val shareText = buildString {
            append("🎙️ ${rec.title}\n\n")
            if (rec.summary.isNotBlank()) {
                append("📋 RESUMEN:\n${rec.summary}\n\n")
            }
            if (rec.rawTranscript.isNotBlank()) {
                append("💬 TRANSCRIPCIÓN:\n${rec.rawTranscript}\n\n")
            }
            if (rec.tags.isNotBlank()) {
                append("🏷️ Tags: ${rec.tags}\n")
            }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, rec.title)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Compartir resumen de grabación"))
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Eliminar grabación")
            .setMessage("¿Estás seguro de que deseas eliminar esta grabación y su archivo de audio?")
            .setPositiveButton("Eliminar") { _, _ ->
                repository.deleteRecording(recordingId)
                Toast.makeText(this, "Grabación eliminada", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroy() {
        playerManager.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RECORDING_ID = "extra_recording_id"
        const val EXTRA_AUTO_PROCESS = "extra_auto_process"
    }
}
