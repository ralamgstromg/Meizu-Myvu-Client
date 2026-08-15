package com.myvu.client.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
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
import com.myvu.client.core.LogBus
import com.myvu.client.database.TodoRepository
import com.myvu.client.database.VoiceRecording
import com.myvu.client.database.VoiceRecordingRepository
import com.myvu.client.recorder.AudioPlayerManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RecordingDetailActivity : AppCompatActivity() {

    private var recordingId: Long = -1L
    private var recording: VoiceRecording? = null
    private lateinit var repository: VoiceRecordingRepository
    private lateinit var aiProcessor: MeetingAiProcessor
    private val playerManager = AudioPlayerManager()

    // Views
    private lateinit var toolbar: MaterialToolbar
    private lateinit var playerSeekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
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
    private lateinit var btnExportAllTasksToTodo: MaterialButton
    private lateinit var rvMeetingTasks: LinearLayout
    private lateinit var tvMindmapContent: TextView

    // QA Chat
    private lateinit var layQaChatMessages: LinearLayout
    private lateinit var etQaQuestion: EditText
    private lateinit var btnSendQaQuestion: MaterialButton

    private var currentSpeedIndex = 0
    private val speedOptions = floatArrayOf(1.0f, 1.25f, 1.5f, 2.0f)
    private val speedLabels = arrayOf("1.0x", "1.25x", "1.5x", "2.0x")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recording_detail)

        recordingId = intent.getLongExtra(EXTRA_RECORDING_ID, -1L)
        repository = VoiceRecordingRepository(this)
        aiProcessor = MeetingAiProcessor(this)

        bindViews()
        setupPlayer()
        setupTabs()
        setupTopBarActions()
        setupQaChat()

        loadRecordingData()

        val autoProcess = intent.getBooleanExtra(EXTRA_AUTO_PROCESS, false)
        if (autoProcess || (recording?.summary.isNullOrBlank() && recording?.rawTranscript.isNullOrBlank())) {
            startAiProcessing()
        }
    }

    private fun bindViews() {
        toolbar = findViewById(R.id.toolbarDetail)
        playerSeekBar = findViewById(R.id.playerSeekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalDuration = findViewById(R.id.tvTotalDuration)
        btnMainPlayPause = findViewById(R.id.btnMainPlayPause)
        btnRewind5s = findViewById(R.id.btnRewind5s)
        btnForward5s = findViewById(R.id.btnForward5s)
        btnSpeedToggle = findViewById(R.id.btnSpeedToggle)
        tabsDetail = findViewById(R.id.tabsDetail)

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
        btnExportAllTasksToTodo = findViewById(R.id.btnExportAllTasksToTodo)
        rvMeetingTasks = findViewById(R.id.rvMeetingTasks)
        tvMindmapContent = findViewById(R.id.tvMindmapContent)

        layQaChatMessages = findViewById(R.id.layQaChatMessages)
        etQaQuestion = findViewById(R.id.etQaQuestion)
        btnSendQaQuestion = findViewById(R.id.btnSendQaQuestion)
    }

    private fun loadRecordingData() {
        recording = repository.getRecordingById(recordingId)
        val rec = recording ?: run {
            Toast.makeText(this, "Grabación no encontrada", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        toolbar.title = rec.title.ifBlank { "Detalle de Grabación" }
        toolbar.subtitle = "${rec.category} • ${rec.formattedDuration()}"
        tvTotalDuration.text = rec.formattedDuration()

        // Populate Transcript & Diarization
        populateTranscript(rec)

        // Populate Summary
        tvSummaryContent.text = if (rec.summary.isNotBlank()) rec.summary else "Aún no se ha generado un resumen. Toca el botón ✨ para procesar con IA."

        // Populate Tasks
        populateTasks(rec)

        // Populate Mind Map
        tvMindmapContent.text = if (rec.mindmapData.isNotBlank()) rec.mindmapData else "Mapa mental no generado todavía."
    }

    private fun populateTranscript(rec: VoiceRecording) {
        layDiarizedContainer.removeAllViews()

        if (rec.diarizedTranscript.isNotBlank()) {
            try {
                val array = JSONArray(rec.diarizedTranscript)
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
                    tvText.text = text

                    layDiarizedContainer.addView(itemView)
                }
                return
            } catch (e: Exception) {
                LogBus.warn("RecordingDetailActivity: Error parsing diarization JSON: ${e.message}")
            }
        }

        // Fallback to raw text
        tvRawTranscript.text = if (rec.rawTranscript.isNotBlank()) rec.rawTranscript else "Sin transcripción disponible."
        layDiarizedContainer.addView(tvRawTranscript)
    }

    private fun populateTasks(rec: VoiceRecording) {
        rvMeetingTasks.removeAllViews()
        val json = rec.actionItems
        if (json.isBlank() || json == "[]") {
            val emptyTv = TextView(this).apply {
                text = "No se encontraron tareas pendientes en esta grabación."
                setTextColor(ContextCompat.getColor(context, R.color.outline_obsidian))
                setPadding(0, 16, 0, 0)
            }
            rvMeetingTasks.addView(emptyTv)
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

                val itemView = inflater.inflate(R.layout.item_meeting_task, rvMeetingTasks, false)
                val cbTask: CheckBox = itemView.findViewById(R.id.cbMeetingTask)
                val tvTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
                val tvOwner: TextView = itemView.findViewById(R.id.tvTaskOwner)
                val tvDeadline: TextView = itemView.findViewById(R.id.tvTaskDeadline)
                val btnAddSingle: MaterialButton = itemView.findViewById(R.id.btnExportSingleTask)

                tvTitle.text = task
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

                btnAddSingle.setOnClickListener {
                    val todoRepo = TodoRepository(this)
                    todoRepo.createTodo(listName = "Reuniones", title = task, tags = "reunion,voz")
                    Toast.makeText(this, "Tarea añadida a To-Do", Toast.LENGTH_SHORT).show()
                }

                rvMeetingTasks.addView(itemView)
            }
        } catch (e: Exception) {
            LogBus.error("RecordingDetailActivity: Error parsing tasks JSON", e)
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
                    tvCurrentTime.text = String.format("%02d:%02d", mins, secs)
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
                    tvCurrentTime.text = "00:00"
                }
            }

            override fun onPlaybackCompleted() {
                runOnUiThread {
                    btnMainPlayPause.setIconResource(R.drawable.ic_play_arrow)
                    playerSeekBar.progress = 0
                    tvCurrentTime.text = "00:00"
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
            playerManager.togglePlayPause(rec.audioPath)
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
            val rec = recording ?: return@setOnClickListener
            val target = (playerSeekBar.progress - 5000).coerceAtLeast(0)
            playerManager.seekTo(target)
        }

        btnForward5s.setOnClickListener {
            val rec = recording ?: return@setOnClickListener
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

    private fun setupTopBarActions() {
        toolbar.setNavigationOnClickListener { finish() }

        findViewById<View>(R.id.btnReAnalyzeAi).setOnClickListener {
            startAiProcessing()
        }

        findViewById<View>(R.id.btnSendToGlasses).setOnClickListener {
            sendSummaryToGlasses()
        }

        findViewById<View>(R.id.btnShareRecording).setOnClickListener {
            shareRecordingSummary()
        }

        findViewById<View>(R.id.btnDeleteRecording).setOnClickListener {
            confirmDelete()
        }
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

    private fun setupQaChat() {
        btnSendQaQuestion.setOnClickListener {
            val q = etQaQuestion.text?.toString()?.trim()
            if (q.isNullOrBlank()) return@setOnClickListener

            val rec = recording ?: return@setOnClickListener
            etQaQuestion.setText("")

            // Add user question bubble
            addChatBubble("🧑‍💻 Tú", q, true)

            // Query AI
            aiProcessor.askQuestionAboutRecording(rec, q) { result ->
                runOnUiThread {
                    result.onSuccess { answer ->
                        addChatBubble("✨ Asistente IA", answer, false)
                    }.onFailure { error ->
                        addChatBubble("⚠️ Error", "No pude procesar la consulta: ${error.message}", false)
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

        val tvSender = TextView(this).apply {
            text = sender
            setTextColor(ContextCompat.getColor(context, if (isUser) R.color.cyber_teal else R.color.cyber_teal_light))
            textSize = 12f
            paint.isFakeBoldText = true
        }

        val tvMsg = TextView(this).apply {
            text = message
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_obsidian))
            textSize = 14f
            setLineSpacing(0f, 1.3f)
            setTextIsSelectable(true)
            setPadding(0, 6, 0, 0)
        }

        inner.addView(tvSender)
        inner.addView(tvMsg)
        card.addView(inner)
        layQaChatMessages.addView(card)
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
