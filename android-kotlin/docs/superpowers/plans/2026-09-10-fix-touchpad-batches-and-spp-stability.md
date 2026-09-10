# Plan de Mejora Integral: Detección Robusta de Gestos Táctiles y Estabilidad SPP/Audio

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminar las fallas de detección de gestos táctiles en las patillas de las gafas (doble toque intermitente que requería múltiples intentos) y evitar la caída del socket SPP de las gafas que provocaba desconexiones de 8-15 segundos cada vez que se usaba Gemini, además de corregir errores de almacenamiento en `BackupManager`.

**Architecture & Root Causes:**
1. **Lotes de Telemetría BLE con Rebotes y Tiempos Simultáneos en Teléfono (`InboundRouter`)**:
   - En el log (Líneas 298-307), la ráfaga `[210, 210, 210]` contiene toques duplicados de rebote capacitivo con el mismo timestamp de hardware (`1789075058000`), y los toques legítimos en un mismo paquete BLE llegan al hilo del teléfono con 1-2ms de diferencia.
   - En la patilla secundaria (`sender: 2`, líneas 378 y 436), un toque emite siempre la pareja `200` (down) y `203` (up). Ambos estaban mapeados a `TAP`, enviando dos toques con 2ms de diferencia y rompiendo el acumulador de doble toque.
   - *Solución*: Deduplicar rebotes con mismo timestamp en el lote, consolidar parejas `200`+`203` de `sender: 2` en un solo toque, y si un lote contiene dos toques con diferencia de hardware entre 80ms y 500ms, sintetizar `DOUBLE_TAP` inmediatamente en el lote.
2. **Reseteo del Acumulador por Ruido `< 40ms` en `TouchGestureManager`**:
   - Si llegaba un evento residual a `< 40ms` (como un rebote), `lastTapTime` se actualizaba a `now`, reseteando la cuenta y haciendo que el segundo toque legítimo del usuario quedara fuera de rango.
   - *Solución*: Si un evento llega a `< 40ms` del anterior en el teléfono, considerarlo rebote eléctrico: ignorarlo **sin** actualizar `lastTapTime`. Además, propagar `eventTime` desde `InboundRouter` para comparar tiempos del hardware de las gafas cuando estén disponibles.
