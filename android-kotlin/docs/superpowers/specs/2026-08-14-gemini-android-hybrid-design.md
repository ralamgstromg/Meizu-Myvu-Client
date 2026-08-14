# Gemini Android Hybrid AI Design

## Goal

Add a selectable Gemini Android provider that uses Gemini Nano on-device when available and falls back to the Gemini API when configured, then routes validated results through the existing glasses AI protocol and response delivery modes.

## Scope

The feature covers provider selection, backend capability detection, Nano-first fallback, secure Gemini API credentials, structured action responses, and integration with `AiConversation`, `PhoneActionExecutor`, and `AiResponseDelivery`.

Existing AI providers remain available. The hybrid provider does not silently switch to OpenAI, Claude, Groq, NVIDIA, or local custom providers.

## Important platform constraint

Gemini Nano availability depends on device model, Android release, Google system components, downloaded model state, region, and supported task. Gemini API is a network service and is not local processing. Settings must state this before the user configures the fallback.

## Architecture

`GeminiHybridClient` implements the existing `AiClient` contract through two adapters:

- `GeminiNanoBackend`: isolates Android AICore/Google AI Edge APIs and reports capability and task errors.
- `GeminiApiBackend`: calls Gemini API using a securely stored API key and the project's existing direct HTTP style unless the selected official dependency requires another adapter.

The hybrid client selects Nano first. If policy permits and the Nano error is fallback-eligible, it invokes Gemini API. The selected backend is returned as metadata for diagnostics; prompts and complete responses are never logged.

```text
Glasses -> Android STT -> AiConversation -> GeminiHybridClient
                                      -> Nano backend
                                      -> Gemini API fallback
                         -> structured result validator
                         -> PhoneActionExecutor
                         -> final response
                         -> AiResponseDelivery
                         -> glasses text / voice / both
```

## Provider and fallback policy

Add one provider ID, `gemini_android`, representing the hybrid provider. Do not expose Nano and Gemini API as unrelated AI providers.

Fallback policies:

- `NANO_THEN_API`: default; use Nano first, then Gemini API for eligible local failures.
- `NANO_ONLY`: never send prompts to Gemini API.
- `API_ONLY`: skip Nano and use Gemini API.

Fallback is not allowed for cancellation, stale sessions, invalid user configuration, or a user-selected `NANO_ONLY` policy. A failed API request produces a bounded error response and closes the glasses turn normally.

## Backend interfaces

Use testable interfaces independent of Android and network implementations:

```kotlin
interface GeminiBackend {
    fun availability(): GeminiAvailability
    fun ask(request: GeminiRequest, callback: (Result<GeminiResult>) -> Unit)
    fun cancel(requestId: String)
}
```

`GeminiRequest` contains only the minimum prompt, system instructions, session request ID, and structured-output requirement. `GeminiResult` contains answer text, optional validated action candidates, backend ID, and request ID.

Nano adapter must avoid class-loading or initialization crashes on unsupported devices. Capability detection and inference run off the main thread with cancellation and a bounded timeout.

## Structured actions

Gemini output must use a constrained JSON contract, never raw glasses protocol JSON:

```json
{
  "answer": "...",
  "actions": [
    {"type": "weather_query", "arguments": {"place": "Barranquilla", "day": "tomorrow"}}
  ]
}
```

Allowed action types are limited to existing phone-side tools: weather, time, currency conversion, calculation, web search, navigation, and approved phone actions. A validator rejects unknown types, malformed arguments, oversized values, and duplicate unsafe operations.

The app executes validated actions locally through existing feature services. Tool results are then included in a second bounded response-generation step, or formatted locally where an existing formatter already provides the final answer. Gemini never sends commands directly to the glasses.

If Gemini returns plain text or invalid structured output, preserve plain text as a response and execute no action. Do not retry indefinitely.

## Glasses integration

No new wire protocol is introduced. `AiResponseDelivery` remains the only final response path:

- visual channel uses `code=122`;
- speech channel uses `code=6` and play-state transitions;
- turn completion uses existing `code=107` behavior;
- response mode remains user-selectable: voice only, visual only, or both.

Session IDs and cancellation guards prevent stale Nano/API callbacks from delivering text, speech, or turn completion to a newer glasses session.

## Settings and privacy

Add `Gemini Android` to the AI provider selector. When selected, show:

- Gemini Nano capability state;
- fallback policy selector;
- secure Gemini API key field when API fallback is enabled;
- a verification action that checks capability without sending user prompts;
- explicit notice that Gemini API sends requests to Google and requires network access.

`NANO_ONLY` must hide or disable API-key use and guarantee no cloud request. API keys use `SecurePrefs`. Logs contain backend, status, request length, error class, and latency only; never API keys, audio, full prompts, or full responses.

## Error handling

Classify errors into:

- local unavailable/model missing/task unsupported: eligible for API fallback;
- API authentication/configuration: final configuration error;
- network timeout/server failure: bounded retry policy, then final error;
- cancellation/stale session: silently discard;
- invalid model output: text-only response, no tool execution.

Every terminal path must deliver or close one glasses turn and release backend resources.

## Testing

Add JVM tests with fake backends for:

- Nano-first selection;
- fallback only for eligible local errors;
- `NANO_ONLY` never calls API;
- `API_ONLY` skips Nano;
- API failure closes turn once;
- cancellation and stale callback suppression;
- structured action validation and rejection;
- plain-text fallback;
- provider and fallback-policy persistence/defaults.

Integration tests must verify that final delivery emits expected `code=122` and/or `code=6` according to the existing response mode, without duplicate `endTurn`.

Manual matrix must include a device with Nano, a device without Nano, missing model, no network, invalid API key, and cancellation during inference.

## Non-goals

- Replacing existing AI providers.
- Implementing a general autonomous agent.
- Sending arbitrary model-generated protocol commands to glasses.
- Making Gemini API local or claiming cloud fallback is offline.
- Replacing existing weather, navigation, or web-search data sources.
