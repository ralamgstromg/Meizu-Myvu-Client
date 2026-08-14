# AI Response Delivery Design

## Goal

Deliver every completed AI interaction to the Myvu glasses as final text, spoken audio, or both. Intermediate status messages such as “Consultando clima...” must never be the only user-visible result.

## Scope

This change covers response path from `AiConversation` through AI-provider answers, tool/action results, external lookups, `AiProtocol.chatAnswer()`, transport delivery, and `TtsPlayer`. It includes duplicate suppression, stale-session protection, failure handling, and unit tests. It applies to every query category, including general knowledge, web search or Google lookup when supported, current time, weather, currency conversion, calculations, navigation, phone actions, and future tools. It does not replace AI providers, STT providers, Bluetooth transport, or existing TTS providers.

## Current Problem

`AiConversation.deliver()` already calls `AiProtocol.chatAnswer()` and `TtsPlayer`, but final delivery is not proven across all paths. Weather and phone-action flows may produce intermediate status only, return an empty action result, or complete outside the normal AI delivery path. TTS failure may also obscure whether text delivery completed. The first implementation step must trace and instrument each boundary before changing behavior.

## Design Decisions

### 1. One final-response delivery boundary

Introduce `AiResponse` and `AiResponseDelivery` under `ai/`. `AiConversation` remains responsible for session orchestration and action execution; `AiResponseDelivery` owns final text delivery, optional speech, playback states, and one-time turn completion.

The delivery boundary receives a complete response:

```kotlin
data class AiResponse(
    val sessionId: String,
    val text: String,
    val shouldSpeak: Boolean,
    val baseStatus: Int = 1,
    val source: Source = Source.AI
) {
    enum class Source {
        AI,
        WEB_SEARCH,
        TIME,
        WEATHER,
        CURRENCY,
        CALCULATION,
        NAVIGATION,
        PHONE_ACTION,
        ERROR
    }
}
```

The response text must be nonblank. A blank provider or action result becomes a user-facing error response before delivery.

### 2. Text is primary; speech is an additional channel

`AiProtocol.chatAnswer(sessionId, text, baseStatus)` is sent before TTS starts. TTS must not be the only response channel. If TTS fails, text remains delivered and the turn still closes cleanly.

`PLAY_STATE_START` is sent only when speech starts. `PLAY_STATE_END` is sent for success, failure, cancellation, and timeout. `endTurn()` is emitted once per session.

### 3. Session identity and idempotency

Every final response carries the active `sessionId`. Delivery rejects stale sessions and duplicate final-delivery attempts. Late AI, weather, or TTS callbacks cannot deliver content after a newer session starts.

The session state must prevent double completion from repeated provider callbacks, repeated TTS callbacks, timeout races, and service teardown.

### 4. Every query category rejoins the same response path

Every query must produce one normalized `AiResponse`, regardless of how result is obtained. Categories include:

- general AI answer;
- web search or Google lookup when supported by configured provider/tool;
- current date and time;
- weather and forecasts;
- currency conversion and exchange rates;
- calculations and unit conversion;
- navigation and route results;
- phone, notification, media, and device actions;
- future tools or integrations.

Intermediate status such as “Buscando...” or “Consultando clima...” may be shown, but never substitutes final answer. Tool results must return structured data or explicit failure to orchestration layer. No tool may finish silently in a log, cache, notification, detached callback, or side channel.

Weather result uses `Source.WEATHER`; web lookup uses `Source.WEB_SEARCH`; current time uses `Source.TIME`; currency uses `Source.CURRENCY`; calculation uses `Source.CALCULATION`; navigation uses `Source.NAVIGATION`. Unknown or provider-only answers use `Source.AI`.

If weather or another lookup is executed as an AI phone action, `PhoneActionExecutor` must return completed user-facing text to `AiConversation`; it must not own final delivery separately.

### 5. Action results have explicit fallback semantics

When an action is confirmed and returns no text, use “Acción ejecutada en el teléfono.” When no action is detected and the AI provider returns blank output, use an error response such as “No pude obtener una respuesta del agente en este momento.” Do not present action-success text for an unconfirmed action.

