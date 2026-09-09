# Plan: Fix Infinite Loop in pendingNotifications Flushing

## Problem
In `ConnectionManager.kt`, `onSessionReady(transport)` flushes `pendingNotifications` using:
```kotlin
while (!pendingNotifications.isEmpty()) {
    val p = pendingNotifications.pollFirst() ?: break
    sendActionNow(p.actionJson, p.targetPkg, p.sourcePkg)
}
```
When `transport == null` (BLE session ready, RFCOMM relay not yet ready), `sendActionNow` detects:
`if (isNotification && transport == null && (relayEstablishing || canConnectRelay()))`
and re-adds the action to `pendingNotifications` and returns!
The `while` loop immediately polls the same action that was just re-added, causing an immediate, infinite synchronous loop on the Handler thread that floods the log with:
`!! app relay not ready -- queued notification for RFCOMM delivery`
`flushing queued action/notification: ...`

## Solution
1. In `ConnectionManager.kt`:
   - Drain `pendingNotifications` into a local list `toFlush`.
   - Iterate through `toFlush`.
   - If `transport == null && isNotification && (relayEstablishing || canConnectRelay())`, keep it in `pendingNotifications` for when the RFCOMM relay establishes, without re-calling `sendActionNow`.
   - Otherwise, flush via `sendActionNow`.
2. Compile and verify with `./gradlew assembleDebug`.
3. Update `docs/PROJECT_MEMORY.md`.
4. Run `codegraph sync`.
