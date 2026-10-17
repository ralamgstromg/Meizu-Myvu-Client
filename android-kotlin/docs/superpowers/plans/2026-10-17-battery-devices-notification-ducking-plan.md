# Plan: batería/rendimiento, pantalla universal de dispositivos y ducking del TTS de notificaciones

Raíz: `android-kotlin/`. Fuentes en `app/src/main/java/com/myvu/client/` (abreviado `$J`).

## Context
Tres peticiones del usuario sobre Meizu-Myvu-Client:
1. Revisar el código y mejorar rendimiento y batería, sin rehacer lo ya documentado (los planes de 2026-09-09 a 2026-10-02 ya cubrieron escucha activa, desconexión total, watchdog, ping-pong RFCOMM, heartbeat BLE, sensores, SCO y ubicación adaptativa).
2. Crear una interfaz de conexión sencilla, válida para cualquier dispositivo Bluetooth. Hoy hay tres superficies solapadas (`ConnectActivity` de 1092 líneas pensada para gafas, `DeviceManagementBottomSheet`, y entradas dispersas), con campo MAC manual, solo discovery clásico, y "desconectar" = desconexión total.
3. Al leer notificaciones por TTS, subir el volumen de la voz y bajar el de la música; al terminar, restaurar. Hoy nada usa audio focus ni cambia volumen, y `TextToSpeechHelper` no tiene `UtteranceProgressListener`.

Restricciones del usuario: plan aprobado antes de implementar; docs y memoria al final; trivy; `codegraph sync` al empezar y terminar.

## Parte A — Batería y rendimiento (solo lo pendiente)

| # | Cambio | Archivo | Impacto |
|---|---|---|---|
| A1 | Tope de reconexión BLE externa: tras ~8 fallos subir el backoff a 5–10 min y reintentar solo ante evento ACL/bond. Hoy `RECONNECT_MAX_MS = 60000` reintenta cada minuto indefinidamente. | `service/ConnectionManager.kt` (~683-695, 1356) | Alto |
| A2 | Prioridad de ubicación según uso: `PRIORITY_BALANCED_POWER_ACCURACY` para clima y para navegación detenida; alta precisión solo moviéndose. Pasar la prioridad a `createLocationRequest`. | `nav/FusedLocationSource.kt` (~113), `weather/WeatherSync.kt` | Medio |
| A3 | `ScanFilter` por MAC bonded / UUIDs 0x0bd1, 0x0bd3 y `SCAN_MODE_LOW_POWER` en reintentos de fondo; `LOW_LATENCY` solo si el usuario pulsa conectar. | `transport/ble/GlassesScanner.kt` (~64-75) | Medio |
| A4 | Búsqueda de notas fuera del hilo UI: debounce ~250 ms + `Dispatchers.IO`. | `ui/NotesActivity.kt` (~214-250) | Medio |
| A5 | `LogBus`: clasificar origen (regex) de forma perezosa, un formateador por hilo, no llamar `Log.i` en release. | `core/LogBus.kt` (~111-290) | Bajo-medio |
| A6 | `markAllDevicesDisconnectedBlocking()` en `onStartCommand` (hilo principal) pasa a coroutine IO. | `service/MyvuService.kt` (~80) | Bajo |

Fuera de esta pasada (se documenta como pendiente, requiere prueba en dispositivo): R8/`isMinifyEnabled` y quitar `largeHeap` (hay reflexión en `AudioProfiles`/`BtTransport` que exigiría reglas keep), R4 (catch silenciosos) y R5 (scopes sin ciclo de vida).

## Parte B — Pantalla universal "Dispositivos"

Decisión: **nueva `DevicesActivity`** (View/XML + viewBinding, sin Compose) como único punto de entrada de conexión. `ConnectActivity` queda como panel de gafas MYVU (HUD, wifi, zen…), accesible desde la fila de gafas ("Abrir panel de gafas"). Sin cambios de esquema Room (evita el `fallbackToDestructiveMigration` que borraría el chat).

