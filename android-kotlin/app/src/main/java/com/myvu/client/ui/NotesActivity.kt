package com.myvu.client.ui

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.myvu.client.R
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
                noteRepo.createNote(text)
                txtNewNote.setText("")
                loadNotes()
                Toast.makeText(this, "Nota guardada", Toast.LENGTH_SHORT).show()
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
                val reminder = reminderRepo.createReminder(text, triggerAt)
                if (reminder != null) {
                    ReminderScheduler.scheduleReminder(this, reminder.id, triggerAt, reminder.alarmRequestCode)
                    txtNewReminder.setText("")
                    loadReminders()
                    Toast.makeText(this, "Recordatorio programado", Toast.LENGTH_SHORT).show()
                }
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
            holder.lblBody.text = n.body
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            holder.lblDate.text = sdf.format(Date(n.updatedAt))
            holder.btnDelete.setOnClickListener {
                noteRepo.deleteNote(n.id)
                loadNotes()
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
            holder.lblBody.text = r.body
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            holder.lblTime.text = "Programado: " + sdf.format(Date(r.triggerAt))
            holder.lblState.text = "Estado: " + r.state

            holder.btnDelete.setOnClickListener {
                ReminderScheduler.cancelReminder(this@NotesActivity, r.alarmRequestCode)
                reminderRepo.deleteReminder(r.id)
                loadReminders()
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
