# Especificación de Arquitectura Técnica — Meizu Myvu Client

Este documento describe la arquitectura interna, diseño de capas, flujo de datos, codecs binarios y ciclo de vida de los servicios del cliente Android para las gafas inteligentes **Meizu Myvu AR**.

---

## 1. Visión General y Topología

La aplicación actúa como puente bidireccional entre el sistema operativo Android y el hardware de las gafas Meizu Myvu (pantalla micro-LED monocromática/verde, micrófonos duales, sensores IMU y trackpad táctil).

```
   ┌─────────────────────────────┐
   │    Meizu Myvu AR Glasses    │
   │  (HUD Micro-LED / Sensors)  │
   └──────────────┬──────────────┘
                  │  Bluetooth RFCOMM (SPP) / BLE GATT
                  ▼
   ┌─────────────────────────────┐
   │       Transport Layer       │
   │   - BtTransport (SPP)       │
   │   - BleTransport (GATT)     │
   │   - FrameReassembler        │
   └──────────────┬──────────────┘
                  │  Raw Frames / Stream
                  ▼
   ┌─────────────────────────────┐
   │       Protocol Layer        │
   │   - LinkProtocol            │
   │   - TlvBox / TlvTags        │
   │   - Pb (Protobuf Codecs)    │
   │   - Relay / RelaySequencer  │
   └──────────────┬──────────────┘
                  │  Decoded Events / Commands
                  ▼
   ┌─────────────────────────────┐
   │     Core Service Layer      │
   │   - MyvuService (Foreground)│
   │   - MirrorNotification      │
   │   - AutoSendAccessibility   │
   └──────────────┬──────────────┘
                  │  Event Bus / Flow
                  ▼
   ┌─────────────────────────────┬─────────────────────────────┐
   │        Skills Engine        │        UI & Storage         │
   │ - SkillManager              │ - Room Database (Notes/Rem) │
   │ - AI Engine (Local/Cloud)   │ - Material 3 Activities     │
   │ - Built-in Skills Handlers  │ - Live StateFlow Observers  │
   └─────────────────────────────┴─────────────────────────────┘
```

---

## 2. Capa de Transporte (`com.myvu.client.transport`)

### 2.1 Bluetooth SPP (`com.myvu.client.transport.bt`)
- **`BtTransport`**: Administra la conexión mediante sockets RFCOMM estándar de Android utilizando el UUID SPP (`00001101-0000-1000-8000-00805F9B34FB`).
- **`RfcommFraming`**: Aplica delimitación de tramas con encabezados de longitud y números de secuencia para evitar corrupción en flujos continuos.
- **`FrameReassembler`**: Ensambla paquetes fragmentados debido a los límites MTU del buffer de transmisión Bluetooth.

### 2.2 Bluetooth Low Energy (`com.myvu.client.transport.ble`)
- **`BleTransport`**: Gestiona el canal GATT para dispositivos emparejados o modo bajo consumo. Mapea diagnósticos detallados de desconexión como `147` (`GATT_CONNECTION_TIMEOUT`).
- **`GlassesScanner`**: Escáner BLE que detecta anuncios de gafas Myvu por coincidencia de nombre ("MYVU") y UUIDs de servicio (`0x0bd3`, `0x0bd1`), permitiendo auto-descubrimiento sin requerir entrada manual de la dirección MAC.
- **`GattQueue`**: Cola secuencial de operaciones GATT (lecturas, escrituras, habilitación de descriptores de notificación) para evitar colisiones nativas en la pila Bluetooth de Android.
- **`BleHeartbeat`**: Envío periódico de paquetes ping/pong para monitorizar el enlace activo y detectar desconexiones silenciosas.
- **`BleReassembler` / `BleMessageChannel`**: Manejo de fragmentación y reensamblado en características BLE con MTU variable (23 a 517 bytes).

---

## 3. Capa de Protocolo y Serialización (`com.myvu.client.protocol`)

El hardware Meizu Myvu utiliza un protocolo híbrido compuesto por:

### 3.1 Link Protocol (`com.myvu.client.protocol.link`)
- **`LinkProtocol`**: Capa base de intercambio de información del dispositivo (`DeviceInfo`, `DeviceId`).
- Negocia capacidades al conectar: versión de firmware, soporte de audio, resolución de HUD y estado de carga.
- **`LinkCommands`**: Comandos de control para encendido de pantalla, apagado, ajuste de brillo y reseteo de display.

