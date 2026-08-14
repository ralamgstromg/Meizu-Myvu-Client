# Gemini Android Hybrid AI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a selectable `Gemini Android` provider that uses Gemini Nano on-device first and falls back to Gemini API when configured, while returning validated responses through the existing Myvu glasses AI protocol.

**Architecture:** Keep one user-facing `GEMINI_ANDROID` provider backed by `GeminiHybridClient`. Hide platform-specific Nano/AICore and HTTP/API details behind `GeminiBackend`; select Nano first according to a persisted fallback policy, then validate structured actions before invoking existing phone tools. `AiConversation` and `AiResponseDelivery` remain orchestration and delivery boundaries, so existing voice/visual response modes and glasses wire codes remain unchanged.

**Tech Stack:** Kotlin/JVM 17, Android SDK 35, Gradle wrapper 8.14.3, existing `AiClient`/`AiProvider` abstractions, `HttpURLConnection` conventions, `SecurePrefs`, Android AICore/Google AI Edge adapter selected after dependency/API verification, JUnit JVM tests.

**Spec:** `docs/superpowers/specs/2026-08-14-gemini-android-hybrid-design.md`

## Global Constraints

- Gemini Nano is local only when the device exposes a compatible Android AICore/Google AI Edge runtime and model.
- Gemini API fallback is network-based and must be labeled as sending requests to Google.
- Default fallback policy is `NANO_THEN_API`; `NANO_ONLY` must never call cloud backend.
- Existing AI providers remain available and are never silently selected as Gemini fallback.
- Model output never sends raw protocol commands to glasses; app validates and executes allowed actions.
- API keys use `SecurePrefs`; logs contain no API keys, audio, full prompts, or full responses.
- Every terminal path closes at most one glasses turn and suppresses stale/cancelled callbacks.
- Do not add external SDK dependencies until their Android/API compatibility is verified in the version catalog and build.
- Work in current checkout; do not commit or push unless explicitly requested.

---

### Task 1: Define provider, backend contracts, and persisted policy

**Files:**
- Create: `app/src/main/java/com/myvu/client/ai/GeminiBackend.kt`
- Create: `app/src/main/java/com/myvu/client/ai/GeminiModels.kt`
- Create: `app/src/main/java/com/myvu/client/ai/GeminiFallbackPolicy.kt`
- Modify: `app/src/main/java/com/myvu/client/ai/AiProvider.kt`
- Modify: `app/src/main/java/com/myvu/client/core/Prefs.kt`
- Test: `app/src/test/java/com/myvu/client/ai/GeminiFallbackPolicyTest.kt`

**Interfaces:**
- `GeminiBackend.availability(): GeminiAvailability`.
- `GeminiBackend.ask(request: GeminiRequest, callback: (Result<GeminiResult>) -> Unit)`.
- `GeminiBackend.cancel(requestId: String)`.
- `GeminiFallbackPolicy` values `NANO_THEN_API`, `NANO_ONLY`, `API_ONLY` with stable IDs.
- `Prefs.geminiFallbackPolicy(context): String` defaults to `nano_then_api`; `Prefs.setGeminiFallbackPolicy(context, id)` persists it.
- `AiProvider.GEMINI_ANDROID` exposes no ordinary provider API key/model fields; hybrid client owns its secure key.

- [ ] **Step 1: Write failing policy tests**

```kotlin
@Test
fun defaultPolicyUsesNanoThenApi() {
    assertEquals(GeminiFallbackPolicy.NANO_THEN_API,
        GeminiFallbackPolicy.fromId(null))
}

@Test
fun unknownPolicyFallsBackToSafeDefault() {
    assertEquals(GeminiFallbackPolicy.NANO_THEN_API,
        GeminiFallbackPolicy.fromId("bad-value"))
}

@Test
fun localOnlyPolicyDisallowsCloudFallback() {
    assertFalse(GeminiFallbackPolicy.NANO_ONLY.allowsApiFallback)
    assertTrue(GeminiFallbackPolicy.NANO_THEN_API.allowsApiFallback)
}
```