When an action is confirmed and returns no text, use “Acción ejecutada en el teléfono.” When no action is detected and the AI provider returns blank output, use an error response such as “No pude obtener una respuesta del agente en este momento.” Do not present action-success text for an unconfirmed action.

### 6. Preserve existing providers and transport

Use existing `AiClient`, `AiProvider`, `TtsPlayer`, `AiProtocol`, `ConnectionManager`, `AppLayer`, and Bluetooth sessions. Do not add an HTTP endpoint, new AI provider, new TTS provider, or transport protocol unless investigation proves an existing protocol consumer cannot handle `chatAnswer`.

## Data Flow

```text
STT or askText
  -> AiConversation session
  -> AiClient.ask()
  -> action processing / weather resolution
  -> AiResponse(sessionId, final text, speech policy)
  -> AiResponseDelivery
  -> AiProtocol.chatAnswer()
  -> ConnectionManager.sendAction()
  -> AppLayer / glasses
  -> optional TtsPlayer
  -> play-state end
  -> endTurn()
```

Intermediate status messages remain separate from final response delivery:

```text
any query -> “Consultando...” -> provider/tool result -> normalized AiResponse -> final chatAnswer

weather request -> “Actualizando clima...” -> WeatherResult -> final chatAnswer
web query -> “Buscando...” -> SearchResult -> final chatAnswer
currency query -> “Consultando tasa...” -> ConversionResult -> final chatAnswer
time query -> “Consultando hora...” -> ClockResult -> final chatAnswer
```

## Investigation and Observability

Before behavior changes, log boundaries using `sessionId`, provider, source, and lengths only. Never log API keys or full sensitive prompts.

Required events:

```text
AI_REQUEST_STARTED sessionId provider questionLength
AI_RESPONSE_RECEIVED sessionId answerLength
AI_ACTION_PROCESSED sessionId source answerLength
AI_TEXT_SENT sessionId answerLength baseStatus
AI_TTS_STARTED sessionId answerLength
AI_TTS_FINISHED sessionId success
AI_TURN_FINISHED sessionId reason
```

Investigation must verify:

- provider or tool returns complete result;
- result category is identified without relying on fragile text matching alone;
- `deliver()` receives normalized answer;
- action processing preserves answer;
- `chatAnswer` reaches transport;
- glasses-side consumer renders answer;
- TTS callback completes;
- `finish()` does not race final delivery;
- no category-specific path bypasses final delivery boundary.

## Error and Lifecycle Handling

Handle provider exceptions, blank responses, weather failures, TTS failures, transport unavailability, stale callbacks, duplicate callbacks, new sessions, and service destruction.

Text delivery failure must be logged and reflected in completion state. TTS failure must not retract text. A disconnected transport must not leave the session permanently in speaking or processing state.

All completion paths must be idempotent:

- one final text attempt per session;
- one `PLAY_STATE_END` when playback was started;
- one `endTurn()`;
- no next turn after cancellation or stale session.

## Testing Strategy

Add JVM tests for pure response construction, delivery orchestration, duplicate suppression, stale sessions, weather formatting, blank answers, action fallbacks, and TTS failure. Use fakes for action sender and TTS player.

Run existing regression suite, lint, debug build, and `git diff --check`. Device validation must cover normal AI, weather, TTS enabled, TTS disabled, TTS failure, disconnect during response, and a new query after completion.

## Acceptance Criteria

- Normal AI query displays final answer on glasses.
- Weather query displays weather result, not only an intermediate status.
- Web or Google lookup displays search answer, not only “Buscando...”.
- Current-time query displays current time.
- Currency conversion displays converted amount and rate context.
- Calculation, navigation, and phone-action queries display completed result or explicit failure.
- Any future provider/tool result uses same final-delivery boundary without new output path.
- Configured TTS speaks final answer.
- Text remains available when TTS fails or is disabled.
- No duplicate final responses or stale-session responses.
- Playback and turn-end protocol events remain balanced.
- Provider, weather, action, transport, and TTS errors produce observable state and user-facing fallback where transport permits.
- `./gradlew test`, `./gradlew :app:lintDebug`, and `./gradlew assembleDebug` pass.
