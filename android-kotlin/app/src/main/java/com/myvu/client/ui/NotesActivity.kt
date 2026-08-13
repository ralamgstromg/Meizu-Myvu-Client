package com.myvu.client.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.myvu.client.R
import com.myvu.client.ai.VoiceNoteRecorder
import com.myvu.client.database.Note
import com.myvu.client.database.NoteRepository
import com.myvu.client.database.Reminder
import com.myvu.client.database.ReminderRepository
import com.myvu.client.reminder.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NotesActivity : AppCompatActivity() {

    private lateinit var noteRepo: NoteRepository
    private lateinit var reminderRepo: ReminderRepository
    private lateinit var voiceRecorder: VoiceNoteRecorder

    private lateinit var pageNotes: View
    private lateinit var pageReminders: View
    private lateinit var txtSearchNotes: TextInputEditText
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var txtNewNote: TextInputEditText
    private lateinit var txtNewReminder: TextInputEditText
    private lateinit var btnPickTime: MaterialButton
    private lateinit var rvNotes: RecyclerView
    private lateinit var rvReminders: RecyclerView

    private var isFabMenuOpen = false
    private lateinit var fabMain: FloatingActionButton
    private lateinit var fabMenu: View
    private lateinit var fabNewText: View
    private lateinit var fabNewVoice: View
    private lateinit var fabNewReminder: View

    private val noteAdapter = NoteAdapter()
    private val reminderAdapter = ReminderAdapter()

    private var selectedTime: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarNotes)
        val navigateToDashboard = {
            if (isTaskRoot) {
                val intent = Intent(this, ConnectActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
            }
            finish()
        }
        toolbar.setNavigationOnClickListener { navigateToDashboard() }
        findViewById<View?>(R.id.btnNotesDrawer)?.setOnClickListener { navigateToDashboard() }

        noteRepo = NoteRepository(this)
        reminderRepo = ReminderRepository(this)
        voiceRecorder = VoiceNoteRecorder(this)

        pageNotes = findViewById(R.id.pageNotes)
        pageReminders = findViewById(R.id.pageReminders)
        txtSearchNotes = findViewById(R.id.txtSearchNotes)
        chipGroupFilter = findViewById(R.id.chipGroupFilter)
        txtNewNote = findViewById(R.id.txtNewNote)
        txtNewReminder = findViewById(R.id.txtNewReminder)
        btnPickTime = findViewById(R.id.btnPickTime)

        rvNotes = findViewById(R.id.rvNotes)
        rvReminders = findViewById(R.id.rvReminders)

        rvNotes.layoutManager = LinearLayoutManager(this)
        rvReminders.layoutManager = LinearLayoutManager(this)

        rvNotes.adapter = noteAdapter
        rvReminders.adapter = reminderAdapter

        setupTabs()
        setupSearchAndFilter()
        setupActions()
        setupSpeedDialFab()
        loadData()

        handleIntent(intent)
    }

    private fun setupSpeedDialFab() {
        fabMain = findViewById(R.id.fabMain)
        fabMenu = findViewById(R.id.fabMenu)
        fabNewText = findViewById(R.id.fabNewText)
        fabNewVoice = findViewById(R.id.fabNewVoice)
        fabNewReminder = findViewById(R.id.fabNewReminder)

        fabMain.setOnClickListener {
            isFabMenuOpen = !isFabMenuOpen
            if (isFabMenuOpen) {
                fabMenu.visibility = View.VISIBLE
                fabMain.animate().rotation(45f).setDuration(200).start()
            } else {
                fabMenu.visibility = View.GONE
                fabMain.animate().rotation(0f).setDuration(200).start()
            }
        }

        fabNewText.setOnClickListener {
            closeFabMenu()
            showNoteDialog(startInVoiceMode = false)
        }

        fabNewVoice.setOnClickListener {
            closeFabMenu()
            showNoteDialog(startInVoiceMode = true)
        }

        fabNewReminder.setOnClickListener {
            closeFabMenu()
            showReminderDialog()
        }
    }

    private fun closeFabMenu() {
        if (isFabMenuOpen) {
            isFabMenuOpen = false
            fabMenu.visibility = View.GONE
            fabMain.animate().rotation(0f).setDuration(200).start()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("SHOW_REMINDERS", false) || intent.getStringExtra("EXTRA_FILTER") == "REMINDERS") {
            chipGroupFilter.check(R.id.chipFilterReminders)
        }
        if (intent.getBooleanExtra("AUTO_RECORD_VOICE", false)) {
            intent.putExtra("AUTO_RECORD_VOICE", false)
            showNoteDialog(startInVoiceMode = true)
        }
    }

    private fun setupTabs() {
        val tabs: TabLayout = findViewById(R.id.tabsNotes)
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (tab.position == 0) {
                    pageNotes.visibility = View.VISIBLE
                    pageReminders.visibility = View.GONE
                } else {
                    pageNotes.visibility = View.GONE
                    pageReminders.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupSearchAndFilter() {
        txtSearchNotes.doOnTextChanged { _, _, _, _ ->
            performSearch()
        }

        chipGroupFilter.setOnCheckedStateChangeListener { _, _ ->
            performSearch()
        }
    }

    private fun performSearch() {
        val query = txtSearchNotes.text?.toString()?.trim() ?: ""
        val checkedId = chipGroupFilter.checkedChipId

        val notes: List<Note>
        val reminders: List<Reminder>

        when (checkedId) {
            R.id.chipFilterText -> {
                notes = noteRepo.search(query, "TEXT")
                reminders = emptyList()
            }
            R.id.chipFilterVoice -> {
                notes = noteRepo.search(query, "VOICE")
                reminders = emptyList()
            }
            R.id.chipFilterReminders -> {
                notes = emptyList()
                reminders = reminderRepo.search(query, "ALL")
            }
            R.id.chipFilterPending -> {
                notes = emptyList()
                reminders = reminderRepo.search(query, "PENDING")
            }
            else -> { // chipFilterAll or default
                notes = noteRepo.search(query, "ALL")
                reminders = reminderRepo.search(query, "ALL")
            }
        }

        val tabs: TabLayout = findViewById(R.id.tabsNotes)
        if (checkedId == R.id.chipFilterReminders || checkedId == R.id.chipFilterPending) {
            tabs.getTabAt(1)?.select()
        } else if (checkedId == R.id.chipFilterText || checkedId == R.id.chipFilterVoice) {
            tabs.getTabAt(0)?.select()
        }

        noteAdapter.setNotes(notes)
        reminderAdapter.setReminders(reminders)
    }

    private fun setupActions() {
        findViewById<View>(R.id.btnAddNote).setOnClickListener {
            val text = txtNewNote.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                showNoteDialog(Note(body = text))
                txtNewNote.setText("")
            } else {
                showNoteDialog(null)
            }
        }

        btnPickTime.setOnClickListener {
            val now = Calendar.getInstance()
            TimePickerDialog(this, { _, hourOfDay, minute ->
                selectedTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                btnPickTime.text = "Hora: " + sdf.format(selectedTime.time)
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
        }

        findViewById<View>(R.id.btnAddReminder).setOnClickListener {
            val text = txtNewReminder.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                val triggerAt = selectedTime.timeInMillis
                showReminderDialog(Reminder(body = text, triggerAt = triggerAt))
                txtNewReminder.setText("")
            } else {
                showReminderDialog(null)
            }
        }
    }

    private fun loadData() {
        performSearch()
    }

    private fun loadNotes() {
        performSearch()
    }

    private fun loadReminders() {
        performSearch()
    }

    fun showNoteDialog(existingNote: Note? = null, startInVoiceMode: Boolean = false) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_note, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        var currentNote: Note? = existingNote

        val lblTitle: TextView = view.findViewById(R.id.lblDialogNoteTitle)
        val txtTitle: TextInputEditText = view.findViewById(R.id.txtNoteTitle)
        val toggleType: MaterialButtonToggleGroup = view.findViewById(R.id.toggleNoteType)
        val btnTypeText: MaterialButton = view.findViewById(R.id.btnTypeText)
        val btnTypeVoice: MaterialButton = view.findViewById(R.id.btnTypeVoice)
        val layoutVoice: LinearLayout = view.findViewById(R.id.layoutVoiceSection)
        val btnRecordVoice: MaterialButton = view.findViewById(R.id.btnRecordVoice)
        val btnPlayAudio: MaterialButton = view.findViewById(R.id.btnPlayAudio)
        val lblVoiceStatus: TextView = view.findViewById(R.id.lblVoiceStatus)
        val txtBody: TextInputEditText = view.findViewById(R.id.txtNoteBody)
        val txtTags: TextInputEditText = view.findViewById(R.id.txtNoteTags)
        val btnDelete: MaterialButton = view.findViewById(R.id.btnDeleteNote)
        val btnCancel: MaterialButton = view.findViewById(R.id.btnCancelNote)
        val btnSave: MaterialButton = view.findViewById(R.id.btnSaveNote)

        var audioPath: String? = currentNote?.audioPath
        var durationSec: Int = currentNote?.durationSec ?: 0

        lblTitle.text = if (currentNote == null || currentNote!!.id == 0L) "Nueva Nota" else "Editar Nota"
        txtTitle.setText(currentNote?.title ?: "")
        txtBody.setText(currentNote?.body ?: "")
        txtTags.setText(currentNote?.tags ?: "")

        if (currentNote?.type == "VOICE" || (currentNote == null && startInVoiceMode)) {
            toggleType.check(R.id.btnTypeVoice)
            layoutVoice.visibility = View.VISIBLE
        } else {
            toggleType.check(R.id.btnTypeText)
            layoutVoice.visibility = View.GONE
        }

        if (!audioPath.isNullOrEmpty()) {
            btnPlayAudio.visibility = View.VISIBLE
            lblVoiceStatus.text = "Nota de voz grabada"
        }

        btnDelete.visibility = if (currentNote == null || currentNote!!.id == 0L) View.GONE else View.VISIBLE

        toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnTypeVoice) {
                    layoutVoice.visibility = View.VISIBLE
                } else {
                    layoutVoice.visibility = View.GONE
                }
            }
        }

        btnRecordVoice.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
                return@setOnClickListener
            }

            if (voiceRecorder.isCurrentlyRecording) {
                btnRecordVoice.text = "Transcribiendo..."
                btnRecordVoice.isEnabled = false
                voiceRecorder.stopRecording { path, transcript ->
                    audioPath = path
                    durationSec = voiceRecorder.getRecordingDurationSeconds()
                    val newBody: String
                    if (transcript.isNotBlank()) {
                        val currentText = txtBody.text?.toString() ?: ""
                        newBody = if (currentText.isBlank()) transcript else "$currentText\n$transcript"
                        txtBody.setText(newBody)
                    } else {
                        newBody = txtBody.text?.toString() ?: ""
                    }

                    // Auto-save STT transcription to SQLite
                    val title = txtTitle.text?.toString()?.trim() ?: ""
                    val tags = txtTags.text?.toString()?.trim() ?: ""
                    val type = "VOICE"

                    if (currentNote == null || currentNote!!.id == 0L) {
                        val createdId = noteRepo.createNote(
                            title = title,
                            body = newBody,
                            type = type,
                            audioPath = audioPath,
                            durationSec = durationSec,
                            tags = tags
                        )
                        if (createdId != -1L) {
                            currentNote = noteRepo.getById(createdId)
                        }
                    } else {
                        currentNote?.let { n ->
                            n.title = title
                            n.body = newBody
                            n.type = type
                            n.audioPath = audioPath
                            n.durationSec = durationSec
                            n.tags = tags
                            noteRepo.update(n)
                        }
                    }

                    loadNotes()

                    btnRecordVoice.text = "Grabar Voz"
                    btnRecordVoice.isEnabled = true
                    lblVoiceStatus.text = "Transcripción completada"
                    if (!audioPath.isNullOrEmpty()) {
                        btnPlayAudio.visibility = View.VISIBLE
                    }
                }
            } else {
                val recordedFile = voiceRecorder.startRecording()
                if (recordedFile != null) {
                    btnRecordVoice.text = "Detener Grabar"
                    lblVoiceStatus.text = "Grabando audio..."
                } else {
                    Toast.makeText(this, "No se pudo iniciar la grabación", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnPlayAudio.setOnClickListener {
            val path = audioPath
            if (!path.isNullOrEmpty()) {
                lblVoiceStatus.text = "Reproduciendo..."
                voiceRecorder.playAudio(path) {
                    lblVoiceStatus.text = "Reproducción finalizada"
                }
            }
        }

        btnDelete.setOnClickListener {
            if (currentNote != null && currentNote!!.id != 0L) {
                noteRepo.deleteNote(currentNote!!.id)
                loadNotes()
                Toast.makeText(this, "Nota eliminada", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            if (voiceRecorder.isCurrentlyRecording) {
                voiceRecorder.cancelRecording()
            }
            voiceRecorder.stopPlayback()
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val title = txtTitle.text?.toString()?.trim() ?: ""
            val body = txtBody.text?.toString()?.trim() ?: ""
            val tags = txtTags.text?.toString()?.trim() ?: ""
            val isVoice = toggleType.checkedButtonId == R.id.btnTypeVoice
            val type = if (isVoice) "VOICE" else "TEXT"

            if (title.isEmpty() && body.isEmpty()) {
                Toast.makeText(this, "Ingresa un título o contenido para la nota", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (currentNote == null || currentNote!!.id == 0L) {
                noteRepo.createNote(
                    title = title,
                    body = body,
                    type = type,
                    audioPath = audioPath,
                    durationSec = durationSec,
                    tags = tags
                )
            } else {
                currentNote?.let { n ->
                    n.title = title
                    n.body = body
                    n.type = type
                    n.audioPath = audioPath
                    n.durationSec = durationSec
                    n.tags = tags
                    noteRepo.update(n)
                }
            }

            loadNotes()
            Toast.makeText(this, "Nota guardada", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            if (voiceRecorder.isCurrentlyRecording) {
                voiceRecorder.cancelRecording()
            }
            voiceRecorder.stopPlayback()
        }

        dialog.show()
    }

    fun showReminderDialog(existingReminder: Reminder? = null) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_reminder, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val lblTitle: TextView = view.findViewById(R.id.lblDialogReminderTitle)
        val txtTitle: TextInputEditText = view.findViewById(R.id.txtReminderTitle)
        val txtBody: TextInputEditText = view.findViewById(R.id.txtReminderBody)
        val btnPickDate: MaterialButton = view.findViewById(R.id.btnPickDate)
        val btnPickReminderTime: MaterialButton = view.findViewById(R.id.btnPickReminderTime)
        val btnDelete: MaterialButton = view.findViewById(R.id.btnDeleteReminder)
        val btnCancel: MaterialButton = view.findViewById(R.id.btnCancelReminder)
        val btnSave: MaterialButton = view.findViewById(R.id.btnSaveReminder)

        val calendar = Calendar.getInstance()
        if (existingReminder != null && existingReminder.triggerAt > 0L) {
            calendar.timeInMillis = existingReminder.triggerAt
        } else {
            calendar.add(Calendar.MINUTE, 30)
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun updateDateTimeButtons() {
            btnPickDate.text = "Fecha: " + dateFormat.format(calendar.time)
            btnPickReminderTime.text = "Hora: " + timeFormat.format(calendar.time)
        }

        updateDateTimeButtons()

        lblTitle.text = if (existingReminder == null || existingReminder.id == 0L) "Nuevo Recordatorio" else "Editar Recordatorio"
        txtTitle.setText(existingReminder?.title ?: "")
        txtBody.setText(existingReminder?.body ?: "")

        btnDelete.visibility = if (existingReminder == null || existingReminder.id == 0L) View.GONE else View.VISIBLE

        btnPickDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    updateDateTimeButtons()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnPickReminderTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)
                    updateDateTimeButtons()
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }

        btnDelete.setOnClickListener {
            if (existingReminder != null && existingReminder.id != 0L) {
                ReminderScheduler.cancelReminder(this, existingReminder.alarmRequestCode)
                reminderRepo.deleteReminder(existingReminder.id)
                loadReminders()
                Toast.makeText(this, "Recordatorio eliminado", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val title = txtTitle.text?.toString()?.trim() ?: ""
            val body = txtBody.text?.toString()?.trim() ?: ""
            val triggerAt = calendar.timeInMillis

            if (title.isEmpty() && body.isEmpty()) {
                Toast.makeText(this, "Ingresa un título o descripción para el recordatorio", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (existingReminder == null || existingReminder.id == 0L) {
                val reminder = reminderRepo.createReminder(title, body, triggerAt)
                if (reminder != null) {
                    ReminderScheduler.scheduleReminder(this, reminder.id, triggerAt, reminder.alarmRequestCode)
                }
            } else {
                existingReminder.title = title
                existingReminder.body = body
                existingReminder.triggerAt = triggerAt
                reminderRepo.update(existingReminder)
                ReminderScheduler.scheduleReminder(this, existingReminder.id, triggerAt, existingReminder.alarmRequestCode)
            }

            loadReminders()
            Toast.makeText(this, "Recordatorio guardado", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private inner class NoteAdapter : RecyclerView.Adapter<NoteAdapter.NoteVH>() {
        private var list: List<Note> = emptyList()

        fun setNotes(notes: List<Note>) {
            this.list = notes
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteVH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
            return NoteVH(v)
        }

        override fun onBindViewHolder(holder: NoteVH, position: Int) {
            val n = list[position]

            if (n.type == "VOICE") {
                holder.lblCategory.text = "VOZ"
            } else {
                holder.lblCategory.text = "TEXTO"
            }

            if (n.title.isNotBlank()) {
                holder.lblTitle.text = n.title
                holder.lblTitle.visibility = View.VISIBLE
            } else {
                holder.lblTitle.visibility = View.GONE
            }

            holder.lblBody.text = n.body
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            holder.lblDate.text = sdf.format(Date(n.updatedAt))

            if (!n.audioPath.isNullOrEmpty()) {
                holder.btnPlayAudio.visibility = View.VISIBLE
                holder.btnPlayAudio.setOnClickListener {
                    voiceRecorder.playAudio(n.audioPath!!) {
                        // Playback finished
                    }
                }
            } else {
                holder.btnPlayAudio.visibility = View.GONE
            }

            holder.chipGroupTags.removeAllViews()
            if (n.tags.isNotBlank()) {
                val tagList = n.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (tagList.isNotEmpty()) {
                    holder.chipGroupTags.visibility = View.VISIBLE
                    for (rawTag in tagList) {
                        val displayTag = if (rawTag.startsWith("#")) rawTag else "#$rawTag"
                        val chip = Chip(this@NotesActivity).apply {
                            text = displayTag
                            isClickable = true
                            isCheckable = false
                            setChipBackgroundColorResource(R.color.obsidian_container_high)
                            setTextColor(ContextCompat.getColor(context, R.color.cyber_teal))
                            setOnClickListener {
                                txtSearchNotes.setText(displayTag)
                                txtSearchNotes.setSelection(txtSearchNotes.text?.length ?: 0)
                                performSearch()
                            }
                        }
                        holder.chipGroupTags.addView(chip)
                    }
                } else {
                    holder.chipGroupTags.visibility = View.GONE
                }
            } else {
                holder.chipGroupTags.visibility = View.GONE
            }

            holder.btnEdit.setOnClickListener {
                showNoteDialog(n)
            }

            holder.itemView.setOnClickListener {
                showNoteDialog(n)
            }

            holder.btnDelete.setOnClickListener {
                noteRepo.deleteNote(n.id)
                loadNotes()
                Toast.makeText(this@NotesActivity, "Nota eliminada", Toast.LENGTH_SHORT).show()
            }
        }

        override fun getItemCount(): Int = list.size

        inner class NoteVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val lblCategory: TextView = itemView.findViewById(R.id.lblNoteCategory)
            val lblTitle: TextView = itemView.findViewById(R.id.lblNoteTitle)
            val lblBody: TextView = itemView.findViewById(R.id.lblNoteBody)
            val lblDate: TextView = itemView.findViewById(R.id.lblNoteDate)
            val chipGroupTags: ChipGroup = itemView.findViewById(R.id.chipGroupTags)
            val btnPlayAudio: ImageButton = itemView.findViewById(R.id.btnPlayAudio)
            val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditNote)
            val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteNote)
        }
    }

    private inner class ReminderAdapter : RecyclerView.Adapter<ReminderAdapter.ReminderVH>() {
        private var list: List<Reminder> = emptyList()

        fun setReminders(reminders: List<Reminder>) {
            this.list = reminders
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderVH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
            return ReminderVH(v)
        }

        override fun onBindViewHolder(holder: ReminderVH, position: Int) {
            val r = list[position]

            holder.lblCategory.text = "HUD SYNC"

            if (r.title.isNotBlank()) {
                holder.lblTitle.text = r.title
                holder.lblTitle.visibility = View.VISIBLE
            } else {
                holder.lblTitle.visibility = View.GONE
            }

            holder.lblBody.text = r.body
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            holder.lblTime.text = "Programado: " + sdf.format(Date(r.triggerAt))
            holder.lblState.text = "Estado: " + r.state

            holder.btnPlayAudio.visibility = View.GONE

            holder.btnEdit.setOnClickListener {
                showReminderDialog(r)
            }

            holder.itemView.setOnClickListener {
                showReminderDialog(r)
            }

            holder.btnDelete.setOnClickListener {
                ReminderScheduler.cancelReminder(this@NotesActivity, r.alarmRequestCode)
                reminderRepo.deleteReminder(r.id)
                loadReminders()
                Toast.makeText(this@NotesActivity, "Recordatorio eliminado", Toast.LENGTH_SHORT).show()
            }
        }

        override fun getItemCount(): Int = list.size

        inner class ReminderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val lblCategory: TextView = itemView.findViewById(R.id.lblReminderCategory)
            val lblTitle: TextView = itemView.findViewById(R.id.lblReminderTitle)
            val lblBody: TextView = itemView.findViewById(R.id.lblReminderBody)
            val lblTime: TextView = itemView.findViewById(R.id.lblReminderTime)
            val lblState: TextView = itemView.findViewById(R.id.lblReminderState)
            val btnPlayAudio: ImageButton = itemView.findViewById(R.id.btnPlayAudio)
            val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditReminder)
            val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteReminder)
        }
    }
}