- [ ] **Step 2: Run the focused test and verify expected unresolved-symbol failure**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.GeminiFallbackPolicyTest'`

Expected: FAIL because policy types do not exist yet.

- [ ] **Step 3: Implement contracts and policy minimally**

Use sealed/data types that carry only request ID, answer, backend ID, and action candidates. Keep callbacks asynchronous and cancellation explicit. Add `GEMINI_ANDROID` to `AiProvider` with a user-facing label describing Nano-first/API fallback.

- [ ] **Step 4: Add `Prefs` accessors and rerun focused tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.GeminiFallbackPolicyTest'`

Expected: PASS.

### Task 2: Add secure Gemini API settings and HTTP backend

**Files:**
- Create: `app/src/main/java/com/myvu/client/ai/GeminiApiBackend.kt`
- Modify: `app/src/main/java/com/myvu/client/core/Prefs.kt`
- Modify: `app/src/main/java/com/myvu/client/core/SecurePrefs.kt` only if a shared helper is required
- Test: `app/src/test/java/com/myvu/client/ai/GeminiApiBackendTest.kt`

**Interfaces:**
- `Prefs.geminiApiKey(context): String` and `Prefs.setGeminiApiKey(context, value: String)` using `SecurePrefs` key `gemini_api_key`.
- `Prefs.geminiModel(context): String` and setter, with documented default model.
- `GeminiApiBackend` implements `GeminiBackend` and accepts an injectable HTTP transport/factory for JVM tests.

- [ ] **Step 1: Write failing HTTP contract tests**

Cover request serialization without real network:

```kotlin
@Test
fun apiBackendMapsSuccessfulJsonToGeminiResult() { /* fake 200 response */ }

@Test
fun apiBackendMapsUnauthorizedResponseToConfigurationError() { /* fake 401 */ }

@Test
fun apiBackendNeverLogsApiKeyOrPromptBody() { /* inspect LogBus history */ }
```

- [ ] **Step 2: Run focused tests and verify red**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.GeminiApiBackendTest'`

Expected: FAIL because backend and request mapping are absent.

- [ ] **Step 3: Implement backend using project HTTP conventions**

Build Gemini API request with system instruction, user content, response MIME/schema request where supported, timeout, cancellation, bounded response size, and HTTP status classification. Store/read API key only through `SecurePrefs`. Never include prompt or API key in logs.

- [ ] **Step 4: Rerun focused tests**

Expected: PASS, including malformed JSON and network failure classification.

### Task 3: Add safe Nano capability adapter

**Files:**
- Create: `app/src/main/java/com/myvu/client/ai/GeminiNanoBackend.kt`
- Create: `app/src/main/java/com/myvu/client/ai/GeminiCapability.kt`
- Modify: `gradle/libs.versions.toml` and `app/build.gradle.kts` only after verifying official compatible dependency/API
- Test: `app/src/test/java/com/myvu/client/ai/GeminiNanoBackendTest.kt`

**Interfaces:**
- `GeminiCapabilityDetector.detect(): GeminiAvailability`.
- `GeminiNanoBackend` implements `GeminiBackend` and receives detector/runtime adapter through constructor.
- Unsupported devices return `UNAVAILABLE` without class initialization crash.

- [ ] **Step 1: Write failing adapter tests**

```kotlin
@Test
fun unavailableNanoDoesNotInvokeRuntime() { /* detector says unavailable */ }

@Test
fun supportedNanoMapsRuntimeTextToResult() { /* fake runtime */ }

@Test
fun modelMissingIsMarkedEligibleForApiFallback() { /* typed error */ }
```

- [ ] **Step 2: Run focused tests and verify red**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.GeminiNanoBackendTest'`

Expected: FAIL because adapter types do not exist.

- [ ] **Step 3: Verify official Android API/dependency before editing Gradle**

Confirm package names, minSdk 26 support, current dependency version, and runtime availability API from official Android/Google documentation. If dependency cannot support project floors, isolate implementation behind reflection or a compile-safe adapter and document unavailable capability; do not fake Nano support.

- [ ] **Step 4: Implement detector/runtime adapter**

