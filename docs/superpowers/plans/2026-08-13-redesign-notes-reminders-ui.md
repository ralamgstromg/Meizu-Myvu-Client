# Notes & Reminders UI Redesign & Enhanced Search Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the Notes & Reminders interface based on Google Stitch spec `stich/gesti_n_de_notas_y_recordatorios`, incorporating automatic voice-to-text (STT) transcription saved directly into SQLite database for full-text search, note tags/labels classification, waveform audio UI indicators, urgency badge indicators, real-time chip filtering (`Todos`, `Texto`, `Voz`, `Recordatorios`), and an expandable Speed Dial FAB (`Nuevo Texto`, `Grabar Voz`, `Añadir Recordatorio`).

**Architecture:** 
1. **Database Schema v3 (`LocalDatabase.kt`, `Note.kt`, `NoteRepository.kt`)**: Add `tags` TEXT column to `notes` table. Upgrade `search(query, filter)` to match across `title`, `body` (transcription), and `tags`.
2. **STT Auto-Save & Transcription**: Automatically convert recorded voice notes to text using `VoiceNoteRecorder` / `OpenAiTranscriptionClient` and store the resulting transcription in `Note.body` for text and proximity search.
3. **Tags Management**: Support tag input in note edit dialogs (`#idea`, `#trabajo`, `#urgente`), save tags in database, and display tag chips on note cards.
4. **UI Layouts (`activity_notes.xml`, `item_note.xml`, `item_reminder.xml`, `dialog_edit_note.xml`)**: Redesign layouts with top bar, search bar, filter chips, speed dial FAB cluster, waveform audio bars, urgency badges, and tag chips.

**Tech Stack:** Android SDK (Kotlin), Material 3 (`MaterialCardView`, `ChipGroup`, `FloatingActionButton`), ViewBinding, SQLite Repositories (`NoteRepository`, `ReminderRepository`), Speech-To-Text Audio Engine (`VoiceNoteRecorder`, `SttProvider`).

## Global Constraints
- Target visual design: `stich/gesti_n_de_notas_y_recordatorios` (`#121416` base, `#1E2022` container, `#00F0FF` Cyber Teal highlights).
- Preserve existing ViewBinding IDs in `activity_notes.xml`, `item_note.xml`, `item_reminder.xml`, and `dialog_edit_note.xml`.
- Ensure 100% clean build via `./gradlew assembleDebug test`.

---

### Task 1: Database Migration v3 & Tags Support (`Note.kt`, `LocalDatabase.kt`, `NoteRepository.kt`)

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/database/Note.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/database/LocalDatabase.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/database/NoteRepository.kt`

**Interfaces:**
- Consumes: SQLite Database v2
- Produces: SQLite Database v3 with `tags` column, updated `Note` model, and multi-field search (`title`, `body` transcription, `tags`).

- [ ] **Step 1: Update `Note.kt` data class**

Add `val tags: String = ""` to `Note` data class.

- [ ] **Step 2: Upgrade `LocalDatabase.kt` to version 3**

Increment `DATABASE_VERSION = 3`. In `onUpgrade`:
```kotlin
if (oldVersion < 3) {
    db.execSQL("ALTER TABLE notes ADD COLUMN tags TEXT DEFAULT ''")
}
```

- [ ] **Step 3: Update `NoteRepository.kt` search query**

Update `search(query, filter)` SQL query:
```sql
SELECT * FROM notes 
WHERE (title LIKE ? OR body LIKE ? OR tags LIKE ?)
```

- [ ] **Step 4: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myvu/client/database/
git commit -m "feat(database): upgrade SQLite schema to v3 with tags column and multi-field search"
```

---

### Task 2: Redesign Notes & Reminders Activity Layout (`activity_notes.xml`)

**Files:**
- Modify: `android-kotlin/app/src/main/res/layout/activity_notes.xml`

**Interfaces:**
- Consumes: Kinetic Obsidian design tokens, `stich/gesti_n_de_notas_y_recordatorios/code.html`
- Produces: Redesigned `activity_notes.xml` with top bar hamburger menu, search input, filter chips, content RecyclerView, and Speed Dial FAB cluster.

- [ ] **Step 1: Update `activity_notes.xml` layout**

Structure layout:
1. **Top Bar**: Hamburger menu button (`btnNotesDrawer`), screen title "Notas y Recordatorios", battery status badge.
2. **Search Input Bar (`txtSearchNotes`)**: 16dp rounded input container (`Input.Myvu`), Cyber Teal search icon ("Buscar por título, contenido o #tags...").
3. **Filter Chip Group (`chipGroupFilter`)**:
   - `chipFilterAll` ("Todos", Cyber Teal active glow)
   - `chipFilterText` ("Texto")
   - `chipFilterVoice` ("Voz")
   - `chipFilterReminders` ("Recordatorios")