### 3.2 Formato TLV (`TlvBox`, `TlvTags`)
Los metadatos y configuraciones se empaquetan en estructuras **Type-Length-Value (TLV)**:
- **Tag (16-bit / 8-bit)**: Identificador del atributo o acción.
- **Length (16-bit)**: Longitud exacta del payload en bytes.
- **Value**: Bytes de datos (cadenas UTF-8, enteros little-endian, o sub-cajas TLV anidadas).

### 3.3 Protocol Buffers y Mensajería Relay (`Pb`, `Relay`, `RelayMessage`)
- Para el contenido visual rico (tarjetas del HUD, teleprompter, previsión del clima, notificaciones complejas), se serializan estructuras compactas estilo Protobuf binario (`Pb`, `PbValue`).
- **`RelaySequencer`**: Asigna identificadores de secuencia únicos a cada mensaje saliente, gestionando acuses de recibo (ACK) y reintentos.

---

## 4. Servicios de Segundo Plano (`com.myvu.client.service`)

### 4.1 `MyvuService` (Foreground Service)
- Configurado con tipo de servicio en primer plano `connectedDevice` (requerido por Android 14+).
- Mantiene el socket de transporte siempre vivo en una Coroutine ligada al ciclo del servicio.
- Punto único de despacho: recibe eventos de las gafas (toques en el touchpad, comandos de voz) y los canaliza al motor de habilidades o a la interfaz de usuario.
- Notificación persistente con estado de batería y reconexión manual/automática.

### 4.2 `MirrorNotificationListener`
- Implementa `NotificationListenerService`.
- Filtra notificaciones según la configuración de aplicaciones habilitadas por el usuario (`NotificationAppsActivity`).
- Extrae título, texto, icono de la app remitente y las formatea como tarjetas de HUD para ser proyectadas en las gafas.

### 4.3 `AutoSendAccessibilityService` y Watchdog de Persistencia
- Permite acciones de accesibilidad para automatizar el envío de mensajes de texto en aplicaciones como WhatsApp, Telegram y SMS sin requerir manipulación manual del dispositivo.
- **Watchdog de Persistencia y Auto-Activación**:
  - Al actualizar el APK (`ACTION_MY_PACKAGE_REPLACED`), Android apaga con frecuencia los servicios de accesibilidad de apps externas.
  - `autoEnableIfPermitted(context)`: Reactiva el servicio de forma programática escribiendo en `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` si la app posee permiso `android.permission.WRITE_SECURE_SETTINGS` (concedido una sola vez mediante `adb shell pm grant com.myvu.client android.permission.WRITE_SECURE_SETTINGS`).
  - `notifyAccessibilityDisabled(context)`: Si no tiene permiso ADB, despacha una notificación de alta prioridad (Heads-Up) con canal propio (`myvu_accessibility_alert`) que conduce directamente a la pantalla de Ajustes de Accesibilidad con un solo toque.
  - Se ejecuta proactivamente en `BootReceiver` (`MY_PACKAGE_REPLACED`, `BOOT_COMPLETED`), `MyvuService` (`onCreate`) y en la UI (`ConnectActivity` y `SettingsActivity` en `onResume`), con banner de alerta y utilidad para copiar el comando ADB al portapapeles.

### 4.4 Gestión de Audio Bluetooth Clásico (`AudioProfiles` & `ConnectionManager`)
- **Doble Enlace (BLE + Classic Bluetooth)**: Las gafas Myvu utilizan BLE para el canal de telemetría/control y Bluetooth clásico (BR/EDR) para audio (HFP para llamadas/micrófono y A2DP para salida estéreo).
- **Auto-conexión Proactiva**: Al pulsar "Connect" y afianzarse el enlace BLE (`onReady` y `onSessionReady`), `ConnectionManager` invoca `AudioProfiles.connect()`.
- **Resolución de MAC Dual**: Mediante `resolveTargetDevice`, busca el dispositivo clásico emparejado en Android con nombre conteniendo `"MYVU"` o coincidencia de dirección MAC.
- **Enlace de Proxies y Políticas**: Realiza binding asíncrono con `BluetoothProfile.HEADSET` y `BluetoothProfile.A2DP`, encola la conexión pendiente si los proxies aún no están vinculados, y aplica por reflexión `setConnectionPolicy(device, 100)` y `setPriority(device, 1000)` para forzar la reconexión de audio en el subsistema Bluetooth del sistema operativo.

