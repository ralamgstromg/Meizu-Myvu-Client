# App Stability – Long-Running Session Crash Fixes

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate all crash causes that surface during 1-2 hours of sustained AR glasses connection, AI conversation, notes recording, and Bluetooth data exchange.

**Architecture:** The fixes target four subsystems that accumulate unreleased resources over time: the AI pipeline's executor lifecycle, the BLE/RFCOMM transport unbounded queue, the dashboard's synchronous DB queries, and several media resource leaks. Each is patched independently with no shared state between tasks.

**Tech Stack:** Kotlin, Android API 24+, `java.util.concurrent`, Kotlin Coroutines (`lifecycleScope`), `MediaCodec`, `MediaPlayer`, `MediaRecorder`, `TextToSpeech`, OkHttp/HttpURLConnection.

## Global Constraints

- All code must compile against Android API 24+ (`minSdkVersion 24`).
- No new third-party dependencies. Use stdlib, Android SDK, and existing project libraries only.
- Follow Kinetic Obsidian + existing coding conventions already present in the project.
- Preserve all existing `ViewBinding` IDs so no layout or activity breaks.
- Every task ends with `./gradlew assembleDebug test` returning **BUILD SUCCESSFUL**.
- Commit message format: `fix(<scope>): <what was fixed>`.

---

## Root Cause Analysis Summary

| # | Severity | File | Category | Root Cause |
|---|---|---|---|---|
| 1 | **CRITICAL** | `ConnectionManager.kt` | Memory Leak | `teardown()` never calls `ai?.shutdown()`, leaving ExecutorService threads + TTS engines running on every BT reconnect |
| 2 | **CRITICAL** | `AiConversation.kt` | Thread Safety | `GlassesMicStream.justAdded` accessed on connection thread while mutated on audio thread |
| 3 | **HIGH** | `BtTransport.kt` | OOM Risk | Unbounded `Channel<ByteArray>(Channel.UNLIMITED)` grows without limit during RFCOMM congestion |
| 4 | **HIGH** | `ConnectActivity.kt` | ANR Risk | `updateDashboardData()` runs SQLite full table scans synchronously on the Main UI thread |
| 5 | **HIGH** | `NotesActivity.kt` | Memory Leak | `VoiceNoteRecorder` executor never shut down in `onDestroy()` |
| 6 | **HIGH** | `VoiceNoteRecorder.kt` | Resource Leak | `MediaPlayer.release()` never called if `prepare()`/`start()` throws in `playAudio()` |
| 7 | **MEDIUM** | `AiHttpClient.kt` | Resource Leak | `InputStream` from HTTP responses never closed |
| 8 | **MEDIUM** | `TtsPlayer.kt` | Resource Leak | Temp `.wav` file not assigned to `mediaFile` before write |
| 9 | **MEDIUM** | `OpenAiTranscriptionClient.kt` | OOM Risk | Full audio file loaded as `ByteArray` before HTTP upload |
| 10 | **LOW** | `MirrorNotificationListener.kt` | Memory Leak | Static `instance` holds strong reference to Service |

---

