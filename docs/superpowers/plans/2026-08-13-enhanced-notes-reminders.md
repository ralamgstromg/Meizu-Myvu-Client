# Enhanced Notes & Reminders System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete text/voice notes and reminders system with STT voice transcription, exact alarm notifications on phone and MYVU AR glasses, full CRUD (Create, Edit, Delete) operations, and instant search across titles and content.

**Architecture:** Upgrade `LocalDatabase.kt` SQLite schema to store note type (`TEXT` vs `VOICE`), audio file paths, titles, and timestamps. Build `VoiceNoteRecorder.kt` to record audio and auto-transcribe using `SttProvider`. Enhance `ReminderScheduler.kt` and `ReminderNotifier.kt` for exact alarms and AR HUD forwarding. Modernize `NotesActivity.kt` with Kinetic Obsidian modal dialogs for CRUD operations and live search filtering.

**Tech Stack:** Android SDK (Kotlin), SQLite (`SQLiteOpenHelper`), `MediaRecorder` & `MediaPlayer`, Material Components (Material 3), OpenAI/Android STT API, AR Glasses Bluetooth Relay.

## Global Constraints
- Target database: `LocalDatabase.kt` (SQLite). Upgrade `DATABASE_VERSION` from 1 to 2.
- UI Design System: Kinetic Obsidian (`#121416` base, 28dp card containers `#1E2022`, 16dp rounded buttons/inputs, 8dp category badges, Cyber Teal `#00F0FF` accents).
- Audio storage location: `context.filesDir/voice_notes/voice_note_<timestamp>.m4a`.
- Compatibility: Preserve all existing View Binding IDs in `NotesActivity.kt` and item layouts (`activity_notes.xml`, `item_note.xml`, `item_reminder.xml`).

---

### Task 1: Database Migration & Repository Extensions (`Note.kt`, `Reminder.kt`, `LocalDatabase.kt`, `NoteRepository.kt`, `ReminderRepository.kt`)

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/database/Note.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/database/Reminder.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/database/LocalDatabase.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/database/NoteRepository.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/database/ReminderRepository.kt`

**Interfaces:**
- Consumes: Design spec from `docs/superpowers/specs/2026-08-13-notes-reminders-enhancement-design.md`
- Produces: SQLite schema version 2 and CRUD search repository APIs.

- [ ] **Step 1: Update Note & Reminder data models**

Update `Note.kt`:
```kotlin
package com.myvu.client.database

data class Note(
    var id: Long = 0,
    var type: String = "TEXT", // "TEXT" or "VOICE"
    var title: String = "",
    var body: String = "",
    var audioPath: String? = null,
    var durationSec: Int = 0,
    var createdAt: Long = 0,
    var updatedAt: Long = 0
)
```

Update `Reminder.kt`:
```kotlin
package com.myvu.client.database

data class Reminder(
    var id: Long = 0,
    var title: String = "",
    var body: String = "",
    var triggerAt: Long = 0,
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
    var state: String = "PENDING", // PENDING, COMPLETED, SNOOZED, CANCELLED
    var alarmRequestCode: Int = 0
)
```

- [ ] **Step 2: Upgrade `LocalDatabase.kt` to version 2**

Update `LocalDatabase.kt` with database migration logic:
```kotlin
companion object {
    const val DATABASE_NAME = "myvu_client.db"
    const val DATABASE_VERSION = 2
    // Migration logic in onUpgrade to ALTER TABLE or recreate tables cleanly
}
```

- [ ] **Step 3: Update `NoteRepository.kt` and `ReminderRepository.kt` with CRUD & search**

Add `search(query: String, filter: String?)` methods to repositories executing `LIKE '%query%'` SQL queries across `title` and `body`.

- [ ] **Step 4: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myvu/client/database/
git commit -m "feat(database): upgrade SQLite schema to v2 with voice note and reminder search fields"
```

---

### Task 2: Voice Recording & STT Audio Engine (`VoiceNoteRecorder.kt`)

**Files:**
- Create: `android-kotlin/app/src/main/java/com/myvu/client/ai/VoiceNoteRecorder.kt`

**Interfaces:**
- Consumes: Audio APIs, `SttProvider.kt`, `OpenAiTranscriptionClient.kt`
- Produces: Voice note audio recorder and auto-STT transcription service.

- [ ] **Step 1: Implement `VoiceNoteRecorder.kt`**

Implement `VoiceNoteRecorder`:
- Uses `MediaRecorder` to capture audio to `.m4a` files in `context.filesDir/voice_notes/`.
- Integrates asynchronous STT call via `SttProvider` to convert recorded audio into text.
- Helper method `playAudio(context: Context, audioPath: String)` using `MediaPlayer`.

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/myvu/client/ai/VoiceNoteRecorder.kt
git commit -m "feat(stt): add VoiceNoteRecorder with automatic STT transcription"
```

---

### Task 3: Reminders Notification & AR HUD Alarm Sync (`ReminderScheduler.kt`, `ReminderNotifier.kt`)

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/reminder/ReminderScheduler.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/reminder/ReminderNotifier.kt`