### 4.5 Subsistema de Gestos Táctiles y Botón Físico (`TouchGestureManager`, `InboundRouter`, `GlassGesture`, `MediaSession`)
- **Separación Estricta: Botón Físico de Montura vs. Sensores Táctiles de Patillas ("Patas")**:
  - **Botón Físico de la Montura (`com.upuphone.ai.assistant`, `checkAiTrigger`, `code: 3`)**: Su función es exclusiva, fija e inmutable: invoca directamente el motor STT hacia el modelo de IA configurado (`ai().onTrigger(code)`). No viaja como evento de telemetría táctil y nunca es interceptado por `TouchGestureManager`.
  - **Sensores Táctiles de las Patillas (`sync_glass_event` -> `key_event`)**: Captura las interacciones táctiles en las varillas de las gafas a través de todos los emisores de hardware:
    - `sender: 1`: Sensor táctil capacitivo de la patilla principal/derecha.
    - `sender: 2`: Sensor táctil capacitivo de la patilla secundaria/izquierda.
    - `sender: 4`: Controlador virtual / phonepad del launcher Flyme XR.
- **Enrutamiento Dual de Toques (Protocolo RFCOMM + Bluetooth AVRCP)**:
  - **Capa de Protocolo StarryNet (`InboundRouter`)**: Analiza eventos entrantes desde `event_tracking`, `sync_glass_event`, `phonepad`, `trackpad`. Extrae `key_code` de `_event_attr_value_` e ignora liberaciones (`down_or_up: 0`) para evitar dobles disparos, procesando todos los emisores táctiles (1, 2, 4). Filtra telemetría no táctil (`suspend_stats`, `iot_screen_status_change`, `iot_voice_wakeup`, `iot_voice_quit`).
    - **Filtrado Antirruido de Micro-Swipes Parásitos**: Al apoyar el dedo sobre la patilla capacitiva, el hardware frecuentemente genera micro-desplazamientos de fricción reportando un swipe (`206` o `207`) junto con un toque (`210`) en el mismo milisegundo (`key_event_time`). `InboundRouter.dispatchGestureBatch` detecta y suprime automáticamente los micro-swipes que ocurran a `<= 50ms` de un toque intencional del mismo sensor.
    - **Deduplicación de Rebotes en Lote y Consolidación Sender 2**: Elimina rebotes de contacto capacitivo idénticos consecutivos con el mismo timestamp de hardware. En la patilla secundaria (`sender: 2`), consolida la pareja `200` (down) y `203` (up) en un único toque, evitando dobles disparos lógicos por un único contacto físico.
    - **Síntesis Directa de `DOUBLE_TAP` en Lote**: Si el buffer de telemetría de las gafas agrupa dos toques `TAP` con diferencia de hardware timestamp entre 60ms y 500ms, promueve los eventos inmediatamente a `DOUBLE_TAP`, neutralizando la latencia de entrega de BLE.
    - **Ordenamiento por Prioridad en Lotes Simultáneos**: Si un lote contiene múltiples gestos en la misma ventana temporal, se procesan respetando la jerarquía de intención: `DOUBLE_TAP` > `TRIPLE_TAP` > `TAP` > `LONG_PRESS` > `SWIPE`.
  - **Capa Bluetooth AVRCP (`MyvuService.MediaSession`)**: Alberga una `MediaSession` activa que intercepta eventos de hardware transmitidos por el perfil de audio clásico (`BluetoothHeadset` / AVRCP) de las gafas (`KEYCODE_HEADSETHOOK`, `KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS`, `KEYCODE_MEDIA_FAST_FORWARD`, `KEYCODE_MEDIA_REWIND`, `KEYCODE_VOICE_ASSIST`), distinguiendo pulsaciones simples y dobles para enrutarlas a `TouchGestureManager`.
  - **Mapeo Unificado (`GlassGesture.fromCode`)**: Asocia keycodes estándar y nativos Flyme XR:
    - `1, 23` (DPAD_CENTER), `66` (ENTER), `79` (HEADSETHOOK), `85` (PLAY_PAUSE), `96` (BUTTON_A), `200, 203, 210` (Flyme Temple/Phonepad Tap) -> `TAP`.
    - `2, 202, 211` (Flyme Temple/Phonepad Double Tap) -> `DOUBLE_TAP`.
    - `3` -> `TRIPLE_TAP`.
    - `4, 212` (Flyme Temple/Phonepad Long Press), `219, 231` (VOICE_ASSIST) -> `LONG_PRESS`.
    - `5, 19` (DPAD_UP), `22` (DPAD_RIGHT), `87` (NEXT), `90` (FAST_FORWARD), `92` (PAGE_UP), `201, 206` (Flyme Temple Swipe Forward) -> `SWIPE_FORWARD`.
    - `6, 20` (DPAD_DOWN), `21` (DPAD_LEFT), `88` (PREV), `89` (REWIND), `93` (PAGE_DOWN), `207, 237` (Flyme Temple Swipe Backward) -> `SWIPE_BACKWARD`.
