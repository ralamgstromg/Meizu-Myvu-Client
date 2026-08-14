# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Native Kotlin Android client for Meizu Myvu AR glasses. The repository contains one Gradle module, `:app`, with namespace and application ID `com.myvu.client`. Kotlin targets JVM 17; Android configuration uses `compileSdk 35`, `targetSdk 35`, and `minSdk 26`.

## Common commands

Run commands from this directory (`android-kotlin/`) with the Gradle wrapper:

```bash
# Compile debug APK
./gradlew assembleDebug

# Compile release APK
./gradlew assembleRelease

# Install debug APK on a connected device or emulator
./gradlew installDebug

# Run all JVM unit tests
./gradlew test

# Run debug unit tests explicitly
./gradlew :app:testDebugUnitTest

# Run one test class
./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.protocol.TlvBoxTest'

# Run one test method
./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.protocol.TlvBoxTest.someTestName'

# Run Android lint
./gradlew lint
```

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.
Release output: `app/build/outputs/apk/release/`. Without `keystore.properties`, release is unsigned. If `keystore.properties` exists, `app/build.gradle.kts` loads it and signs the release variant. Full signing setup is documented in `BUILD_INSTRUCTIONS.md`.

Unit-test HTML report: `app/build/reports/tests/testDebugUnitTest/index.html`.

Prerequisites: JDK 17+, Android SDK API 35. Gradle version comes from the wrapper. Dependency and plugin versions live in `gradle/libs.versions.toml`.

## Architecture

### Android entry points and lifecycle

`app/src/main/AndroidManifest.xml` declares `MyApp` as the `Application`, `ConnectActivity` as launcher, four secondary activities, `MyvuService` as a connected-device foreground service, `MirrorNotificationListener` as a notification listener, and reminder broadcast receivers. `MyApp` installs `CrashReporter` and initializes logging.

The UI uses XML layouts with View Binding and classic `AppCompatActivity` classes under `ui/`. `ConnectActivity` starts/binds `MyvuService`; settings, notes, trackpad, and notification-app screens call the service connection API rather than owning Bluetooth state.

`MyvuService` keeps the glasses link alive while app is backgrounded or screen is locked. It owns one `ConnectionManager`, exposes it through a local binder, publishes connection state through the foreground notification, and tears down connection-owned resources in `onDestroy`.

### Connection and transport pipeline

`ConnectionManager` is central coordinator. It owns protocol/session state, feature services, reconnect policy, and the connection lifecycle. All mutable protocol state is serialized on its dedicated `HandlerThread` named `myvu-conn`; UI and transport callbacks post work to that handler. Public state is exposed as `StateFlow<ConnectionState>`.

Connection setup has two ordered but independent links:

1. BLE (`transport/ble`) discovers, pairs, performs ECDH/Starry encryption, exchanges device/link information, and announces the per-session RFCOMM UUID.
2. RFCOMM Classic Bluetooth (`transport/bt`) connects to that learned UUID and carries the app relay traffic.

Both links implement the `Transport` abstraction and have their own `RelaySession`, framing/reassembly, sequence IDs, and handshake. `RelaySupervisor` maintains the RFCOMM relay. When relay is unavailable, `ConnectionManager` can fall back to the ready BLE session; relay-only features may not work during fallback.

### Protocol and application routing

`protocol/` contains binary wire formats and session mechanics: TLV and protobuf encoders, link commands, authentication/session messages, relay frames, and sequencing. `protocol/link/` handles StarryNet link messages and device metadata. `crypto/` provides ECDH key material and Starry encryption.

After session authentication and the `assets/captured_init.txt` initialization burst, `AppLayer` wraps JSON actions in protobuf payloads addressed to Myvu package names. `InboundRouter` parses actions initiated by the glasses and dispatches AI, weather, battery, notification, and other feature callbacks. `app/feature/` builds feature-specific action JSON for trackpad, teleprompter, notifications, settings, clock, navigation, and weather.

### Feature subsystems

- `ai/`: provider clients and `AiConversation`; handles assistant turns, streaming speech/audio, transcription, TTS, and action callbacks through `ConnectionManager`.
- `nav/`: location, OSRM routing, route caching, and HUD navigation sessions.
- `weather/`: Open-Meteo HTTP client and periodic sync.
- `database/`: hand-written SQLite storage via `LocalDatabase`, with note/reminder models and repositories; not Room.
- `reminder/`: exact-alarm scheduling, notification actions, and boot/time-change receivers.
- `service/`: Bluetooth connection lifecycle, relay supervision, audio profiles, and notification mirroring.
- `core/`: preferences, secure preferences, logging ring buffer, HTTP cache, buffer pooling, and shared configuration.

Network clients use `HttpURLConnection` directly. Coroutines are used for asynchronous work, but connection protocol state remains confined to the `myvu-conn` handler thread.

## Tests

JVM tests are under `app/src/test/java/com/myvu/client/`, grouped by app routing, core utilities, protocol codecs, service state/lifecycle, and BLE/RFCOMM transport behavior. Prefer the narrowest Gradle test selector while iterating, then run `./gradlew test` before finishing broader changes.