3. **Caída Sistemática del Servidor SPP al Liberar Bluetooth SCO (`TouchGestureManager`)**:
   - En el log (Líneas 236, 333, 526, 585, 672), exactamente 50ms después de `releaseBluetoothSco()`, las gafas cierran el servidor RFCOMM: `<- SPP server closed by the glasses -- dropping the relay`. La app pasa 8-15 segundos reconectando, período durante el cual no procesa gestos.
   - *Causa*: En Android 12+ (API 31+), mezclar llamadas legacy `am.stopBluetoothSco()` / `startBluetoothSco()` con `setCommunicationDevice()` envía comandos HCI que reinician el chip Bluetooth de las gafas y desconectan SPP.
   - *Solución*: En Android 12+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`), utilizar exclusivamente `setCommunicationDevice` con `MODE_IN_COMMUNICATION` al activar y `clearCommunicationDevice` con `MODE_NORMAL` al liberar, eliminando por completo `startBluetoothSco()` / `stopBluetoothSco()` en API 31+.
4. **Fallo EACCES en `BackupManager` (Línea 145)**:
   - Escritura directa en `/storage/emulated/0/Download/` bloqueada por Scoped Storage en Android 10+.
   - *Solución*: Usar `getExternalFilesDir()` o `MediaStore.Downloads` para exportar copias de seguridad sin violar Scoped Storage.

**Tech Stack:** Kotlin, Android SDK 35, Coroutines, JUnit 4, Robolectric.

---

### Task 1: Deduplicación y Consolidación de Lotes Táctiles en `InboundRouter`

**Files:**
- Modify: `app/src/main/java/com/myvu/client/app/InboundRouter.kt`
- Modify: `app/src/main/java/com/myvu/client/app/feature/GlassGesture.kt`
- Test: `app/src/test/java/com/myvu/client/app/InboundGestureTest.kt`

- [x] **Step 1: Escribir tests en `InboundGestureTest`:**
  * Prueba con array de rebote de hardware `[{"key_code":"210", "event_time": T}, {"key_code":"210", "event_time": T}]` verificando deduplicación a un solo toque.
  * Prueba con ráfaga de `sender: 2` conteniendo `[{"key_code":"200"}, {"key_code":"203"}]` verificando consolidación a un solo toque.
  * Prueba con dos toques en el mismo lote con delta temporal de 180ms verificando síntesis de `DOUBLE_TAP`.
- [x] **Step 2: Ejecutar tests y verificar fallos esperados.**
- [x] **Step 3: Implementar mejoras en `InboundRouter.kt`:**
  * En `dispatchGestureBatch`:
    1. Filtrar eventos duplicados consecutivos del mismo emisor con timestamps idénticos o diferencia `<= 50ms`.
    2. Manejar la pareja `200` y `203` en `sender: 2`: Si `203` acompaña a `200` en el mismo lote o ráfaga, ignorar `203` como evento de confirmación de fin de toque.
    3. Si el lote contiene dos toques `TAP` con `eventTime` diferente dentro del intervalo de doble toque (80ms..500ms), promoverlos a un único `GlassGesture.DOUBLE_TAP`.
- [x] **Step 4: Ejecutar `InboundGestureTest` y verificar que pase al 100%.**

---

### Task 2: Protección contra Rebotes Eléctricos `< 40ms` y Soporte de Hardware Timestamps en `TouchGestureManager`

**Files:**
- Modify: `app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt`
- Test: `app/src/test/java/com/myvu/client/app/TouchGestureManagerTest.kt`

- [x] **Step 1: Escribir tests en `TouchGestureManagerTest`:**
  * Toque a T=0ms seguido de rebote parásito a T=5ms: verificar que el rebote a 5ms NO sobreescriba `lastTapTime`.
  * Segundo toque legítimo a T=200ms: verificar que promueva exitosamente a `DOUBLE_TAP` (gracias a que el rebote a 5ms no arruinó el acumulador).
- [x] **Step 2: Ejecutar tests y verificar fallo.**
- [x] **Step 3: Implementar en `TouchGestureManager.kt`:**
  * En `handleGesture`: Si `gesture == GlassGesture.TAP` y `now - lastTapTime < DOUBLE_TAP_MIN_INTERVAL_MS` (40ms):
    Loguear descarte de rebote de contacto (`Contact bounce tap ignored (< 40ms)`) y salir **sin** actualizar `lastTapTime`.
- [x] **Step 4: Ejecutar `TouchGestureManagerTest` y verificar 100% éxito.**

---

### Task 3: Estabilidad Bluetooth SCO sin Takedown de Servidor SPP en Android 12+

**Files:**
- Modify: `app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt`
- Test: `app/src/test/java/com/myvu/client/app/TouchGestureManagerTest.kt`

- [x] **Step 1: Modificar `launchGeminiAssistant` y `releaseBluetoothSco` en `TouchGestureManager.kt`:**
  * En Android 12+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`):
    - Al activar: Usar únicamente `setCommunicationDevice` con dispositivo `TYPE_BLUETOOTH_SCO` y `am.mode = AudioManager.MODE_IN_COMMUNICATION`. **No llamar** a `startBluetoothSco()`.
    - Al liberar: Usar únicamente `clearCommunicationDevice()` y restaurar `am.mode = AudioManager.MODE_NORMAL`. **No llamar** a `stopBluetoothSco()` ni alterar `isBluetoothScoOn`.
  * En Android legacy (< 12): Mantener `startBluetoothSco()` / `stopBluetoothSco()`.
- [x] **Step 2: Verificar en tests unitarios que la lógica de release y launch no arroje excepciones y limpie dispositivos correctamente.**

---

### Task 4: Corrección de Almacenamiento en `BackupManager`

**Files:**
- Modify: `app/src/main/java/com/myvu/client/core/BackupManager.kt`

- [x] **Step 1: Inspeccionar `BackupManager.kt` y actualizar la ruta de exportación a `context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)` o MediaStore seguro, evitando el fallo `open failed: EACCES`.**
- [x] **Step 2: Verificar compilación y pruebas de `BackupManager`.**

---

### Task 5: Verificación Integral, Memoria Viva y Documentación

**Files:**
- Modify: `docs/PROJECT_MEMORY.md`
- Modify: `README.md`
- Modify: `docs/ARCHITECTURE.md`
- Command: `codegraph sync`

- [x] **Step 1: Ejecutar suite de pruebas completa `./gradlew testDebugUnitTest` y compilar APK `./gradlew assembleDebug`.**
- [x] **Step 2: Actualizar `docs/PROJECT_MEMORY.md`, `README.md` y `docs/ARCHITECTURE.md` con las optimizaciones aplicadas.**
- [x] **Step 3: Ejecutar `codegraph sync` al finalizar.**