- **Despacho Personalizable, Debounce Inteligente y Sintetizador de Gestos (`TouchGestureManager`)**:
  - `ConnectionManager` y `GlassesEventHandler` enrutan los toques de patilla detectados a través de `TouchGestureManager.handleGesture()`, pasando el timestamp de hardware (`eventTime`) extraído de la telemetría Flyme XR (`_event_time_`, `key_event_time`).
  - **Sincronización con Reloj de Hardware de las Gafas**: Si los toques sufren retrasos de transmisión por reconexión o buffer Bluetooth tras desuspensión, `TouchGestureManager` calcula el intervalo real usando los timestamps físicos de las gafas (`Math.abs(eventTime - lastTapEventTime)`), eliminando falsos negativos por jitter de red.
  - **Inmunidad de Debounce para Acciones Nulas (`NONE`)**: Los gestos sin acción configurada o no asignados no actualizan la marca de tiempo de debounce, impidiendo que roces accidentales bloqueen toques intencionales.
  - **Debounce Optimizado (200ms)**: Reducido de 350ms a 200ms para evitar pérdida de toques o gestos sucesivos legítimos.
  - **Protección contra Rebotes Eléctricos (< 30ms)**: Si llega un `TAP` dentro de un intervalo menor a 30ms respecto al anterior, se descarta como rebote eléctrico sin sobreescribir `lastTapTime`, manteniendo intacta la ventana de detección para el segundo toque real.
  - **Sintetizador Software Multi-Toque Ampliado (Doble y Triple Toque entre Paquetes)**:
    - **Doble Toque (30ms a 1100ms)**: Cuando las gafas emiten eventos `TAP` individuales en paquetes separados, `TouchGestureManager` acumula los toques y promueve automáticamente a `DOUBLE_TAP` (por defecto `LAUNCH_GEMINI`), tolerando una ventana extendida de hasta 1.1s para toques pausados y naturales.
    - **Triple Toque (hasta 1350ms)**: Soporte completo para sintetizar `TRIPLE_TAP` a partir de 3 toques consecutivos que viajen en paquetes Bluetooth separados, promoviendo de inmediato a la acción asignada (por defecto `LAUNCH_PHONE_ASSISTANT`).
  - Respeta las preferencias del usuario para cada gesto: `TAP`, `DOUBLE_TAP`, `TRIPLE_TAP`, `SWIPE_FORWARD`, `SWIPE_BACKWARD`, `LONG_PRESS`.
  - **Lanzamiento Universal de Apps (`app:<package_name>`)**: Permite vincular cualquier gesto con aplicaciones instaladas en el teléfono móvil (ej. Spotify, WhatsApp, Cámara, YouTube). Al detectarse el gesto, `LockScreenHelper.wakeUpScreen()` enciende la pantalla, `SendTrampolineActivity` descarta el keyguard y lanza la app al frente, notificando en el HUD de las gafas (*"Abriendo [App]..."*).
  - **Integración con App Gemini y Desbloqueo (`LAUNCH_GEMINI`)**: Al ejecutarse mediante gesto de patilla (doble toque por defecto), enciende la pantalla con brillo completo (`PowerManager.WakeLock`), descarta el bloqueo/keyguard mediante `SendTrampolineActivity.launchWithKeyguardDismiss` y lanza de forma directa y explícita la app oficial de Gemini (`com.google.android.apps.bard`), eliminando interferencias de `googlequicksearchbox` (Google App) o colisiones de `KEYCODE_VOICE_ASSIST`.
  - **Enrutamiento Forzado de Micrófono Bluetooth SCO de Gafas**: Activado por defecto (`Prefs.isGeminiForceScoEnabled = true`). Configura el dispositivo de comunicación a Bluetooth SCO (`setCommunicationDevice` / `startBluetoothSco`) para capturar la voz del usuario directamente a través de los micrófonos de la montura MYVU.
  - **Activación de Micrófono Robusta y Aislamiento de Widgets en Accesibilidad**: `AutoSendAccessibilityService` orquesta la pulsación del micrófono mediante `findAndClickGeminiMicButton`, aceptando ventanas reales de la app de Gemini (`com.google.android.apps.bard` y el renderer `com.google.android.googlequicksearchbox` con `isGeminiAppWindow`) pero descartando de forma estricta launchers y widgets de búsqueda del escritorio (`search_widget`, `ghost_voice`, etc.). Cuenta con el permiso nativo `android:canPerformGestures="true"` en `accessibility_service_config.xml` para permitir gestos de inyección táctil. El algoritmo de búsqueda BFS encola todos los nodos hijos antes de cualquier filtro de exclusión de contenedores, evitando podas erróneas del subárbol de Compose. Cuando se detecta el botón de micrófono o Live, se ejecuta un **doble despacho (Dual-Action Click)**: `performAction(ACTION_CLICK)` y de forma complementaria `dispatchGesture` simulando un toque físico en el centro geométrico del botón con callback y log de confirmación. Libera el canal SCO tras 8.5s para restaurar A2DP estéreo y oír la respuesta en las gafas.
  - **Modo Conversación Continua para Gemini Live (`LAUNCH_GEMINI_LIVE`)**: Acción configurable en Ajustes (`gemini_live`). Lanza la aplicación de Gemini (`com.google.android.apps.bard`) sobre la pantalla desbloqueada sin colisión de teclas de medios, mantiene la pantalla despierta con wakelock de 60s, orquesta mediante `AutoSendAccessibilityService` la pulsación del botón de onda / conversación de Gemini Live (*waveform / "open gemini live"*) con BFS sin podas, doble despacho (Compose Action + DispatchGesture) e inmunidad frente a widgets del launcher, y **mantiene el canal SCO permanentemente abierto** durante toda la conversación interactiva sin cortes prematuros.
  - **Asistente del Teléfono Separado (`LAUNCH_PHONE_ASSISTANT`)**: Acción independiente que sí emite `KEYCODE_VOICE_ASSIST` y `ACTION_VOICE_COMMAND` para invocar al asistente predeterminado del sistema operativo (Google Assistant / Bixby).
  - Acciones nativas adicionales: `LAUNCH_LOCAL_AI`, `LAUNCH_PHONE_ASSISTANT`, `MEDIA_PLAY_PAUSE`, `MEDIA_NEXT`, `MEDIA_PREV`, `WEATHER_SYNC`, `ZEN_MODE`, `TOGGLE_MIRROR`, `OPEN_TELEPROMPTER` o `NONE`.
