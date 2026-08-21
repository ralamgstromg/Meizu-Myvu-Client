package com.myvu.client.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.myvu.client.R
import com.myvu.client.ai.MeetingAiProcessor
import com.myvu.client.core.EdgeToEdgeHelper
import com.myvu.client.core.LogBus
import com.myvu.client.core.setMarkdown
import com.myvu.client.database.Attachment
import com.myvu.client.database.VoiceRecording
import com.myvu.client.database.VoiceRecordingRepository
import com.myvu.client.recorder.AudioPlayerManager
import com.myvu.client.ui.common.AiChatController
import com.myvu.client.ui.common.AttachmentUiController
import com.myvu.client.ui.common.MindMapController
import com.myvu.client.ui.common.TaskChecklistController
import org.json.JSONArray

class RecordingDetailActivity : AppCompatActivity(), AudioPlayerManager.Listener {

    private var recordingId: Long = -1L
    private var recording: VoiceRecording? = null
    private lateinit var repository: VoiceRecordingRepository
    private lateinit var aiProcessor: MeetingAiProcessor
    private val playerManager = AudioPlayerManager()

    // Controllers
    private lateinit var attachmentController: AttachmentUiController
    private lateinit var taskController: TaskChecklistController
    private lateinit var chatController: AiChatController
    private lateinit var mindMapController: MindMapController

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
    private lateinit var tvSummaryContent: TextView

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
            setupControllers()

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
        tvSummaryContent = findViewById(R.id.tvSummaryContent)
    }

    private fun setupControllers() {
        // 1. Attachments Controller
        attachmentController = AttachmentUiController(
            activity = this,
            btnTakePhoto = findViewById(R.id.btnRecordingTakePhoto),
            btnAttachFile = findViewById(R.id.btnRecordingAttachFile),
            laySection = findViewById(R.id.layRecordingAttachmentsSection),
            tvHeader = findViewById(R.id.tvRecordingAttachmentsHeader),
            layList = findViewById(R.id.layRecordingAttachmentsList),
            onAttachmentAdded = { att -> addAttachment(att) },
            onAttachmentRemoved = { att -> removeAttachment(att) },
            onAskAiInChat = { query ->
                tabsDetail.getTabAt(4)?.select()
                chatController.setQuestionText(query)
            }
        )

        // 2. Task Checklist Controller
        taskController = TaskChecklistController(
            activity = this,
            layTasksContainer = findViewById(R.id.layMeetingTasksContainer),
            btnAddManualTask = findViewById(R.id.btnAddNewManualTask),
            btnExportToTodo = findViewById(R.id.btnExportAllTasksToTodo),
            defaultCategory = "Grabaciones",
            onTasksChanged = { updatedJson ->
                recording?.let {
                    it.actionItems = updatedJson
                    repository.updateRecording(it)
                }
            }
        )

        // 3. Mind Map Controller
        mindMapController = MindMapController(
            activity = this,
            webView = findViewById(R.id.wvInteractiveMindMap),
            scrollText = findViewById(R.id.scrollMindmapText),
            tvMindmapText = findViewById(R.id.tvMindmapContent),
            btnToggleMode = findViewById(R.id.btnToggleMindMapView)
        )

        // 4. AI QA Chat Controller
        chatController = AiChatController(
            activity = this,
            layChatMessages = findViewById(R.id.layQaChatMessages),
            scrollChat = findViewById(R.id.scrollQaChat),
            etQuestion = findViewById(R.id.etQaQuestion),
            btnSend = findViewById(R.id.btnSendQaQuestion),
            onExecuteAiQuery = { question, onComplete ->
                recording?.let { rec ->
                    aiProcessor.askQuestionAboutRecording(rec, question) { result ->
                        onComplete(result.getOrNull())
                    }
                } ?: onComplete(null)
            }
        )
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
                com.myvu.client.core.MarkdownUtils.sanitizeToMarkdown(rec.summary)
            } else {
                "*Aún no se ha generado un resumen.*\n\nToca el botón ✨ arriba a la derecha para procesar la grabación con IA."
            }
            tvSummaryContent.setMarkdown(summaryText)

            // Populate Tasks & Mind Map & Attachments
            taskController.populateTasks(rec.actionItems)
            mindMapController.loadMindMap(displayTitle, rec.mindmapData)
            attachmentController.renderAttachments(rec.getAttachments())
        } catch (e: Exception) {
            LogBus.error("RecordingDetailActivity: Error loading recording data", e)
        }
    }

    private fun addAttachment(attachment: Attachment) {
        val rec = recording ?: return
        val list = rec.getAttachments().toMutableList()
        list.add(attachment)
        rec.attachmentsJson = Attachment.listToJson(list)
        repository.updateAttachments(rec.id, rec.attachmentsJson)
        attachmentController.renderAttachments(list)
        Toast.makeText(this, "Adjunto añadido: ${attachment.fileName}", Toast.LENGTH_SHORT).show()
    }

    private fun removeAttachment(attachment: Attachment) {
        val rec = recording ?: return
        val list = rec.getAttachments().toMutableList()
        list.removeAll { it.id == attachment.id }
        rec.attachmentsJson = Attachment.listToJson(list)
        repository.updateAttachments(rec.id, rec.attachmentsJson)
        attachmentController.renderAttachments(list)
    }

    private fun populateTranscript(rec: VoiceRecording) {
        val diarizedJson = rec.diarizedTranscript
        if (diarizedJson.isNotBlank() && diarizedJson.startsWith("[")) {
            try {
                val array = JSONArray(diarizedJson)
                if (array.length() > 0) {
                    tvRawTranscript.visibility = View.GONE
                    val sb = StringBuilder()
                    for (i in 0 until array.length()) {
                        val turn = array.getJSONObject(i)
                        val speaker = turn.optString("speaker", "Hablante")
                        val text = turn.optString("text", "")
                        val startMs = turn.optInt("startMs", 0)
                        sb.appendLine("🎙️ **$speaker** (${formatMs(startMs)}):")
                        sb.appendLine(text)
                        sb.appendLine()
                    }
                    tvRawTranscript.setMarkdown(sb.toString().trim())
                    tvRawTranscript.visibility = View.VISIBLE
                    return
                }
            } catch (e: Exception) {
                LogBus.warn("RecordingDetailActivity: Error parsing diarized transcript: ${e.message}")
            }
        }

        // Fallback to raw transcript
        tvRawTranscript.visibility = View.VISIBLE
        tvRawTranscript.text = rec.rawTranscript.ifBlank { "Transcripción no disponible." }
    }

    private fun setupPlayer() {
        playerManager.listener = this

        btnMainPlayPause.setOnClickListener {
            val rec = recording ?: return@setOnClickListener
            playerManager.togglePlayPause(rec.audioPath)
        }

        btnRewind5s.setOnClickListener {
            val curPos = playerSeekBar.progress
            playerManager.seekTo((curPos - 5000).coerceAtLeast(0))
        }

        btnForward5s.setOnClickListener {
            val curPos = playerSeekBar.progress
            playerManager.seekTo((curPos + 5000).coerceAtMost(playerSeekBar.max))
        }

        btnSpeedToggle.setOnClickListener {
            currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
            val speed = speedOptions[currentSpeedIndex]
            playerManager.setSpeed(speed)
            btnSpeedToggle.text = speedLabels[currentSpeedIndex]
        }

        playerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    playerManager.seekTo(progress)
                    tvCurrentPosition.text = formatMs(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupTopBarActions() {
        toolbar.setNavigationOnClickListener { finish() }

        findViewById<View>(R.id.btnEditRecordingTitle)?.setOnClickListener { showEditTitleDialog() }
        tvDetailTitle.setOnClickListener { showEditTitleDialog() }

        findViewById<View>(R.id.btnReAnalyzeAi)?.setOnClickListener {
            startAiProcessing()
        }

        findViewById<View>(R.id.btnSendToGlasses)?.setOnClickListener {
            sendToGlasses()
        }

        findViewById<View>(R.id.btnShareRecording)?.setOnClickListener {
            shareContent()
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
                rec.title = newTitle
                repository.updateRecording(rec)
                loadRecordingData()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startAiProcessing() {
        layProcessingBanner.visibility = View.VISIBLE
        tvProcessingStatus.text = "Analizando con IA..."

        aiProcessor.processFullMeeting(
            recordingId = recordingId,
            onProgress = { stage ->
                runOnUiThread { tvProcessingStatus.text = stage }
            },
            callback = { result ->
                runOnUiThread {
                    layProcessingBanner.visibility = View.GONE
                    val updated = result.getOrNull()
                    if (updated != null) {
                        recording = updated
                        loadRecordingData()
                        Toast.makeText(this, "Análisis de Grabación completado con IA", Toast.LENGTH_SHORT).show()
                    } else {
                        val err = result.exceptionOrNull()?.message ?: "Error desconocido"
                        Toast.makeText(this, "Error en el análisis de IA: $err", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    private fun sendToGlasses() {
        val rec = recording ?: return
        val summary = rec.summary.ifBlank { rec.rawTranscript }
        if (summary.isBlank()) {
            Toast.makeText(this, "No hay contenido para enviar", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            com.myvu.client.app.AppLayer.sendNotification(
                title = "Grabación: ${rec.title.ifBlank { "Reunión" }}",
                body = summary
            )
            Toast.makeText(this, "Enviado a las gafas Myvu", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            LogBus.error("RecordingDetailActivity: Failed to send to glasses", e)
            Toast.makeText(this, "Error enviando a las gafas: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareContent() {
        val rec = recording ?: return
        val shareText = buildString {
            appendLine("🎙️ ${rec.title.ifBlank { "Grabación de Audio" }}")
            appendLine("📅 ${rec.formattedDate()} • ${rec.formattedDuration()}")
            appendLine("-------------------")
            if (rec.summary.isNotBlank()) {
                appendLine("✨ Resumen:")
                appendLine(rec.summary)
                appendLine()
            }
            if (rec.rawTranscript.isNotBlank()) {
                appendLine("📝 Transcripción:")
                appendLine(rec.rawTranscript)
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
            .setTitle("Eliminar Grabación")
            .setMessage("¿Estás seguro de que deseas eliminar esta grabación y sus datos de audio? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                playerManager.stop()
                repository.deleteRecording(recordingId)
                Toast.makeText(this, "Grabación eliminada", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupTabs() {
        tabsDetail.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showTabView(viewTabTranscript)
                    1 -> showTabView(viewTabSummary)
                    2 -> showTabView(viewTabTasks)
                    3 -> showTabView(viewTabMindmap)
                    4 -> showTabView(viewTabQa)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showTabView(targetView: View) {
        viewTabTranscript.visibility = if (targetView == viewTabTranscript) View.VISIBLE else View.GONE
        viewTabSummary.visibility = if (targetView == viewTabSummary) View.VISIBLE else View.GONE
        viewTabTasks.visibility = if (targetView == viewTabTasks) View.VISIBLE else View.GONE
        viewTabMindmap.visibility = if (targetView == viewTabMindmap) View.VISIBLE else View.GONE
        viewTabQa.visibility = if (targetView == viewTabQa) View.VISIBLE else View.GONE
    }

    // AudioPlayerManager.Listener callbacks
    override fun onPlaybackStarted(path: String, durationMs: Int) {
        runOnUiThread {
            btnMainPlayPause.setIconResource(R.drawable.ic_pause)
            playerSeekBar.max = durationMs
            tvTotalDuration.text = formatMs(durationMs)
        }
    }

    override fun onPlaybackProgress(currentMs: Int, durationMs: Int) {
        runOnUiThread {
            playerSeekBar.progress = currentMs
            tvCurrentPosition.text = formatMs(currentMs)
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
            btnMainPlayPause.setIconResource(R.drawable.ic_play_arrow)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatMs(ms: Int): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.stop()
        mindMapController.destroy()
    }

    companion object {
        const val EXTRA_RECORDING_ID = "EXTRA_RECORDING_ID"
        const val EXTRA_AUTO_PROCESS = "EXTRA_AUTO_PROCESS"
    }
}
