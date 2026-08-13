# Android Java to Kotlin Migration Plan (`android-kotlin`)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the full Meizu Myvu Client Android app from Java 17 to modern, idiomatic Kotlin 2.1+ in the isolated directory `android-kotlin/`, upgrading build scripts to Gradle KTS + Version Catalog (`libs.versions.toml`) and replacing raw threads/callbacks with Kotlin Coroutines and `StateFlow`.

**Architecture:** The project is organized in `android-kotlin/app/src/main/java/com/myvu/client/`. It decouples binary communication (TLV and Protobuf codecs in `protocol`), transport channels (`transport.bt` and `transport.ble`), background service orchestration (`service.ConnectionManager`), and application features (`app.feature`, `nav`, `weather`, `ai`, `reminder`, `ui`).

**Tech Stack:** Kotlin 2.1.0, AGP 8.8.0, Gradle 8.14, AndroidX KTX (Core, AppCompat, Activity, Lifecycle), Coroutines 1.10.1, Material 3, Play Services Location, JUnit 4 + org.json.

## Global Constraints

- All generated project files must live strictly within `android-kotlin/`.
- Target SDK: 35, Min SDK: 26, Java Target: 17.
- Use Gradle KTS (`.gradle.kts`) and Version Catalog (`gradle/libs.versions.toml`).
- Binary protocol compatibility with Meizu Myvu smart glasses must be maintained 100%.

---

### Task 1: Project Scaffolding & Gradle Build Setup in `android-kotlin/`

**Files:**
- Create: `android-kotlin/settings.gradle.kts`
- Create: `android-kotlin/build.gradle.kts`
- Create: `android-kotlin/gradle/libs.versions.toml`
- Create: `android-kotlin/app/build.gradle.kts`
- Create: `android-kotlin/app/src/main/AndroidManifest.xml`
- Create: `android-kotlin/app/proguard-rules.pro`

**Interfaces:**
- Consumes: AGP 8.8.0, Kotlin 2.1.0 plugin definitions.
- Produces: Gradle buildable Android application module `com.myvu.client`.

- [ ] **Step 1: Create `gradle/libs.versions.toml`**
  Add versions and library aliases for AGP, Kotlin, Coroutines, AndroidX, and Play Services.
- [ ] **Step 2: Create root `settings.gradle.kts` and `build.gradle.kts`**
  Configure plugin repositories, plugin management, and project name `android-kotlin`.
- [ ] **Step 3: Create `app/build.gradle.kts`**
  Configure `android` block, `compileSdk = 35`, `minSdk = 26`, `viewBinding = true`, Kotlin compile options, and dependency declarations from version catalog.
- [ ] **Step 4: Create `AndroidManifest.xml`**
  Declare permissions (Bluetooth, Bluetooth Admin, Bluetooth Connect, Bluetooth Scan, Foreground Service, Internet, Access Fine Location) and service/activity declarations.
- [ ] **Step 5: Verify Scaffolding Build**
  Run `./gradlew tasks` inside `android-kotlin/` (or verify gradle configuration).

---

### Task 2: Core Package Migration (`com.myvu.client.core`)

**Files:**
- Create: `android-kotlin/app/src/main/java/com/myvu/client/core/Hex.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/core/BufferPool.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/core/HttpCache.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/core/LogBus.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/core/Prefs.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/core/SecurePrefs.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/core/SslUtils.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/core/GlassesConfig.kt`

**Interfaces:**
- Consumes: Android `Context`, `SharedPreferences`.
- Produces: `LogBus` reactive log event flow, `Prefs` preference accessors, byte utilities (`Hex`), and pool management.

- [ ] **Step 1: Write `Hex.kt`**
  Implement `ByteArray.toHexString()` and `String.hexToByteArray()` extension functions.
- [ ] **Step 2: Write `BufferPool.kt`**
  Implement thread-safe `BufferPool` object managing `ByteArray` allocation/reuse.
- [ ] **Step 3: Write `LogBus.kt`**
  Implement `LogBus` singleton with `SharedFlow<LogMessage>` for reactive logging (`log(tag, msg)`, `error(tag, msg, throwable)`).
- [ ] **Step 4: Write `Prefs.kt` & `SecurePrefs.kt`**
  Implement preference wrappers using property delegates and type-safe defaults.
- [ ] **Step 5: Write `HttpCache.kt`, `SslUtils.kt`, `GlassesConfig.kt`**
  Implement caching and network SSL helper utilities.

---

### Task 3: Protocol & Binary Codecs Migration (`com.myvu.client.protocol`)

**Files:**
- Create: `android-kotlin/app/src/main/java/com/myvu/client/protocol/TlvBox.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/protocol/TlvTags.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/protocol/PbValue.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/protocol/Pb.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/protocol/Session.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/protocol/InitBurst.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/protocol/Relay.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/protocol/AbilityReply.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/protocol/MsgType.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/protocol/RelayMessage.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/protocol/RelaySequencer.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/protocol/link/DeviceId.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/protocol/link/DeviceInfo.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/protocol/link/LinkCommands.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/protocol/link/LinkMessage.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/protocol/link/LinkProtocol.kt`

**Interfaces:**
- Consumes: Raw bytes from transport layer.
- Produces: High-level `TlvBox`, `Pb` messages, and `LinkProtocol` frames.