**Interfaces:**
- Consumes: `Reminder` model, `AlarmManager`, `Notifications.kt`
- Produces: Exact alarm notifications on Android phone and Meizu MYVU AR glasses.

- [ ] **Step 1: Update `ReminderScheduler.kt`**

Ensure exact alarm scheduling using `AlarmManager.setExactAndAllowWhileIdle()`.

- [ ] **Step 2: Update `ReminderNotifier.kt`**

Format notification with title and description, action buttons ("Completar", "Posponer 10m"), and push JSON payload to Meizu MYVU glasses HUD via `MyvuService`.

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/myvu/client/reminder/
git commit -m "feat(reminders): enhance alarm scheduling and AR glasses notification sync"
```

---

### Task 4: Interactive Dialogs & CRUD UI (`dialog_edit_note.xml`, `dialog_edit_reminder.xml`, `NotesActivity.kt`)

**Files:**
- Create: `android-kotlin/app/src/main/res/layout/dialog_edit_note.xml`
- Create: `android-kotlin/app/src/main/res/layout/dialog_edit_reminder.xml`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ui/NotesActivity.kt`

**Interfaces:**
- Consumes: Kinetic Obsidian design tokens, `NoteRepository`, `ReminderRepository`
- Produces: Full Create, Edit, Delete modal dialog UX in `NotesActivity.kt`.

- [ ] **Step 1: Create `dialog_edit_note.xml`**

Create modal layout with Kinetic Obsidian card container (`28dp` radius, `#1E2022`), Title input (`16dp` radius), Text/Voice selector, Record button, Transcribed body editor, Save, and Delete buttons.

- [ ] **Step 2: Create `dialog_edit_reminder.xml`**

Create modal layout with Title input, Description input, Date/Time picker trigger buttons ("Fecha", "Hora"), Save, and Delete buttons.

- [ ] **Step 3: Wire CRUD dialogs in `NotesActivity.kt`**

Add `showNoteDialog(note: Note?)` and `showReminderDialog(reminder: Reminder?)` in `NotesActivity.kt` supporting creation, editing, and deletion.

- [ ] **Step 4: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/dialog_edit_*.xml app/src/main/java/com/myvu/client/ui/NotesActivity.kt
git commit -m "feat(ui): add CRUD modal dialogs for notes and reminders"
```

---

### Task 5: Dynamic Search & Content Filtering (`activity_notes.xml`, `item_note.xml`, `item_reminder.xml`, `NotesActivity.kt`)

**Files:**
- Modify: `android-kotlin/app/src/main/res/layout/activity_notes.xml`
- Modify: `android-kotlin/app/src/main/res/layout/item_note.xml`
- Modify: `android-kotlin/app/src/main/res/layout/item_reminder.xml`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ui/NotesActivity.kt`

**Interfaces:**
- Consumes: Repository search APIs from Task 1
- Produces: Real-time search by title/content and filter chip UX.

- [ ] **Step 1: Update `activity_notes.xml` with search input & filter chips**

Add `txtSearchNotes` search bar input (`16dp` radius, Cyber Teal search icon) and filter chip group (`[Todas]`, `[Texto]`, `[Voz]`, `[Recordatorios]`).

- [ ] **Step 2: Update item layouts (`item_note.xml`, `item_reminder.xml`)**

Add title, body, audio play/pause button (for voice notes), category badge (`8dp` radius), and edit button.

- [ ] **Step 3: Implement real-time search filtering in `NotesActivity.kt`**

Connect `txtSearchNotes` `doOnTextChanged` listener to invoke repository `search(query, filter)` and refresh RecyclerView adapters.

- [ ] **Step 4: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/ app/src/main/java/com/myvu/client/ui/NotesActivity.kt
git commit -m "feat(search): implement real-time title/content search and type filtering"
```

---

### Task 6: End-to-End Build & Functional Verification

**Files:**
- All updated application source files.

**Interfaces:**
- Entire Android build pipeline.

- [ ] **Step 1: Clean build check**

Run: `./gradlew clean assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL with 0 errors.

- [ ] **Step 2: Commit & working tree check**

Run: `git status`
Expected: Working tree clean.

---

## Plan Self-Review Checklist
1. **Spec Coverage:** Covers text notes, voice notes + STT, exact alarm notifications + AR HUD sync, full CRUD (Create, Edit, Delete), and instant search.
2. **No Placeholders:** Every step specifies exact file paths, schemas, and build validation commands.
3. **Compatibility:** All existing view IDs in `activity_notes.xml` preserved.
