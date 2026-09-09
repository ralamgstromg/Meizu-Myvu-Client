# Plan: Fix Local / OpenAI-Compatible Response Handling & Tool Calling

## Problem
1. LiteLLM endpoint `https://soft-ia.co/litellm/v1/chat/completions` with token `gGzvt...` only accepts `model="gafas"`.
2. When model returns `tool_calls`, `message.content` is `null`.
3. `LocalAiClient.chat()` was calling `askOnce(body)` in `AiHttpClient`, which called `extractText()`. Because `content == null`, `extractText()` returned `""`, triggering `IOException("Local API returned an empty answer")` and failing the request.
4. `LOCAL_READ_TIMEOUT_MS` was set to 240s, hiding connection delays.

## Solution Steps
1. In `AiHttpClient.kt`:
   - Implement `postRaw(body: String): String` to execute HTTP POST and return the unparsed response body.
   - Add detailed logging of URL and HTTP status code.
   - Refactor `askOnce(body)` to delegate to `postRaw(body)`.
   - Reduce `LOCAL_READ_TIMEOUT_MS` to 45000ms.
2. In `LocalAiClient.kt`:
   - Change `chat()` to use `postRaw(body)` instead of `askOnce(body)`.
3. In `AiConversation.kt`:
   - Include `endpoint` and `model` in `AI_REQUEST_STARTED` logging.
4. Verify with `./gradlew assembleDebug`.
5. Run `codegraph sync`.