- [ ] **Step 1: Write `TlvBox.kt` & `TlvTags.kt`**
  Implement TLV byte parser/builder using Kotlin bitwise operations (`shl`, `ushr`, `and`, `or`).
- [ ] **Step 2: Write `PbValue.kt` & `Pb.kt`**
  Implement `sealed class PbValue` (Varint, Fixed64, LengthDelimited, Fixed32) and `Pb` protobuf field encoder/decoder.
- [ ] **Step 3: Write `Session.kt`, `InitBurst.kt`, `Relay.kt`, `AbilityReply.kt`**
  Implement session state handshake models and burst packet builders.
- [ ] **Step 4: Write `protocol/link` package (`DeviceInfo.kt`, `LinkProtocol.kt`, etc.)**
  Implement link-layer frame framing, checksum calculation, and command constants.

---

### Task 4: Transport Layer Migration (`com.myvu.client.transport`)

**Files:**
- Create: `android-kotlin/app/src/main/java/com/myvu/client/transport/Transport.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/transport/TransportListener.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/transport/bt/BtTransport.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/transport/ble/BleTransport.kt`

**Interfaces:**
- Consumes: Bluetooth RFCOMM socket (`BluetoothSocket`) & GATT (`BluetoothGatt`).
- Produces: `Transport` abstraction with `suspend fun connect()`, `suspend fun send(data: ByteArray)`, and `Flow<ByteArray>` stream.

- [ ] **Step 1: Write `Transport.kt` & `TransportListener.kt`**
  Define Kotlin interfaces for non-blocking transports and callbacks/flows.
- [ ] **Step 2: Write `BtTransport.kt`**
  Implement Bluetooth Classic RFCOMM socket transport with Coroutine `Dispatchers.IO` read/write loops.
- [ ] **Step 3: Write `BleTransport.kt`**
  Implement BLE GATT transport with GATT callback wrapper and MTU negotiation.

---

### Task 5: Service & Connection Management (`com.myvu.client.service`)

**Files:**
- Create: `android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionState.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/service/MyvuService.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/service/MirrorNotificationListener.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/service/AudioProfiles.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/service/Bonding.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/service/NotificationFilter.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/service/RelaySupervisor.kt`

**Interfaces:**
- Consumes: `Transport`, `Protocol`, Android Service lifecycle.
- Produces: Reactive `ConnectionManager` state (`StateFlow<ConnectionState>`), Foreground Service `MyvuService`, and notification listener.

- [ ] **Step 1: Write `ConnectionState.kt`**
  `sealed class ConnectionState` (Disconnected, Connecting, Connected, Error).
- [ ] **Step 2: Write `ConnectionManager.kt`**
  Implement connection orchestrator managing reconnect loops, heartbeat ping/pong, and message transmission.
- [ ] **Step 3: Write `MyvuService.kt` & `MirrorNotificationListener.kt`**
  Implement Android Foreground Service and NotificationListenerService for glass notification mirroring.
- [ ] **Step 4: Write `AudioProfiles.kt`, `Bonding.kt`, `NotificationFilter.kt`, `RelaySupervisor.kt`**
  Implement companion services for A2DP/HFP profile management, hidden API bonding reflection, and notification filtering.

---

### Task 6: Application Layer & Features (`com.myvu.client.app`, `ai`, `nav`, `weather`, `ui`, `crypto`, `database`)

**Files:**
- Create: `android-kotlin/app/src/main/java/com/myvu/client/app/AppLayer.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/app/InboundRouter.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/app/GlassesEventHandler.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/app/RelaySession.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/ai/AiHandler.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/nav/LocationSource.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/weather/WeatherClient.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/reminder/ReminderManager.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/ui/MainActivity.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/crypto/CryptoUtils.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/database/AppDatabase.kt`

**Interfaces:**
- Consumes: `ConnectionManager`, `InboundRouter`, Android UI components.
- Produces: Complete, running Android client application in Kotlin.

- [ ] **Step 1: Write `InboundRouter.kt` & `GlassesEventHandler.kt`**
  Dispatch inbound glass events to appropriate handlers using Kotlin `when` expressions.
- [ ] **Step 2: Write feature handlers (`AiHandler`, `LocationSource`, `WeatherClient`, `ReminderManager`)**
  Implement features using Coroutine background jobs.
- [ ] **Step 3: Write `MainActivity.kt` & `CryptoUtils.kt`**
  UI Activity with ViewBinding and AES encryption helpers.

---

### Task 7: Unit Testing & Verification

**Files:**
- Create: `android-kotlin/app/src/test/java/com/myvu/client/protocol/TlvBoxTest.kt`
- Create: `android-kotlin/app/src/test/java/com/myvu/client/protocol/PbTest.kt`
- Create: `android-kotlin/app/src/test/java/com/myvu/client/core/HexTest.kt`

**Interfaces:**
- Consumes: Codec classes.
- Produces: Clean passing test suite.

- [ ] **Step 1: Write `HexTest.kt`**
  Test string to hex byte conversion.
- [ ] **Step 2: Write `TlvBoxTest.kt`**
  Test TLV box encoding, decoding, string/int values, and multi-tag parsing.
- [ ] **Step 3: Write `PbTest.kt`**
  Test Protobuf varint encoding, field serialization, and decoding.
- [ ] **Step 4: Execute `./gradlew test`**
  Verify all unit tests pass cleanly.