Keep all AICore/Google AI Edge references in this file or a small adapter. Execute inference off main thread, apply bounded timeout, support cancellation, classify unavailable/model-missing/task-unsupported errors, and avoid prompt/response logging.

- [ ] **Step 5: Rerun focused tests**

Expected: PASS on JVM fakes; unsupported runtime path must remain safe.

### Task 4: Implement Nano-first hybrid client

**Files:**
- Create: `app/src/main/java/com/myvu/client/ai/GeminiHybridClient.kt`
- Modify: `app/src/main/java/com/myvu/client/ai/AiProvider.kt`
- Test: `app/src/test/java/com/myvu/client/ai/GeminiHybridClientTest.kt`

**Interfaces:**
- `GeminiHybridClient` implements `AiClient` and receives Nano backend, API backend, policy, dispatcher, and clock/timeout dependencies where needed.
- `GeminiHybridClient.ask(request, callback)` returns one terminal `GeminiResult` or one typed failure.

- [ ] **Step 1: Write failing fallback tests**

```kotlin
@Test
fun nanoSuccessDoesNotCallApi() { /* fake both; assert API calls == 0 */ }

@Test
fun eligibleNanoFailureFallsBackToApi() { /* model missing */ }

@Test
fun nanoOnlyNeverCallsApi() { /* policy NANO_ONLY */ }

@Test
fun apiOnlySkipsNano() { /* policy API_ONLY */ }

@Test
fun cancellationSuppressesLateBackendCallback() { /* cancel then callback */ }
```

- [ ] **Step 2: Run focused tests and verify red**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.GeminiHybridClientTest'`

Expected: FAIL because hybrid client is absent.

- [ ] **Step 3: Implement policy-driven selection**

Use Nano first only for `NANO_THEN_API`; fallback only typed eligible local errors. Preserve request ID and suppress stale callbacks. Do not fallback to existing non-Gemini providers.

- [ ] **Step 4: Rerun focused tests**

Expected: PASS with exactly one terminal callback per request.

### Task 5: Validate structured actions and connect existing phone tools

**Files:**
- Create: `app/src/main/java/com/myvu/client/ai/GeminiActionSchema.kt`
- Create: `app/src/main/java/com/myvu/client/ai/GeminiActionValidator.kt`
- Modify: `app/src/main/java/com/myvu/client/ai/PhoneActionExecutor.kt`
- Modify: `app/src/main/java/com/myvu/client/ai/AiConversation.kt`
- Test: `app/src/test/java/com/myvu/client/ai/GeminiActionValidatorTest.kt`

**Interfaces:**
- `GeminiActionValidator.parse(text: String): GeminiParsedResponse`.
- `GeminiActionValidator` accepts only allowlisted action types and bounded argument lengths.
- `PhoneActionExecutor` exposes an internal typed execution boundary for validated actions; existing action strings remain supported for backward compatibility.

- [ ] **Step 1: Write failing validation tests**

```kotlin
@Test
fun parsesAnswerAndWeatherAction() { /* valid JSON */ }

@Test
fun rejectsUnknownActionWithoutExecutingIt() { /* type=send_raw_protocol */ }

@Test
fun plainTextBecomesAnswerWithNoActions() { /* no JSON */ }

@Test
fun oversizedArgumentsAreRejected() { /* bounded input */ }
```

- [ ] **Step 2: Run focused tests and verify red**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.GeminiActionValidatorTest'`

Expected: FAIL because parser/validator are absent.

- [ ] **Step 3: Implement strict parser and validator**

Parse only expected top-level fields. Reject unknown actions, malformed argument types, empty required values, and protocol-shaped payloads. Plain text remains displayable and never triggers a tool.

- [ ] **Step 4: Integrate execution in `AiConversation`**

After Gemini result, validate actions, execute only valid actions via existing phone tools, then produce final answer using local formatter or one bounded follow-up request. Keep session/cancellation guards and one final `deliverFinal` call.

- [ ] **Step 5: Rerun focused tests and existing AI tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.GeminiActionValidatorTest' --tests 'com.myvu.client.ai.AiResponseDeliveryTest'`

Expected: PASS.