- **Control de Reenvío del Launcher**:
  - Al cambiar cualquier preferencia en `SettingsActivity`, se envía de inmediato `SystemSettings.setMusicTpControl(true)` a las gafas para garantizar que el launcher FlymeAR reenvíe todos los eventos táctiles en lugar de consumirlos localmente.
- **Selector de Apps en Ajustes**:
  - `SettingsActivity.showAppPickerDialog()` permite seleccionar interactivamente cualquier app del dispositivo y mapearla al gesto táctil deseado.



---

## 5. Motor Modular de Habilidades (Skills Engine) y Tool Calling

El subsistema en `com.myvu.client.skills` permite añadir funcionalidades al dispositivo y gafas de forma modular:

1. **Definición de Skill**: Cada habilidad se declara en `app/src/main/assets/skills/built-in/<skill-name>/SKILL.md` con parámetros tipados.
2. **`SkillToolConverter`**: Transforma dinámicamente las habilidades registradas en esquemas JSON Schema de herramientas (**OpenAI / LiteLLM Tool Definitions**) con sus propiedades y tipos requeridos.
3. **`SkillManager` & `SkillRegistry`**: Mapea identificadores normalizados (`call_contact` <-> `call-contact`) y expone los ejecutores nativos (`SkillHandler`).
4. **`BaseSkillHandler`**: Expone métodos para interactuar con el HUD (`renderCard()`, `showToast()`, `streamText()`).

