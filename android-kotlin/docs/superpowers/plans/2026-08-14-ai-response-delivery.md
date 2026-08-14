# AI Response Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver final result for every AI query as glasses text, voice, or both, including general answers, web searches, current time, weather, currency conversion, calculations, navigation, phone actions, and future tools.

**Architecture:** Trace and instrument current response flow first. Then normalize every provider/tool/action result into one `AiResponse` and send it through one idempotent `AiResponseDelivery` boundary. Text reaches `AiProtocol.chatAnswer()` first; TTS remains an optional second channel. Intermediate statuses never replace final responses.

**Tech Stack:** Kotlin, existing `AiClient`/`AiProvider`, `AiConversation`, `PhoneActionExecutor`, weather and other feature services, `AiProtocol`, `ConnectionManager`, `AppLayer`, `TtsPlayer`, JUnit 4 JVM tests, Gradle.

**Spec:** `docs/superpowers/specs/2026-08-14-ai-response-delivery-design.md`

## Global Constraints

- Every completed query category uses one final-response delivery boundary.
- Final response carries active `sessionId`; stale and duplicate callbacks are ignored.
- `AiProtocol.chatAnswer()` sends text before TTS starts.
- TTS failure never removes already-sent text.
- Intermediate messages such as “Buscando...” and “Consultando clima...” are not final answers.
- Existing AI, STT, TTS, Bluetooth, and transport providers remain unchanged unless investigation proves a required compatibility defect.
- Do not add external search, currency, weather, or AI APIs during this fix; use currently configured provider/tool capabilities.
- Do not log API keys, full prompts, or sensitive response content.
- Preserve Android minSdk 26, compileSdk 35, and JVM 17.

---

### Task 1: Trace and instrument complete response flow

**Files:**
- Inspect: `app/src/main/java/com/myvu/client/ai/AiConversation.kt`
- Inspect: `app/src/main/java/com/myvu/client/ai/AiClient.kt`
- Inspect: concrete AI clients under `app/src/main/java/com/myvu/client/ai/`
- Inspect: `app/src/main/java/com/myvu/client/ai/PhoneActionExecutor.kt`
- Inspect: `app/src/main/java/com/myvu/client/app/feature/AiProtocol.kt`
- Inspect: `app/src/main/java/com/myvu/client/service/ConnectionManager.kt`
- Inspect: `app/src/main/java/com/myvu/client/app/AppLayer.kt`
- Inspect: `app/src/main/java/com/myvu/client/app/InboundRouter.kt`
- Inspect: `app/src/main/java/com/myvu/client/ai/TtsPlayer.kt`
- Inspect: weather, navigation, clock, currency, search, and action feature files found during trace
- Modify: same files only where boundary logs are required

**Interfaces:**
- Consumes: current AI query, tool, action, transport, and TTS flows.
- Produces: verified call-flow map and logs identifying where final result disappears.

- [ ] **Step 1: Enumerate every result-producing path.**

  Record, for each category, producer, result type, callback/thread, intermediate message, final text source, and current completion call:

  ```text
  general AI
  web/Google lookup
  current time
  weather
  currency conversion
  calculation/unit conversion
  navigation
  phone/device action
  unknown/future tool
  ```

  Do not assume all categories use `PhoneActionExecutor`; confirm callers and callbacks.

- [ ] **Step 2: Add boundary logs with session and category metadata.**

  Use events with `sessionId`, provider/source, lengths, and outcome only:

  ```text
  AI_REQUEST_STARTED
  AI_RESPONSE_RECEIVED
  AI_ACTION_PROCESSED
  AI_TOOL_RESULT_RECEIVED
  AI_TEXT_SENT
  AI_TTS_STARTED
  AI_TTS_FINISHED
  AI_TURN_FINISHED
  ```

  Redact question and answer bodies. Include category/source when known.

- [ ] **Step 3: Run focused tests and inspect logs.**

  Run:

  ```bash
  ./gradlew :app:testDebugUnitTest
  ```

  Reproduce at least general AI, weather, and one action query on device if available. Identify whether loss occurs before `deliver()`, inside action/tool processing, at `chatAnswer()`, transport, glasses consumer, or TTS lifecycle.

