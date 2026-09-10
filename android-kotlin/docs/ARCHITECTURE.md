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
   │ - AI Engine (MediaPipe)     │ - Material 3 Activities     │
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

### 4.3 `AutoSendAccessibilityService`
- Permite acciones de accesibilidad para automatizar el envío de mensajes de texto en aplicaciones como WhatsApp o Telegram sin requerir manipulación manual del dispositivo.

### 4.4 Gestión de Audio Bluetooth Clásico (`AudioProfiles` & `ConnectionManager`)
- **Doble Enlace (BLE + Classic Bluetooth)**: Las gafas Myvu utilizan BLE para el canal de telemetría/control y Bluetooth clásico (BR/EDR) para audio (HFP para llamadas/micrófono y A2DP para salida estéreo).
- **Auto-conexión Proactiva**: Al pulsar "Connect" y afianzarse el enlace BLE (`onReady` y `onSessionReady`), `ConnectionManager` invoca `AudioProfiles.connect()`.
- **Resolución de MAC Dual**: Mediante `resolveTargetDevice`, busca el dispositivo clásico emparejado en Android con nombre conteniendo `"MYVU"` o coincidencia de dirección MAC.
- **Enlace de Proxies y Políticas**: Realiza binding asíncrono con `BluetoothProfile.HEADSET` y `BluetoothProfile.A2DP`, encola la conexión pendiente si los proxies aún no están vinculados, y aplica por reflexión `setConnectionPolicy(device, 100)` y `setPriority(device, 1000)` para forzar la reconexión de audio en el subsistema Bluetooth del sistema operativo.


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
- **Motor Local Offline (MediaPipe Tasks GenAI)**:
  - Inferencia local de respaldo en el dispositivo para comandos y respuestas cuando no hay conexión.
- **Motor Nube Conversacional (Gemini Live)**:
  - Streaming de audio bidireccional de baja latencia con Gemini Live.

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
- **Optimización de Batería en las Gafas (Escucha Activa y Wake Word)**:
  - **Diálogo Continuo / Escucha Activa (`isContinuousDialogueEnable`)**: Deshabilitado por defecto (`continuous_dialogue_enabled = false`). En el firmware FlymeAR, este flag mantenía el DSP de audio y el circuito de micrófono en escucha continua, causando drenajes acelerados de ~16.5% por hora.
  - **Activación por Voz / Wake Word (`isLowPowerWakeupEnable`)**: Deshabilitado por defecto (`voice_wakeup_enabled = false`).
  - **Sincronización Dinámica en Caliente**: Modificaciones en `SettingsActivity` despachan en tiempo real `AiProtocol.assistantConfig()` a las gafas sin reiniciar el enlace Bluetooth.

---

## 9. Entorno de Compilación y Toolchain

- **JDK del Host / Gradle Daemon**: OpenJDK 25 (`/usr/lib/jvm/java-25-openjdk-amd64`), configurado en `gradle.properties` (`org.gradle.java.home`).
- **Gradle**: 8.14.3 (con wrapper oficial `./gradlew`).
- **Kotlin Gradle Plugin**: 2.1.10 con soporte de bytecode Java 21 (`jvmTarget = "21"`).
- **Compatibilidad Android (D8 / Desugar)**: `JavaVersion.VERSION_21` para `compileOptions`.
