# Plan: Fix MYVU Smart Glasses Touchpad Customization & Temple Routing

## Problem Summary
User cannot customize the touchpad / temple gesture behavior of the Meizu MYVU smart glasses from `SettingsActivity`. Even though custom actions are assigned (Phone Assistant, Play/Pause, Next Track, Zen Mode, etc.), physical touches and long-presses on the glasses always either trigger the local AI or get absorbed locally by the glasses launcher.

## Root Causes Identified
1. **Hardcoded AI Bypass in `ConnectionManager.kt` & `GlassesEventHandler.kt`**:
   Hardware temple long press (`code: 3`) arrives via `checkAiTrigger` (`setAiTriggerListener`). Both `ConnectionManager` and `GlassesEventHandler` directly call `ai().onTrigger(code)` without consulting `TouchGestureManager` or `Prefs.touchpadLongPressAction(context)`.
2. **Missing FlymeAR Underscored Attribute Parsing in `InboundRouter.kt`**:
   `InboundRouter.dispatchGestureItem` only looked for `action_name` and `action_value`. Real FlymeAR telemetry uses `_action_name_`, `_event_name_`, `_action_value_`, `_event_value_`, `_event_id_`, and `actionType`.
3. **Incomplete Synonyms and Keycodes in `GlassGesture.kt`**:
   `GlassGesture.fromCode` lacked synonyms for FlymeAR gestures (`slide_forward`, `slip_forward`, `slide_backward`, `slip_backward`, `deep_touch`, `press_long`) and hardware trackpad codes (`SWIPE_UP`=19, `SWIPE_DOWN`=20, `SWIPE_LEFT`=21, `SWIPE_RIGHT`=22).
4. **Music TP Control Sync in `SettingsActivity.kt`**:
   When gestures are customized, `SystemSettings.setMusicTpControl(true)` was not pushed to the glasses to ensure launcher touchpad event forwarding is active.

## Proposed Changes

### 1. `GlassGesture.kt`
- Expand `fromCode(code: Int, name: String?)` to support all FlymeAR synonyms (`slip_forward`, `slide_forward`, `slip_backward`, `slide_backward`, `flick_forward`, `flick_backward`, `deep_touch`, `press_long`, etc.).
- Add hardware Trackpad codes (19, 20, 21, 22) mapping to `SWIPE_FORWARD` / `SWIPE_BACKWARD`.

### 2. `InboundRouter.kt`
- Update `dispatchGestureItem` to extract names (`_action_name_`, `action_name`, `_event_name_`, `event_name`, `_event_id_`, `event_id`, `name`, `action`) and values (`_action_value_`, `action_value`, `_event_value_`, `event_value`, `actionType`, `action_type`, `value`, `code`, `event_code`).
- Broaden `checkGestureTracking` to recognize both nested and flat telemetry variants.

### 3. `ConnectionManager.kt` & `GlassesEventHandler.kt`
- In `inbound.setAiTriggerListener`:
  - When `code == 3` (physical temple long press / deep touch): route through `TouchGestureManager.handleGesture(this.context, GlassGesture.LONG_PRESS, code, createGestureActionExecutor())`.
  - When assigned action is `LAUNCH_LOCAL_AI`, it invokes `executeAiAssistant(3)` -> `ai().onTrigger(3)`, preserving default behavior while allowing user customization!
  - When `code == 7` (voice wake word): route directly to `ai().onTrigger(7)`.

### 4. `SettingsActivity.kt`
- In `wireTouchpad()`:
  - When user selects any gesture action, ensure `Prefs.setMusicTouchPanelEnabled(this, true)`.
  - Send `SystemSettings.setMusicTpControl(true)` to active connection so glasses launcher forwards touchpad events.

### 5. Verification & Tests
- Update `InboundGestureTest.kt` with tests for:
  - Underscored FlymeAR telemetry (`_action_name_`, `_action_value_`, `_event_name_`, etc.).
  - Slide / slip / swipe synonyms.
  - Trackpad codes (19..22).
  - Code 3 routing through `TouchGestureManager` in `GlassesEventHandler`.
- Run unit test suite `./gradlew testDebugUnitTest`.
- Compile debug build `./gradlew assembleDebug`.
- Execute `codegraph sync`.
- Update project documentation and memory (`docs/PROJECT_MEMORY.md`).
