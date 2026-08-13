package com.myvu.client.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
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
    private lateinit var txtNewNote: TextInputEditText
    private lateinit var txtNewReminder: TextInputEditText
    private lateinit var btnPickTime: MaterialButton
    private lateinit var rvNotes: RecyclerView
    private lateinit var rvReminders: RecyclerView

    private val noteAdapter = NoteAdapter()
    private val reminderAdapter = ReminderAdapter()

    private var selectedTime: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)

        noteRepo = NoteRepository(this)
        reminderRepo = ReminderRepository(this)
        voiceRecorder = VoiceNoteRecorder(this)

        pageNotes = findViewById(R.id.pageNotes)
        pageReminders = findViewById(R.id.pageReminders)
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
        setupActions()
        loadData()
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
        loadNotes()
        loadReminders()
    }

    private fun loadNotes() {
        noteAdapter.setNotes(noteRepo.getAllNotes())
    }

    private fun loadReminders() {
        reminderAdapter.setReminders(reminderRepo.getPendingReminders())
    }

    fun showNoteDialog(existingNote: Note?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_note, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

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
        val btnDelete: MaterialButton = view.findViewById(R.id.btnDeleteNote)
        val btnCancel: MaterialButton = view.findViewById(R.id.btnCancelNote)
        val btnSave: MaterialButton = view.findViewById(R.id.btnSaveNote)

        var audioPath: String? = existingNote?.audioPath
        var durationSec: Int = existingNote?.durationSec ?: 0

        lblTitle.text = if (existingNote == null || existingNote.id == 0L) "Nueva Nota" else "Editar Nota"
        txtTitle.setText(existingNote?.title ?: "")
        txtBody.setText(existingNote?.body ?: "")

        if (existingNote?.type == "VOICE") {
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

        btnDelete.visibility = if (existingNote == null || existingNote.id == 0L) View.GONE else View.VISIBLE

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
                    if (transcript.isNotBlank()) {
                        val currentText = txtBody.text?.toString() ?: ""
                        txtBody.setText(if (currentText.isBlank()) transcript else "$currentText\n$transcript")
                    }
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
            if (existingNote != null && existingNote.id != 0L) {
                noteRepo.deleteNote(existingNote.id)
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
            val isVoice = toggleType.checkedButtonId == R.id.btnTypeVoice
            val type = if (isVoice) "VOICE" else "TEXT"

            if (title.isEmpty() && body.isEmpty()) {
                Toast.makeText(this, "Ingresa un título o contenido para la nota", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (existingNote == null || existingNote.id == 0L) {
                noteRepo.createNote(
                    title = title,
                    body = body,
                    type = type,
                    audioPath = audioPath,
                    durationSec = durationSec
                )
            } else {
                existingNote.title = title
                existingNote.body = body
                existingNote.type = type
                existingNote.audioPath = audioPath
                existingNote.durationSec = durationSec
                noteRepo.update(existingNote)
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

    fun showReminderDialog(existingReminder: Reminder?) {
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
            val textDisplay = if (n.title.isNotBlank()) "${n.title}\n${n.body}" else n.body
            holder.lblBody.text = textDisplay
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            holder.lblDate.text = sdf.format(Date(n.updatedAt))

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
            val lblBody: TextView = itemView.findViewById(R.id.lblNoteBody)
            val lblDate: TextView = itemView.findViewById(R.id.lblNoteDate)
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
            val textDisplay = if (r.title.isNotBlank()) "${r.title}\n${r.body}" else r.body
            holder.lblBody.text = textDisplay
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            holder.lblTime.text = "Programado: " + sdf.format(Date(r.triggerAt))
            holder.lblState.text = "Estado: " + r.state

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
            val lblBody: TextView = itemView.findViewById(R.id.lblReminderBody)
            val lblTime: TextView = itemView.findViewById(R.id.lblReminderTime)
            val lblState: TextView = itemView.findViewById(R.id.lblReminderState)
            val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteReminder)
        }
    }
}