### Categorías de Habilidades
- **Voz y Asistencia**: `ai-voice-recorder`, `gemini-live-assistant`, `smart-translate-hud`.
- **Productividad**: `create-note`, `create-reminder`, `calendar-events`, `smart-agenda-planner`.
- **Comunicación**: `call-contact`, `send-whatsapp`, `send-telegram`, `unread-notifications`.
- **Información Rápida**: `weather-forecast`, `hud-navigation`, `google-search`, `currency-convert`.

### 5.1 Control de Medios y Aplicaciones Externas (`com.myvu.client.media.MediaPlaybackHelper`)
- **Doble Capa de Control de Transporte**:
  - **`MediaSessionManager` + `MediaController`**: Aprovecha el permiso de escucha de notificaciones de `MirrorNotificationListener` para interactuar directamente con la sesión activa de reproducción. Soporta comandos directos (`play`, `pause`, `skipToNext`, `skipToPrevious`, `stop`) y lectura de metadatos (`METADATA_KEY_TITLE`, `METADATA_KEY_ARTIST`) para responder en HUD y voz a *"¿Qué canción está sonando?"*.
  - **`AudioManager.dispatchMediaKeyEvent`**: Fallback seguro mediante keycodes multimedia del sistema (`KEYCODE_MEDIA_PLAY`, `KEYCODE_MEDIA_PAUSE`, etc.).
- **Soporte de Aplicaciones Libres y Comerciales**:
  - **NewPipe y forks (Tubular, BraveNewPipe)**: Búsqueda mediante `ACTION_SEARCH` y reproducción por deep link interceptable de YouTube.
  - **OpenTune y clientes YouTube Music (InnerTune, RiMusic, ViMusic)**: Búsqueda y reproducción mediante `INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` y MediaSession.
  - **Spotify, YouTube Music, YouTube, VLC, Deezer**: Integración nativa con URIs de búsqueda y paquetes declarados en `<queries>`.
- **Ejecución con Pantalla Bloqueada en Bolsillo**:
  - Uso de `SendTrampolineActivity` y `LockScreenHelper` para despachar intents multimedia sin ser bloqueados por las restricciones en segundo plano de Android.
- **Fast-Paths en `VoiceActionRouter` y Skill Agéntica**:
  - Respuestas en <5ms para órdenes de voz frecuentes (*"reproduce [canción] en NewPipe"*, *"busca [video] en OpenTune"*, *"salta la canción"*, *"qué canción suena"*).
  - Habilidad `app-media-control` expuesta a Gemini/LiteLLM con esquemas JSON de OpenAPI para invocación agéntica autónoma.

---

## 6. Integración de Inteligencia Artificial (Gemini + LiteLLM & On-Device)

- **Gemini vía LiteLLM Proxy con Native Tool Calling**:
  - `LocalAiClient` se comunica con el proxy LiteLLM (`/v1/chat/completions`) soportando Function Calling nativo de Gemini.
  - **`AgenticToolExecutor`**: Orquesta el bucle autónomo **ReAct**:
    1. Envía el mensaje del usuario junto con las definiciones de herramientas (`tools: [...]`).
    2. Si Gemini solicita invocar una o varias herramientas (`tool_calls`), la aplicación ejecuta los handlers correspondientes en segundo plano.
    3. Retorna las observaciones a Gemini con el rol `tool` (`tool_call_id`).
    4. Gemini sintetiza la respuesta informada final optimizada para el display HUD de las gafas AR (1-2 oraciones claras).
  - **Structured Outputs**: Salida JSON garantizada (`response_format: {"type": "json_object"}`) en `NoteAiProcessor` y `MeetingAiProcessor` para extracción sin fallos de regex.