- [ ] **Step 4: Record confirmed root cause in the implementation notes.**

  Do not change delivery behavior until the failing boundary is identified. If a category has no final-result callback, mark that path for Task 3 integration.

---

### Task 2: Add normalized response model and testable delivery dependencies

**Files:**
- Create: `app/src/main/java/com/myvu/client/ai/AiResponse.kt`
- Create: `app/src/main/java/com/myvu/client/ai/AiResponseSource.kt` only if keeping enum separate improves existing package style
- Modify: `app/src/main/java/com/myvu/client/ai/TtsPlayer.kt` only if a small injectable interface is required
- Test: `app/src/test/java/com/myvu/client/ai/AiResponseTest.kt`

**Interfaces:**
- Consumes: final text from AI provider, tool, weather, search, clock, currency, calculation, navigation, and phone action.
- Produces: immutable `AiResponse` with `sessionId`, nonblank text, speech policy, status, and source.

- [ ] **Step 1: Write failing model tests.**

  Cover all source categories and reject blank text after normalization:

  ```kotlin
  @Test fun responsePreservesSessionAndSource()
  @Test fun responseTrimsWhitespace()
  @Test(expected = IllegalArgumentException::class)
  fun blankResponseIsRejected()
  ```

- [ ] **Step 2: Run model tests and verify failure.**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.AiResponseTest'
  ```

- [ ] **Step 3: Implement `AiResponse`.**

  Include sources `AI`, `WEB_SEARCH`, `TIME`, `WEATHER`, `CURRENCY`, `CALCULATION`, `NAVIGATION`, `PHONE_ACTION`, and `ERROR`. Keep user-facing text separate from intermediate status. Normalize surrounding whitespace and reject blank output.

- [ ] **Step 4: Run model tests.**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.AiResponseTest'
  ```

- [ ] **Step 5: Commit the model independently.**

  ```bash
  git add app/src/main/java/com/myvu/client/ai/AiResponse.kt app/src/test/java/com/myvu/client/ai/AiResponseTest.kt
  git commit -m "feat(ai): add normalized response model"
  ```

---

### Task 3: Normalize all AI, tool, and action results

**Files:**
- Modify: `app/src/main/java/com/myvu/client/ai/AiConversation.kt`
- Modify: `app/src/main/java/com/myvu/client/ai/PhoneActionExecutor.kt`
- Modify: result-producing weather/search/time/currency/calculation/navigation classes found in Task 1
- Test: `app/src/test/java/com/myvu/client/ai/AiResultNormalizationTest.kt`
- Test: category-specific formatter tests beside existing feature tests

**Interfaces:**
- Consumes: raw `AiClient` output, action execution result, and tool result callbacks.
- Produces: one `AiResponse` per completed query; no result path completes silently.

- [ ] **Step 1: Write failing normalization tests for every category.**

  Test concrete representative results:

  ```kotlin
  @Test fun generalAnswerBecomesAiResponse()
  @Test fun webSearchAnswerBecomesWebSearchResponse()
  @Test fun currentTimeBecomesTimeResponse()
  @Test fun weatherResultBecomesWeatherResponse()
  @Test fun currencyResultIncludesAmountAndRateContext()
  @Test fun calculationResultBecomesCalculationResponse()
  @Test fun navigationResultBecomesNavigationResponse()
  @Test fun confirmedPhoneActionWithNoTextUsesActionFallback()
  @Test fun blankUnclassifiedAnswerUsesErrorResponse()
  @Test fun toolFailureBecomesVisibleErrorResponse()
  ```

- [ ] **Step 2: Run tests and verify failure.**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.AiResultNormalizationTest'
  ```

- [ ] **Step 3: Define one result contract at orchestration boundary.**

  Each tool/action must return either completed user-facing text plus source, or explicit failure. Intermediate status stays outside this contract. Do not classify only by fragile answer wording when caller already knows source.

- [ ] **Step 4: Route general provider output through same contract.**

  `AiConversation` must convert nonblank provider output to `AiResponse(source = AI)` and blank output to `Source.ERROR` with visible fallback.

- [ ] **Step 5: Route web/search, time, weather, currency, calculations, navigation, and phone actions through same contract.**

  Results must return to `AiConversation` or shared orchestration layer, not directly call `chatAnswer`, TTS, notification-only output, or detached completion. Preserve existing intermediate status messages.

- [ ] **Step 6: Run normalization tests.**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.AiResultNormalizationTest'
  ```

