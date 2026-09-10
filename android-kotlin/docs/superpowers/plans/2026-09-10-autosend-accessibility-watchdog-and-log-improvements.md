# Auto-Send Accessibility Watchdog y Correcciones del Log de Conexión

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Proteger el servicio de accesibilidad `AutoSendAccessibilityService` frente a deshabilitaciones automáticas de Android tras actualizar el APK (auto-activándolo si posee `WRITE_SECURE_SETTINGS` o notificando inmediatamente al usuario con acceso directo y alerta visual), y corregir el falso timeout de 10s en `ConnectionManager` que cortaba el handshake RFCOMM a mitad de la ráfaga `init burst`.

**Architecture:** 
1. `AutoSendAccessibilityService`: Implementar `autoEnableIfPermitted(context)` (vía `Settings.Secure` con `WRITE_SECURE_SETTINGS`), `notifyAccessibilityDisabled(context)` (Heads-up Notification de alta prioridad con Intent directo a Ajustes de Accesibilidad) y `checkAndRestoreOrNotify(context)`.
2. `BootReceiver`, `MyvuService`, `ConnectActivity` y `SettingsActivity`: Integrar watchdog proactivo al recibir `ACTION_MY_PACKAGE_REPLACED`, en inicio de servicio y en `onResume()` de actividades, mostrando además un banner/tarjeta de estado en la UI con opción de copiar comando ADB permanente.
3. `ConnectionManager`: Corregir el temporizador `relayEstablishTimeout` para que no se agote prematuramente cuando el socket RFCOMM tarda segundos en negociar, reiniciando el timeout de 10s al conectarse efectivamente el socket.
4. `TouchGestureManager` & `LockScreenHelper`: Suavizar la liberación de SCO para evitar reseteos abruptos del controlador de las gafas y silenciar advertencias ruidosas en pantalla bloqueada.

**Tech Stack:** Kotlin, Android SDK 35 (AudioManager, AccessibilityManager, Settings.Secure, KeyguardManager, NotificationManager), Android Jetpack.

**Spec:** Análisis del log `/home/rcastro/Descargas/myvu_client_log.txt` y requerimientos de usuario para persistencia de Auto-Send y estabilidad RFCOMM.

## Global Constraints
- Compatibilidad mínima Android 10 (API 29), objetivo Android 15 (API 35).
- Nunca realizar operaciones bloqueantes en el hilo principal.
- Preservar TDD y verificar con `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.

---

### Task 1: Auto-activación, Notificación y Watchdog en `AutoSendAccessibilityService`

**Files:**
- Modify: `app/src/main/java/com/myvu/client/service/AutoSendAccessibilityService.kt`
- Test: `app/src/test/java/com/myvu/client/service/AutoSendAccessibilityServiceTest.kt`

**Interfaces:**
- `AutoSendAccessibilityService.autoEnableIfPermitted(context: Context): Boolean`
- `AutoSendAccessibilityService.notifyAccessibilityDisabled(context: Context)`
- `AutoSendAccessibilityService.checkAndRestoreOrNotify(context: Context): Boolean`
- `AutoSendAccessibilityService.cancelDisabledNotification(context: Context)`

- [x] **Step 1: Escribir pruebas unitarias para autoEnableIfPermitted y checkAndRestoreOrNotify**
- [x] **Step 2: Ejecutar pruebas y verificar fallo inicial**
- [x] **Step 3: Implementar la lógica de escritura en `Settings.Secure`, canal de notificación heads-up con PendingIntent directo a accesibilidad y cancelación al restaurarse**
- [x] **Step 4: Ejecutar pruebas unitarias para confirmar que pasen al 100%**

---

### Task 2: Integración del Watchdog en `BootReceiver`, `MyvuService`, `ConnectActivity` y `SettingsActivity`

**Files:**
- Modify: `app/src/main/java/com/myvu/client/reminder/BootReceiver.kt`
- Modify: `app/src/main/java/com/myvu/client/service/MyvuService.kt`
- Modify: `app/src/main/java/com/myvu/client/ui/ConnectActivity.kt`
- Modify: `app/src/main/java/com/myvu/client/ui/SettingsActivity.kt`

**Interfaces:**
- En `BootReceiver.onReceive`: Invocar `AutoSendAccessibilityService.checkAndRestoreOrNotify(context)` en `MY_PACKAGE_REPLACED` y `BOOT_COMPLETED`.
- En `MyvuService.onCreate`: Invocar `AutoSendAccessibilityService.checkAndRestoreOrNotify(this)`.
- En UI: Banner/Tarjeta informativa en `ConnectActivity` y switch/estado en `SettingsActivity` con acción de 1 tap a Accesibilidad y diálogo para copiar comando ADB.

- [x] **Step 1: Añadir llamadas al watchdog en `BootReceiver` y `MyvuService`**
- [x] **Step 2: Añadir verificación en `ConnectActivity.onResume` y feedback visual en UI**
- [x] **Step 3: Proporcionar utilidad en `SettingsActivity` para ver estado y copiar comando ADB permanente**
- [x] **Step 4: Compilar y verificar tests unitarios**

---

### Task 3: Corrección Crítica del Timeout de Handshake RFCOMM en `ConnectionManager`

**Files:**
- Modify: `app/src/main/java/com/myvu/client/service/ConnectionManager.kt:790-835`

**Problema identificado en Log L715-717**:
- `relayEstablishTimeout` de 10s se iniciaba en `transport.connect()`. Si la negociación RFCOMM tardaba 8.5s (como a las 15:22:02), el timeout de 10s vencía a los 1.5s de haber conectar el socket, cerrando el enlace a mitad del `init burst` (mensaje 12 de 27).

- [x] **Step 1: Reiniciar el temporizador de 10s en `relayListener.onConnected(transport)` para que la sesión de handshake e init burst disponga de sus 10 segundos completos desde la conexión efectiva del socket**
- [x] **Step 2: Proteger el temporizador de cancelación en `closeRelay()` y `relayEstablished()`**
- [x] **Step 3: Ejecutar pruebas unitarias de protocolo y ConnectionManager**

---

### Task 4: Suavizado de Ciclo de Vida SCO en `TouchGestureManager` y Ajuste de Logs

**Files:**
- Modify: `app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt:57-72`
- Modify: `app/src/main/java/com/myvu/client/core/LockScreenHelper.kt:61-66`

- [x] **Step 1: En `TouchGestureManager.releaseBluetoothSco`, limpiar dispositivos de comunicación de forma segura y solo cambiar a `MODE_NORMAL` si el modo no era ya normal, previniendo resets del stack Bluetooth en las gafas**
- [x] **Step 2: En `LockScreenHelper`, degradar `Keyguard dismiss error` a log informativo normal cuando la pantalla tiene bloqueo seguro activo**
- [x] **Step 3: Ejecutar suite de pruebas completa `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`**

---

### Task 5: Documentación y Memoria del Proyecto

**Files:**
- Modify: `docs/PROJECT_MEMORY.md`
- Modify: `README.md`
- Modify: `docs/ARCHITECTURE.md`
- Command: `codegraph sync`

- [x] **Step 1: Actualizar `docs/PROJECT_MEMORY.md` con los hallazgos del log, causas raíz y soluciones**
- [x] **Step 2: Actualizar `README.md` y `docs/ARCHITECTURE.md` con el nuevo watchdog de accesibilidad y comando ADB**
- [x] **Step 3: Ejecutar `codegraph sync` al finalizar**