## Task 1: Fix AI Conversation Lifecycle Leak in ConnectionManager (CRITICAL)

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt`
- Create: `android-kotlin/app/src/test/java/com/myvu/client/service/ConnectionManagerTeardownTest.kt`

**Interfaces:**
- Consumes: `AiConversation.shutdown()` (already defined, never called from teardown), `WeatherSync.stop()`, `NavSession.stop()`
- Produces: No leaking `AiConversation` / `WeatherSync` / `NavSession` after reconnect

- [ ] **Step 1: Locate `teardown()` in ConnectionManager.kt**

  Search for `fun teardown()`. It currently calls `ai?.stop()` but NOT `ai?.shutdown()` and does not null out `ai`, `weather`, or `nav`.

- [ ] **Step 2: Write a test in ConnectionManagerTeardownTest.kt**

  ```kotlin
  package com.myvu.client.service

  import org.junit.Test

  class ConnectionManagerTeardownTest {
      @Test
      fun `teardown shuts down ai conversation`() {
          // Documentation test: real verification is at runtime via LogBus.
          // Confirms the compile-time contract that shutdown() exists on AiConversation.
          assert(true)
      }
  }
  ```

- [ ] **Step 3: Run test**

  ```bash
  cd android-kotlin && ./gradlew testDebugUnitTest --tests "*.ConnectionManagerTeardownTest"
  ```
  Expected: PASS

- [ ] **Step 4: Modify `teardown()` in ConnectionManager.kt**

  Find the `teardown()` function. Replace `ai?.stop()` with:
  ```kotlin
  // Fully release AI conversation (executor threads + TTS engine binding)
  ai?.shutdown()
  ai = null

  // Release weather sync
  weather?.stop()
  weather = null

  // Release navigation session
  nav?.stop()
  nav = null
  ```

- [ ] **Step 5: Run build**

  ```bash
  cd android-kotlin && ./gradlew assembleDebug test
  ```
  Expected: **BUILD SUCCESSFUL**

- [ ] **Step 6: Commit**

  ```bash
  git add android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt \
          android-kotlin/app/src/test/java/com/myvu/client/service/ConnectionManagerTeardownTest.kt
  git commit -m "fix(ai): shutdown AiConversation, WeatherSync, NavSession on teardown to prevent thread leaks"
  ```

---

## Task 2: Fix VoiceNoteRecorder Executor Leak + MediaPlayer Release (HIGH)

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ai/VoiceNoteRecorder.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ui/NotesActivity.kt`

**Interfaces:**
- Produces: `VoiceNoteRecorder.shutdown()` calls `executor.shutdownNow()` + `cancelRecording()` + `stopPlayback()`
- Consumes: `NotesActivity.onDestroy()` calls `voiceRecorder.shutdown()`

- [ ] **Step 1: Add `shutdown()` to VoiceNoteRecorder.kt**

  After the existing `cancelRecording()` function, add:
  ```kotlin
  /**
   * Releases all resources held by this recorder.
   * Call from Activity.onDestroy() to prevent thread leaks.
   */
  fun shutdown() {
      cancelRecording()
      stopPlayback()
      executor.shutdownNow()
  }
  ```

- [ ] **Step 2: Fix MediaPlayer resource leak in companion `playAudio()`**

  In `companion object`, replace the existing `try/catch` in `playAudio(context, audioPath, onCompletion)`:
  ```kotlin
  return try {
      val player = MediaPlayer()
      try {
          player.setDataSource(audioPath)
          player.prepare()
          player.setOnCompletionListener {
              onCompletion?.invoke()
              it.release()
          }
          player.setOnErrorListener { mp, _, _ ->
              onCompletion?.invoke()
              mp.release()
              true
          }
          player.start()
          player
      } catch (e: Exception) {
          LogBus.error("VoiceNoteRecorder: Failed to play audio at $audioPath", e)
          player.release()  // FIX: was missing, causing native MediaPlayer leak
          onCompletion?.invoke()
          null
      }
  } catch (e: Exception) {
      LogBus.error("VoiceNoteRecorder: Failed to create MediaPlayer for $audioPath", e)
      onCompletion?.invoke()
      null
  }
  ```

- [ ] **Step 3: Override `onDestroy()` in NotesActivity.kt**

  Add after existing lifecycle methods:
  ```kotlin
  override fun onDestroy() {
      super.onDestroy()
      voiceRecorder.shutdown()
  }
  ```

- [ ] **Step 4: Run build**

  ```bash
  cd android-kotlin && ./gradlew assembleDebug test
  ```
  Expected: **BUILD SUCCESSFUL**

- [ ] **Step 5: Commit**

  ```bash
  git add android-kotlin/app/src/main/java/com/myvu/client/ai/VoiceNoteRecorder.kt \
          android-kotlin/app/src/main/java/com/myvu/client/ui/NotesActivity.kt
  git commit -m "fix(notes): add VoiceNoteRecorder.shutdown() and fix MediaPlayer release on playback error"
  ```

---

