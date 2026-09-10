# Plan: Fix Spontaneous Agent Launch From Background Glasses Telemetry

## Problem Analysis
User observed that the assistant/agent (configured as Google Assistant or Local AI for Long Press) triggers repeatedly and spontaneously on the phone, without anyone touching or pressing the temples/touchpad of the glasses.

### Log Evidence (`/home/rcastro/Descargas/myvu_client_log.txt`):
```text
13:22:54.893  <- msgId=1016 com.upuphone.star.launcher {"action":"event_tracking","data":{"action":"sync_glass_event","value":[{"name":"suspend_stats",...}]}}
13:22:54.893  Touch gesture received: UNKNOWN (code=-1, name=suspend_stats)
13:22:54.894  Touchpad gesture received (UNKNOWN, code=-1) -> Action: phone_assistant (LAUNCH_PHONE_ASSISTANT)
13:22:54.896  Dispatched KEYCODE_VOICE_ASSIST for Voice Assistant (Google/Gemini)
13:22:54.918  Launched Google Assistant voice command

13:22:57.785  <- msgId=1017 {"action":"event_tracking","data":{"action":"sync_glass_event","value":[{"name":"iot_notification_reminder",...}]}}
13:22:57.788  Touch gesture received: UNKNOWN (code=-1, name=iot_notification_reminder)
13:22:57.789  Touchpad gesture received (UNKNOWN, code=-1) -> Action: phone_assistant (LAUNCH_PHONE_ASSISTANT)

13:23:12.431  <- msgId=1044 {"action":"event_tracking","data":{"action":"sync_glass_event","value":[{"name":"iot_screen_status_change",...}]}}
13:23:12.435  Touch gesture received: UNKNOWN (code=-1, name=iot_screen_status_change)
13:23:12.436  Touchpad gesture received (UNKNOWN, code=-1) -> Action: phone_assistant (LAUNCH_PHONE_ASSISTANT)
```

### Root Causes
1. **`TouchGestureManager.kt` maps `GlassGesture.UNKNOWN` to `touchpadLongPressAction`**:
   In `getActionForGesture`:
   ```kotlin
   GlassGesture.UNKNOWN -> Prefs.touchpadLongPressAction(context)
   ```
   If user configured `touchpadLongPressAction` as `phone_assistant` or `ai_assistant`, ANY unknown event resolved to that action!
2. **`TouchGestureManager.handleGesture` does not guard against `UNKNOWN`**:
   It executes the mapped action without verifying that `gesture != GlassGesture.UNKNOWN`.
3. **`InboundRouter.dispatchGestureItem` dispatches `GlassGesture.UNKNOWN`**:
   Non-touch OS telemetry packets (`suspend_stats`, `iot_screen_status_change`, `iot_sys_usages`, `battery_stats`, `iot_notification_reminder`) sent by the glasses launcher over `sync_glass_event` were parsed as `GlassGesture.UNKNOWN` and dispatched to `TouchGestureListener`.

## Proposed Solution

### 1. `TouchGestureManager.kt`
- In `getActionForGesture`: map `GlassGesture.UNKNOWN -> GestureAction.NONE` (both when context is null and when context is present).
- In `handleGesture`: add guard:
  ```kotlin
  if (gesture == GlassGesture.UNKNOWN) {
      LogBus.trace("Ignoring non-gesture event (UNKNOWN)")
      return
  }
  ```

### 2. `InboundRouter.kt`
- In `dispatchGestureItem`:
  - Verify that the item actually represents a touch/gesture before treating it as one. Ignore pure OS metrics (`suspend_stats`, `iot_screen_status_change`, `iot_sys_usages`, `battery_stats`, `screen_off_timeout_change`, `standby_position`, `iot_notification_reminder`, `starrynet_*`, `air_starrynet_*`).
  - Calculate `gesture = GlassGesture.fromCode(actionValue, actionName)`.
  - If `gesture == GlassGesture.UNKNOWN`, do **NOT** invoke `listener.onTouchGesture()`. Drop it silently.

### 3. Verification & Tests
- Update `InboundGestureTest.kt` and `TouchGestureManagerTest.kt`:
  - Verify that `suspend_stats`, `iot_screen_status_change`, etc. are ignored and do NOT invoke touch gesture listener or trigger actions.
  - Verify that `GlassGesture.UNKNOWN` maps to `GestureAction.NONE` and does not trigger any action.
  - Verify that valid gestures (single tap, double tap, swipe forward/backward, long press code: 3) continue to work properly.
- Run `./gradlew testDebugUnitTest`.
- Run `./gradlew assembleDebug`.
- Synchronize with `codegraph sync`.
- Update `docs/PROJECT_MEMORY.md` and documentation.
