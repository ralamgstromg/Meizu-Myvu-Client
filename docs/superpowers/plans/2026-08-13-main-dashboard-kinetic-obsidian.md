# Main Dashboard Landing Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the main landing screen of `android-kotlin` (`ConnectActivity` / `activity_connect.xml`) into a dynamic, feature-rich Dashboard based on Google Stitch spec `stich/dashboard_principal_actualizado`. The dashboard will feature central AR glasses connection status, a voice control action banner, quick telemetry stats (battery, uptime, AI provider model), and real-time widgets for recent notes and upcoming reminders.

**Architecture:** Update `activity_connect.xml` tab structure to host the new Kinetic Obsidian Dashboard layout as the primary screen. In `ConnectActivity.kt`, dynamically query `NoteRepository` for the 2 most recent notes and `ReminderRepository` for the 2 upcoming pending reminders, updating the UI widgets on resume. Wire the central voice action button to trigger instant voice note recording.

**Tech Stack:** Android SDK (Kotlin), Material 3 (`MaterialCardView`, `MaterialButton`, `TextInputLayout`), ViewBinding, SQLite Repositories (`NoteRepository`, `ReminderRepository`).

## Global Constraints
- Target visual design: `stich/dashboard_principal_actualizado` (`#121416` base, `#1A1C1E` containers, `#00F0FF` Cyber Teal highlights).
- Preserve existing ViewBinding IDs in `activity_connect.xml` (`btnConnect`, `btnDisconnect`, `txtStatus`, `btnSettings`, `btnNotes`, `btnTrackpad`, `btnNotificationApps`, `drawerLayout`, `navigationView`, `tabs`, `viewPager`).
- Ensure 100% clean build via `./gradlew assembleDebug test`.

---

### Task 1: Refactor `activity_connect.xml` Dashboard Layout

**Files:**
- Modify: `android-kotlin/app/src/main/res/layout/activity_connect.xml`
- Create: `android-kotlin/app/src/main/res/layout/view_dashboard.xml` (or embed inside dashboard tab layout)

**Interfaces:**
- Consumes: Kinetic Obsidian design tokens, `stich/dashboard_principal_actualizado/code.html`
- Produces: Updated dashboard UI with Hero Connection Card, Voice Action Banner, Telemetry Bento Cards, Recent Notes Card, and Upcoming Reminders Card.

- [ ] **Step 1: Create Dashboard layout `view_dashboard.xml`**

Create `view_dashboard.xml` with:
1. **Hero Connection Card**: 28dp radius card (`cardBackgroundColor="#1A1C1E"`), glowing glasses status ring (`pulse-border`), status text (`txtStatus`), status dot, and Connect (`btnConnect`) / Disconnect (`btnDisconnect`) buttons.
2. **Voice Control Banner**: Cyber Teal rounded banner (`btnVoiceControl`) with microphone icon ("Inicializar Control por Voz").
3. **Bento Stats Grid**:
   - Battery card (`txtBatteryStat` e.g. "85%", "Descargando")
   - Uptime card (`txtUptimeStat` e.g. "14h 2m", "Tiempo de actividad")
   - Active AI Model card (`txtAiModelStat` e.g. "OpenAI / GPT-4o Vision")
4. **Recent Notes Card (`cardRecentNotes`)**:
   - Header title "Notas Recientes" + `btnOpenAllNotes` button.
   - List container (`layoutRecentNotesContainer`) holding 2 note item views (`txtRecentNote1Title`, `txtRecentNote1Body`, `txtRecentNote2Title`, `txtRecentNote2Body`).
5. **Upcoming Reminders Card (`cardUpcomingReminders`)**:
   - Header title "Próximamente" + bell icon.
   - List container (`layoutUpcomingRemindersContainer`) holding 2 reminder item views (`txtReminder1Title`, `txtReminder1Time`, `txtReminder2Title`, `txtReminder2Time`).

- [ ] **Step 2: Embed Dashboard view inside `activity_connect.xml`**

Set `view_dashboard.xml` inside the main tab scrollable view container of `activity_connect.xml`.

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/
git commit -m "style(dashboard): implement Kinetic Obsidian main dashboard layout per Stitch spec"
```

---

### Task 2: Implement Real-Time Data Binding in `ConnectActivity.kt`

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ui/ConnectActivity.kt`

**Interfaces:**
- Consumes: `NoteRepository`, `ReminderRepository`, `Prefs`
- Produces: Dynamic dashboard widgets displaying recent notes, upcoming reminders, and AI provider info.

- [ ] **Step 1: Add `updateDashboardData()` method in `ConnectActivity.kt`**

```kotlin
private fun updateDashboardData() {
    // 1. Fetch recent notes
    val noteRepo = NoteRepository(this)
    val recentNotes = noteRepo.getAllNotes().take(2)
    populateRecentNotesWidget(recentNotes)

    // 2. Fetch upcoming pending reminders
    val reminderRepo = ReminderRepository(this)
    val upcomingReminders = reminderRepo.getPendingReminders().filter { it.triggerAt > System.currentTimeMillis() }.take(2)
    populateUpcomingRemindersWidget(upcomingReminders)

    // 3. Fetch active AI Model provider info
    val provider = Prefs.aiProvider(this)
    binding.txtAiModelStat?.text = when (provider) {
        "gemini" -> "Google Gemini 1.5"
        "claude" -> "Anthropic Claude 3.5"
        "local" -> "AI Local / Ollama"
        else -> "OpenAI / GPT-4o"
    }
}
```

- [ ] **Step 2: Bind item click listeners**

- Clicking `btnOpenAllNotes` or any note item launches `NotesActivity`.
- Clicking any reminder item launches `NotesActivity` with the Reminders filter active.
- Call `updateDashboardData()` in `onResume()` to ensure fresh data whenever returning to dashboard.

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/myvu/client/ui/ConnectActivity.kt
git commit -m "feat(dashboard): bind real-time recent notes, upcoming reminders, and AI provider data"
```

---

### Task 3: Implement Quick Voice Control Action Handler

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ui/ConnectActivity.kt`

**Interfaces:**
- Consumes: Voice control button click events (`btnVoiceControl`)
- Produces: Instant voice note recording / voice prompt dialog.

- [ ] **Step 1: Wire `btnVoiceControl` click listener**

In `ConnectActivity.kt`:
Clicking `btnVoiceControl` launches `NotesActivity` with an auto-trigger flag to record a voice note, or opens instant audio capture.

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/myvu/client/ui/ConnectActivity.kt
git commit -m "feat(dashboard): wire quick voice control action button"
```

---

### Task 4: Clean Build & End-to-End Verification

**Files:**
- All updated dashboard files.

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
1. **Spec Coverage:** Covers main landing dashboard refactoring per `stich/dashboard_principal_actualizado`, recent notes widget, upcoming reminders widget, AI model info, voice control banner, and real-time repository data binding.
2. **No Placeholders:** All view IDs and repository calls explicitly detailed.
3. **Compatibility:** All existing view IDs preserved.