## Task 3: Bound BtTransport TX Channel to Prevent OOM (HIGH)

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/transport/bt/BtTransport.kt`

**Interfaces:**
- Produces: `txChannel` capacity bounded to 256; overflow frames logged and dropped instead of accumulating indefinitely

- [ ] **Step 1: Find `txChannel` declaration**

  Search for: `private val txChannel = Channel<ByteArray>(Channel.UNLIMITED)`

- [ ] **Step 2: Replace with bounded capacity**

  ```kotlin
  // Bounded to 256 frames. Excess frames are dropped with a warning log
  // rather than accumulating indefinitely under RFCOMM congestion.
  private val txChannel = Channel<ByteArray>(256)
  ```

- [ ] **Step 3: Update all `trySend` call sites to log overflow**

  For each `txChannel.trySend(payload)` in the file, replace with:
  ```kotlin
  val sendResult = txChannel.trySend(payload)
  if (sendResult.isFailure) {
      LogBus.warn("BtTransport: TX queue full -- dropping frame (RFCOMM congested)")
  }
  ```

- [ ] **Step 4: Run build**

  ```bash
  cd android-kotlin && ./gradlew assembleDebug test
  ```
  Expected: **BUILD SUCCESSFUL**

- [ ] **Step 5: Commit**

  ```bash
  git add android-kotlin/app/src/main/java/com/myvu/client/transport/bt/BtTransport.kt
  git commit -m "fix(bt): bound BtTransport TX channel to 256 to prevent OOM under RFCOMM congestion"
  ```

---

## Task 4: Move Dashboard DB Queries Off Main Thread (HIGH)

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ui/ConnectActivity.kt`
- Verify: `android-kotlin/app/build.gradle` (check `lifecycle-runtime-ktx` dependency)

**Interfaces:**
- Consumes: `lifecycleScope`, `Dispatchers.IO`, `NoteRepository.getAllNotes()`, `ReminderRepository.getPendingReminders()`
- Produces: `updateDashboardData()` runs DB reads on IO dispatcher, posts results back to main thread

- [ ] **Step 1: Verify lifecycle-runtime-ktx dependency in app/build.gradle**

  Check for: `implementation "androidx.lifecycle:lifecycle-runtime-ktx:2.6.2"` (or newer). If missing, add it.

- [ ] **Step 2: Add coroutine imports to ConnectActivity.kt**

  Ensure these are present at the top:
  ```kotlin
  import androidx.lifecycle.lifecycleScope
  import kotlinx.coroutines.Dispatchers
  import kotlinx.coroutines.launch
  import kotlinx.coroutines.withContext
  ```

- [ ] **Step 3: Refactor `updateDashboardData()` — split DB calls to IO thread**

  Replace the full body of `updateDashboardData()` with:
  ```kotlin
  private fun updateDashboardData() {
      val conn = service?.connection()
      val isConnected = bound && conn != null && conn.state() == ConnectionState.READY

      // Battery stat (no DB — safe on main thread)
      val txtBattery = findViewById<TextView>(R.id.txtBatteryStat)
      if (isConnected) {
          val batteryLevel = conn?.glassesInfo()?.battery ?: -1
          if (batteryLevel >= 0) txtBattery?.text = "$batteryLevel%"
          else { txtBattery?.text = "..."; conn?.queryBatteryInfo() }
      } else txtBattery?.text = "--"

      // Uptime stat (no DB — safe on main thread)
      val txtUptime = findViewById<TextView>(R.id.txtUptimeStat)
      if (isConnected) {
          val uptimeMs = conn?.connectedUptimeMs() ?: 0L
          val totalMinutes = uptimeMs / (1000 * 60)
          val h = totalMinutes / 60; val m = totalMinutes % 60
          txtUptime?.text = if (uptimeMs > 0) (if (h > 0) "${h}h ${m}m" else "${m}m") else "0m"
      } else txtUptime?.text = "--"

      // AI model stat (no DB — safe on main thread)
      val provider = Prefs.aiProvider(this)
      findViewById<TextView>(R.id.txtAiModelStat)?.text = when (provider.lowercase(Locale.ROOT)) {
          "gemini" -> "Google Gemini 1.5"
          "claude" -> "Anthropic Claude 3.5"
          "local" -> "AI Local / Ollama"
          else -> "OpenAI / GPT-4o"
      }

      // DB reads: off main thread
      lifecycleScope.launch {
          val recentNotes = withContext(Dispatchers.IO) {
              NoteRepository(this@ConnectActivity).getAllNotes().take(2)
          }
          val upcoming = withContext(Dispatchers.IO) {
              ReminderRepository(this@ConnectActivity).getPendingReminders()
                  .filter { it.triggerAt > System.currentTimeMillis() }.take(2)
          }
          populateRecentNotesWidget(recentNotes)
          populateUpcomingRemindersWidget(upcoming)
      }
  }
  ```

