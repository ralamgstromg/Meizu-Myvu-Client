# Meizu MYVU Client

> Unofficial, community-built client for Meizu MYVU AR glasses; not affiliated
> with or endorsed by Meizu.

A reverse-engineered client for the **Meizu MYVU (Star Air, model `XGA010C`)**
AR glasses. It speaks the glasses' own Bluetooth protocol directly — no official
app required — to pair, drive the on-lens UI, push notifications, run a
teleprompter and turn-by-turn navigation, act as a remote trackpad, customizable gesture controls, and host an on-device local AI voice assistant.

There are three implementations of the protocol in this repository:

| Folder | What it is | Runs on |
|---|---|---|
| [⭐ `android-kotlin/`](android-kotlin/) | **Modern Native Kotlin 2.1+ Client** (`com.myvu.client`). Reactive `StateFlow` connection engine, Material 3 Kinetic Obsidian UI, and Local-First AI (Gemma 4 E2B IT / Whisper v3 Turbo). **Recommended.** | Android (minSdk 26, tested on API 31–35) |
| [`android/`](android/) | Full-featured Java Android app (`com.myvu.client`). Stable baseline client. | Android (minSdk 26, tested on API 31+) |
| [`python/`](python/) | The original reverse-engineering reference the Android ports were built from. **Rough and not stable** — kept for protocol study. | Windows / Linux (BLE + classic-BT) |

Each folder has its own README with build/run details:
[android-kotlin/README.md](android-kotlin/README.md) · [android/README.md](android/README.md) · [python/README.md](python/README.md).

## What works

- **Connection & Reactive State Engine** — BLE bring-up + ECDH bond, then the classic-Bluetooth app relay with reactive Kotlin `StateFlow` synchronization and auto-reconnect. Optional **auto-search** discovers the glasses over a BLE scan without needing the MAC address.
- **"Phone connected" state** — connects standard HFP/A2DP profiles so the glasses light their own connected indicator, not just the app relay.
- **Notifications** — send your own, or mirror real phone notifications to the lens with per-app filters and custom vibration alerts.
- **Teleprompter**, **system settings** (volume, brightness, Wi-Fi, wear detection, zen mode, screen-off, standby position…), **clock sync**, and status **queries**.
- **Weather Sync** — manual and periodic weather data fetching via Open-Meteo API, updating the glasses' weather widget on the lens HUD.
- **Custom Temple Touch & Gesture Controls (`TouchGestureManager`)** — map physical touch gestures from the glasses' temple trackpad and hardware buttons to customizable phone actions:
  - **6 Recognized Physical Gestures (`GlassGesture`)**: Tap, Double Tap, Triple Tap, Long Press / Deep Touch, Swipe Forward, Swipe Backward.
  - **Configurable Actions (`GestureAction`)**:
    - **Phone Voice Assistant (Google Assistant / Gemini)**: Injects `KEYCODE_VOICE_ASSIST` and launches voice command intent, waking up the phone's native assistant using the phone's microphone with instant HUD feedback.
    - **Glasses Local-First AI Assistant**: Activates on-device STT (Whisper) + LLM (Gemma) / Cloud AI using the glasses' Opus microphone stream.
    - **Media Controls**: Play / Pause, Next Track, Previous Track via Android system media keys.
    - **Force Weather Sync**: On-demand Open-Meteo refresh sent to the glasses' weather widget.
    - **Toggle Notification Mirroring**: Switch notification mirroring on/off dynamically.
    - **Open Teleprompter**: Launch the HUD teleprompter display.
    - **Zen Mode**: Toggle Do-Not-Disturb / Zen Mode on the glasses.
    - **None (Disabled)**: Ignore specific gestures.
  - **Anti-Rebound Debounce**: 350ms software filter preventing accidental repeated triggers.
  - **Material 3 Settings UI**: 6 Exposed Dropdown Menus in `SettingsActivity` for individual per-gesture mapping.
- **Navigation** — full turn-by-turn HUD (OSRM routing, Nominatim geocoding), driven by the phone's location.
- **Virtual Trackpad ("Phonepad")** — use the phone screen as a remote touchpad for the glasses' launcher HUD (`com.upuphone.star.launcher`) with support for tap, double-tap, long-press, and 4-directional swipes (up, down, left, right), reactive `StateFlow` lifecycle tracking, obsidian visual state indicators, and haptic feedback.
- **Diagnostics & Live Logging (`LogBus`)** — centralized thread-safe log bus delivering real-time diagnostics to the app UI with full file export capabilities.
- **Local-First AI assistant** — press the glasses' button or speak; speech-to-text, an LLM answer, and text-to-speech back to the glasses:
  - **STT**: Whisper Large v3 Turbo INT4 On-Device with Groq API fallback, Spanish voice-command priming.
  - **LLM**: Gemma 4 E2B IT / Gemma 2B IT (LiteRT-LM & MediaPipe GenAI) On-Device with cloud fallback (Groq, Gemini, Claude, OpenAI).
  - **Optimized Prompt & Turn Structure**: Gemma turn template (`<start_of_turn>user\n...<end_of_turn>\n<start_of_turn>model\n...`) and strict system prompts tuned for AR micro-LED HUD (monochrome 640x480) and TTS (concise 1-2 sentence plain-text answers, no markdown formatting or emoji artifacts).
  - **Live Web & External Search (`ExternalInfoService`)**: Real-time Google search via HTML snippet parsing with Wikipedia and DuckDuckGo fallbacks, Open-Meteo worldwide city weather geocoding, and live currency conversions (USD, EUR, COP, MXN, ARS, etc.).
  - **Fast-Path Voice Actions (<5ms)**: Intercepts direct commands deterministically before reaching the LLM (fuzzy contact search & background calling via `TelecomManager`, WhatsApp/Telegram E.164 messages, SQLite v4 to-do lists, alarms/timers, unread notification summaries, HUD navigation, teleprompter, OpenTune/Spotify media control).
  - **Native Phone Actions Architecture**: 100% native Android execution without external plugins (legacy Tasker module removed in favor of direct Android framework APIs and internal SQLite database).
  - **Extensible Skill Engine (`SKILL.md`)**: Dynamic YAML frontmatter skill loader and parser supporting `SKILL.md` manifests (`skills/built-in/...`) for phone calls, email sending, WhatsApp, and Telegram. See [docs/SKILLS.md](docs/SKILLS.md) for full details.

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
   on the glasses (e.g. `com.upuphone.star.launcher` or `com.upuphone.star.interconnect`).

The glasses stream audio back as Opus frames and report physical touch telemetry (`sync_glass_event` / `event_tracking`). Navigation, the virtual trackpad ("phonepad"), weather, notifications, and the assistant are all JSON actions over the same relay.

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
