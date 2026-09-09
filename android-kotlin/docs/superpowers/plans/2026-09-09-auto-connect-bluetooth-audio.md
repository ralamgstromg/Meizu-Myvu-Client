# Plan: Auto-Connect Bluetooth Classic Audio Profiles (HFP + A2DP)

## Problema Detectado
El usuario reporta que al pulsar "Connect", las gafas conectan a la app (vía BLE), pero no se conectan al Bluetooth del teléfono (audio, llamadas, multimedia).
Causas encontradas en el código:
1. **Llamada a `audioProfiles.connect(device)` bloqueada por condición errónea**: En `ConnectionManager.onSessionReady()`, la llamada a `audioProfiles?.connect(device)` estaba condicionada por `if (!relayExpected)`. Como `relayExpected` es `true` cuando el transporte es BLE (`transport == null` y `sppUuidVal != null`), la conexión de perfiles de audio se omitía por completo a la espera de un socket RFCOMM que aún no existía.
2. **Race condition con los proxies de perfil**: `AudioProfiles` inicializa la vinculación asíncrona de los proxies `BluetoothHeadset` y `BluetoothA2dp` (`adapter.getProfileProxy()`). Si `connect(device)` se invocaba antes de que Android llamara a `onServiceConnected()` (que tarda 100-500ms), `headset` y `a2dp` eran `null`, la conexión se descartaba en silencio y `audioProfilesAttempted = true` impedía cualquier reintento posterior.
3. **Falta de auto-conexión al completar el enlace de los proxies**: `proxyListener.onServiceConnected()` no intentaba conectar los perfiles una vez que el proxy finalmente quedaba enlazado.
4. **Discrepancia de MAC entre BLE y Bluetooth Clásico**: Si las gafas se detectaron por auto-búsqueda BLE, el objeto `BluetoothDevice` recibido puede tener la dirección MAC de BLE en vez de la MAC de Audio Clásico si el dispositivo las anuncia por separado. `AudioProfiles` debe resolver el dispositivo emparejado (`adapter.bondedDevices`) que coincida por nombre ("MYVU") o MAC para garantizar que la pila de audio de Android aplique la conexión al dispositivo de sonido correcto.
5. **Métodos de prioridad y vinculación**: Además de `setConnectionPolicy(device, 100)`, debe intentarse `setPriority(device, 1000)` (Android 9/10) y si el dispositivo no está emparejado, solicitar `createBond()`.

## Fases de Implementación

### Fase 1: Enriquecer `AudioProfiles.kt`
- Resolver el dispositivo destino: si `device` no está emparejado o es una dirección BLE pura, buscar en `adapter.bondedDevices` el dispositivo con nombre que contenga `"MYVU"` o coincidencia de MAC.
- En `proxyListener.onServiceConnected()`: cuando `headset` o `a2dp` se enlace, si hay una solicitud de conexión pendiente o activa (`targetDevice != null`), ejecutar inmediatamente `tryConnect()` para ese perfil.
- En `tryConnect()`: intentar tanto `setConnectionPolicy(device, 100)` como `setPriority(device, 1000)` y `connect(device)`.
- Si el dispositivo aún no está emparejado (`bondState == BOND_NONE`), disparar `device.createBond()` para registrar los perfiles en el sistema.

### Fase 2: Activar Auto-Conexión en `ConnectionManager.kt`
- Eliminar la restricción `if (!relayExpected)` que bloqueaba la llamada a `audioProfiles?.connect(device)`.
- Invocar `connectAudioProfiles()` tanto cuando la sesión BLE esté lista como cuando se complete la negociación de capacidades y en `onSessionReady()`.
- Permitir reintentos controlados en caso de que los proxies tarden en enlazar.

### Fase 3: Verificación y Compilación
- Ejecutar pruebas unitarias.
- Compilar APK: `./gradlew assembleDebug`.
- Sincronizar memoria y documentación.