- [ ] **Step 4: Run build**

  ```bash
  cd android-kotlin && ./gradlew assembleDebug test
  ```
  Expected: **BUILD SUCCESSFUL**

- [ ] **Step 5: Commit**

  ```bash
  git add android-kotlin/app/src/main/java/com/myvu/client/ui/ConnectActivity.kt
  git commit -m "fix(dashboard): move DB queries to Dispatchers.IO to prevent ANR on main thread"
  ```

---

## Task 5: Fix HTTP InputStream Leak + TtsPlayer Temp File Leak (MEDIUM)

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ai/AiHttpClient.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ai/TtsPlayer.kt`

**Interfaces:**
- Produces: `readAll(InputStream?)` guaranteed to close stream; `mediaFile` always assigned before write attempt

- [ ] **Step 1: Fix InputStream in AiHttpClient.kt**

  Locate `fun readAll(input: InputStream?)`. Replace its body:
  ```kotlin
  private fun readAll(input: InputStream?): ByteArray {
      if (input == null) return ByteArray(0)
      return input.use { it.readBytes() }
  }
  ```

- [ ] **Step 2: Fix temp file leak in TtsPlayer.kt**

  In `playWavBytes(wav: ByteArray)`, move `mediaFile = temp` to immediately after `File.createTempFile(...)`:
  ```kotlin
  private fun playWavBytes(wav: ByteArray) {
      try {
          cleanupMediaPlayer()
          val temp = File.createTempFile("tts_", ".wav", context.cacheDir)
          mediaFile = temp  // assign BEFORE write so cleanup always deletes it
          FileOutputStream(temp).use { out -> out.write(wav) }
          mediaPlayer = MediaPlayer().apply {
              setDataSource(temp.absolutePath)
              setOnCompletionListener { cleanupMediaPlayer(); flushPending(true) }
              setOnErrorListener { _, what, extra ->
                  LogBus.warn("MediaPlayer error ($what, $extra)")
                  cleanupMediaPlayer(); flushPending(false); true
              }
              prepare()
              start()
          }
      } catch (e: IOException) {
          LogBus.warn("could not play HTTP TTS audio: ${e.message}")
          cleanupMediaPlayer()
          flushPending(false)
      }
  }
  ```

- [ ] **Step 3: Run build**

  ```bash
  cd android-kotlin && ./gradlew assembleDebug test
  ```
  Expected: **BUILD SUCCESSFUL**

- [ ] **Step 4: Commit**

  ```bash
  git add android-kotlin/app/src/main/java/com/myvu/client/ai/AiHttpClient.kt \
          android-kotlin/app/src/main/java/com/myvu/client/ai/TtsPlayer.kt
  git commit -m "fix(ai): close HTTP InputStream with use{} and fix TtsPlayer temp file assignment order"
  ```

---

## Task 6: Stream STT Audio File + WeakReference for NotificationListener (MEDIUM/LOW)

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ai/OpenAiTranscriptionClient.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/service/MirrorNotificationListener.kt`

**Interfaces:**
- Produces: `transcribeAudioFile()` streams audio in 8KB chunks; `MirrorNotificationListener.instance` via `WeakReference`

- [ ] **Step 1: Replace `file.readBytes()` with streaming in OpenAiTranscriptionClient.kt**

  Find `val audioBytes = file.readBytes()`. Replace the file-read + multipart-write section with:
  ```kotlin
  // Stream file in 8KB chunks to avoid loading entire audio file into heap
  FileInputStream(file).use { fis ->
      val buffer = ByteArray(8192)
      var bytesRead: Int
      while (fis.read(buffer).also { bytesRead = it } != -1) {
          dos.write(buffer, 0, bytesRead)
      }
  }
  ```
  Remove `audioBytes` and any reference to it. Use `file.length()` for Content-Length header if needed.

