# Plan de Trabajo: Validación de Ajustes de Otros Dispositivos y Protección contra Drenaje de Batería

## Contexto y Objetivo
El usuario solicita:
1. Validar que las configuraciones de otros dispositivos (auriculares, wearables Bluetooth) se lean y guarden de forma correcta.
2. Verificar y garantizar que no queden abiertas conexiones ni recursos que drenen la batería de los dispositivos (gafas, auriculares) y del móvil (SoC, radio Bluetooth, CPU).

---

## 1. Análisis de Ajustes de Otros Dispositivos (Auriculares y Wearables)

### 1.1 Diagnóstico
1. **Fallback Tóxico a Gafas**: En `HeadphoneSettingsActivity.kt` y `HeadphoneGestureManager.kt`, si no se pasaba MAC o no se encontraba el dispositivo, el código ejecutaba `dao.getActiveConnectedDevice()`. Si las gafas MYVU estaban conectadas, este método devolvía la entidad de las gafas, permitiendo que la configuración de auriculares sobrescribiera las gafas o viceversa.
2. **Pérdida Silenciosa en `BluetoothDeviceManager.updateDeviceGestures`**: Si `dao.getDevice(mac)` devolvía null, el método abortaba silenciosamente (`return`), descartando los cambios del usuario.
3. **Inicialización Incompleta de Spinners cuando `device == null`**: En `HeadphoneSettingsActivity`, si no había dispositivo en DB, los spinners quedaban en el índice 0 (`MEDIA_PLAY_PAUSE` para todos los toques), y la MAC se fijaba como `"00:00:00:00:00:00"`.

### 1.2 Solución
1. Corregir `loadDevice()` en `HeadphoneSettingsActivity.kt` para aislar estrictamente dispositivos de tipo `HEADPHONES` o `GENERIC`, buscando primero por MAC, luego por conectados de tipo `HEADPHONES`, luego en la lista de dispositivos de Room, y en dispositivos vinculados del sistema Bluetooth.
2. Si no existe registro previo, instanciar un fallback limpio con defaults correctos (`MEDIA_PLAY_PAUSE`, `LAUNCH_GEMINI`, `LAUNCH_PHONE_ASSISTANT`, `CREATE_AI_NOTE`, `AUDIO_ONLY`).
3. En `saveSettings()`, asegurar la persistencia en Room (`dao.insertOrUpdate`) y refrescar `BluetoothDeviceManager.activeDevice`.
4. En `BluetoothDeviceManager.updateDeviceGestures`, si la entidad no existe en DB, crearla e insertarla para nunca descartar configuraciones.
5. En `HeadphoneGestureManager`, aislar la consulta a auriculares para que jamás consuma gestos dirigidos a gafas.

---

## 2. Análisis y Blindaje contra Drenaje de Batería y Recursos Abiertos

### 2.1 Diagnóstico de Fugas de Batería
1. **Fuga Indefinida de Bluetooth SCO**: En `TouchGestureManager.kt`, cuando `isLive == true` (modo Gemini Live), el canal SCO (`startBluetoothSco`) se mantenía abierto de forma continua sin ningún temporizador de seguridad. Si el usuario bloqueaba el teléfono o abandonaba la conversación, el canal SCO continuaba abierto permanentemente, drenando el chip Bluetooth y micrófono de las gafas/auriculares y del móvil.
2. **Escaneo Bluetooth sin Timeout Automático**: En `BluetoothDeviceManager.kt`, `startScanning()` invocaba `bluetoothAdapter?.startDiscovery()`, el cual no tenía temporizador de apagado por software. En `DeviceManagementBottomSheet`, el botón de escaneo ocultaba la barra de progreso a los 8 segundos pero nunca llamaba a `stopScanning()`, ni tampoco al destruir el diálogo (`onDestroyView`/`dismiss`).
3. **Falta de Detección de Pantalla Apagada (`ACTION_SCREEN_OFF`)**: No existía receptor en el servicio para liberar recursos activos cuando la pantalla del teléfono se apaga o bloquea.
4. **Hilos No Terminados en `WeatherSync`**: El `ExecutorService` de `WeatherSync` no se apagaba en `stop()`.

### 2.2 Solución
1. **Temporizador de Seguridad para SCO**:
   - En `TouchGestureManager.kt`, incorporar un timeout de seguridad de 5 minutos para sesiones continuas de `isLive`, liberando automáticamente el audio SCO si el usuario deja el teléfono inactivo.
   - Invocar `releaseBluetoothSco` explícitamente en `ConnectionManager.teardown()`, `ConnectionManager.stop()` y `LockScreenHelper.lockDevice()`.
2. **Auto-Cancelación de Escaneo Bluetooth**:
   - En `BluetoothDeviceManager.kt`, añadir un watchdog de 12 segundos a `startScanning()` que invoque automáticamente `stopScanning()`.
   - En `DeviceManagementBottomSheet.kt`, llamar a `devManager.stopScanning()` en `onDestroyView()` y en el callback de finalización de progreso.
3. **Receptor `ACTION_SCREEN_OFF` en `MyvuService`**:
   - Registrar `ACTION_SCREEN_OFF` en `MyvuService` para liberar audio SCO y cancelar búsquedas activas cuando la pantalla se apague.
4. **Apagado Limpio en `WeatherSync`**:
   - Invocar `net.shutdownNow()` en `WeatherSync.stop()`.

---

## 3. Plan de Pruebas y Verificación
1. Pruebas unitarias para `HeadphoneSettingsActivity` y persistencia en Room (`BluetoothDeviceManagerTest` / `SettingsGestureConfigTest`).
2. Pruebas de timeout de escaneo y liberación de SCO.
3. Compilación completa con `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.
4. Sincronización final con `codegraph sync`.
5. Registro exhaustivo en `PROJECT_MEMORY.md`, `ARCHITECTURE.md` y `README.md`.