Diseño de la pantalla (una sola lista, estilo iOS inset-grouped, tokens `ios_*`, `Card.Myvu`, `EdgeToEdgeHelper`):
- Cabecera con interruptor de Bluetooth/estado, botón "Buscar" con spinner.
- Sección **Mis dispositivos** (bonded, desde `BluetoothDeviceManager.getAllDevicesFlow()`): icono por tipo, nombre, batería, chip de estado (Conectado / Conectando / Desconectado / Error). Tap → conectar o desconectar ese dispositivo. Menú ⋮: principal, configurar, olvidar.
- Sección **Cercanos** (clásico + BLE fusionados, dedupe por MAC): nombre (o "Dispositivo sin nombre" + MAC corta), RSSI, tap → emparejar y conectar con estado de bond visible.
- Estados vacíos y de error: Bluetooth apagado (botón `ACTION_REQUEST_ENABLE`), permiso denegado (botón a ajustes), sin resultados, bond fallido con motivo.

Piezas nuevas (mínimas):
- `ScannedDevice(mac, name?, rssi?, bondState, bluetoothClass?, transport, serviceUuids)` en memoria, no persistido; evita construir `BluetoothDeviceEntity` de 20 campos por cada hallazgo y deja de descartar dispositivos sin nombre.
- Escáner BLE genérico en `BluetoothDeviceManager` (nuevo `startLeScan` sin filtro MYVU, timeout 12 s, fusionado con `ACTION_FOUND`); `GlassesScanner` no se toca salvo A3.
- `DeviceRowState` (sealed: Nearby, Pairing, Bonded, Connecting, Connected, Failed(reason)) alimentado por `ACTION_BOND_STATE_CHANGED` (hoy solo log) y `getConnectionState` de los proxies HEADSET/A2DP.
- Desconexión por dispositivo: audio → `AudioProfiles.disconnect`; gafas → `ConnectionManager.stop()/disconnectForSwitch()`. `TotalDisconnectHelper` se queda como "Apagar todo".

Reutilizar tal cual: `BluetoothDeviceManager` (`syncPairedDevices`, `pairDevice`, `unpairDevice`, `setPrimaryDevice`, `connectDeviceWithCleanSwitch`, `classifyDeviceSimple`, flows `unbondedDiscoveredDevices`/`isScanning`), `item_bluetooth_device.xml` y el `DeviceAdapter` (extraer del bottom sheet), drawables/iconos `ic_glasses`, `ic_headphones`, `ic_bluetooth_device`, `bg_ios_card`, `bg_ios_badge_*`, `GlassesSettingsActivity.start`, `HeadphoneSettingsActivity.start`.

Correcciones necesarias en el camino:
- Validar `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` (SDK ≥31) o ubicación (<31) al abrir la pantalla; hoy `hasBluetoothPermission()` no valida SCAN.
- No marcar `isConnected = true` en Room para audio sin confirmar el estado real del perfil.
- `unpairDevice`: no borrar de Room si `removeBond` falla.
- Conectar un auricular debe asegurar `MyvuService` vivo (MediaSession y gestos dependen de él).
- Mejorar `classifyDeviceSimple`: usar `BluetoothClass.Device.Major` + UUIDs de servicio en vez de listas de marcas; altavoces/TV/auto como `GENERIC`.
- Textos de la pantalla nueva en español (strings.xml).

Entradas a reconectar a `DevicesActivity`: `cardTodayDevices` en `TodayFragment`, "Dispositivos y conexión" en `MoreFragment`, `nav_devices`/`btnDevices`/`tabDevices`/`cardWearablePair` en `ChatActivity`, `NotesActivity:90`, `SettingsActivity:341/344`. El bottom sheet se retira (o queda como atajo que lanza la actividad).

## Parte C — Ducking y volumen del TTS de notificaciones

Alcance: solo voz de notificaciones (parámetro `duck: Boolean` en `TextToSpeechHelper.speak`); gestos y briefings no cambian.

