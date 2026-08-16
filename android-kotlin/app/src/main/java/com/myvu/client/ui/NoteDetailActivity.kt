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
import com.myvu.client.ai.NoteAiProcessor
import com.myvu.client.core.DocumentExtractor
import com.myvu.client.core.EdgeToEdgeHelper
import com.myvu.client.core.LogBus
import com.myvu.client.core.MindMapVisualizerHelper
import com.myvu.client.core.setMarkdown
import com.myvu.client.database.Attachment
import com.myvu.client.database.AttachmentType
import com.myvu.client.database.Note
import com.myvu.client.database.NoteRepository
import com.myvu.client.database.Reminder
import com.myvu.client.database.ReminderRepository
import com.myvu.client.database.TodoRepository
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class NoteDetailActivity : AppCompatActivity() {

    private var itemType: String = TYPE_NOTE
    private var itemId: Long = -1L

    private var currentNote: Note? = null
    private var currentReminder: Reminder? = null

    private lateinit var noteRepo: NoteRepository
    private lateinit var reminderRepo: ReminderRepository
    private lateinit var aiProcessor: NoteAiProcessor

    // Attachments
    private var pendingPhotoFile: File? = null
    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingPhotoFile != null && pendingPhotoFile!!.exists() && pendingPhotoFile!!.length() > 0) {
            lifecycleScope.launch {
                val att = DocumentExtractor.processPhotoFile(this@NoteDetailActivity, pendingPhotoFile!!)
                addAttachment(att)
            }
        }
    }

    private val pickDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                try {
                    val att = DocumentExtractor.processUriAttachment(this@NoteDetailActivity, uri)
                    addAttachment(att)
                } catch (e: Exception) {
                    Toast.makeText(this@NoteDetailActivity, "Error al procesar archivo: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Views
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvNoteDetailTitle: TextView
    private lateinit var tvNoteDetailDate: TextView
    private lateinit var tvNoteTags: TextView
    private lateinit var btnEditNoteTags: MaterialButton
    private lateinit var tabLayout: TabLayout

    // Processing banner
    private lateinit var layProcessingBanner: LinearLayout
    private lateinit var tvProcessingStatus: TextView

    // Tab Views
    private lateinit var viewTabNoteContent: View
    private lateinit var viewTabNoteSummary: View
    private lateinit var viewTabNoteTasks: View
    private lateinit var viewTabNoteMindmap: View
    private lateinit var viewTabNoteQa: View

    // Content Views
    private lateinit var tvNoteBodyContent: TextView
    private lateinit var btnEditNoteBody: MaterialButton
    private lateinit var btnNoteTakePhoto: MaterialButton
    private lateinit var btnNoteAttachFile: MaterialButton
    private lateinit var layNoteAttachmentsSection: LinearLayout
    private lateinit var tvNoteAttachmentsHeader: TextView
    private lateinit var layNoteAttachmentsList: LinearLayout

    private lateinit var tvNoteSummaryContent: TextView
    private lateinit var btnAddNoteManualTask: MaterialButton
    private lateinit var btnExportNoteTasksToTodo: MaterialButton
    private lateinit var layNoteTasksContainer: LinearLayout

    // Mind Map Views
    private lateinit var wvNoteInteractiveMindMap: WebView
    private lateinit var scrollNoteMindmapText: ScrollView
    private lateinit var tvNoteMindmapContent: TextView
    private lateinit var btnToggleNoteMindMapView: MaterialButton
    private var isMindMapGraphMode = true

    // QA Chat
    private lateinit var layNoteQaChatMessages: LinearLayout
    private lateinit var scrollNoteQaChat: ScrollView
    private lateinit var etNoteQaQuestion: EditText
    private lateinit var btnSendNoteQaQuestion: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_note_detail)

            itemType = intent.getStringExtra(EXTRA_ITEM_TYPE) ?: TYPE_NOTE
            itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)

            noteRepo = NoteRepository(this)
            reminderRepo = ReminderRepository(this)
            aiProcessor = NoteAiProcessor(this)

            bindViews()
            EdgeToEdgeHelper.setupEdgeToEdge(this, toolbar)
            setupTabs()
            setupTopBarActions()
            setupAttachmentsUi()
            setupMindMapToggle()
            setupQaChat()

            loadData()

            val autoProcess = intent.getBooleanExtra(EXTRA_AUTO_PROCESS, false)
            if (autoProcess) {
                startAiProcessing()
            }
        } catch (e: Throwable) {
            LogBus.error("NoteDetailActivity: Fatal error in onCreate", e)
            Toast.makeText(this, "Error al abrir el detalle: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun bindViews() {
        toolbar = findViewById(R.id.toolbarNoteDetail)
        tvNoteDetailTitle = findViewById(R.id.tvNoteDetailTitle)
        tvNoteDetailDate = findViewById(R.id.tvNoteDetailDate)
        tvNoteTags = findViewById(R.id.tvNoteTags)
        btnEditNoteTags = findViewById(R.id.btnEditNoteTags)
        tabLayout = findViewById(R.id.tabLayoutNoteDetail)

        layProcessingBanner = findViewById(R.id.layNoteProcessingBanner)
        tvProcessingStatus = findViewById(R.id.tvNoteProcessingStatus)

        viewTabNoteContent = findViewById(R.id.viewTabNoteContent)
        viewTabNoteSummary = findViewById(R.id.viewTabNoteSummary)
        viewTabNoteTasks = findViewById(R.id.viewTabNoteTasks)
        viewTabNoteMindmap = findViewById(R.id.viewTabNoteMindmap)
        viewTabNoteQa = findViewById(R.id.viewTabNoteQa)

        tvNoteBodyContent = findViewById(R.id.tvNoteBodyContent)
        btnEditNoteBody = findViewById(R.id.btnEditNoteBody)
        btnNoteTakePhoto = findViewById(R.id.btnNoteTakePhoto)
        btnNoteAttachFile = findViewById(R.id.btnNoteAttachFile)
        layNoteAttachmentsSection = findViewById(R.id.layNoteAttachmentsSection)
        tvNoteAttachmentsHeader = findViewById(R.id.tvNoteAttachmentsHeader)
        layNoteAttachmentsList = findViewById(R.id.layNoteAttachmentsList)

        tvNoteSummaryContent = findViewById(R.id.tvNoteSummaryContent)
        btnAddNoteManualTask = findViewById(R.id.btnAddNoteManualTask)
        btnExportNoteTasksToTodo = findViewById(R.id.btnExportNoteTasksToTodo)
        layNoteTasksContainer = findViewById(R.id.layNoteTasksContainer)

        wvNoteInteractiveMindMap = findViewById(R.id.wvNoteInteractiveMindMap)
        scrollNoteMindmapText = findViewById(R.id.scrollNoteMindmapText)
        tvNoteMindmapContent = findViewById(R.id.tvNoteMindmapContent)
        btnToggleNoteMindMapView = findViewById(R.id.btnToggleNoteMindMapView)

        scrollNoteQaChat = findViewById(R.id.scrollNoteQaChat)
        layNoteQaChatMessages = findViewById(R.id.layNoteQaChatMessages)
        etNoteQaQuestion = findViewById(R.id.etNoteQaQuestion)
        btnSendNoteQaQuestion = findViewById(R.id.btnSendNoteQaQuestion)
    }

    private fun loadData() {
        if (itemType == TYPE_NOTE) {
            val note = noteRepo.getById(itemId) ?: run {
                Toast.makeText(this, "Nota no encontrada", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            currentNote = note
            val title = note.title.ifBlank { "Nota #${note.id}" }
            toolbar.title = "Nota"
            toolbar.subtitle = note.formattedDate()
            tvNoteDetailTitle.text = title
            tvNoteDetailDate.text = note.formattedDate()
            tvNoteTags.text = if (note.tags.isNotBlank()) "🏷️ ${note.tags}" else "🏷️ Sin tags"

            tvNoteBodyContent.setMarkdown(note.body.ifBlank { "*Sin contenido.*" })
            tvNoteSummaryContent.setMarkdown(note.summary.ifBlank { "*Resumen no generado. Toca ✨ para analizar con IA.*" })
            populateTasks(note.actionItems)
            loadMindMap(title, note.mindmapData)
            renderAttachments()
        } else {
            val reminder = reminderRepo.getReminder(itemId) ?: run {
                Toast.makeText(this, "Recordatorio no encontrado", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            currentReminder = reminder
            val title = reminder.title.ifBlank { "Recordatorio #${reminder.id}" }
            toolbar.title = "Recordatorio"
            toolbar.subtitle = "Vence: ${reminder.formattedTriggerDate()}"
            tvNoteDetailTitle.text = title
            tvNoteDetailDate.text = "📅 ${reminder.formattedTriggerDate()}"
            tvNoteTags.text = if (reminder.tags.isNotBlank()) "🏷️ ${reminder.tags}" else "🏷️ Sin tags"

            tvNoteBodyContent.setMarkdown(reminder.body.ifBlank { "*Sin detalle.*" })
            tvNoteSummaryContent.setMarkdown(reminder.summary.ifBlank { "*Resumen no generado. Toca ✨ para analizar con IA.*" })
            populateTasks(reminder.actionItems)
            loadMindMap(title, reminder.mindmapData)
            renderAttachments()
        }
    }

    private fun setupAttachmentsUi() {
        btnNoteTakePhoto.setOnClickListener {
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

        btnNoteAttachFile.setOnClickListener {
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
        if (itemType == TYPE_NOTE) {
            val note = currentNote ?: return
            val currentList = note.getAttachments().toMutableList()
            currentList.add(attachment)
            note.attachmentsJson = Attachment.listToJson(currentList)
            noteRepo.updateAttachments(note.id, note.attachmentsJson)
            Toast.makeText(this, "Adjunto añadido: ${attachment.fileName}", Toast.LENGTH_SHORT).show()
        } else {
            val reminder = currentReminder ?: return
            val currentList = reminder.getAttachments().toMutableList()
            currentList.add(attachment)
            reminder.attachmentsJson = Attachment.listToJson(currentList)
            reminderRepo.updateAttachments(reminder.id, reminder.attachmentsJson)
            Toast.makeText(this, "Adjunto añadido: ${attachment.fileName}", Toast.LENGTH_SHORT).show()
        }
        renderAttachments()
    }

    private fun removeAttachment(attachment: Attachment) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Adjunto")
            .setMessage("¿Deseas eliminar '${attachment.fileName}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                if (itemType == TYPE_NOTE) {
                    val note = currentNote ?: return@setPositiveButton
                    val currentList = note.getAttachments().toMutableList()
                    currentList.removeAll { it.id == attachment.id }
                    note.attachmentsJson = Attachment.listToJson(currentList)
                    noteRepo.updateAttachments(note.id, note.attachmentsJson)
                } else {
                    val reminder = currentReminder ?: return@setPositiveButton
                    val currentList = reminder.getAttachments().toMutableList()
                    currentList.removeAll { it.id == attachment.id }
                    reminder.attachmentsJson = Attachment.listToJson(currentList)
                    reminderRepo.updateAttachments(reminder.id, reminder.attachmentsJson)
                }
                renderAttachments()
                Toast.makeText(this, "Adjunto eliminado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun renderAttachments() {
        val attachments = if (itemType == TYPE_NOTE) currentNote?.getAttachments() ?: emptyList() else currentReminder?.getAttachments() ?: emptyList()
        if (attachments.isEmpty()) {
            layNoteAttachmentsSection.visibility = View.GONE
            return
        }
        layNoteAttachmentsSection.visibility = View.VISIBLE
        tvNoteAttachmentsHeader.text = "📎 Archivos y Fotos Adjuntas (${attachments.size}):"
        layNoteAttachmentsList.removeAllViews()

        for (att in attachments) {
            val cardView = layoutInflater.inflate(R.layout.item_attachment_card, layNoteAttachmentsList, false)
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

            layNoteAttachmentsList.addView(cardView)
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
                tabLayout.getTabAt(4)?.select()
                etNoteQaQuestion.setText("Explica o resume el archivo ${att.fileName}")
            }
            .setNeutralButton("Eliminar") { _, _ ->
                removeAttachment(att)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun setupTopBarActions() {
        toolbar.setNavigationOnClickListener { finish() }

        findViewById<View>(R.id.btnEditNoteTitle)?.setOnClickListener { showEditTitleDialog() }
        tvNoteDetailTitle.setOnClickListener { showEditTitleDialog() }

        btnEditNoteBody.setOnClickListener { showEditBodyDialog() }
        btnEditNoteTags.setOnClickListener { showEditTagsDialog() }

        findViewById<View>(R.id.btnReAnalyzeNoteAi)?.setOnClickListener {
            startAiProcessing()
        }

        findViewById<View>(R.id.btnSendNoteToGlasses)?.setOnClickListener {
            sendToGlasses()
        }

        findViewById<View>(R.id.btnShareNote)?.setOnClickListener {
            shareContent()
        }

        findViewById<View>(R.id.btnDeleteNote)?.setOnClickListener {
            confirmDelete()
        }
    }

    private fun showEditTitleDialog() {
        val currentTitle = if (itemType == TYPE_NOTE) currentNote?.title.orEmpty() else currentReminder?.title.orEmpty()
        val input = EditText(this).apply {
            setText(currentTitle)
            setSelection(currentTitle.length)
            setHint("Título...")
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_obsidian))
            setHintTextColor(ContextCompat.getColor(context, R.color.outline_obsidian))
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Editar Título")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (itemType == TYPE_NOTE) {
                    currentNote?.let {
                        it.title = newTitle
                        noteRepo.update(it)
                    }
                } else {
                    currentReminder?.let {
                        it.title = newTitle
                        reminderRepo.update(it)
                    }
                }
                tvNoteDetailTitle.text = newTitle.ifBlank { "Sin título" }
                Toast.makeText(this, "Título actualizado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEditBodyDialog() {
        val currentBody = if (itemType == TYPE_NOTE) currentNote?.body.orEmpty() else currentReminder?.body.orEmpty()
        val input = EditText(this).apply {
            setText(currentBody)
            setSelection(currentBody.length)
            setHint("Contenido...")
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_obsidian))
            setHintTextColor(ContextCompat.getColor(context, R.color.outline_obsidian))
            setPadding(48, 32, 48, 32)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
        }

        AlertDialog.Builder(this)
            .setTitle("Editar Contenido")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val newBody = input.text.toString().trim()
                if (itemType == TYPE_NOTE) {
                    currentNote?.let {
                        it.body = newBody
                        noteRepo.update(it)
                    }
                } else {
                    currentReminder?.let {
                        it.body = newBody
                        reminderRepo.update(it)
                    }
                }
                tvNoteBodyContent.setMarkdown(newBody)
                Toast.makeText(this, "Contenido actualizado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEditTagsDialog() {
        val currentTags = if (itemType == TYPE_NOTE) currentNote?.tags.orEmpty() else currentReminder?.tags.orEmpty()
        val input = EditText(this).apply {
            setText(currentTags)
            setSelection(currentTags.length)
            setHint("Etiquetas separadas por comas (ej. trabajo, reunión)...")
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_obsidian))
            setHintTextColor(ContextCompat.getColor(context, R.color.outline_obsidian))
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Editar Tags")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val newTags = input.text.toString().trim()
                if (itemType == TYPE_NOTE) {
                    currentNote?.let {
                        it.tags = newTags
                        noteRepo.update(it)
                    }
                } else {
                    currentReminder?.let {
                        it.tags = newTags
                        reminderRepo.update(it)
                    }
                }
                tvNoteTags.text = if (newTags.isNotBlank()) "🏷️ $newTags" else "🏷️ Sin tags"
                Toast.makeText(this, "Tags actualizados", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ==========================================
    // TASK CRUD (Create, Read, Update, Delete)
    // ==========================================
    private fun populateTasks(actionItemsJson: String) {
        layNoteTasksContainer.removeAllViews()

        btnAddNoteManualTask.setOnClickListener {
            showTaskEditDialog(null, -1)
        }

        if (actionItemsJson.isBlank() || actionItemsJson == "[]") {
            val emptyTv = TextView(this).apply {
                text = "No hay tareas registradas. Toca '+ Nueva Tarea' o ejecuta el análisis con IA."
                setTextColor(ContextCompat.getColor(context, R.color.outline_obsidian))
                setPadding(0, 16, 0, 0)
                textSize = 14f
            }
            layNoteTasksContainer.addView(emptyTv)
            btnExportNoteTasksToTodo.visibility = View.GONE
            return
        }

        btnExportNoteTasksToTodo.visibility = View.VISIBLE
        btnExportNoteTasksToTodo.setOnClickListener {
            val count = aiProcessor.exportActionItemsToTodos(actionItemsJson, if (itemType == TYPE_NOTE) "Notas" else "Recordatorios")
            Toast.makeText(this, "✅ $count tareas exportadas a Mis Tareas", Toast.LENGTH_SHORT).show()
        }

        try {
            val array = JSONArray(actionItemsJson)
            val inflater = LayoutInflater.from(this)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val task = obj.optString("task", "")
                val owner = obj.optString("owner", "")
                val deadline = obj.optString("deadline", "")
                val completed = obj.optBoolean("completed", false)

                val itemView = inflater.inflate(R.layout.item_meeting_task, layNoteTasksContainer, false)
                val cbTask: CheckBox = itemView.findViewById(R.id.cbMeetingTask)
                val tvTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
                val tvOwner: TextView = itemView.findViewById(R.id.tvTaskOwner)
                val tvDeadline: TextView = itemView.findViewById(R.id.tvTaskDeadline)
                val btnEdit: MaterialButton = itemView.findViewById(R.id.btnEditSingleTask)
                val btnAddSingle: MaterialButton = itemView.findViewById(R.id.btnExportSingleTask)
                val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDeleteSingleTask)

                tvTitle.text = task
                cbTask.isChecked = completed
                tvTitle.paint.isStrikeThruText = completed
                tvTitle.alpha = if (completed) 0.5f else 1.0f

                cbTask.setOnCheckedChangeListener { _, isChecked ->
                    obj.put("completed", isChecked)
                    saveTasksArray(array)
                    tvTitle.paint.isStrikeThruText = isChecked
                    tvTitle.alpha = if (isChecked) 0.5f else 1.0f
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

                btnEdit.setOnClickListener { showTaskEditDialog(obj, i) }

                btnAddSingle.setOnClickListener {
                    val todoRepo = TodoRepository(this)
                    todoRepo.createTodo(listName = if (itemType == TYPE_NOTE) "Notas" else "Recordatorios", title = task, tags = "ia")
                    Toast.makeText(this, "Tarea añadida a To-Do", Toast.LENGTH_SHORT).show()
                }

                btnDelete.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Eliminar Tarea")
                        .setMessage("¿Eliminar '$task'?")
                        .setPositiveButton("Eliminar") { _, _ ->
                            array.remove(i)
                            saveTasksArray(array)
                            populateTasks(array.toString())
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }

                layNoteTasksContainer.addView(itemView)
            }
        } catch (e: Exception) {
            LogBus.error("NoteDetailActivity: Error parsing tasks JSON", e)
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

                val currentJson = if (itemType == TYPE_NOTE) currentNote?.actionItems.orEmpty() else currentReminder?.actionItems.orEmpty()
                val array = try {
                    if (currentJson.isNotBlank()) JSONArray(currentJson) else JSONArray()
                } catch (_: Exception) { JSONArray() }

                val targetObj = existingObj ?: JSONObject().apply {
                    put("completed", false)
                }
                targetObj.put("task", taskTitle)
                targetObj.put("owner", etOwner.text.toString().trim())
                targetObj.put("deadline", etDeadline.text.toString().trim())

                if (isNew) array.put(targetObj) else array.put(index, targetObj)

                saveTasksArray(array)
                populateTasks(array.toString())
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun saveTasksArray(array: JSONArray) {
        val json = array.toString()
        if (itemType == TYPE_NOTE) {
            currentNote?.let {
                it.actionItems = json
                noteRepo.update(it)
            }
        } else {
            currentReminder?.let {
                it.actionItems = json
                reminderRepo.update(it)
            }
        }
    }

    // ==========================================
    // MIND MAP INTERACTIVE WEBVIEW & OUTLINE
    // ==========================================
    private fun setupMindMapToggle() {
        wvNoteInteractiveMindMap.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        btnToggleNoteMindMapView.setOnClickListener {
            isMindMapGraphMode = !isMindMapGraphMode
            if (isMindMapGraphMode) {
                btnToggleNoteMindMapView.text = "📝 Esquema"
                wvNoteInteractiveMindMap.visibility = View.VISIBLE
                scrollNoteMindmapText.visibility = View.GONE
            } else {
                btnToggleNoteMindMapView.text = "🎨 Gráfico"
                wvNoteInteractiveMindMap.visibility = View.GONE
                scrollNoteMindmapText.visibility = View.VISIBLE
            }
        }
    }

    private fun loadMindMap(title: String, mindmapData: String) {
        if (mindmapData.isBlank()) {
            tvNoteMindmapContent.text = "Mapa mental no disponible todavía. Toca ✨ para analizar con IA."
            return
        }

        tvNoteMindmapContent.text = mindmapData
        try {
            val html = MindMapVisualizerHelper.buildMindMapHtml(title, mindmapData)
            wvNoteInteractiveMindMap.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            LogBus.error("NoteDetailActivity: Error loading mindmap HTML", e)
        }
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                switchTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun switchTab(position: Int) {
        viewTabNoteContent.visibility = if (position == 0) View.VISIBLE else View.GONE
        viewTabNoteSummary.visibility = if (position == 1) View.VISIBLE else View.GONE
        viewTabNoteTasks.visibility = if (position == 2) View.VISIBLE else View.GONE
        viewTabNoteMindmap.visibility = if (position == 3) View.VISIBLE else View.GONE
        viewTabNoteQa.visibility = if (position == 4) View.VISIBLE else View.GONE
    }

    private fun startAiProcessing() {
        layProcessingBanner.visibility = View.VISIBLE
        tvProcessingStatus.text = "Iniciando análisis con IA..."

        if (itemType == TYPE_NOTE) {
            aiProcessor.processNote(
                noteId = itemId,
                onProgress = { stage -> runOnUiThread { tvProcessingStatus.text = stage } },
                callback = { result ->
                    runOnUiThread {
                        layProcessingBanner.visibility = View.GONE
                        result.onSuccess { updated ->
                            currentNote = updated
                            loadData()
                            Toast.makeText(this@NoteDetailActivity, "✨ Análisis IA completado", Toast.LENGTH_SHORT).show()
                        }.onFailure { error ->
                            Toast.makeText(this@NoteDetailActivity, "Error en IA: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        } else {
            aiProcessor.processReminder(
                reminderId = itemId,
                onProgress = { stage -> runOnUiThread { tvProcessingStatus.text = stage } },
                callback = { result ->
                    runOnUiThread {
                        layProcessingBanner.visibility = View.GONE
                        result.onSuccess { updated ->
                            currentReminder = updated
                            loadData()
                            Toast.makeText(this@NoteDetailActivity, "✨ Análisis IA completado", Toast.LENGTH_SHORT).show()
                        }.onFailure { error ->
                            Toast.makeText(this@NoteDetailActivity, "Error en IA: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }

    // ==========================================
    // AI CHAT WITH MARKDOWN & QUICK SUGGESTIONS
    // ==========================================
    private fun setupQaChat() {
        findViewById<Chip>(R.id.chipNoteQuickExpand)?.setOnClickListener {
            sendChatMessage("¿Cómo podemos desarrollar y expandir las ideas clave de esta nota?")
        }
        findViewById<Chip>(R.id.chipNoteQuickSummary)?.setOnClickListener {
            sendChatMessage("¿Puedes darme un resumen estructurado en 3 puntos clave?")
        }
        findViewById<Chip>(R.id.chipNoteQuickNextSteps)?.setOnClickListener {
            sendChatMessage("¿Cuáles deberían ser los próximos pasos lógicos a seguir?")
        }

        btnSendNoteQaQuestion.setOnClickListener {
            val q = etNoteQaQuestion.text?.toString()?.trim()
            if (!q.isNullOrBlank()) {
                sendChatMessage(q)
            }
        }
    }

    private fun sendChatMessage(question: String) {
        etNoteQaQuestion.setText("")
        addChatBubble("🧑‍💻 Tú", question, isUser = true)

        if (itemType == TYPE_NOTE) {
            val note = currentNote ?: return
            aiProcessor.askQuestionAboutNote(note, question) { result ->
                runOnUiThread {
                    result.onSuccess { answer ->
                        addChatBubble("✨ Asistente IA", answer, isUser = false)
                    }.onFailure { error ->
                        addChatBubble("⚠️ Error", "No pude responder: ${error.message}", isUser = false)
                    }
                }
            }
        } else {
            val reminder = currentReminder ?: return
            aiProcessor.askQuestionAboutReminder(reminder, question) { result ->
                runOnUiThread {
                    result.onSuccess { answer ->
                        addChatBubble("✨ Asistente IA", answer, isUser = false)
                    }.onFailure { error ->
                        addChatBubble("⚠️ Error", "No pude responder: ${error.message}", isUser = false)
                    }
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
                    Toast.makeText(this@NoteDetailActivity, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
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
        layNoteQaChatMessages.addView(card)

        scrollNoteQaChat.post {
            scrollNoteQaChat.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun sendToGlasses() {
        val textToSend = if (itemType == TYPE_NOTE) {
            val n = currentNote ?: return
            if (n.summary.isNotBlank()) n.summary else n.body
        } else {
            val r = currentReminder ?: return
            if (r.summary.isNotBlank()) r.summary else r.body
        }

        if (textToSend.isBlank()) {
            Toast.makeText(this, "No hay contenido para enviar a las gafas", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent("com.myvu.client.ACTION_TELEPROMPTER").apply {
            putExtra("text", textToSend.take(400))
        }
        sendBroadcast(intent)
        Toast.makeText(this, "👓 Enviado al teleprompter de las gafas", Toast.LENGTH_SHORT).show()
    }

    private fun shareContent() {
        val shareText = if (itemType == TYPE_NOTE) {
            val n = currentNote ?: return
            buildString {
                append("📝 ${n.title}\n\n")
                if (n.summary.isNotBlank()) append("📋 RESUMEN:\n${n.summary}\n\n")
                append("📄 CONTENIDO:\n${n.body}\n")
                if (n.tags.isNotBlank()) append("\n🏷️ Tags: ${n.tags}")
            }
        } else {
            val r = currentReminder ?: return
            buildString {
                append("⏰ ${r.title}\n\n")
                append("📅 Fecha: ${r.formattedTriggerDate()}\n")
                if (r.summary.isNotBlank()) append("📋 RESUMEN:\n${r.summary}\n\n")
                append("📄 DETALLE:\n${r.body}\n")
            }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Compartir"))
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(if (itemType == TYPE_NOTE) "Eliminar Nota" else "Eliminar Recordatorio")
            .setMessage("¿Estás seguro de que deseas eliminar este elemento?")
            .setPositiveButton("Eliminar") { _, _ ->
                if (itemType == TYPE_NOTE) {
                    noteRepo.delete(itemId)
                } else {
                    reminderRepo.delete(itemId)
                }
                Toast.makeText(this, "Eliminado con éxito", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    companion object {
        const val EXTRA_ITEM_TYPE = "extra_item_type"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_AUTO_PROCESS = "extra_auto_process"

        const val TYPE_NOTE = "NOTE"
        const val TYPE_REMINDER = "REMINDER"
    }
}
