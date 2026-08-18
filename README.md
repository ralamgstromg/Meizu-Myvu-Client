# Meizu MYVU Client

> Unofficial, community-built client for Meizu MYVU AR glasses; not affiliated
> with or endorsed by Meizu.

A reverse-engineered client for the **Meizu MYVU (Star Air, model `XGA010C`)**
AR glasses. It speaks the glasses' own Bluetooth protocol directly — no official
app required — to pair, drive the on-lens UI, push notifications, run a
teleprompter and turn-by-turn navigation, act as a remote trackpad, customizable gesture controls, host an on-device local AI voice assistant, and integrate bi-directionally with **Tasker** automation.

There are three implementations of the protocol in this repository:

| Folder | What it is | Runs on |
|---|---|---|
| [⭐ `android-kotlin/`](android-kotlin/) | **Modern Native Kotlin 2.1+ Client** (`com.myvu.client`). Reactive `StateFlow` connection engine, Material 3 Kinetic Obsidian UI, Local-First AI (Gemma 4 E2B IT / Whisper v3 Turbo), and bi-directional **Tasker Plugin**. **Recommended.** | Android (minSdk 26, tested on API 31–35) |
| [`android/`](android/) | Full-featured Java Android app (`com.myvu.client`). Stable baseline client. | Android (minSdk 26, tested on API 31+) |
| [`python/`](python/) | The original reverse-engineering reference the Android ports were built from. **Rough and not stable** — kept for protocol study. | Windows / Linux (BLE + classic-BT) |

Each folder has its own README with build/run details:
[android-kotlin/README.md](android-kotlin/README.md) · [android/README.md](android/README.md) · [python/README.md](python/README.md).

## What works

- **Connection & Reactive State Engine** — BLE bring-up + ECDH bond, then the classic-Bluetooth app relay with reactive Kotlin `StateFlow` synchronization and auto-reconnect. Optional **auto-search** discovers the glasses over a BLE scan without needing the MAC address.
- **"Phone connected" state** — connects standard HFP/A2DP profiles so the glasses light their own connected indicator, not just the app relay.
- **Tasker Plugin & Bidirectional Automation** — native Locale/Tasker plugin support:
  - **Tasker ➡️ Glasses**: Show HUD messages with dynamic variables (`%var`), trigger teleprompter, adjust brightness/volume, toggle Wi-Fi / Zen Mode / Air Mode, set standby FOV position, and send raw JSON commands.
  - **Glasses ➡️ Tasker**: Temple touch gestures, AI button / voice trigger, connection states, and battery level events exported directly as Tasker variables (`%myvu_gesture`, `%myvu_event`, `%myvu_battery`, `%myvu_state`).
  - *Full Tasker manual:* [android-kotlin/docs/TASKER_INTEGRATION.md](android-kotlin/docs/TASKER_INTEGRATION.md).
- **Notifications** — send your own, or mirror real phone notifications to the lens with per-app filters and custom vibration alerts.
- **Teleprompter**, **system settings** (volume, brightness, Wi-Fi, wear detection, zen mode, screen-off, standby position…), **clock sync**, and status **queries**.
- **Weather Sync** — manual and periodic weather data fetching via Open-Meteo API, updating the glasses' weather widget on the lens HUD.
- **Custom Touch & Gesture Controls (`TouchGestureManager`)** — map the glasses' temple touch/button long-press trigger to customizable phone actions:
  - Launch AI Assistant
  - Force Weather Sync
  - Toggle Notification Mirroring
  - Media Play / Pause
  - Tasker Event Trigger
  - None (disabled)
- **Navigation** — full turn-by-turn HUD (OSRM routing, Nominatim geocoding), driven by the phone's location.
- **Trackpad** — the phone as a remote touchpad for the glasses' launcher (tap / double-tap / long-press / swipe).
- **Diagnostics & Live Logging (`LogBus`)** — centralized thread-safe log bus delivering real-time diagnostics to the app UI with full file export capabilities.
- **Local-First AI assistant** — press the glasses' button or speak; speech-to-text, an LLM answer, and text-to-speech back to the glasses:
  - **STT**: Whisper Large v3 Turbo INT4 On-Device with Groq API fallback.
  - **LLM**: Gemma 4 E2B IT / Gemma 2B IT (LiteRT-LM & MediaPipe GenAI) On-Device with cloud fallback (Groq, Gemini, Claude, OpenAI).
  - **Fast-Path Voice Actions (<5ms)**: Direct WhatsApp chat opening, background phone calls (`TelecomManager`), notification summaries, to-do lists, alarms, OpenTune media control, and real-time translation.

## How it works (short version)

The glasses require **two Bluetooth links at once**:

1. **BLE first.** The classic radio won't answer a page until BLE has woken the
   glasses. BLE carries version negotiation, an **ECDH bond** (AES-encrypted
   `DeviceInfo` exchange), the `AUTH_SUCCESS` handshake, an "init burst" of
   opening messages, and a 3 s heartbeat. It is also the **only** place the
   app-relay's address is announced — the glasses regenerate a random RFCOMM
   service UUID every session and sync it over BLE (`SPP_SERVER_UUID_SYNC`).
2. **Classic-BT (RFCOMM) second**, to that per-session UUID. This is the link
   that actually carries app traffic. Each feature is a JSON
   `{"action": …}` message over a "RunAsOne" relay, routed to a target package
   on the glasses (e.g. `com.upuphone.star.launcher`).

The glasses' microphone streams back as Opus frames; navigation, the trackpad
("phonepad"), weather, gestures, Tasker triggers, and the assistant are all JSON actions over the same relay.

## Hardware / prerequisites

- A Meizu MYVU / Star Air pair, already **BR/EDR-bonded** to the phone/PC.
- During testing, keep any *other* paired phone (and other machines running a
  client) disconnected — the glasses accept **one** central at a time.

## Reverse-engineering note

The protocol was recovered from Bluetooth captures and by studying the official
app. That decompiled app is **not included here** — it's Meizu's proprietary
code. What was learned from it lives in these clients and their docs, not in the
form of redistributed sources.

## Status

A hobby/interoperability project, developed and hardware-verified against one
pair of glasses. It is not a product and comes with no warranty. See the
disclaimer at the top.

## License

[MIT](LICENSE) © 2026 Panny777. The licence covers the original code in this
repository only; it grants no rights to Meizu's trademarks, patents, or the
decompiled app, which is not distributed here.