- **Motor Local / Privado (`LocalAiClient`)**:
  - Inferencia mediante servidor local u OpenAI-compatible endpoint (LiteLLM, Ollama, vLLM) en la red local o dispositivo con soporte completo de Function Calling.
- **Motor Nube Conversacional (Gemini Live / GeminiClient)**:
  - Streaming conversacional de baja latencia con Gemini Live o API REST de Gemini.

---

## 7. Persistencia y Almacenamiento

- **Room Database (`AppDatabase`)**:
  - `NoteEntity` y `ReminderEntity` con soporte para índices de texto y timestamps de activación.
  - `NoteRepository` y `ReminderRepository` como fuentes de verdad para la UI y el motor de sincronización.
- **AlarmManager**:
  - Alarmas exactas (`SCHEDULE_EXACT_ALARM`) para activar recordatorios que se proyectan directamente en el HUD cuando expira el temporizador.

---

---

## 8. Gestión del Ciclo de Vida, Desconexión y Ahorro de Batería

- **Desconexión Limpia (`stopConnection`)**:
  - Al presionar **Disconnect** en `ConnectActivity`, se fija `Prefs.autoReconnectEnabled(context, false)`.
  - Se invoca `ServiceWatchdogReceiver.cancelWatchdog(context)` para cancelar la alarma periódica de 15 minutos en `AlarmManager`.
  - Se envía `ACTION_STOP` a `MyvuService`, retirando la notificación persistente y finalizando el servicio foreground (`stopSelf()`).
  - `ConnectionManager.stop()` desactiva todos los temporizadores de reintento (`cancelReconnect()`), libera GATT (`ble.close()`), RFCOMM, sincronización de clima y navegación, fijando el estado en `IDLE`.
- **Protección contra Reactivaciones Fantasma**:
  - `ServiceKeepAliveHelper.ensureServiceRunning`: aborta de inmediato si `autoReconnectEnabled` es false.
  - `ServiceWatchdogReceiver.onReceive`: cancela la alarma y omite cualquier acción si `autoReconnectEnabled` es false.
  - `BootReceiver`: omite reactivar el servicio tras eventos del sistema (`ACTION_USER_PRESENT`, `POWER_CONNECTED`) si el usuario se desconectó voluntariamente.
  - `ConnectionManager`: en `onDisconnected()` y `fail()`, comprueba `userStopped` para no cambiar a `FAILED` ni programar reintentos en bucle cuando el usuario detuvo la conexión.
- **Optimización Integral de Batería y Recursos (Gafas y Teléfono)**:
  - **Diálogo Continuo / Escucha Activa (`isContinuousDialogueEnable`)**: Deshabilitado por defecto (`continuous_dialogue_enabled = false`). En el firmware FlymeAR, este flag mantenía el DSP de audio y el circuito de micrófono en escucha continua, causando drenajes acelerados de ~16.5% por hora.
  - **Activación por Voz / Wake Word (`isLowPowerWakeupEnable`)**: Deshabilitado por defecto (`voice_wakeup_enabled = false`).
  - **Filtro Estricto en `InitBurst`**: `InitBurst.load()` descarta tramas con `com.upuphone.ai.assistant` e `isContinuousDialogueEnable` de `captured_init.txt`, impidiendo que reconexiones del relay RFCOMM sobreescriban los ajustes de bajo consumo.
  - **Caché Inteligente de Batería**: Las gafas reportan telemetría espontánea vía `sync_glass_battery_info`. `ConnectionManager.queryBatteryInfo()` evita emitir `get_device_info` si la batería ya es conocida y tiene menos de 15 minutos, suprimiendo despertares del procesador de las gafas.
  - **Heartbeat BLE Adaptativo con Coalescencia**: `BleHeartbeat` opera a 20s en reposo (en lugar de 10s ciegos) y pospone el pulso ante tráfico real en los canales internos/externos de `BleTransport`.
  - **Sensores de Salud de Bajo Consumo (`HealthService`)**: Uso de `SENSOR_DELAY_NORMAL` con batching de hardware de 60 segundos (`maxReportLatencyUs = 60_000_000`) y supresión de `STEP_DETECTOR` redundante cuando `STEP_COUNTER` está disponible, permitiendo al SoC del celular entrar en reposo profundo (*deep sleep*).
  - **Watchdog Compatible con Android Doze Mode**: `ServiceWatchdogReceiver` utiliza `AlarmManager.ELAPSED_REALTIME` (sin WAKEUP), evitando despertar al dispositivo en reposo prolongado cuando el servicio en primer plano ya está corriendo saludablemente.
  - **Backoff Exponencial en Reconexión RFCOMM (`RelaySupervisor`)**: Evita tormentas de reintentos consecutivos tras desconexiones de socket SPP, permitiendo a la pila Bluetooth de las gafas reciclarse limpiamente.
  - **Sincronización Dinámica en Caliente**: Modificaciones en `SettingsActivity` despachan en tiempo real `AiProtocol.assistantConfig()` a las gafas sin reiniciar el enlace Bluetooth.

