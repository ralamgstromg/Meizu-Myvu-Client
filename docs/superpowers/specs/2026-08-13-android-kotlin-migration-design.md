# Design Specification: Java to Kotlin Migration for Meizu Myvu Client (`android-kotlin`)

**Date:** 2026-08-13  
**Status:** Approved (Approach 1: Idiomatic Kotlin 2.1+ & Modern Gradle KTS)  
**Target Directory:** `android-kotlin/`  

---

## 1. Executive Summary

This specification outlines the full migration of the **Meizu Myvu Client** Android codebase from legacy Java 17 to modern, idiomatic Kotlin (v2.1+). The output will be isolated completely inside the `android-kotlin/` directory.

The migrated project will leverage:
- **Kotlin 2.1+** with K2 Compiler.
- **Gradle Version Catalog (`gradle/libs.versions.toml`)** and **Gradle KTS (`build.gradle.kts`)**.
- **Kotlin Coroutines & StateFlow/SharedFlow** for async Bluetooth I/O, service events, and UI reactivity (replacing raw `Thread` and `Handler` callbacks).
- **Type-safe, Null-safe, High-performance** data processing for custom TLV (Type-Length-Value) and Protobuf-like binary protocols used by Meizu Myvu AR smart glasses.

---

## 2. Target Architecture & Project Structure

The project in `android-kotlin/` mirrors the modular package organization of the original Java client while introducing modern Android KTX patterns and Coroutines:

```
android-kotlin/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── local.properties
├── gradle/
│   ├── wrapper/
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/myvu/client/   --> (Kotlin source root)
        │   │   ├── ai/
        │   │   ├── app/
        │   │   │   └── feature/
        │   │   ├── core/
        │   │   ├── crypto/
        │   │   ├── database/
        │   │   ├── nav/
        │   │   ├── protocol/
        │   │   │   └── link/
        │   │   ├── reminder/
        │   │   ├── service/
        │   │   ├── transport/
        │   │   │   ├── ble/
        │   │   │   └── bt/
        │   │   ├── ui/
        │   │   └── weather/
        │   └── res/
        └── test/
            └── java/com/myvu/client/
```

---

## 3. Dependency & Tooling Upgrade Stack

| Component / Library | Java Version | Target Kotlin (`android-kotlin`) |
| :--- | :--- | :--- |
| **Language** | Java 17 | Kotlin 2.1.0+ (Java 17 target) |
| **Build Tooling** | AGP 8.7.3 (Groovy) | AGP 8.8.0+ (Gradle KTS + Version Catalog) |
| **Async / Concurrency** | Raw Threads / Handlers | `kotlinx-coroutines-android:1.10.1`, `StateFlow` |
| **AndroidX Core & KTX** | `appcompat:1.7.1` | `core-ktx:1.15.0`, `appcompat:1.7.0`, `lifecycle-runtime-ktx:2.8.7` |
| **Location** | `play-services-location:21.4.0` | `play-services-location:21.4.0` (with KTX extensions) |
| **UI & Binding** | ViewBinding | ViewBinding with Kotlin Extension utilities |
| **Testing** | JUnit 4 + org.json | JUnit 4/5 + `kotlinx-coroutines-test` + `org.json` |

---

## 4. Subsystem Migration Details

### 4.1. Core Utilities (`com.myvu.client.core`)
- **`Prefs.kt` & `SecurePrefs.kt`**: Replace Java getters/setters with Kotlin property delegation (`by`) or `EncryptedSharedPreferences`.
- **`LogBus.kt`**: Replace callback listener lists with `SharedFlow<LogMessage>` or `StateFlow` for non-blocking reactive log streaming across components.
- **`BufferPool.kt`**: Idiomatic object pool managing thread-safe byte arrays with `ReentrantLock` or Atomic data structures.
- **`HttpCache.kt` & `SslUtils.kt`**: Suspend functions with OkHttp / Hurl wrappers for HTTP operations.

### 4.2. Protocol & Binary Codecs (`com.myvu.client.protocol`)
- **`TlvBox.kt` & `TlvTags.kt`**: Bitwise byte manipulation using Kotlin `shl`, `ushr`, `and`, `or`. Nullable returns (`ByteArray?`, `Int?`) instead of returning `null` with unsafe assertions.
- **`Pb.kt` & `PbValue.kt`**: Sealed hierarchy (`sealed class PbValue`) replacing untyped object wrappers.
- **`Session.kt`, `InitBurst.kt`, `Relay.kt`**: Idiomatic Kotlin data classes and `sealed interface` message representations.

### 4.3. Transport & Service Layer (`com.myvu.client.transport`, `com.myvu.client.service`)
- **`Transport.kt` & `TransportListener.kt`**: Kotlin `interface` with suspend methods and `Flow<ByteArray>` for inbound bytes.
- **`BtTransport.kt` & `BleTransport.kt`**: Threading refactored to Coroutine `Dispatchers.IO`, handling socket connect/read/write loops safely.
- **`ConnectionManager.kt`**: Large Java class refactored into modular Kotlin component using `StateFlow<ConnectionState>`, structured concurrency, and atomic state transitions.
- **`MyvuService.kt`**: Foreground service managed via Kotlin Coroutine scope (`Service` lifecycle bound scope).

### 4.4. Application Features (`com.myvu.client.app`, `ai`, `nav`, `weather`, `ui`)
- **`InboundRouter.kt` & `GlassesEventHandler.kt`**: Pattern matching via Kotlin `when` expressions for message dispatching.
- **Features (`AiHandler`, `NavigationSource`, `WeatherClient`, etc.)**: Idiomatic Kotlin coroutine jobs for background fetches and UI notifications.

---

## 5. Performance & Quality Best Practices

1. **Zero Unnecessary Allocations**: Re-use byte buffers in RFCOMM/BLE transport to avoid GC pressure during high-frequency glass display streaming.
2. **Strict Null Safety**: Explicit non-null Types (`String`, `ByteArray`) vs Nullable types (`String?`), eliminating `NullPointerException` risks.
3. **Immutability**: Use `val` everywhere by default, `var` only when mutable state is strictly necessary.
4. **Structured Concurrency**: All coroutine jobs attached to controlled `CoroutineScope`s (Service scope, ViewModel scope, or application scope) to prevent memory leaks.

---

## 6. Verification & Test Plan

1. **Gradle Build Verification**: Ensure `./gradlew assembleDebug` succeeds cleanly in `android-kotlin/`.
2. **Unit Test Verification**: Migrate and execute all unit tests in `com.myvu.client.protocol` and `com.myvu.client.core` using `./gradlew test`.
3. **Protocol Integrity Check**: Verify binary equivalence of TLV and Protobuf serialization between Kotlin implementation and Java baseline.
