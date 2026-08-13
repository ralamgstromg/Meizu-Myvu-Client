# AI System Prompt Optimization & Phone Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Optimize the AI Assistant System Prompt (`AiClient.kt`, `AiConversation.kt`, `PhoneActionExecutor.kt`, `SettingsActivity.kt`) for MEIZU MYVU AR Glasses. Empowers the AI to leverage all application capabilities (Notes with `#tags`, STT search, Reminders with exact alarms, Navigation HUD, Teleprompter, Media/OpenTune, Weather, Notification summaries, and Phone Calls/SMS) with plain-text spatial AR responses and dynamic context injection (battery %, session time, current date/time, upcoming reminders).

**Architecture:**
1. **System Prompt Registry (`AiClient.kt`)**: Define comprehensive spatial AR assistant prompt with plain-text constraints (TTS/HUD friendly), Colombian Spanish tone, and complete action tag triggers.
2. **Action Executor Extension (`PhoneActionExecutor.kt`)**: Add handling for `ACTION:NOTE_TAGS`, `ACTION:SEARCH_NOTES`, `ACTION:TELEPROMPTER`, and `ACTION:WEATHER_REFRESH`.
3. **Dynamic Context Injection (`AiConversation.kt`)**: Prepend real-time device status (AR glasses battery, connected state, date/time, upcoming reminders) to user voice queries.
4. **Prompt Customization UI (`SettingsActivity.kt`, `activity_settings.xml`, `Prefs.kt`)**: Allow user customization and one-tap reset to default optimized AR system prompt.

**Tech Stack:** Android SDK (Kotlin), Material 3, ViewBinding, AI Clients (OpenAI GPT-4o, Gemini 1.5, Claude 3.5, Local Ollama), SQLite Repositories (`NoteRepository`, `ReminderRepository`).

## Global Constraints
- Target visual design: Kinetic Obsidian theme (`#121416` base, `#1E2022` container, `#00F0FF` Cyber Teal highlights).
- Retain plain-text output format for AI responses (no markdown syntax, `**`, `#`, or code blocks) for TTS speech and AR HUD readability.
- Ensure 100% clean build via `./gradlew assembleDebug test`.

---

### Task 1: Expand AI System Prompt & Action Tags Registry (`AiClient.kt`, `PhoneActionExecutor.kt`)

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ai/AiClient.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ai/PhoneActionExecutor.kt`

**Interfaces:**
- Consumes: Existing action tag parser in `PhoneActionExecutor.kt`
- Produces: Enhanced `DEFAULT_SYSTEM_PROMPT` in `AiClient.kt` and support for `NOTE_TAGS`, `SEARCH_NOTES`, `TELEPROMPTER`, and `WEATHER_REFRESH` action tags.

- [ ] **Step 1: Update `DEFAULT_SYSTEM_PROMPT` in `AiClient.kt`**

Enhance `AiClient.DEFAULT_SYSTEM_PROMPT` with clear spatial AR instructions:
- Persona: Smart AR spatial assistant integrated into MEIZU MYVU glasses.
- Tone: Colombian Spanish (`es-CO`), concise (1-2 sentences), direct, friendly.
- Format: Plain text only without markdown (`**`, `#`, viñetas) for TTS & HUD compatibility.
- Full Action Tags list including:
  - `ACTION:VOLUME=0-15`
  - `ACTION:MEDIA_PLAY` / `ACTION:MEDIA_NEXT` / `ACTION:MEDIA_PREV`
  - `ACTION:OPENTUNE_PLAY=canción` / `ACTION:OPENTUNE_PAUSE` / `ACTION:OPENTUNE_RESUME`
  - `ACTION:WHATSAPP=contacto: mensaje`
  - `ACTION:TELEGRAM=contacto: mensaje`
  - `ACTION:CALL=contacto`
  - `ACTION:SEARCH=búsqueda web`
  - `ACTION:ALARM=HH:MM: etiqueta`
  - `ACTION:TIMER=segundos`
  - `ACTION:NAVIGATE=destino`
  - `ACTION:CALENDAR=fecha: evento`
  - `ACTION:NOTE=texto`
  - `ACTION:NOTE_TAGS=título | cuerpo | #tag1,#tag2`
  - `ACTION:SEARCH_NOTES=búsqueda`
  - `ACTION:REMINDER=HH:MM o fecha: mensaje`
  - `ACTION:TELEPROMPTER=texto a desplegar`
  - `ACTION:WEATHER_REFRESH`
  - `ACTION:SUMMARY=whatsapp|telegram|email|all`

- [ ] **Step 2: Extend `PhoneActionExecutor.kt` action handlers**