- [ ] **Step 7: Commit normalization changes.**

  ```bash
  git add app/src/main/java app/src/test/java
  git commit -m "fix(ai): normalize tool and action results"
  ```

---

### Task 4: Implement idempotent text-and-voice delivery

**Files:**
- Create: `app/src/main/java/com/myvu/client/ai/AiResponseDelivery.kt`
- Modify: `app/src/main/java/com/myvu/client/ai/AiConversation.kt`
- Modify: `app/src/main/java/com/myvu/client/ai/TtsPlayer.kt` only for injectable callback boundary if needed
- Test: `app/src/test/java/com/myvu/client/ai/AiResponseDeliveryTest.kt`

**Interfaces:**
- Consumes: `AiResponse`, active-session predicate, action sender, TTS fake/player.
- Produces: one `chatAnswer`, optional TTS lifecycle, one `endTurn`, and completion result.

- [ ] **Step 1: Write failing delivery tests.**

  ```kotlin
  @Test fun sendsFinalTextBeforeStartingTts()
  @Test fun doesNotStartTtsWhenSpeechDisabled()
  @Test fun keepsTextWhenTtsFails()
  @Test fun ignoresDuplicateDeliveryForSameSession()
  @Test fun ignoresResponseFromStaleSession()
  @Test fun sendsPlayEndAfterTtsFailure()
  @Test fun sendsEndTurnOnlyOnce()
  @Test fun doesNotSendPlayEndWhenTtsNeverStarted()
  ```

- [ ] **Step 2: Run tests and verify failure.**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.AiResponseDeliveryTest'
  ```

- [ ] **Step 3: Implement `AiResponseDelivery`.**

  Required order:

  ```text
  validate active session
  suppress duplicate final delivery
  send AiProtocol.chatAnswer()
  record text outcome
  if speech enabled: send PLAY_STATE_START and start TTS
  on every TTS terminal callback: send PLAY_STATE_END once
  send endTurn once
  notify AiConversation to finish or start next turn
  ```

  Text-send failure must complete with logged failure. TTS failure must not retract text. Late callbacks must be ignored.

- [ ] **Step 4: Replace direct delivery logic in `AiConversation.deliver()`.**

  `deliver()` only normalizes raw result, creates `AiResponse`, and delegates. Remove duplicate direct `chatAnswer`, playback, and turn-end sequences after tests prove new boundary handles them.

- [ ] **Step 5: Run delivery tests.**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.AiResponseDeliveryTest'
  ```

- [ ] **Step 6: Commit delivery boundary.**

  ```bash
  git add app/src/main/java/com/myvu/client/ai app/src/test/java/com/myvu/client/ai
  git commit -m "fix(ai): deliver final responses as text and voice"
  ```

---

### Task 5: Close weather and external-lookup callback gaps

**Files:**
- Modify: weather result producer identified in Task 1
- Modify: web/search result producer identified in Task 1
- Modify: current-time, currency, calculation, and navigation result producers identified in Task 1
- Modify: `app/src/main/java/com/myvu/client/app/GlassesEventHandler.kt` only if trigger currently emits status without result callback
- Test: category-specific result and error tests

**Interfaces:**
- Consumes: asynchronous provider/tool results and failures.
- Produces: exactly one normalized `AiResponse` callback for success or failure.

- [ ] **Step 1: Add failing tests for asynchronous completion.**

  Cover success, timeout, network failure, parse failure, missing data, cancellation, and callback-after-cancel for weather and each existing external lookup.

- [ ] **Step 2: Run focused tests and verify failure.**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests '*Weather*' --tests '*Search*' --tests '*Currency*'
  ```

- [ ] **Step 3: Return completed results to orchestration.**

  Keep “Actualizando clima...”, “Buscando...”, and equivalent messages as intermediate state. Convert completed data into localized concise text with relevant context. Examples:

  ```text
  Mañana habrá 31 °C en Barranquilla, con probabilidad de lluvia de 40 %.
  En Google encontré: ...
  1 USD equivale a ... COP; 20 USD son ... COP.
  En Barranquilla son las 14:35.
  ```

  Do not hard-code these examples as universal output; use actual returned values and existing locale conventions.

- [ ] **Step 4: Deliver failures through same response boundary.**

  Use explicit visible text for provider, timeout, unavailable-location, malformed-data, and disconnected-transport failures. Never end after intermediate status only.

- [ ] **Step 5: Run focused tests.**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests '*Weather*' --tests '*Search*' --tests '*Currency*'
  ```

