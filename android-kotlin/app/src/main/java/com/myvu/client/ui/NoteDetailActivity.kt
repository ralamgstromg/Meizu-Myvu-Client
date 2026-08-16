package com.myvu.client.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.myvu.client.R
import com.myvu.client.ai.NoteAiProcessor
import com.myvu.client.core.EdgeToEdgeHelper
import com.myvu.client.core.LogBus
import com.myvu.client.core.setMarkdown
import com.myvu.client.database.Attachment
import com.myvu.client.database.Note
import com.myvu.client.database.NoteRepository
import com.myvu.client.database.Reminder
import com.myvu.client.database.ReminderRepository
import com.myvu.client.ui.common.AiChatController
import com.myvu.client.ui.common.AttachmentUiController
import com.myvu.client.ui.common.MindMapController
import com.myvu.client.ui.common.TaskChecklistController

class NoteDetailActivity : AppCompatActivity() {

    private var itemType: String = TYPE_NOTE
    private var itemId: Long = -1L

    private var currentNote: Note? = null
    private var currentReminder: Reminder? = null

    private lateinit var noteRepo: NoteRepository
    private lateinit var reminderRepo: ReminderRepository
    private lateinit var aiProcessor: NoteAiProcessor

    // Controllers
    private lateinit var attachmentController: AttachmentUiController
    private lateinit var taskController: TaskChecklistController
    private lateinit var chatController: AiChatController
    private lateinit var mindMapController: MindMapController

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
    private lateinit var tvNoteSummaryContent: TextView

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
            setupControllers()

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
        tvNoteSummaryContent = findViewById(R.id.tvNoteSummaryContent)
    }

    private fun setupControllers() {
        // 1. Attachments Controller
        attachmentController = AttachmentUiController(
            activity = this,
            btnTakePhoto = findViewById(R.id.btnNoteTakePhoto),
            btnAttachFile = findViewById(R.id.btnNoteAttachFile),
            laySection = findViewById(R.id.layNoteAttachmentsSection),
            tvHeader = findViewById(R.id.tvNoteAttachmentsHeader),
            layList = findViewById(R.id.layNoteAttachmentsList),
            onAttachmentAdded = { att -> addAttachment(att) },
            onAttachmentRemoved = { att -> removeAttachment(att) },
            onAskAiInChat = { query ->
                tabLayout.getTabAt(4)?.select()
                chatController.setQuestionText(query)
            }
        )

        // 2. Task Checklist Controller
        taskController = TaskChecklistController(
            activity = this,
            layTasksContainer = findViewById(R.id.layNoteTasksContainer),
            btnAddManualTask = findViewById(R.id.btnAddNoteManualTask),
            btnExportToTodo = findViewById(R.id.btnExportNoteTasksToTodo),
            defaultCategory = if (itemType == TYPE_NOTE) "Notas" else "Recordatorios",
            onTasksChanged = { updatedJson ->
                if (itemType == TYPE_NOTE) {
                    currentNote?.let {
                        it.actionItems = updatedJson
                        noteRepo.update(it)
                    }
                } else {
                    currentReminder?.let {
                        it.actionItems = updatedJson
                        reminderRepo.update(it)
                    }
                }
            }
        )

        // 3. Mind Map Controller
        mindMapController = MindMapController(
            activity = this,
            webView = findViewById(R.id.wvNoteInteractiveMindMap),
            scrollText = findViewById(R.id.scrollNoteMindmapText),
            tvMindmapText = findViewById(R.id.tvNoteMindmapContent),
            btnToggleMode = findViewById(R.id.btnToggleNoteMindMapView)
        )

        // 4. AI QA Chat Controller
        chatController = AiChatController(
            activity = this,
            layChatMessages = findViewById(R.id.layNoteQaChatMessages),
            scrollChat = findViewById(R.id.scrollNoteQaChat),
            etQuestion = findViewById(R.id.etNoteQaQuestion),
            btnSend = findViewById(R.id.btnSendNoteQaQuestion),
            onExecuteAiQuery = { question, onComplete ->
                if (itemType == TYPE_NOTE) {
                    currentNote?.let { note ->
                        aiProcessor.askQuestionAboutNote(note, question) { result ->
                            onComplete(result.getOrNull())
                        }
                    } ?: onComplete(null)
                } else {
                    currentReminder?.let { reminder ->
                        aiProcessor.askQuestionAboutReminder(reminder, question) { result ->
                            onComplete(result.getOrNull())
                        }
                    } ?: onComplete(null)
                }
            }
        )
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
            taskController.populateTasks(note.actionItems)
            mindMapController.loadMindMap(title, note.mindmapData)
            attachmentController.renderAttachments(note.getAttachments())
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
            taskController.populateTasks(reminder.actionItems)
            mindMapController.loadMindMap(title, reminder.mindmapData)
            attachmentController.renderAttachments(reminder.getAttachments())
        }
    }

    private fun addAttachment(attachment: Attachment) {
        if (itemType == TYPE_NOTE) {
            val note = currentNote ?: return
            val list = note.getAttachments().toMutableList()
            list.add(attachment)
            note.attachmentsJson = Attachment.listToJson(list)
            noteRepo.updateAttachments(note.id, note.attachmentsJson)
            attachmentController.renderAttachments(list)
        } else {
            val reminder = currentReminder ?: return
            val list = reminder.getAttachments().toMutableList()
            list.add(attachment)
            reminder.attachmentsJson = Attachment.listToJson(list)
            reminderRepo.updateAttachments(reminder.id, reminder.attachmentsJson)
            attachmentController.renderAttachments(list)
        }
        Toast.makeText(this, "Adjunto añadido: ${attachment.fileName}", Toast.LENGTH_SHORT).show()
    }

    private fun removeAttachment(attachment: Attachment) {
        if (itemType == TYPE_NOTE) {
            val note = currentNote ?: return
            val list = note.getAttachments().toMutableList()
            list.removeAll { it.id == attachment.id }
            note.attachmentsJson = Attachment.listToJson(list)
            noteRepo.updateAttachments(note.id, note.attachmentsJson)
            attachmentController.renderAttachments(list)
        } else {
            val reminder = currentReminder ?: return
            val list = reminder.getAttachments().toMutableList()
            list.removeAll { it.id == attachment.id }
            reminder.attachmentsJson = Attachment.listToJson(list)
            reminderRepo.updateAttachments(reminder.id, reminder.attachmentsJson)
            attachmentController.renderAttachments(list)
        }
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
                loadData()
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
            minLines = 4
        }

        AlertDialog.Builder(this)
            .setTitle("Editar Contenido")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val newBody = input.text.toString()
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
                loadData()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEditTagsDialog() {
        val currentTags = if (itemType == TYPE_NOTE) currentNote?.tags.orEmpty() else currentReminder?.tags.orEmpty()
        val input = EditText(this).apply {
            setText(currentTags)
            setSelection(currentTags.length)
            setHint("Etiquetas separadas por coma...")
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_obsidian))
            setHintTextColor(ContextCompat.getColor(context, R.color.outline_obsidian))
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Editar Etiquetas")
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
                loadData()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startAiProcessing() {
        layProcessingBanner.visibility = View.VISIBLE
        tvProcessingStatus.text = "Analizando con IA..."

        if (itemType == TYPE_NOTE) {
            val note = currentNote ?: return
            aiProcessor.processNote(
                noteId = note.id,
                onProgress = { status ->
                    runOnUiThread { tvProcessingStatus.text = status }
                },
                callback = { result ->
                    runOnUiThread {
                        layProcessingBanner.visibility = View.GONE
                        val updated = result.getOrNull()
                        if (updated != null) {
                            currentNote = updated
                            loadData()
                            Toast.makeText(this, "Análisis de Nota completado con IA", Toast.LENGTH_SHORT).show()
                        } else {
                            val err = result.exceptionOrNull()?.message ?: "Error desconocido"
                            Toast.makeText(this, "Error en el análisis de IA: $err", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        } else {
            val reminder = currentReminder ?: return
            aiProcessor.processReminder(
                reminderId = reminder.id,
                onProgress = { status ->
                    runOnUiThread { tvProcessingStatus.text = status }
                },
                callback = { result ->
                    runOnUiThread {
                        layProcessingBanner.visibility = View.GONE
                        val updated = result.getOrNull()
                        if (updated != null) {
                            currentReminder = updated
                            loadData()
                            Toast.makeText(this, "Análisis de Recordatorio completado con IA", Toast.LENGTH_SHORT).show()
                        } else {
                            val err = result.exceptionOrNull()?.message ?: "Error desconocido"
                            Toast.makeText(this, "Error en el análisis de IA: $err", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }

    private fun sendToGlasses() {
        val title = if (itemType == TYPE_NOTE) currentNote?.title.orEmpty() else currentReminder?.title.orEmpty()
        val body = if (itemType == TYPE_NOTE) currentNote?.body.orEmpty() else currentReminder?.body.orEmpty()
        val textToSend = "$title\n\n$body".trim()

        if (textToSend.isBlank()) {
            Toast.makeText(this, "No hay contenido para enviar", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            com.myvu.client.app.AppLayer.sendNotification(
                title = if (itemType == TYPE_NOTE) "Nota: $title" else "Recordatorio: $title",
                body = body
            )
            Toast.makeText(this, "Enviado a las gafas Myvu", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            LogBus.error("NoteDetailActivity: Failed to send to glasses", e)
            Toast.makeText(this, "Error enviando a las gafas: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareContent() {
        val title = if (itemType == TYPE_NOTE) currentNote?.title.orEmpty() else currentReminder?.title.orEmpty()
        val body = if (itemType == TYPE_NOTE) currentNote?.body.orEmpty() else currentReminder?.body.orEmpty()
        val summary = if (itemType == TYPE_NOTE) currentNote?.summary.orEmpty() else currentReminder?.summary.orEmpty()

        val shareText = buildString {
            appendLine("📝 $title")
            appendLine("-------------------")
            appendLine(body)
            if (summary.isNotBlank()) {
                appendLine()
                appendLine("✨ Resumen IA:")
                appendLine(summary)
            }
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Compartir con..."))
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Eliminar ${if (itemType == TYPE_NOTE) "Nota" else "Recordatorio"}")
            .setMessage("¿Estás seguro de que deseas eliminar este elemento? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                if (itemType == TYPE_NOTE) {
                    noteRepo.delete(itemId)
                } else {
                    reminderRepo.deleteReminder(itemId)
                }
                Toast.makeText(this, "Elemento eliminado", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showTabView(viewTabNoteContent)
                    1 -> showTabView(viewTabNoteSummary)
                    2 -> showTabView(viewTabNoteTasks)
                    3 -> showTabView(viewTabNoteMindmap)
                    4 -> showTabView(viewTabNoteQa)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showTabView(targetView: View) {
        viewTabNoteContent.visibility = if (targetView == viewTabNoteContent) View.VISIBLE else View.GONE
        viewTabNoteSummary.visibility = if (targetView == viewTabNoteSummary) View.VISIBLE else View.GONE
        viewTabNoteTasks.visibility = if (targetView == viewTabNoteTasks) View.VISIBLE else View.GONE
        viewTabNoteMindmap.visibility = if (targetView == viewTabNoteMindmap) View.VISIBLE else View.GONE
        viewTabNoteQa.visibility = if (targetView == viewTabNoteQa) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        mindMapController.destroy()
    }

    companion object {
        const val EXTRA_ITEM_TYPE = "EXTRA_ITEM_TYPE"
        const val EXTRA_ITEM_ID = "EXTRA_ITEM_ID"
        const val EXTRA_AUTO_PROCESS = "EXTRA_AUTO_PROCESS"

        const val TYPE_NOTE = "TYPE_NOTE"
        const val TYPE_REMINDER = "TYPE_REMINDER"
    }
}
