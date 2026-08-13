# Enhanced Notes & Reminders System Design Specification

## Overview
This specification details the architecture, data models, voice-to-text (STT) integration, notification dispatching, search capabilities, and CRUD operations for text notes, voice notes, and reminders in the `android-kotlin` client application.

---

## 1. Database Architecture & Schemas (`LocalDatabase.kt`)

### Database Version Upgrade
- Increase `DATABASE_VERSION` from `1` to `2` in `LocalDatabase.kt`.
- Execute migration statements to preserve existing data while upgrading schema structure.

### `notes` Table Schema
```sql
CREATE TABLE notes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL DEFAULT 'TEXT',        -- 'TEXT' or 'VOICE'
    title TEXT NOT NULL,
    body TEXT NOT NULL,                        -- Typed text or STT transcript
    audio_path TEXT,                           -- Path to saved .m4a file if type is 'VOICE'
    duration_sec INTEGER NOT NULL DEFAULT 0,  -- Duration of voice note in seconds
    created_at INTEGER NOT NULL,               -- Milliseconds timestamp
    updated_at INTEGER NOT NULL                -- Milliseconds timestamp
);
```

### `reminders` Table Schema
```sql
CREATE TABLE reminders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    trigger_at INTEGER NOT NULL,               -- Scheduled execution timestamp
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    state TEXT NOT NULL DEFAULT 'PENDING',      -- 'PENDING', 'COMPLETED', 'SNOOZED', 'CANCELLED'
    alarm_request_code INTEGER NOT NULL
);
```

---

## 2. Data Access Layer (`NoteRepository.kt` & `ReminderRepository.kt`)

### `NoteRepository.kt` Methods
- `insert(note: Note): Long`
- `update(note: Note): Boolean`
- `delete(noteId: Long): Boolean`
- `getById(noteId: Long): Note?`
- `getAll(): List<Note>`
- `search(query: String, typeFilter: String?): List<Note>`

### `ReminderRepository.kt` Methods
- `insert(reminder: Reminder): Long`
- `update(reminder: Reminder): Boolean`
- `delete(reminderId: Long): Boolean`
- `getById(reminderId: Long): Reminder?`
- `getAll(): List<Reminder>`
- `search(query: String, stateFilter: String?): List<Reminder>`

---

## 3. Voice Notes & STT Engine (`VoiceNoteRecorder.kt`)

### Recording Lifecycle
1. User taps "Grabar Voz" in note dialog.
2. `VoiceNoteRecorder` utilizes Android `MediaRecorder` to capture high-quality AAC audio (`.m4a`) to `context.filesDir/voice_notes/voice_note_<timestamp>.m4a`.
3. Duration timer tracks recording length in seconds.

### Speech-to-Text (STT) Processing
1. On recording completion, `VoiceNoteRecorder` sends audio stream to `SttProvider` / `OpenAiTranscriptionClient` (or Android native `SpeechRecognizer` fallback).
2. Transcribed text is populated into the `body` field of the Note model.
3. If STT fails, the audio file is preserved with fallback text `"[Nota de voz sin transcripción]"` and user can edit body text manually.

---

## 4. Notifications & Alarm Scheduling

### Alarm Registration (`ReminderScheduler.kt`)
- `scheduleReminder(context: Context, reminder: Reminder)` registers `AlarmManager.setExactAndAllowWhileIdle()` targeting `ReminderReceiver`.
- Handles `BootReceiver` re-scheduling on device reboot.

### Notification Execution (`ReminderNotifier.kt`)
- Phone Notification: High-priority channel `myvu_reminders` with "Completar" (`ACTION_COMPLETE`) and "Posponer 10m" (`ACTION_SNOOZE`) action buttons.
- AR Glasses Notification: Formats JSON payload via `Notifications.buildShow()` and transmits to active Meizu MYVU glasses via `MyvuService`.

---

## 5. User Interface & Search (`NotesActivity.kt` & Dialogs)

### Kinetic Obsidian Styling
- Background: `#121416`
- Card Containers: `28dp` corner radius, `#1E2022` fill, `1dp` ghost border `#3B494B`.
- Badges & Chips: `8dp` corner radius, Cyber Teal `#00F0FF` accents, JetBrains Mono font (`label-md`).

### Search & Filtering
- Search bar (`txtSearchNotes`) filters both Notes and Reminders dynamically using `LIKE '%query%'` matching against `title` and `body`.
- Category Filter Chips: `[Todas]` `[Texto]` `[Voz]` `[Recordatorios]` `[Pendientes]`.

### CRUD Interaction Dialogs
1. **Create / Edit Note Dialog (`dialog_edit_note.xml`)**: Title input, Text/Voice toggle, Voice recorder UI, Transcribed body preview, Save & Delete buttons.
2. **Create / Edit Reminder Dialog (`dialog_edit_reminder.xml`)**: Title input, Description input, Date/Time picker triggers, Save & Delete buttons.
3. **Delete Confirmation Dialog**: Deletes database record and cleans up associated `.m4a` audio file if present.
