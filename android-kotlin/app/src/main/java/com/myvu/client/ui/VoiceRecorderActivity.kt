package com.myvu.client.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.myvu.client.R
import com.myvu.client.ai.MeetingAiProcessor
import com.myvu.client.database.VoiceRecording
import com.myvu.client.database.VoiceRecordingRepository
import com.myvu.client.recorder.AudioPlayerManager

class VoiceRecorderActivity : AppCompatActivity() {

    private lateinit var repository: VoiceRecordingRepository
    private lateinit var aiProcessor: MeetingAiProcessor
    private val playerManager = AudioPlayerManager()

    // Views
    private lateinit var toolbar: MaterialToolbar
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageView
    private lateinit var chipGroupCategories: ChipGroup
    private lateinit var scrollTags: HorizontalScrollView
    private lateinit var chipGroupTags: ChipGroup
    private lateinit var rvRecordings: RecyclerView
    private lateinit var layEmpty: LinearLayout
    private lateinit var fabRecord: ExtendedFloatingActionButton

    private lateinit var adapter: VoiceRecordingAdapter

    private var currentCategoryFilter: String = "ALL"
    private var currentTagFilter: String = "ALL"
    private var currentSearchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_recorder)

        repository = VoiceRecordingRepository(this)
        aiProcessor = MeetingAiProcessor(this)

        bindViews()
        setupAdapter()
        setupSearchAndFilters()
        setupPlayerManager()
        setupFab()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun bindViews() {
        toolbar = findViewById(R.id.toolbarVoiceRecorder)
        etSearch = findViewById(R.id.etSearchRecordings)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        chipGroupCategories = findViewById(R.id.chipGroupCategories)
        scrollTags = findViewById(R.id.scrollTags)
        chipGroupTags = findViewById(R.id.chipGroupTags)
        rvRecordings = findViewById(R.id.rvVoiceRecordings)
        layEmpty = findViewById(R.id.layEmptyRecordings)
        fabRecord = findViewById(R.id.fabStartRecording)

        toolbar.setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.btnQuickAiAction)?.setOnClickListener {
            Toast.makeText(this, "🤖 Grabadora Inteligente con Transcripción, Resumen y Mapas Mentales", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAdapter() {
        adapter = VoiceRecordingAdapter(
            context = this,
            onItemClick = { rec ->
                openDetailActivity(rec.id, false)
            },
            onPlayClick = { rec ->
                playerManager.togglePlayPause(rec.audioPath)
            },
            onDeleteClick = { rec ->
                confirmDelete(rec)
            },
            onReAnalyzeClick = { rec ->
                openDetailActivity(rec.id, true)
            },
            onShareClick = { rec ->
                shareRecording(rec)
            }
        )

        rvRecordings.layoutManager = LinearLayoutManager(this)
        rvRecordings.adapter = adapter
    }

    private fun setupSearchAndFilters() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            etSearch.setText("")
        }

        chipGroupCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            currentCategoryFilter = when {
                checkedIds.contains(R.id.chipCatMeeting) -> VoiceRecording.CATEGORY_MEETING
                checkedIds.contains(R.id.chipCatIdea) -> VoiceRecording.CATEGORY_IDEA
                checkedIds.contains(R.id.chipCatConversation) -> VoiceRecording.CATEGORY_CONVERSATION
                else -> "ALL"
            }
            applyFilters()
        }
    }

    private fun setupPlayerManager() {
        playerManager.listener = object : AudioPlayerManager.Listener {
            override fun onPlaybackStarted(path: String, durationMs: Int) {
                adapter.setPlaybackState(path, true)
            }

            override fun onPlaybackProgress(currentMs: Int, durationMs: Int) {}

            override fun onPlaybackPaused() {
                adapter.setPlaybackState(playerManager.currentPath, false)
            }

            override fun onPlaybackResumed() {
                adapter.setPlaybackState(playerManager.currentPath, true)
            }

            override fun onPlaybackStopped() {
                adapter.setPlaybackState(null, false)
            }

            override fun onPlaybackCompleted() {
                adapter.setPlaybackState(null, false)
            }

            override fun onPlaybackError(message: String) {
                adapter.setPlaybackState(null, false)
                Toast.makeText(this@VoiceRecorderActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupFab() {
        fabRecord.setOnClickListener {
            playerManager.stop()
            val sheet = LiveRecordingBottomSheet.newInstance()
            sheet.listener = object : LiveRecordingBottomSheet.Listener {
                override fun onRecordingSaved(recordingId: Long) {
                    loadData()
                    openDetailActivity(recordingId, true)
                }
            }
            sheet.show(supportFragmentManager, LiveRecordingBottomSheet.TAG)
        }
    }

    private fun loadData() {
        loadTagsChips()
        applyFilters()
    }

    private fun loadTagsChips() {
        val tags = repository.getAllTags()
        chipGroupTags.removeAllViews()

        if (tags.isEmpty()) {
            scrollTags.visibility = View.GONE
            return
        }

        scrollTags.visibility = View.VISIBLE

        // "Todos" tag chip
        val allChip = Chip(this).apply {
            text = "Todos los tags"
            isCheckable = true
            isChecked = (currentTagFilter == "ALL")
            setOnClickListener {
                currentTagFilter = "ALL"
                applyFilters()
            }
        }
        chipGroupTags.addView(allChip)

        for (tag in tags) {
            val chip = Chip(this).apply {
                text = "#$tag"
                isCheckable = true
                isChecked = (currentTagFilter == tag)
                setOnClickListener {
                    currentTagFilter = if (isChecked) tag else "ALL"
                    applyFilters()
                }
            }
            chipGroupTags.addView(chip)
        }
    }

    private fun applyFilters() {
        val list = repository.searchRecordings(
            query = currentSearchQuery.ifBlank { null },
            tag = if (currentTagFilter == "ALL") null else currentTagFilter,
            category = if (currentCategoryFilter == "ALL") null else currentCategoryFilter
        )

        adapter.setRecordings(list)

        if (list.isEmpty()) {
            layEmpty.visibility = View.VISIBLE
            rvRecordings.visibility = View.GONE
        } else {
            layEmpty.visibility = View.GONE
            rvRecordings.visibility = View.VISIBLE
        }
    }

    private fun openDetailActivity(id: Long, autoProcess: Boolean) {
        playerManager.stop()
        val intent = Intent(this, RecordingDetailActivity::class.java).apply {
            putExtra(RecordingDetailActivity.EXTRA_RECORDING_ID, id)
            putExtra(RecordingDetailActivity.EXTRA_AUTO_PROCESS, autoProcess)
        }
        startActivity(intent)
    }

    private fun confirmDelete(rec: VoiceRecording) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar grabación")
            .setMessage("¿Deseas eliminar '${rec.title}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                if (playerManager.currentPath == rec.audioPath) {
                    playerManager.stop()
                }
                repository.deleteRecording(rec.id)
                loadData()
                Toast.makeText(this, "Grabación eliminada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun shareRecording(rec: VoiceRecording) {
        val text = buildString {
            append("🎙️ ${rec.title}\n\n")
            if (rec.summary.isNotBlank()) append("📋 RESUMEN:\n${rec.summary}\n\n")
            if (rec.rawTranscript.isNotBlank()) append("💬 TRANSCRIPCIÓN:\n${rec.rawTranscript}\n\n")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, rec.title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Compartir grabación"))
    }

    override fun onDestroy() {
        playerManager.stop()
        super.onDestroy()
    }
}
