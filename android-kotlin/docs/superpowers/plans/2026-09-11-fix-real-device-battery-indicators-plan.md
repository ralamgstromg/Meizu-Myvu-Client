# Plan de Implementación: Corrección y Actualización en Tiempo Real de Indicadores de Batería de Dispositivos Conectados

## 1. Diagnóstico y Causas Raíz
El usuario reportó:
> "valida el indicador de la bateria de los dispositivos conectados, dado que al parecer no se esta actualizando con los valores reales."

Tras auditar el código fuente, se detectaron las siguientes fallas críticas:
1. **Valores Falsos Hardcodeados (`85%` y `92%`)**:
   - En `ChatActivity.kt`: `txtGlassesBattery?.text = "${glasses.batteryLevel ?: 85}%"` y `txtHeadphonesBattery?.text = "${headphones.batteryLevel ?: 92}%"`. Si la base de datos tenía `batteryLevel == null`, mostraba falsos 85% y 92% engañando al usuario.
   - En `GlassesSettingsActivity.kt`: `txtGlassesBattery.text = "${dev.batteryLevel ?: 85}%"`.
2. **`updateBatteryLevel` en `BluetoothDeviceDao` Nunca Era Invocado**:
   - `BluetoothDeviceDao.updateBatteryLevel` existía en la interfaz pero nunca se llamaba desde ninguna parte de la aplicación.
3. **Falta de Persistencia y Propagación de Batería de Gafas AR**:
   - Cuando `InboundRouter` recibía telemetría de batería de las gafas y llamaba a `ConnectionManager.updateGlassesBattery`, solo actualizaba un campo en memoria (`glassesInfoVal`). Nunca guardaba en la base de datos Room (`BluetoothDeviceEntity`) ni notificaba a `BluetoothDeviceManager`.
4. **Bug en `InboundRouter.checkBatteryUpdate`**:
   - El comentario decía `// 4. Action containing battery or get_device_info`, pero el código hacía `if (action.contains("battery"))`. Si las gafas respondían a `get_device_info`, la respuesta no coincidía y se descartaba la batería. Además se descartaba el nivel 0% (`battery in 1..100` en lugar de `battery in 0..100`).
5. **Cero Soporte de Batería para Auriculares / Dispositivos Bluetooth Estándar en Android**:
   - `BluetoothDeviceManager` no registraba `BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED` (`android.bluetooth.device.action.BATTERY_LEVEL_CHANGED`) ni `BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT` (eventos `+IPHONEACCEV` usados por AirPods y auriculares TWS).
   - Tampoco realizaba lectura reflexiva de `BluetoothDevice.getBatteryLevel()` (API 26/28+) al sincronizar dispositivos vinculados o al conectarse.

---

## 2. Solución Sistemática

### A. Capa de Protocolo de Gafas (`InboundRouter.kt` & `ConnectionManager.kt`)
1. **`InboundRouter.kt`**:
   - Corregir detección en `checkBatteryUpdate` para contemplar `action.contains("device_info")`, `action.contains("get_device_info")`, respuestas con `battery` directo en el objeto raíz, y aceptar el rango `0..100`.
2. **`ConnectionManager.kt`**:
   - En `updateGlassesBattery(battery, isCharging)`: persistir el nivel de batería real directamente en `BluetoothDeviceManager` / `BluetoothDeviceDao` para la MAC de las gafas AR (`Prefs.targetMac(context)`).

### B. Gestor de Dispositivos Bluetooth (`BluetoothDeviceManager.kt`)
1. **Recepción de Eventos de Batería del Sistema Operativo**:
   - Registrar en `bluetoothReceiver`:
     - `android.bluetooth.device.action.BATTERY_LEVEL_CHANGED` (`BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED`).
     - `android.bluetooth.headset.action.VENDOR_SPECIFIC_HEADSET_EVENT` (comandos `+IPHONEACCEV`).
     - `BluetoothDevice.ACTION_ACL_CONNECTED`.
2. **Lectura Directa de Batería (`getBatteryLevel`)**:
   - Implementar `readDeviceBattery(device: BluetoothDevice): Int?` utilizando reflexión sobre `BluetoothDevice.getBatteryLevel()`.
   - Al sincronizar (`syncPairedDevices()`) y al recibir conexión (`handleDeviceConnectionChanged()`), leer la batería real y actualizar la entidad Room.
3. **Método de Refresco Forzado**:
   - `refreshAllDeviceBatteries()`: itera los dispositivos conectados/vinculados y actualiza sus niveles en Room y en el StateFlow activo.
   - `updateGlassesBatteryLevel(mac: String?, battery: Int)`: actualiza la entidad de las gafas y emite cambio.

### C. Interfaces de Usuario (`ChatActivity.kt`, `GlassesSettingsActivity.kt`, `HeadphoneSettingsActivity.kt`, `DeviceManagementBottomSheet.kt`)
1. **`ChatActivity.kt`**:
   - Eliminar los valores hardcodeados `85%` y `92%`.
   - Mostrar el porcentaje real si está disponible (`${device.batteryLevel}%`). Si no está disponible pero está conectado, consultar `ConnectionManager` (para gafas) o mostrar `"--"`.
   - En `onResume()`, invocar `BluetoothDeviceManager.getInstance(this).refreshAllDeviceBatteries()`.
2. **`GlassesSettingsActivity.kt`**:
   - Eliminar `85%` hardcodeado. Consultar `dev.batteryLevel ?: MyvuService.activeConnection()?.glassesInfo()?.battery`. Si no hay dato, mostrar `"--"`.
3. **`HeadphoneSettingsActivity.kt` & `activity_headphone_settings.xml`**:
   - Añadir `txtHeadphoneBattery` en la cabecera mostrando el porcentaje real de los audífonos.
4. **`DeviceManagementBottomSheet.kt`**:
   - Si `device.batteryLevel != null && device.batteryLevel in 0..100`, incluir el porcentaje de batería en la línea de estado del ítem (`● Conectado • 80% • HUD + TTS`).

---

## 3. Pruebas y Verificación
1. **Pruebas Unitarias**:
   - Actualizar `BluetoothDeviceManagerTest` y `InboundGestureTest` para validar el parser de batería de `InboundRouter` con `get_device_info`, top-level `battery: 0` y `battery: 100`, y el método extractor de batería de dispositivos Bluetooth.
2. **Compilación**:
   - `rtk ./gradlew testDebugUnitTest`
   - `rtk ./gradlew assembleDebug`
3. **Protocolo Final**:
   - `rtk codegraph sync`
   - Actualizar `PROJECT_MEMORY.md`, `ARCHITECTURE.md` y `README.md`.