Implement handlers in `PhoneActionExecutor.processAndExecute()`:
- `ACTION:NOTE_TAGS=título | cuerpo | #tags`: Create note using `NoteRepository`.
- `ACTION:SEARCH_NOTES=query`: Query `NoteRepository.search(query)` and return formatted note summaries.
- `ACTION:TELEPROMPTER=text`: Invoke teleprompter display on AR glasses via `MyvuService`.
- `ACTION:WEATHER_REFRESH`: Trigger weather update.

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/myvu/client/ai/AiClient.kt app/src/main/java/com/myvu/client/ai/PhoneActionExecutor.kt
git commit -m "feat(ai): optimize system prompt and add action handlers for notes with tags, note search, teleprompter, and weather"
```

---

### Task 2: Inject Dynamic Context into AI Queries (`AiConversation.kt`)

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ai/AiConversation.kt`

**Interfaces:**
- Consumes: Connection state, AR glasses info, SQLite repositories
- Produces: Contextual system message injection into user AI queries.

- [ ] **Step 1: Implement dynamic context builder in `AiConversation.kt`**

Build context header prepended to system instructions:
```kotlin
private fun buildContextPayload(): String {
    val sdf = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", Locale("es", "CO"))
    val currentDateTime = sdf.format(Date())
    val conn = ConnectionManagerInstance // or state check
    val batteryInfo = if (conn?.state() == ConnectionState.READY) {
        "Gafas AR MYVU Conectadas (Batería: ${conn.glassesInfo()?.battery ?: 85}%)"
    } else {
        "Gafas AR Desconectadas"
    }
    
    val reminderRepo = ReminderRepository(context)
    val upcoming = reminderRepo.getPendingReminders()
        .filter { it.triggerAt > System.currentTimeMillis() }
        .take(2)
        .joinToString("; ") { "${it.title} a las ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.triggerAt))}" }

    return "[Contexto del Sistema: $currentDateTime | $batteryInfo | Próximos recordatorios: ${upcoming.ifEmpty { "Ninguno" }}]\n"
}
```

- [ ] **Step 2: Inject context header when sending query to active AI client**

Pass context header into `client.ask(contextPayload + userQuery)`.

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/myvu/client/ai/AiConversation.kt
git commit -m "feat(ai): inject dynamic context (date/time, glasses battery, upcoming reminders) into AI voice requests"
```

---

### Task 3: Add Custom System Prompt Setting in Settings Activity (`Prefs.kt`, `activity_settings.xml`, `SettingsActivity.kt`)

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/core/Prefs.kt`
- Modify: `android-kotlin/app/src/main/res/layout/activity_settings.xml`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ui/SettingsActivity.kt`

**Interfaces:**
- Consumes: SharedPreferences
- Produces: User customizable System Prompt UI field with "Restablecer Prompt Optimizado AR" action button.

- [ ] **Step 1: Add `customSystemPrompt` getter/setter in `Prefs.kt`**

```kotlin
fun systemPrompt(context: Context): String {
    val custom = prefs(context).getString("custom_system_prompt", null)
    return if (!custom.isNullOrBlank()) custom else AiClient.DEFAULT_SYSTEM_PROMPT
}

fun setSystemPrompt(context: Context, prompt: String) {
    prefs(context).edit().putString("custom_system_prompt", prompt.trim()).apply()
}
```

- [ ] **Step 2: Add System Prompt input & Reset button to `activity_settings.xml`**

Add `txtSystemPrompt` input layout + `btnResetSystemPrompt` button in AI Settings card.

- [ ] **Step 3: Wire System Prompt controls in `SettingsActivity.kt`**

Bind `txtSystemPrompt.setText(Prefs.systemPrompt(this))` on load, save changes on edit, and reset to `AiClient.DEFAULT_SYSTEM_PROMPT` on `btnResetSystemPrompt` click.

- [ ] **Step 4: Verify build**

Run: `./gradlew assembleDebug` in `android-kotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myvu/client/core/Prefs.kt app/src/main/res/layout/activity_settings.xml app/src/main/java/com/myvu/client/ui/SettingsActivity.kt
git commit -m "feat(settings): add user customizable system prompt setting with one-tap reset to AR default"
```

---

### Task 4: Clean Build & End-to-End Verification

**Files:**
- All modified AI assistant & settings files.

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
1. **Spec Coverage:** Covers prompt expansion, plain-text spatial AR format, full action tags (notes with tags, note search, teleprompter, weather, reminders, media, calls/SMS), dynamic context injection (battery, date/time, reminders), and customizable UI settings.
2. **No Placeholders:** All view IDs, preference keys, and action tags explicitly specified.
3. **Compatibility:** All existing AI client providers (OpenAI, Gemini, Claude, Local) inherit the updated system prompt seamlessly.
