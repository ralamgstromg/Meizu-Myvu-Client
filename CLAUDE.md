# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This repository is an unofficial, reverse-engineered client for Meizu MYVU (`Star Air`, model `XGA010C`) AR glasses. It contains two client implementations:
1. `android/` — Android App (`com.myvu.client`), written in Java. **Primary stable production codebase**.
2. `python/` — Reference RE client (Windows/WinRT BLE & RFCOMM). Unstable reference implementation.

## Common Commands

### Android Development (`android/`)
Navigate to `android/` before executing Gradle commands or run `./gradlew` from `android/`:

- **Build Debug APK**: `./gradlew :app:assembleDebug`
- **Build Release APK**: `./gradlew :app:assembleRelease`
- **Run Unit Tests**: `./gradlew test`
- **Run Single Unit Test**: `./gradlew test --tests "com.myvu.client.InboundRouterTest"`
- **Clean Build**: `./gradlew clean`

### Python Reference (`python/`)
- **Install Dependencies**: `pip install -r python/requirements.txt`
- **Run Self-Test Protocol**: `python python/selftest.py`
- **Run Reference Client**: `python python/run.py`
- **Run RFCOMM Teleprompter Client**: `python python/run_glasses.py <BT-ADDRESS> --no-hfp`

## Architecture & Protocol Overview

The MYVU glasses require two concurrent Bluetooth links:

1. **BLE Link (Bring-up & Pairing)**:
   - Discovered via BLE service UUID `0x0bd3` or device name matching `MYVU` (or auto-discovered via `GlassesScanner`).
   - Performs Version Negotiation, ECDH handshake (`AES/CBC/PKCS5Padding`), `AUTH_SUCCESS` handshake, and handles heartbeats (3s interval).
   - Syncs the dynamic classic Bluetooth SPP service UUID via `CMD_SPP_SERVER_UUID_SYNC`.

2. **Classic Bluetooth RFCOMM Link (App Relay / Data Transfer)**:
   - Connects to the dynamic SPP UUID synced over BLE.
   - Encapsulates payload in protobuf/framing (`eaca9353` framing header) sending JSON actions targeted to on-lens system packages (e.g. `com.upuphone.star.launcher`).

### Key Android Architecture Components (`android/app/src/main/java/com/myvu/client/`)

- **Service Layer (`service/`)**:
  - `MyvuService`: Foreground service keeping the connection and background loops alive.
  - `ConnectionManager`: Core orchestrator managing connection state, BLE scanner, RFCOMM sockets, retry limits, and routing inbound/outbound packets.
  - `MirrorNotificationListener`: Service mirroring Android notifications to the glasses with custom app filters (`NotificationFilter`).
- **Protocol & Framing (`protocol/`)**:
  - `Session`: Handles `RunAsOne` ability/AUTH handshake.
  - `LinkProtocol`: Proto & payload builder for StarryNet messages on BLE internal characteristic (`0x2020`).
  - `Pb`: Custom Protobuf varint/bytes codec for protocol framing.
  - `InitBurst`: Replays initial handshake parameters upon connection.
- **App Features (`app/feature/` & `app/`)**:
  - `InboundRouter`: Dispatches incoming RFCOMM messages to AI triggers, gesture actions, weather, and battery listeners.
  - `TouchGestureManager`: Maps temple touch/button triggers (`code: 3`) to customizable actions (AI Assistant, Weather sync, Mirror toggle, Media control).
  - `Weather`: Pushes Open-Meteo weather forecast payloads (`StMessage`) to the glasses' HUD weather widget.
  - `AppLayer`: Teleprompter, navigation, notification actions, and HUD UI builders.
  - `SystemSettings`: Flat vs. nested JSON message format builders for volume, brightness, wear detection, DND, etc.
- **Core Subsystems (`core/`)**:
  - `LogBus`: Thread-safe, event-driven log buffer broadcasting diagnostic events to the UI (`LogAdapter`).
  - `Prefs`: SharedPreference helpers for assistant endpoints, gesture bindings, weather refresh intervals, and retry thresholds.
  - `BufferPool`: Reusable byte buffer pooling for high-throughput stream decoding.
- **AI Engine (`ai/`)**:
  - `AiConversation`: Coordinates VAD (450ms end-of-speech detection), Opus microphone audio streaming (`GlassesMicStream`, `OpusDecoderStream`), AI providers (`AiProvider`), TTS (`TtsPlayer`), and local intent execution (`PhoneActionExecutor`).
  - `PhoneActionExecutor`: Executes native phone tasks based on AI action tags (Calls via `TelecomManager`, WhatsApp/Telegram, Google/Outlook Calendar, Google Keep notes, OpenTune music player, Maps navigation, Alarms, Translations, Currency conversions).
