# Android Native STT Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Android native `SpeechRecognizer` as selectable third STT provider for AI commands and voice notes, without HTTP calls for this provider.

**Architecture:** Keep HTTP/local file and PCM transcription unchanged. Add a lifecycle-safe live microphone engine for Android native recognition, selected through existing `Prefs`; route its final text into existing AI transcript handling and coordinate it with `MediaRecorder` for notes.

**Tech Stack:** Kotlin, Android `android.speech.SpeechRecognizer`, `RecognizerIntent`, Android main looper, existing Gradle/JUnit 4 tests, XML/AppCompat settings UI.

**Spec:** `docs/superpowers/specs/2026-08-13-android-native-stt-design.md`

## Global Constraints

- Provider ID is exactly `android`.
- Android provider uses `SpeechRecognizer`; no app HTTP endpoint, API key, external dependency, model download, Whisper, or Vosk.
- `SpeechRecognizer` receives phone microphone input; it does not receive glasses Opus/PCM.
- Existing HTTP/local providers remain available and unchanged.
- Native recognition requires `RECORD_AUDIO` permission and device speech-service availability.
- No automatic HTTP fallback after native recognition failure.
- Native recognizer must be cancelled/destroyed on terminal error, cancellation, owner shutdown, and owner destruction.
- Tests must use fakes; JVM tests must not depend on a real speech engine.

## File map

- Create `app/src/main/java/com/myvu/client/ai/AndroidSpeechRecognizer.kt`: platform wrapper and lifecycle state.
- Create `app/src/main/java/com/myvu/client/ai/AndroidSpeechEngine.kt`: small injectable live-recognition contract/factory seam for AI and notes.
- Modify `app/src/main/java/com/myvu/client/ai/SttProvider.kt`: register `android` and no-configuration behavior.
- Modify `app/src/main/java/com/myvu/client/core/Prefs.kt`: preserve and validate Android provider selection without API configuration reads.
- Modify `app/src/main/java/com/myvu/client/ui/SettingsActivity.kt` and `app/src/main/res/layout/activity_settings.xml`: third option and Android-specific field visibility/help text.
- Modify `app/src/main/java/com/myvu/client/ai/AiConversation.kt`: native branch, lifecycle cancellation, stale-session protection.
- Modify `app/src/main/java/com/myvu/client/ai/VoiceNoteRecorder.kt`: coordinate native recognition and file recording.
- Create or modify tests under `app/src/test/java/com/myvu/client/ai/`: provider, fake engine, AI lifecycle, and voice-note coordination tests.

---

### Task 1: Add provider ID and deterministic configuration rules

**Files:**
- Modify: `app/src/main/java/com/myvu/client/ai/SttProvider.kt`
- Modify: `app/src/main/java/com/myvu/client/core/Prefs.kt`
- Create: `app/src/test/java/com/myvu/client/ai/SttProviderTest.kt`

**Interfaces:**
- Produces provider ID `android`.
- `SttProvider.fromId("android")` returns native provider.
- Native provider reports no required API key, endpoint, or model.

- [ ] **Step 1: Write failing provider tests**

```kotlin
@Test
fun `android provider is selectable`() {
    assertEquals("android", SttProvider.fromId("android").id)
}

@Test
fun `android provider has no HTTP configuration requirement`() {
    val provider = SttProvider.fromId("android")
    assertFalse(provider.requiresApiKey)
    assertFalse(provider.requiresEndpoint)
    assertFalse(provider.requiresModel)
}
```

Use actual existing `SttProvider` property names; if current type has no requirement flags, first define those exact properties in the enum/data type and use them in Settings and recorder logic.

- [ ] **Step 2: Run focused test and confirm failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.SttProviderTest'
```

Expected: FAIL because `android` and native configuration metadata do not exist.

- [ ] **Step 3: Implement minimal provider metadata**

Add `android` to provider definitions. Keep current IDs/defaults for remote and local providers. Make `Prefs.sttProvider()` return existing saved value unchanged, with current default preserved for existing installations. Do not migrate stored provider values.

- [ ] **Step 4: Run focused test and confirm pass**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.SttProviderTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myvu/client/ai/SttProvider.kt app/src/main/java/com/myvu/client/core/Prefs.kt app/src/test/java/com/myvu/client/ai/SttProviderTest.kt
git commit -m "feat(stt): add Android native provider"
```

### Task 2: Build lifecycle-safe native recognizer wrapper