---

## 9. Entorno de Compilación y Toolchain

- **JDK del Host / Gradle Daemon**: OpenJDK 25 (`/usr/lib/jvm/java-25-openjdk-amd64`), configurado en `gradle.properties` (`org.gradle.java.home`).
- **Gradle**: 8.14.3 (con wrapper oficial `./gradlew`).
- **Kotlin Gradle Plugin**: 2.1.10 con soporte de bytecode Java 21 (`jvmTarget = "21"`).
- **Compatibilidad Android (D8 / Desugar)**: `JavaVersion.VERSION_21` para `compileOptions`.

---

## 10. Asistente Cotidiano, Automatizaciones y Segundo Cerebro (`com.myvu.client.ai`)

Para delegación y automatización cotidiana con mínima fricción cognitiva:

### 10.1 Daily Briefing Ejecutivo (`DailyBriefingService`)
- Síntesis verbal (TTS) y proyección visual HUD en <5ms sin invocar modelos LLM:
  - Saludo adaptado según la hora del día.
  - Clima meteorológico local inmediato (`WeatherSync.lastSummary`).
  - Próximos compromisos y reuniones del día (`CalendarService.getEvents`).
  - Conteo y detalles prioritarios de tareas pendientes (`TodoRepository`).
  - Avisos y mensajes VIP no leídos (`MirrorNotificationListener`).
  - Nivel de batería actual de las gafas AR.

### 10.2 Modos Contextuales y Macros de Rutina (`RoutineManager`)
- Conmutación orquestada del estado del sistema con una sola orden vocal:
  - **Modo Reunión**: `SystemSettings.setZenMode(true)` en gafas, celular en `RINGER_MODE_VIBRATE`. Desactivación restaura `ZenMode(false)` y `RINGER_MODE_NORMAL`.
  - **Modo Conducción**: Brillo de display al máximo (`setBrightness(3)`), audio manos libres preparado.
  - **Modo Gimnasio**: Conteo de pasos y métricas de salud (`HealthService`), feedback en audio/HUD.
  - **Modo Noche**: Brillo mínimo de pantalla (`setBrightness(1)`), celular en silencio absoluto (`RINGER_MODE_SILENT`), reporte preventivo de carga de batería.

### 10.3 Memoria Espacial y Parking (`SpatialMemoryManager`)
- Registro de coordenadas de estacionamiento con timestamp y nota en `Prefs`.
- Consulta con cálculo síncrono de distancia (metros/kilómetros) y rumbo cardinal de navegación (`bearingToCardinal`: Norte, Noreste, etc.) utilizando `LocationManager` y `FusedLocationProviderClient`.

### 10.4 Listas de Compras y Checklists Manos Libres
- Fast-paths en `VoiceActionRouter` para agregar productos (`createTodo(title, listName = "Compras")`), consultar pendientes y tachar ítems comprados (`markCompletedByTitle`) en Room SQLite sin interacción visual.

### 10.5 Despacho Rápido de Ubicación GPS
- Obtención inmediata de coordenadas del usuario y generación de URL de Google Maps despachada por WhatsApp o Telegram mediante `PhoneActionExecutor.sendLocationToContact(contact, app)` con encendido de pantalla asistido (`LockScreenHelper.wakeUpScreen`).
