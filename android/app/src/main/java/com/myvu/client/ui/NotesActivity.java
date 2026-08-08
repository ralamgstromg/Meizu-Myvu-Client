package com.myvu.client.ui;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.myvu.client.R;
import com.myvu.client.database.Note;
import com.myvu.client.database.NoteRepository;
import com.myvu.client.database.Reminder;
import com.myvu.client.database.ReminderRepository;
import com.myvu.client.reminder.ReminderScheduler;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotesActivity extends AppCompatActivity {

    private NoteRepository noteRepo;
    private ReminderRepository reminderRepo;

    private View pageNotes, pageReminders;
    private TextInputEditText txtNewNote, txtNewReminder;
    private MaterialButton btnPickTime;
    private RecyclerView rvNotes, rvReminders;

    private NoteAdapter noteAdapter;
    private ReminderAdapter reminderAdapter;

    private Calendar selectedTime = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        noteRepo = new NoteRepository(this);
        reminderRepo = new ReminderRepository(this);

        pageNotes = findViewById(R.id.pageNotes);
        pageReminders = findViewById(R.id.pageReminders);
        txtNewNote = findViewById(R.id.txtNewNote);
        txtNewReminder = findViewById(R.id.txtNewReminder);
        btnPickTime = findViewById(R.id.btnPickTime);

        rvNotes = findViewById(R.id.rvNotes);
        rvReminders = findViewById(R.id.rvReminders);

        rvNotes.setLayoutManager(new LinearLayoutManager(this));
        rvReminders.setLayoutManager(new LinearLayoutManager(this));

        noteAdapter = new NoteAdapter();
        reminderAdapter = new ReminderAdapter();
        rvNotes.setAdapter(noteAdapter);
        rvReminders.setAdapter(reminderAdapter);

        setupTabs();
        setupActions();
        loadData();
    }

    private void setupTabs() {
        TabLayout tabs = findViewById(R.id.tabsNotes);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    pageNotes.setVisibility(View.VISIBLE);
                    pageReminders.setVisibility(View.GONE);
                } else {
                    pageNotes.setVisibility(View.GONE);
                    pageReminders.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupActions() {
        findViewById(R.id.btnAddNote).setOnClickListener(v -> {
            String text = txtNewNote.getText() != null ? txtNewNote.getText().toString().trim() : "";
            if (!text.isEmpty()) {
                noteRepo.insertNote(text);
                txtNewNote.setText("");
                loadNotes();
                Toast.makeText(this, "Nota guardada", Toast.LENGTH_SHORT).show();
            }
        });

        btnPickTime.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                selectedTime = Calendar.getInstance();
                selectedTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                selectedTime.set(Calendar.MINUTE, minute);
                selectedTime.set(Calendar.SECOND, 0);
                if (selectedTime.getTimeInMillis() <= System.currentTimeMillis()) {
                    selectedTime.add(Calendar.DAY_OF_YEAR, 1);
                }
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                btnPickTime.setText("Hora: " + sdf.format(selectedTime.getTime()));
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();
        });

        findViewById(R.id.btnAddReminder).setOnClickListener(v -> {
            String text = txtNewReminder.getText() != null ? txtNewReminder.getText().toString().trim() : "";
            if (!text.isEmpty()) {
                long triggerAt = selectedTime.getTimeInMillis();
                int reqCode = (int) (System.currentTimeMillis() & 0x7FFFFFFF);
                long id = reminderRepo.insertReminder(text, triggerAt, reqCode);
                if (id != -1) {
                    ReminderScheduler.scheduleReminder(this, id, triggerAt, reqCode);
                    txtNewReminder.setText("");
                    loadReminders();
                    Toast.makeText(this, "Recordatorio programado", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loadData() {
        loadNotes();
        loadReminders();
    }

    private void loadNotes() {
        noteAdapter.setNotes(noteRepo.getActiveNotes());
    }

    private void loadReminders() {
        reminderAdapter.setReminders(reminderRepo.getAllReminders());
    }

    // ------------------------------------------------------------- Adapters

    private class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteVH> {
        private List<Note> list;

        void setNotes(List<Note> notes) {
            this.list = notes;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public NoteVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
            return new NoteVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull NoteVH holder, int position) {
            Note n = list.get(position);
            holder.lblBody.setText(n.getBody());
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            holder.lblDate.setText(sdf.format(new Date(n.getUpdatedAt())));
            holder.btnDelete.setOnClickListener(v -> {
                noteRepo.deleteNote(n.getId());
                loadNotes();
            });
        }

        @Override
        public int getItemCount() { return list != null ? list.size() : 0; }

        class NoteVH extends RecyclerView.ViewHolder {
            TextView lblBody, lblDate;
            ImageButton btnDelete;
            NoteVH(View itemView) {
                super(itemView);
                lblBody = itemView.findViewById(R.id.lblNoteBody);
                lblDate = itemView.findViewById(R.id.lblNoteDate);
                btnDelete = itemView.findViewById(R.id.btnDeleteNote);
            }
        }
    }

    private class ReminderAdapter extends RecyclerView.Adapter<ReminderAdapter.ReminderVH> {
        private List<Reminder> list;

        void setReminders(List<Reminder> reminders) {
            this.list = reminders;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ReminderVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reminder, parent, false);
            return new ReminderVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ReminderVH holder, int position) {
            Reminder r = list.get(position);
            holder.lblBody.setText(r.getBody());
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            holder.lblTime.setText("Programado: " + sdf.format(new Date(r.getTriggerAt())));
            holder.lblState.setText("Estado: " + r.getState());

            holder.btnDelete.setOnClickListener(v -> {
                ReminderScheduler.cancelReminder(NotesActivity.this, r.getAlarmRequestCode());
                reminderRepo.deleteReminder(r.getId());
                loadReminders();
            });
        }

        @Override
        public int getItemCount() { return list != null ? list.size() : 0; }

        class ReminderVH extends RecyclerView.ViewHolder {
            TextView lblBody, lblTime, lblState;
            ImageButton btnDelete;
            ReminderVH(View itemView) {
                super(itemView);
                lblBody = itemView.findViewById(R.id.lblReminderBody);
                lblTime = itemView.findViewById(R.id.lblReminderTime);
                lblState = itemView.findViewById(R.id.lblReminderState);
                btnDelete = itemView.findViewById(R.id.btnDeleteReminder);
            }
        }
    }
}