**Files:**
- Create: `app/src/main/java/com/myvu/client/ai/AndroidSpeechRecognizer.kt`
- Create: `app/src/main/java/com/myvu/client/ai/AndroidSpeechEngine.kt`
- Create: `app/src/test/java/com/myvu/client/ai/AndroidSpeechEngineTest.kt`

**Interfaces:**

```kotlin
interface AndroidSpeechEngine {
    fun start(languageTag: String?, onPartial: ((String) -> Unit)?, onResult: (String) -> Unit, onError: (Int, String) -> Unit): Boolean
    fun stop()
    fun cancel()
    fun destroy()
}
```

The production implementation owns `SpeechRecognizer`, `RecognitionListener`, main-looper dispatch, duplicate-terminal protection, and `destroy()`. The test fake implements the interface without Android speech services.

- [ ] **Step 1: Write fake-driven lifecycle tests**

Cover these cases:

Implement three tests with a recording fake:

- `terminal result is delivered once`: start fake, emit two terminal results, assert consumer callback count is `1` and text equals first non-blank result.
- `cancel prevents later result`: start fake, call `cancel()`, emit result, assert consumer callback count is `0`.
- `destroy is idempotent`: call `destroy()` twice and assert no exception plus fake destroy count is `1`.

- [ ] **Step 2: Run tests and confirm failure**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.AndroidSpeechEngineTest'
```

Expected: FAIL because contract and implementation do not exist.

- [ ] **Step 3: Implement production wrapper**

Use `SpeechRecognizer.isRecognitionAvailable(context)` before creation. Build `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` with `LANGUAGE_MODEL_FREE_FORM`, optional `EXTRA_LANGUAGE`, `EXTRA_PARTIAL_RESULTS` only when requested, and `EXTRA_PREFER_OFFLINE = true`. Create recognizer/listener on main looper. Select first non-blank final hypothesis. Convert platform errors to `(code, message)`. Ensure every terminal path clears listener state and destroys recognizer.

- [ ] **Step 4: Run focused tests**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.AndroidSpeechEngineTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myvu/client/ai/AndroidSpeechRecognizer.kt app/src/main/java/com/myvu/client/ai/AndroidSpeechEngine.kt app/src/test/java/com/myvu/client/ai/AndroidSpeechEngineTest.kt
git commit -m "feat(stt): wrap Android SpeechRecognizer"
```

### Task 3: Add Settings selection and native configuration UI

**Files:**
- Modify: `app/src/main/java/com/myvu/client/ui/SettingsActivity.kt`
- Modify: `app/src/main/res/layout/activity_settings.xml`
- Modify: `app/src/main/res/values/strings.xml` if text resources are used

**Interfaces:**
- Existing STT selector persists `android` through `Prefs.setSttProvider`.
- Native choice hides/disables STT API key, endpoint, and model fields.

- [ ] **Step 1: Add UI behavior test seam or inspect existing selector tests**

If `SettingsActivity` has no JVM-testable seam, extract a pure helper with signature:

```kotlin
fun isNativeSttProvider(providerId: String): Boolean = providerId == "android"
```

Test `true` for `android`, `false` for current HTTP providers.

- [ ] **Step 2: Implement third choice**

Add Android provider label to current selector list. On selection, set visibility/enabled state for STT key, endpoint, and model controls. Show help text explaining phone microphone, Android speech services, and permission/availability dependency. Do not validate or load API key for `android`.

- [ ] **Step 3: Verify manually through build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/myvu/client/ui/SettingsActivity.kt app/src/main/res/layout/activity_settings.xml app/src/main/res/values/strings.xml
 git commit -m "feat(settings): expose Android native STT"
```

### Task 4: Integrate Android recognition into AI commands

**Files:**
- Modify: `app/src/main/java/com/myvu/client/ai/AiConversation.kt`
- Create: `app/src/test/java/com/myvu/client/ai/AiConversationNativeSttTest.kt`

**Interfaces:**
- `AiConversation` receives an injectable `AndroidSpeechEngine` factory/constructor dependency with production default.
- Native result enters existing `onTranscript(text)` path.

- [ ] **Step 1: Write fake-engine tests**

Cover:

Implement five fake-driven tests:

- `android provider does not start glasses microphone`: start an Android-provider trigger and assert fake glasses mic start count is `0`.
- `native result uses existing transcript path`: emit text and assert existing transcript handler receives exactly that text.
- `native error finishes without HTTP fallback`: emit an error and assert session finishes and HTTP client call count stays `0`.
- `old session result is ignored`: start session A, start session B, emit A's result, and assert no AI turn is created from A.
- `shutdown cancels and destroys recognizer`: call shutdown and assert fake engine received one cancel and one destroy.

- [ ] **Step 2: Run focused tests and confirm failure**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.AiConversationNativeSttTest'
```

