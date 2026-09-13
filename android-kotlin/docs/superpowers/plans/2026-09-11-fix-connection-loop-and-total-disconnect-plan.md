# Plan: Reparación del Ciclo de Conexión/Desconexión y Botón de Desconexión Total

## 1. Diagnóstico del Problema

### A. Ciclo de Conexión/Desconexión de las Gafas (Logs de `myvu_client_log.txt`)
1. **Error de Integridad de Esquema Room (`IllegalStateException`)**:
   - Al agregar `activeListeningEnabled` a `BluetoothDeviceEntity`, la versión de `AppDatabase` permaneció en `4`.
   - Room arrojó un error fatal continuo:
     `Room cannot verify the data integrity. Looks like you've changed schema but forgot to update the version number. Expected identity hash: 56b9b8288dccce96b84121e4119b55a5, found: 8b3b2fae764430338a9dc01c6f7d51a1`
   - Toda actualización de telemetría de batería (`updateGlassesBattery`) y consulta de estado fallaba y bloqueaba la persistencia y actualización del estado de conexión.
2. **Doble InitBurst y Despertar Cíclico de Pantalla**:
   - Cuando BLE se conectaba, se enviaban los 27 mensajes de `InitBurst`.
   - Cuando RFCOMM relay se conectaba segundos después, se volvía a emitir el `InitBurst` de 27 mensajes completo sobre el launcher de las gafas (`com.upuphone.star.launcher`), forzando a Flyme XR a despertar la pantalla (`screen_status: 1`), reiniciar el launcher y aplicar timers cortos (5s).
   - Al apagarse la pantalla tras 5s, el relay de RFCOMM entraba en suspensión/reconexión, reiniciando el ciclo.

### B. Requerimiento de Desconexión Total
- El usuario solicitó un **botón de desconexión total** que, sin importar el dispositivo (gafas, auriculares, genéricos), inactive todos los servicios de la app y garantice reposo absoluto:
  - Detención inmediata de BLE, RFCOMM y reconexión automática.
  - Cancelación de watchdogs (`ServiceWatchdogReceiver`), keep-alive y reconexión.
  - Liberación de canal de audio SCO (`TouchGestureManager`).
  - Inactivación de `MediaSession` (evita captura innecesaria de botones).
  - Desregistro de sensores de hardware (`HealthService`).
  - Apagado de hilos de clima y AI.
  - Actualización en Room DB de todos los dispositivos a `isConnected = false`.
  - Detención del servicio en primer plano (`MyvuService.stopSelf()`) y retiro de la notificación persistente.
  - Accesible de forma destacada en:
    1. Dashboard (`view_dashboard.xml` -> botón "Desconexión Total").
    2. Menú Lateral Drawer (`menu_navigation_drawer.xml`).
    3. BottomSheet de dispositivos (`bottom_sheet_devices.xml`).

---

## 2. Plan de Implementación

### Tarea 1: Incrementar Versión de Room DB y Manejo de Migración
- En `AppDatabase.kt`: Incrementar `version = 5`.
- Asegurar `fallbackToDestructiveMigration(dropAllTables = true)`.
- Esto soluciona de inmediato el `IllegalStateException` y permite que `BluetoothDeviceManager` guarde y actualice dispositivos sin errores.

### Tarea 2: Evitar Doble `InitBurst` en Sesión de Relay
- En `ConnectionManager.kt`:
  - En `relayListener.onConnected()`, solo enviar `InitBurst` si la sesión BLE no ha enviado ya el init burst (`!bleSession.ready`).
  - Si BLE ya está listo, no volver a enviar los 27 paquetes de reinicio de launcher al relay RFCOMM.

### Tarea 3: Crear `TotalDisconnectManager` (o `TotalDisconnectHelper`)
- Crear helper centralizado `TotalDisconnectHelper`:
  - Pone `Prefs.setAutoReconnectEnabled(context, false)`.
  - Cancela `ServiceWatchdogReceiver.cancelWatchdog(context)`.
  - Envía `ACTION_STOP` / `ACTION_TOTAL_DISCONNECT` a `MyvuService`.
  - Llama a `BluetoothDeviceManager.getInstance(context).markAllDevicesDisconnected()`.
  - Llama a `HealthService.getInstance(context).unregisterHardwareSensor()`.
  - Llama a `TouchGestureManager.releaseBluetoothSco(context)`.
  - Desactiva escaneo de dispositivos.

### Tarea 4: Actualizar `BluetoothDeviceManager` y `HealthService`
- En `BluetoothDeviceManager.kt`:
  - Agregar `suspend fun markAllDevicesDisconnected()` que pone `isConnected = false` a todas las entidades en Room y actualiza `_activeDevice = null`.
  - Agregar variante síncrona `markAllDevicesDisconnectedBlocking()`.
- En `HealthService.kt`:
  - Agregar `unregisterHardwareSensor()`.

### Tarea 5: Integrar Botón de Desconexión Total en la UI
- **Dashboard (`view_dashboard.xml`)**:
  - Convertir `btnDisconnect` en botón prominente de "Desconexión Total", con icono o estilo de apagado completo.
- **Drawer Lateral (`menu_navigation_drawer.xml`)**:
  - Agregar item `nav_total_disconnect` ("Desconexión Total").
  - En `ConnectActivity.kt` / drawer listener: invocar `TotalDisconnectHelper.performTotalDisconnect(this)` y mostrar feedback visual.
- **BottomSheet Dispositivos (`bottom_sheet_devices.xml`)**:
  - Agregar botón "Desconectar Todo" que invoque `TotalDisconnectHelper.performTotalDisconnect(requireContext())`.

### Tarea 6: Pruebas y Verificación
- Crear tests para `TotalDisconnectHelper` y `markAllDevicesDisconnected()`.
- Verificar compilación completa con `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.
- Sincronizar `codegraph`.
- Actualizar `PROJECT_MEMORY.md`, `ARCHITECTURE.md`, `README.md`.