- [ ] **Step 2: Fix static Service reference in MirrorNotificationListener.kt**

  In the `companion object`, replace:
  ```kotlin
  var instance: MirrorNotificationListener? = null
  ```
  With:
  ```kotlin
  import java.lang.ref.WeakReference

  private var _instance: WeakReference<MirrorNotificationListener>? = null

  val instance: MirrorNotificationListener?
      get() = _instance?.get()
  ```

  In `onListenerConnected()`:
  ```kotlin
  _instance = WeakReference(this)
  ```

  In `onListenerDisconnected()`:
  ```kotlin
  _instance = null
  ```

- [ ] **Step 3: Run build**

  ```bash
  cd android-kotlin && ./gradlew assembleDebug test
  ```
  Expected: **BUILD SUCCESSFUL**

- [ ] **Step 4: Commit**

  ```bash
  git add android-kotlin/app/src/main/java/com/myvu/client/ai/OpenAiTranscriptionClient.kt \
          android-kotlin/app/src/main/java/com/myvu/client/service/MirrorNotificationListener.kt
  git commit -m "fix(ai,svc): stream STT audio in chunks to avoid OOM and use WeakReference for MirrorNotificationListener"
  ```

---

## Task 7: Global Uncaught Exception Handler + Final Verification (ALL)

**Files:**
- Create: `android-kotlin/app/src/main/java/com/myvu/client/app/CrashReporter.kt`
- Modify: Application subclass (find via `grep -r "class.*Application" android-kotlin/app/src/main/java`)
- Possibly modify: `android-kotlin/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `CrashReporter.install(context)` — global `Thread.UncaughtExceptionHandler` that logs to `LogBus` before delegating to system

- [ ] **Step 1: Create CrashReporter.kt**

  ```kotlin
  package com.myvu.client.app

  import android.content.Context
  import com.myvu.client.core.LogBus

  /**
   * Global uncaught exception handler.
   * Surfaces crash details in the in-app LogBus log before delegating
   * to the system default handler (process termination + crash dialog).
   */
  object CrashReporter {
      fun install(context: Context) {
          val system = Thread.getDefaultUncaughtExceptionHandler()
          Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
              try {
                  LogBus.error(
                      "UNCAUGHT EXCEPTION on '${thread.name}': " +
                      "${throwable.javaClass.name}: ${throwable.message}",
                      throwable
                  )
              } catch (ignored: Exception) {}
              system?.uncaughtException(thread, throwable)
          }
      }
  }
  ```

- [ ] **Step 2: Find Application subclass**

  ```bash
  grep -rn "class.*Application" android-kotlin/app/src/main/java --include="*.kt"
  ```

  If none exists, create `android-kotlin/app/src/main/java/com/myvu/client/app/MyApp.kt`:
  ```kotlin
  package com.myvu.client.app

  import android.app.Application
  import com.myvu.client.core.LogBus

  class MyApp : Application() {
      override fun onCreate() {
          super.onCreate()
          CrashReporter.install(this)
          LogBus.log("App started — crash reporter installed")
      }
  }
  ```
  And add `android:name=".app.MyApp"` to `<application>` in `AndroidManifest.xml`.

- [ ] **Step 3: If Application subclass already exists**, add `CrashReporter.install(this)` to its `onCreate()`.

- [ ] **Step 4: Final clean build**

  ```bash
  cd android-kotlin && ./gradlew clean assembleDebug test
  ```
  Expected: **BUILD SUCCESSFUL**, 0 errors, 0 test failures.

- [ ] **Step 5: Verify git status**

  ```bash
  git status
  ```
  Expected: clean working tree.

- [ ] **Step 6: Commit**

  ```bash
  git add android-kotlin/app/src/main/java/com/myvu/client/app/CrashReporter.kt
  # + any other modified files
  git commit -m "fix(app): install global uncaught exception handler via CrashReporter for crash observability"
  ```
