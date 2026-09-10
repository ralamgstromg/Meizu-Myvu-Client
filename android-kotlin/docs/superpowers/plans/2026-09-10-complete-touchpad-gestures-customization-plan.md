# Plan: Enable Complete Touchpad Gestures Customization (Tap, Double Tap, Swipes, Long Press)

## Problem Summary
User reports that gestures other than long press (Tap, Double Tap, Triple Tap, Swipe Forward, Swipe Backward) on the glasses touchpad are not working / not triggering configured custom actions.

## Root Causes Identified
1. **Key Event Telemetry Not Decoded in `InboundRouter.kt`**:
   - The glasses send physical touch events as `{"name": "key_event", "key_code": ...}` inside `sync_glass_event`.
   - `InboundRouter.dispatchGestureItem` did not check `key_code`, `keyCode`, `keyName`, `key_name`, `actionType`, or `direction`.
   - `actionName` was set to `"key_event"`, which did not match any gesture keywords in `GlassGesture.fromCode`, while `actionValue` remained `-1`, causing all physical key events to be classified as `GlassGesture.UNKNOWN` and dropped.
2. **Missing Android / Flyme Hardware Keycodes in `GlassGesture.kt`**:
   - `GlassGesture.fromCode` only checked codes `1..6, 19..22`.
   - Glasses send standard keycodes:
     - `23` (`KEYCODE_DPAD_CENTER`), `66` (`KEYCODE_ENTER`), `79` (`KEYCODE_HEADSETHOOK`), `85` (`KEYCODE_MEDIA_PLAY_PAUSE`), `96` (`KEYCODE_BUTTON_A`) for Single Tap.
     - `87` (`KEYCODE_MEDIA_NEXT`), `90` (`KEYCODE_MEDIA_FAST_FORWARD`), `92` (`KEYCODE_PAGE_UP`) for Swipe Forward.
     - `88` (`KEYCODE_MEDIA_PREVIOUS`), `89` (`KEYCODE_MEDIA_REWIND`), `93` (`KEYCODE_PAGE_DOWN`) for Swipe Backward.
3. **Missing `phonepad` / `trackpad` Inbound Action Matching**:
   - If the glasses launcher sends `action = "phonepad"` or `action = "trackpad"`, `checkGestureTracking` did not match these actions.
4. **Bluetooth AVRCP / Headset Media Button Reception**:
   - When `music_tp_control_mode` is enabled, the glasses temple touches also broadcast Bluetooth AVRCP media keys over the classic audio profile (`BluetoothHeadset` / A2DP).
   - Without an active `MediaSession` in `MyvuService`, Android drops or diverts these keys to background music apps instead of `TouchGestureManager`.

## Proposed Changes

### 1. `GlassGesture.kt`
- Map hardware keycodes in `fromCode(code: Int, name: String?)`:
  - `1, 23, 66, 79, 85, 96` -> `TAP`
  - `2` -> `DOUBLE_TAP`
  - `3` -> `TRIPLE_TAP`
  - `4, 219, 231` -> `LONG_PRESS`
  - `5, 19, 22, 87, 90, 92` -> `SWIPE_FORWARD`
  - `6, 20, 21, 88, 89, 93` -> `SWIPE_BACKWARD`
- Expand name parsing for `key_event`, `click`, `select`, `enter`, `center`, `hook`, `next`, `prev`, `fast_forward`, `rewind`, `page_up`, `page_down`.

### 2. `InboundRouter.kt`
- In `checkGestureTracking`: Add support for `phonepad`, `trackpad`, `touchpad`, `touch_gesture`, `key_event`.
- In `dispatchGestureItem`:
  - Search specific event/key names (`key_name`, `keyName`, `gesture_name`, `touch_name`, `actionName`) before generic `"key_event"`.
  - Check all value keys (`key_code`, `keyCode`, `keycode`, `keyValue`, `key_value`, `key`, `actionType`, `action_type`, `direction`, `gesture_code`, `touch_type`).

### 3. `MyvuService.kt`
- Register an active `android.media.session.MediaSession` to intercept Bluetooth AVRCP media button events from the glasses audio connection (`KEYCODE_HEADSETHOOK`, `KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS`, `KEYCODE_MEDIA_FAST_FORWARD`, `KEYCODE_MEDIA_REWIND`, `KEYCODE_VOICE_ASSIST`).
- Implement tap count detection (single tap vs double tap within 400ms for headset hook/play-pause) and dispatch through `connection?.executeGesture()`.

### 4. `ConnectionManager.kt`
- Expose `executeGesture(gesture: GlassGesture, rawCode: Int)` so `MyvuService` can execute mapped actions directly.

### 5. Verification & Tests
- Update `InboundGestureTest.kt` with tests for:
  - `key_event` packets with `key_code: 23` (DPAD_CENTER -> TAP).
  - `key_event` packets with `key_code: 22` (DPAD_RIGHT -> SWIPE_FORWARD).
  - `key_event` packets with `key_code: 21` (DPAD_LEFT -> SWIPE_BACKWARD).
  - `phonepad` action packets (`click`, `doubleClick`, `gestureMode`).
- Run `./gradlew testDebugUnitTest`.
- Run `./gradlew assembleDebug`.
- Execute `codegraph sync`.
- Update `docs/PROJECT_MEMORY.md` and documentation.