Expected: FAIL because `AiConversation` always starts glasses capture and HTTP transcription.

- [ ] **Step 3: Add explicit provider branch**

At listening start, read `Prefs.sttProvider(context)`. For `android`, keep AI session setup and timers, but skip `mic.start()` and decoder startup. Start engine with `Locale.getDefault().toLanguageTag()` or configured language. For HTTP/local, retain existing code unchanged.

- [ ] **Step 4: Add cancellation and stale-session guards**

Store active native engine/session identity. Cancel on page close, timeout, `finish()`, and `shutdown()`. In callbacks, require active state and matching `sessionId` before calling `onTranscript`. Native errors log and finish; never construct or call `OpenAiTranscriptionClient`.

- [ ] **Step 5: Run focused tests**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.AiConversationNativeSttTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/myvu/client/ai/AiConversation.kt app/src/test/java/com/myvu/client/ai/AiConversationNativeSttTest.kt
git commit -m "feat(ai): support Android native command recognition"
```

### Task 5: Integrate Android recognition into voice notes

**Files:**
- Modify: `app/src/main/java/com/myvu/client/ai/VoiceNoteRecorder.kt`
- Create: `app/src/test/java/com/myvu/client/ai/VoiceNoteRecorderNativeSttTest.kt`

**Interfaces:**
- `VoiceNoteRecorder` receives an injectable native engine factory with production default.
- Existing `TranscriptionCallback(audioPath, transcript)` remains unchanged.

- [ ] **Step 1: Write fake-engine coordination tests**

Cover:

Implement four fake-driven tests:

- `native note starts recorder and recognizer`: start an Android-provider note and assert MediaRecorder and fake engine both start once.
- `native note callback fires once after result and recorder stop`: emit result, stop recording, and assert one callback containing the audio path and result text.
- `native error preserves path and uses fallback transcript`: emit native error and assert callback contains existing audio path and `FALLBACK_TRANSCRIPT`.
- `shutdown cancels and destroys native recognizer`: call shutdown and assert fake engine receives cancellation and destruction once.

- [ ] **Step 2: Run focused tests and confirm failure**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.VoiceNoteRecorderNativeSttTest'
```

Expected: FAIL because recorder always transcribes the file through HTTP.

- [ ] **Step 3: Add Android-provider start path**

When `Prefs.sttProvider(context) == "android"`, start native recognition alongside `MediaRecorder`; do not schedule `performSttTranscription(file)`. Keep file recording for playback and storage.

- [ ] **Step 4: Coordinate terminal states**

Track recording stopped, native result/error, callback delivered, and shutdown. Stop/cancel native recognizer during `stopRecording`, `cancelRecording`, and `shutdown`. Deliver callback once after file validation and native terminal result. On native error, keep valid audio path and use `FALLBACK_TRANSCRIPT`.

- [ ] **Step 5: Run focused tests**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.VoiceNoteRecorderNativeSttTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/myvu/client/ai/VoiceNoteRecorder.kt app/src/test/java/com/myvu/client/ai/VoiceNoteRecorderNativeSttTest.kt
git commit -m "feat(notes): support Android native transcription"
```

### Task 6: Validate complete behavior and regression safety

**Files:**
- Modify: affected files only if validation exposes defects.
- Test: all existing and new tests.

- [ ] **Step 1: Run all JVM tests**

```bash
./gradlew test
```

Expected: PASS. If failure comes from pre-existing environment or unrelated test, record exact failing task and line; do not hide it.

- [ ] **Step 2: Run lint**

```bash
./gradlew lint
```

Expected: BUILD SUCCESSFUL, with no new permission, lifecycle, or API-level errors.

- [ ] **Step 3: Build debug APK**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL; APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Manual device checks**

On Android device/emulator with speech service:

1. Select Android STT in Settings; confirm HTTP fields disable/hide.
2. Deny microphone permission; confirm command and note fail cleanly without crash.
3. Grant permission; trigger AI command from glasses; confirm phone speech prompt/listening and response returns through glasses.
4. Record note; confirm `.m4a` remains playable and transcript arrives once.
5. Select each existing HTTP provider; confirm previous flows still use their configured endpoints.
6. Stop service/activity during recognition; confirm no late callback or leaked recognizer.

- [ ] **Step 5: Commit validation fixes**

```bash
git add app/src/main/java app/src/main/res app/src/test
git commit -m "test(stt): validate native provider integration"
```
