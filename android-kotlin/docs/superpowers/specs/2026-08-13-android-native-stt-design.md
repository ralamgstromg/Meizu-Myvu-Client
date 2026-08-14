# Android Native STT Design

## Goal

Add Android native speech recognition as a selectable third STT provider. It must use Android's available speech services through `android.speech.SpeechRecognizer`, not the app's HTTP transcription API.

The provider must support both AI commands and voice notes. For AI commands, recognition uses the phone microphone because `SpeechRecognizer` accepts live microphone input, not Opus or PCM already received from the glasses.

## Provider model

Keep existing provider IDs and add `android`:

- Existing remote/local HTTP providers remain unchanged.
- `android` requires no API key, endpoint, or model.
- Settings persists the provider selection through existing `Prefs.sttProvider()`.

The implementation must distinguish live microphone recognition from transcription of an existing audio file or PCM buffer. Do not force both operations behind an interface that hides their incompatible input models.

## AndroidSpeechRecognizer

Create `AndroidSpeechRecognizer` as a lifecycle-safe wrapper around `SpeechRecognizer`.

Responsibilities:

- Check `SpeechRecognizer.isRecognitionAvailable(context)`.
- Run all recognizer calls on the main looper.
- Start free-form recognition with the configured language.
- Set `RecognizerIntent.EXTRA_PREFER_OFFLINE` to `true`.
- Return final non-empty hypothesis through a callback.
- Optionally expose partial results for future UI feedback.
- Expose `stop()`, `cancel()`, and `destroy()`.
- Prevent duplicate starts, duplicate terminal callbacks, and callbacks after destruction.
- Report recognition error code and internal message to caller.

Use `RECORD_AUDIO` permission checks at the UI boundary before starting an operation. The wrapper must not request permission itself.

## AI command flow

Current HTTP/local flow remains:

```text
GlassesMicStream -> OpusDecoderStream -> PCM -> HTTP STT -> onTranscript()
```

For provider `android`:

```text
AI trigger -> AndroidSpeechRecognizer(phone microphone) -> onTranscript() -> existing AI flow -> glasses response
```

`AiConversation` must branch explicitly by selected provider:

- Do not start `GlassesMicStream` or `OpusDecoderStream` for Android recognition.
- Preserve existing AI session acknowledgement and downstream `onTranscript()` processing.
- Cancel native recognition on page close, timeout, `finish()`, and `shutdown()`.
- Ignore callbacks belonging to an older `sessionId`.
- Do not silently fall back to HTTP STT after native recognition failure.
- Keep response transport unchanged through `ConnectionManager`.

Phone and glasses recognition must not run concurrently.

## Voice note flow

For HTTP/local providers, preserve existing behavior:

```text
MediaRecorder -> .m4a -> HTTP transcription -> note callback
```

For Android provider:

```text
MediaRecorder -> .m4a
SpeechRecognizer(phone microphone) -> transcript
```

`VoiceNoteRecorder` must:

1. Start `MediaRecorder` and native recognition for an Android-provider note.
2. Stop/cancel native recognition before or while stopping `MediaRecorder`.
3. Preserve the `.m4a` file for playback and note storage.
4. Complete callback exactly once after both operations reach terminal state.
5. Never send the `.m4a` file to an HTTP endpoint for Android provider.
6. Preserve the file and return `FALLBACK_TRANSCRIPT` when native recognition fails.
7. Cancel and destroy recognizer during `shutdown()` and cancellation.

Existing callback shape should remain stable unless implementation requires an internal coordinator.

## Settings behavior

Add Android as the third STT choice in `SettingsActivity`.

When selected:

- Hide or disable STT API key, endpoint, and model fields.
- Do not read or validate an API key.
- Show that Android speech services are used and availability depends on device configuration; microphone permission is required.
- Persist `android` through `Prefs`.
- Availability failure should not prevent saving the choice; operation reports failure when started.

Optionally persist STT language separately from UI language. If no setting is added, use `Locale.getDefault()`.

## Lifecycle and errors

Handle explicitly:

- Native recognition unavailable.
- Missing or denied microphone permission.
- Microphone busy.
- Unsupported language.
- Empty result.
- Timeout without speech.
- Recognition service disconnect.
- Normal cancellation.
- Duplicate or late callback.

`SpeechRecognizer` must be destroyed on terminal error, cancellation, owner shutdown, and owner destruction. Do not store it globally in `MyvuService`.

## Tests

Add JVM tests for deterministic logic:

- Provider IDs and Android provider classification.
- Android provider does not require API key, endpoint, or model.
- `AiConversation` fake recognizer path does not start glasses audio capture.
- Native result reaches existing transcript handling.
- Native error finishes session without HTTP fallback.
- Session-old callbacks are ignored.
- Cancellation and shutdown release native engine.
- `VoiceNoteRecorder` coordinates recorder and recognizer and invokes callback once.
- Native note error preserves audio path and returns fallback transcript.

Use a fake recognizer/engine seam. Do not make JVM tests depend on a real Android speech engine. Add instrumented tests for permission and platform recognizer behavior only if the repository's Android test setup supports them.

## Constraints

- No external dependency for native STT.
- No app HTTP call for provider `android`.
- Existing HTTP/local providers remain available.
- No model download or embedded Whisper/Vosk implementation in this change.