4. **Content RecyclerView (`rvNotesAndReminders`)**: Unified vertical/grid list.
5. **Speed Dial FAB Cluster**:
   - Primary `+` FAB (`fabMain`).
   - Expandable sub-button container (`fabMenu`):
     - `fabNewText` ("Nuevo Texto", icon `edit_note`)
     - `fabNewVoice` ("Grabar Voz", icon `mic`)
     - `fabNewReminder` ("Añadir Recordatorio", icon `add_alarm`)

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/activity_notes.xml
git commit -m "style(notes): redesign activity_notes.xml layout per Stitch spec"
```

---

### Task 3: Redesign Item Cards & Edit Dialog with Tags & STT (`item_note.xml`, `item_reminder.xml`, `dialog_edit_note.xml`)

**Files:**
- Modify: `android-kotlin/app/src/main/res/layout/item_note.xml`
- Modify: `android-kotlin/app/src/main/res/layout/item_reminder.xml`
- Modify: `android-kotlin/app/src/main/res/layout/dialog_edit_note.xml`

**Interfaces:**
- Consumes: Kinetic Obsidian tokens, Stitch item specifications
- Produces: Updated card views for text/voice notes (with tag chips, waveform bars, STT transcribe action) and edit dialog with tag input.

- [ ] **Step 1: Update `item_note.xml`**

- Container: `MaterialCardView` with `28dp` corner radius, `#1E2022` background, `1dp` ghost border `#3B494B`.
- Header: Category badge `lblNoteCategory` (`[Texto]` in purple `#E7D0FF` or `[Nota de Voz]` in Cyber Teal `#00F0FF`) + Close/Delete button `btnDeleteNote`.
- Body: Note title `lblNoteTitle`, body text `lblNoteBody` (stores STT transcription).
- Tags container: `chipGroupTags` holding small `8dp` radius tag chips (e.g. `#idea`, `#trabajo`).
- Voice Note extras: Animated waveform bars, duration counter `lblAudioDuration`, transcription button `btnTranscribe`, and circular audio play/pause button `btnPlayAudio`.

- [ ] **Step 2: Update `item_reminder.xml`**

- Container: `MaterialCardView` with `28dp` corner radius, `#1E2022` background, `1dp` ghost border `#3B494B`.
- Header: Urgency badge `lblReminderCategory` (`[Urgente]` in Red `#FFB4AB` or `[Próximo]` in Silver `#BAC9CD`) + Delete button `btnDeleteReminder`.
- Body: Reminder title `lblReminderTitle`, description `lblReminderBody`.
- Footer: Schedule time indicator `lblReminderTime` ("Hoy, 14:30").

- [ ] **Step 3: Update `dialog_edit_note.xml`**

Add `txtNoteTags` input field ("Etiquetas / Tags p.ej: trabajo, idea") under title and body input fields.

- [ ] **Step 4: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/
git commit -m "style(notes): add tag chips, STT transcription elements, and edit dialog tag input"
```

---

### Task 4: Speed Dial FAB, Tags Filtering & STT Auto-Save in `NotesActivity.kt`

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ui/NotesActivity.kt`

**Interfaces:**
- Consumes: Layout bindings from Tasks 1, 2, & 3
- Produces: Speed Dial FAB toggle animations, STT transcription DB persistence, tag chip binding, and unified search.

- [ ] **Step 1: Implement Speed Dial FAB toggle in `NotesActivity.kt`**

```kotlin
private var isFabMenuOpen = false

private fun setupSpeedDialFab() {
    binding.fabMain.setOnClickListener {
        isFabMenuOpen = !isFabMenuOpen
        if (isFabMenuOpen) {
            binding.fabMenu.visibility = View.VISIBLE
            binding.fabMain.animate().rotation(45f).setDuration(200).start()
        } else {
            binding.fabMenu.visibility = View.GONE
            binding.fabMain.animate().rotation(0f).setDuration(200).start()
        }
    }

    binding.fabNewText.setOnClickListener {
        closeFabMenu()
        showNoteDialog(startInVoiceMode = false)
    }

    binding.fabNewVoice.setOnClickListener {
        closeFabMenu()
        showNoteDialog(startInVoiceMode = true)
    }

    binding.fabNewReminder.setOnClickListener {
        closeFabMenu()
        showReminderDialog()
    }
}
```

- [ ] **Step 2: Bind Tags & STT Auto-Save in Dialog & Adapters**

- When voice recording finishes, STT transcription is automatically written to `txtNoteBody` and saved into `Note.body` in SQLite.
- In `showNoteDialog`, populate and save `tags` field (comma-separated string).
- In `NoteAdapter`, dynamically inflate tag chips into `chipGroupTags`.
- When tapping any tag chip, automatically insert the tag name into `txtSearchNotes` to filter notes by tag!

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/myvu/client/ui/NotesActivity.kt
git commit -m "feat(notes): implement Speed Dial FAB, STT DB auto-save, tag chip filtering, and multi-field search"
```

---

### Task 5: Clean Build & End-to-End Verification

**Files:**
- All updated notes & reminders files.

**Interfaces:**
- Entire Android build & test pipeline.

- [ ] **Step 1: Run clean build and unit tests**

Run: `./gradlew clean assembleDebug test` in `android-kotlin`
Expected: BUILD SUCCESSFUL with 0 errors and 0 test failures.

- [ ] **Step 2: Commit & working tree check**

Run: `git status`
Expected: Working tree clean.

---

## Plan Self-Review Checklist
1. **Spec Coverage:** Covers full UI redesign of `activity_notes.xml`, `item_note.xml`, `item_reminder.xml`, `dialog_edit_note.xml`, SQLite Database v3 migration for tags, STT auto-save to DB `body` column for search, tag chip UI & tag-based search filtering, and Speed Dial FAB cluster.
2. **No Placeholders:** All view IDs, database migrations, and click listeners explicitly specified.
3. **Compatibility:** All existing view IDs preserved.