- [ ] **Step 6: Commit external-result integration.**

  ```bash
  git add app/src/main/java app/src/test/java
  git commit -m "fix(ai): return external lookup results to delivery"
  ```

---

### Task 6: Harden lifecycle, concurrency, and transport failure

**Files:**
- Modify: `app/src/main/java/com/myvu/client/ai/AiConversation.kt`
- Modify: `app/src/main/java/com/myvu/client/ai/AiResponseDelivery.kt`
- Modify: `app/src/main/java/com/myvu/client/service/ConnectionManager.kt` only if send outcome is currently invisible
- Test: `app/src/test/java/com/myvu/client/ai/AiConversationLifecycleTest.kt`

**Interfaces:**
- Consumes: session cancellation, new query, service destruction, provider timeout, transport disconnect, and repeated callbacks.
- Produces: balanced final state with no stale or duplicate output.

- [ ] **Step 1: Write failing lifecycle tests.**

  ```kotlin
  @Test fun lateProviderAnswerCannotReachNewSession()
  @Test fun repeatedProviderCallbackDeliversOnce()
  @Test fun repeatedTtsCallbackEndsTurnOnce()
  @Test fun cancellationPreventsNextTurn()
  @Test fun serviceShutdownLeavesNoSpeakingState()
  @Test fun transportUnavailableDoesNotHangProcessingState()
  ```

- [ ] **Step 2: Run tests and verify failure.**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.AiConversationLifecycleTest'
  ```

- [ ] **Step 3: Implement session state guards.**

  Enforce valid states `LISTENING`, `PROCESSING`, `DELIVERING_TEXT`, `SPEAKING`, `COMPLETED`, `FAILED`, and `CANCELLED`. Ignore callbacks from old session IDs and terminal states. Ensure only one `PLAY_STATE_END` follows a started playback and only one `endTurn()` occurs.

- [ ] **Step 4: Handle transport and teardown outcomes.**

  If text cannot be sent because no active session exists, log outcome and complete locally. If TTS is active during cancellation or destruction, stop it and close playback state exactly once.

- [ ] **Step 5: Run lifecycle tests.**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.AiConversationLifecycleTest'
  ```

- [ ] **Step 6: Commit lifecycle hardening.**

  ```bash
  git add app/src/main/java app/src/test/java
  git commit -m "fix(ai): guard response delivery lifecycle"
  ```

---

### Task 7: Run full verification and device matrix

**Files:**
- Inspect: generated reports and runtime logs
- Modify: no source changes unless verification exposes a confirmed defect

- [ ] **Step 1: Run JVM regression tests.**

  ```bash
  ./gradlew test
  ```

- [ ] **Step 2: Run lint.**

  ```bash
  ./gradlew :app:lintDebug
  ```

  Expected: zero lint errors. Existing nonblocking warnings may remain if unrelated.

- [ ] **Step 3: Build debug APK.**

  ```bash
  ./gradlew assembleDebug
  ```

- [ ] **Step 4: Check patch formatting.**

  ```bash
  git diff --check
  ```

- [ ] **Step 5: Validate on connected glasses.**

  Execute each query and confirm final text plus optional speech:

  ```text
  general answer
  web/Google lookup
  current time
  weather forecast
  currency conversion
  calculation/unit conversion
  navigation
  phone/device action
  provider/tool failure
  TTS disabled
  TTS failure
  disconnect during response
  new query after previous completion
  ```

- [ ] **Step 6: Review logs for one complete lifecycle per query.**

  Confirm each session has request, result/tool, text attempt, optional TTS terminal event, and one turn-finished event. Confirm no query ends after intermediate status without final result or explicit error.

- [ ] **Step 7: Report exact verification outcomes.**

  Include tests, lint, build, device availability, and any unverified category. Do not claim device success without actual glasses test.