Nuevo `core/SpeechAudioSession.kt` (objeto, refcount, idempotente, hilo-seguro):
- `acquire()`: `requestAudioFocus` con `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, `AudioAttributes(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, CONTENT_TYPE_SPEECH)`, `setWillPauseWhenDucked(false)`. Si el foco se deniega (llamada), no sube volumen. Si `AudioManager.mode != MODE_NORMAL` (SCO/Gemini), no actúa.
- Subida de volumen: guarda índice actual de `STREAM_MUSIC` en SharedPreferences *antes* de cambiar; sube a `max(actual, objetivo)` con flag `0` (sin UI). Objetivo = % configurable del máximo (por defecto 80%), tope duro para seguridad auditiva. Lee de vuelta `getStreamVolume` por si el dispositivo tiene volumen fijo.
- `release()` (cuando el contador llega a 0): restaura el volumen solo si `getStreamVolume == valor que pusimos` (si el usuario lo cambió, se respeta); `abandonAudioFocus`; borra el valor persistido.
- Watchdog (duración estimada + margen) fuerza `release()` si el motor no emite `onDone`.
- Recuperación tras muerte del proceso: `MyApp.onCreate` (donde ya está `TextToSpeechHelper.attach`) restaura el volumen persistido si quedó pendiente.

Cambios en `core/TextToSpeechHelper.kt`:
- Añadir `UtteranceProgressListener` (hoy inexistente) con conjunto de ids pendientes (incluye `pendingUtterances`). `acquire()` al encolar el primero; `release()` cuando el conjunto queda vacío en `onDone`/`onError`/`onStop`. También en `stop()`, `shutdown()`, fallo de `onInit` y retorno no-SUCCESS de `tts.speak`.
- Notificaciones consecutivas: `QUEUE_ADD` en vez del `QUEUE_FLUSH` por defecto (hoy la segunda corta a la primera); `readLastNotificationAloud` mantiene flush.
- Corregir de paso el doble `speak` existente en `readLastNotificationAloud` y `InboundRouter.kt:110-113` (`init{ speak }` + `speak`).

`service/MirrorNotificationListener.kt` (línea ~149): llamar `speak(..., duck = true, queueMode = QUEUE_ADD)`.

Preferencias nuevas en `core/Prefs.kt`: `notif_duck_enabled` (true), `notif_boost_volume_enabled` (true), `notif_boost_percent` (80), más el valor persistido de recuperación. Controles en la pantalla de ajustes de notificaciones existente.

Límites a documentar: el ducking por foco lo respetan Spotify, YouTube Music, VLC, apps ExoPlayer; podcasts/audiolibros pueden pausar y las apps que ignoran el foco no bajan. No hay API pública de volumen por app; pausar la sesión activa (`MediaPlaybackHelper.getActiveController`) queda como opción futura, no incluida.

## Orden de ejecución
1. `codegraph sync`.
2. Parte C (aislada, testeable) → tests.
3. Parte B (`DevicesActivity` + escáner + estados) → tests.
4. Parte A (A1–A6) → tests.
5. `trivy fs` sobre el repo; corregir hallazgos propios.
6. Documentación: `docs/PROJECT_MEMORY.md` (nueva entrada con todos los cambios y los pendientes R4/R5/R8), `docs/ARCHITECTURE.md` (pantalla de dispositivos, `SpeechAudioSession`, cambios de reconexión/ubicación/scan), nuevo plan en `docs/superpowers/plans/2026-10-17-…-plan.md`. Memoria del proyecto con los ajustes.
7. `codegraph sync`.

## Verificación
- `./gradlew testDebugUnitTest` (JDK 25 en `/opt/jdk-25`, ver `BUILD_INSTRUCTIONS.md`) y `./gradlew assembleDebug`.
- Tests nuevos:
  - `SpeechAudioSessionTest` (Robolectric `ShadowAudioManager`): sube/restaura volumen, no restaura si el usuario lo cambió, refcount con dos locuciones, no actúa con foco denegado o `MODE_IN_COMMUNICATION`, recuperación tras reinicio.
  - Extender `TtsRegressionTest`: el listener libera en `onDone`/`onError`/`onStop`/`stop()`; el cola `QUEUE_ADD` restaura solo tras la última (verificar si `ShadowTextToSpeech` permite disparar el listener en esta versión de Robolectric; si no, probar vía fake inyectable).
  - Tests de `classifyDeviceSimple` (altavoz/TV → `GENERIC`), de fusión y dedupe de `ScannedDevice`, y de backoff de `scheduleReconnect`.
- Manual en dispositivo (queda como pendiente declarado si no hay hardware): reproducir música, recibir notificación → baja música, voz clara, restaura al terminar; barge-in con auricular restaura; emparejar un auricular genérico y uno BLE desde `DevicesActivity`; medir con `adb shell dumpsys batterystats` el efecto de A1–A3.
- `trivy fs --scanners vuln,secret,misconfig .` sin hallazgos nuevos atribuibles al cambio.