### Task 6: Add Settings UI and secure fallback controls

**Files:**
- Modify: `app/src/main/res/layout/activity_settings.xml`
- Modify: `app/src/main/java/com/myvu/client/ui/SettingsActivity.kt`
- Modify: `app/src/main/java/com/myvu/client/core/Prefs.kt`
- Test: `app/src/test/java/com/myvu/client/ai/GeminiSettingsPolicyTest.kt`

**Interfaces:**
- `Prefs` exposes secure API key, model, and fallback policy accessors.
- Settings maps provider `GEMINI_ANDROID` to Gemini fields without leaking key into ordinary preferences.

- [ ] **Step 1: Write failing persistence tests**

Cover default policy, round-trip policy IDs, API key setter/getter delegation, and invalid policy fallback.

- [ ] **Step 2: Run focused tests and verify red**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.GeminiSettingsPolicyTest'`

Expected: FAIL until accessors and provider mapping exist.

- [ ] **Step 3: Implement secure accessors and provider selector**

Add Gemini Android provider button. When selected, show Nano status, fallback toggle (`Nano y luego API`, `Solo Nano`, `Solo API`), secure API key, model, and a capability check action. Hide or disable API key when `Solo Nano` is selected.

- [ ] **Step 4: Add explicit privacy copy**

State that Nano runs locally when supported; Gemini API sends requests to Google and requires network. Do not claim API fallback is offline.

- [ ] **Step 5: Run persistence tests and resource compilation**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.GeminiSettingsPolicyTest' :app:compileDebugKotlin`

Expected: PASS.

### Task 7: Wire provider into `AiConversation` and glasses delivery

**Files:**
- Modify: `app/src/main/java/com/myvu/client/ai/AiProvider.kt`
- Modify: `app/src/main/java/com/myvu/client/ai/AiConversation.kt`
- Modify: `app/src/main/java/com/myvu/client/ai/AiResponseDelivery.kt` only if backend metadata/logging needs a narrow extension
- Test: `app/src/test/java/com/myvu/client/ai/AiConversationGeminiTest.kt`

**Interfaces:**
- `AiProvider.newClient(...)` creates `GeminiHybridClient` with configured policy and secure credentials.
- Existing `AiResponseMode` controls final text/voice delivery unchanged.

- [ ] **Step 1: Write failing integration tests with fake hybrid client**

Cover successful local answer, API fallback answer, validated weather action, API failure error, stale session, and response modes. Assert no duplicate `code=107`/turn completion.

- [ ] **Step 2: Run focused tests and verify red**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.AiConversationGeminiTest'`

Expected: FAIL because provider wiring is incomplete.

- [ ] **Step 3: Wire provider construction and backend metadata logs**

Log only `AI_GEMINI_BACKEND_SELECTED backend=NANO|GEMINI_API`, status, and bounded lengths. Preserve current context construction and existing delivery mode selection.

- [ ] **Step 4: Rerun focused and all AI tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.*'`

Expected: PASS.

### Task 8: Full verification and device test matrix

**Files:**
- Modify: `docs/superpowers/specs/2026-08-14-gemini-android-hybrid-design.md` only for verified implementation notes.
- Test artifacts: Gradle reports; no source changes unless verification exposes a defect.

- [ ] **Step 1: Run complete JVM tests**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew test`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run lint and APK build**

Run: `./gradlew lint assembleDebug`

Expected: BUILD SUCCESSFUL with no new lint errors.

- [ ] **Step 3: Install debug build**

Run: `./gradlew installDebug`

Expected: APK installs on connected test device.

- [ ] **Step 4: Execute manual matrix**

Verify on device with Nano, device without Nano, missing model, no network, invalid API key, cancellation during inference, `NANO_ONLY`, `API_ONLY`, and `NANO_THEN_API`. Verify weather/tool action, plain text answer, and all three existing glasses response modes.

- [ ] **Step 5: Inspect logs and protocol behavior**

Confirm logs expose only backend/status metadata. Confirm visual mode emits `code=122`, voice mode emits `code=6`, combined emits both, and each turn emits one completion. Confirm no prompt/API key/audio appears in logs.
