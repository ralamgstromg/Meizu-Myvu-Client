# Plan de Implementación: Transformación a Plataforma Agente Universal para Dispositivos Bluetooth

## 1. Visión General y Objetivos
Transformar la aplicación Android (Kotlin) de un cliente exclusivo para gafas Meizu Myvu AR a una **Plataforma Agente Universal de Inteligencia Artificial para Todo Tipo de Dispositivos Bluetooth** (Gafas AR, Auriculares/Audífonos Bluetooth, Wearables y Dispositivos Genéricos).

La aplicación situará al **Chat del Agente como la Interfaz Principal y Home Launcher**, ofreciendo gestión multi-dispositivo, configuraciones específicas por tipo de hardware, captura de gestos multimedia para auriculares (1, 2 y 3 toques) y orquestación unificada de IA (Gemini, Gemini Live, IA Local, Notas de Voz, Herramientas del Sistema y Llamadas/Mensajería).

---

## 2. Requerimientos Clave

1. **Detección y Adición de Dispositivos Bluetooth**:
   - Escaneo y emparejamiento tanto de dispositivos **Bluetooth Clásico (Audio/A2DP/HFP/SPP)** como **Bluetooth Low Energy (BLE)**.
   - Clasificación automática por tipo de dispositivo según clase Bluetooth (`BluetoothClass.Device`) y servicios anunciados:
     - `SMART_GLASSES`: Gafas Meizu Myvu (y similares con HUD y protocolo de tramas).
     - `HEADPHONES`: Auriculares, audífonos TWS, diademas y altavoces manos libres.
     - `GENERIC_BLUETOOTH`: Wearables, controles remotos y otros periféricos.
2. **Configuración Aislada por Dispositivo**:
   - Base de datos local persistente (`bluetooth_devices`) que almacena el perfil individual de cada dispositivo emparejado/registrado.
   - Cada tipo de dispositivo tiene su propia interfaz y conjunto de opciones:
     - **Gafas AR**: Brillo de pantalla HUD, teleprompter, gestos de patilla capacitiva, modo zen, protocolo SPP/BLE.
     - **Auriculares Bluetooth**: Configuración de gestos táctiles/botones (1 toque, 2 toques, 3 toques, pulsación larga), síntesis de voz (TTS) para respuestas habladas en la oreja, lectura automática de notificaciones entrantes y selección de micrófono.
3. **Funcionalidades de IA Universales**:
   - Todas las habilidades (Skills) de IA, notas inteligentes, recordatorios, llamadas manos libres, envío de WhatsApp/Telegram/SMS y consultas a Gemini/LiteLLM disponibles para **todos** los dispositivos.
   - Enrutamiento inteligente de salida:
     - Si la interacción proviene de Gafas: respuesta proyectada en HUD + audio.
     - Si la interacción proviene de Auriculares: respuesta leída por Text-To-Speech (TTS) directamente en los audífonos.
     - En ambos casos: la interacción queda registrada en tiempo real en la pantalla del Chat Principal.
4. **Gestos para Auriculares (1, 2, 3 Toques / Botones Multimedia)**:
   - Interceptación y conteo de toques en botones de auriculares vía `MediaSession` (`KEYCODE_HEADSETHOOK`, `KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS`, `KEYCODE_VOICE_ASSIST`).
   - Mapeo configurable por el usuario para cada auricular guardado:
     - **1 Toque**: Play/Pausa, Consulta rápida por voz al Agente, o Iniciar asistente.
     - **2 Toques**: Lanzar Gemini (App/Voz), Gemini Live (conversación continua), o Siguiente canción.
     - **3 Toques**: Asistente del Teléfono (Google/Bixby), Leer notificaciones no leídas, o Canción anterior.
     - **Pulsación Larga**: Crear Nota con IA, IA Local, o Modo Zen.
5. **Ajuste de la UI para Multi-Dispositivo**:
   - Selector y gestor visual de dispositivos (`DeviceManagerBottomSheet` o `DevicesActivity`): lista de dispositivos vinculados, estado de conexión (Conectado/Desconectado), nivel de batería, icono según tipo y botón de configuración específica.
   - Diálogo / Pantalla de escaneo y vinculación rápida de nuevos dispositivos Bluetooth cercanos.
   - Botón universal de acceso a "Dispositivos" (`btnDevices`) presente en la cabecera de todas las interfaces clave.
6. **El Chat como Interfaz Principal (Home Screen)**:
   - `ChatActivity` pasa a ser la **Actividad Lanzadora Principal** (`android.intent.action.MAIN`, `android.intent.category.LAUNCHER`) en `AndroidManifest.xml`.
   - Cabecera del Chat con indicador dinámico de dispositivo activo (`[🎧 Sony WH-1000XM5]`, `[👓 MYVU AR]`, `[+ Conectar Dispositivo]`).
   - El Agente gestiona las peticiones de texto, voz, imágenes y comandos del sistema, proyectando tarjetas y resúmenes en el stream de mensajes.

---

## 3. Arquitectura del Sistema Multi-Dispositivo

```
                      ┌──────────────────────────────────────────────┐
                      │    Main Chat & Agent Home (ChatActivity)     │
                      └──────────────┬───────────────────────────────┘
                                     │
           ┌─────────────────────────┴─────────────────────────┐
           ▼                                                   ▼
┌──────────────────────────────┐              ┌──────────────────────────────┐
│   Dispositivos Bluetooth     │              │    Motor de IA y Skills      │
│   - BluetoothDeviceEntity    │              │    - Gemini / Gemini Live    │
│   - BluetoothDeviceManager   │              │    - LiteLLM Local AI        │
│   - BluetoothDeviceDao       │              │    - PhoneActionExecutor     │
└──────────────┬───────────────┘              │    - Skills Engine (30 tools)│
               │                              └──────────────┬───────────────┘
  ┌────────────┴─────────────┐                               │
  ▼                          ▼                               ▼
┌─────────────────────┐   ┌─────────────────────┐   ┌─────────────────────────┐
│ Smart Glasses (HUD) │   │ Headphones/Earbuds  │   │  Speech & Feedback Hub  │
│ - ConnectionManager │   │ - HeadphoneManager  │   │  - TextToSpeechHelper   │
│ - TouchGestureMgr   │   │ - MediaSession Hook │   │  - Bluetooth SCO Mic    │
│ - SettingsActivity  │   │ - HeadphoneSettings │   │  - HUD Card Formatter   │
└─────────────────────┘   └─────────────────────┘   └─────────────────────────┘
```

---

## 4. Fases de Implementación

### Fase 1: Capa de Datos y Modelos Multi-Dispositivo
- **Entidad `BluetoothDeviceEntity`** en Room (`AppDatabase`):
  - `macAddress`: String (PK)
  - `name`: String
  - `deviceType`: String (`SMART_GLASSES`, `HEADPHONES`, `GENERIC`)
  - `isConnected`: Boolean
  - `batteryLevel`: Int?
  - `lastConnectedTime`: Long
  - `tap1Action`: String (por defecto: `MEDIA_PLAY_PAUSE` o `VOICE_QUERY`)
  - `tap2Action`: String (por defecto: `LAUNCH_GEMINI`)
  - `tap3Action`: String (por defecto: `LAUNCH_PHONE_ASSISTANT`)
  - `longPressAction`: String (por defecto: `CREATE_AI_NOTE`)
  - `ttsEnabled`: Boolean (por defecto: true para auriculares)
  - `autoReadNotifications`: Boolean (por defecto: false)
- **`BluetoothDeviceDao`**:
  - `getAllDevicesFlow(): Flow<List<BluetoothDeviceEntity>>`
  - `getDevice(mac): BluetoothDeviceEntity?`
  - `insertOrUpdate(device: BluetoothDeviceEntity)`
  - `setConnectionState(mac: String, isConnected: Boolean)`
- Actualización de versión en `AppDatabase.kt` con migración destructiva controlada / segura.

### Fase 2: Gestor Universal de Dispositivos (`BluetoothDeviceManager`)
- Componente singleton `BluetoothDeviceManager`:
  - Enumerable de dispositivos emparejados con Android (`BluetoothAdapter.bondedDevices`).
  - Escáner dinámico para descubrir nuevos dispositivos (Clásico y BLE).
  - Clasificador heurístico de tipo de dispositivo basado en nombre y `BluetoothClass.Device`:
    - Gafas: contiene "MYVU", "GLASS", "AR", etc.
    - Auriculares: `AUDIO_VIDEO_HEADPHONES`, `AUDIO_VIDEO_WEARABLE_HEADSET`, `AUDIO_VIDEO_HANDSFREE`, "buds", "airpods", "wh-", "wf-", "headset", "freebuds", etc.
    - Genéricos: Otros dispositivos.
  - Escucha de eventos del sistema para conexión/desconexión (`ACTION_ACL_CONNECTED`, `ACTION_ACL_DISCONNECTED`, `BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED`, `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED`).
  - Sincronización con Room para mantener la lista actualizada en tiempo real.

### Fase 3: Controlador de Gestos y Audio para Auriculares (`HeadphoneGestureManager` + `TextToSpeechHelper`)
- **`HeadphoneGestureManager`**:
  - Integrado con la `MediaSession` en `MyvuService`: detecta ráfagas de pulsaciones de botones de auriculares (1 toque, 2 toques, 3 toques, pulsación larga) en cualquier auricular Bluetooth conectado.
  - Lee la configuración específica del auricular activo desde `BluetoothDeviceDao`.
  - Ejecuta la acción configurada:
    - Invocación a Gemini / Gemini Live (despertando la pantalla con `LockScreenHelper` y canal SCO abierto).
    - Asistente de teléfono.
    - Creación de nota por voz con transcripción e IA.
    - Lectura de notificaciones pendientes vía TTS.
    - Control multimedia (Play/Pause, Next, Prev).
- **`TextToSpeechHelper`**:
  - Motor Text-To-Speech nativo de Android configurado para síntesis en español (`es-CO` / `es-ES` / default).
  - Enrutamiento preferencial a dispositivos de audio Bluetooth conectados para feedback auditivo privado en auriculares.

### Fase 4: Chat como Actividad Principal y Selector Multi-Dispositivo
- **`AndroidManifest.xml`**:
  - Convertir `com.myvu.client.ui.chat.ChatActivity` en la actividad principal con `<intent-filter>` de `LAUNCHER`.
  - Mantener `ConnectActivity` como actividad secundaria para emparejamiento avanzado de gafas MYVU.
- **Top Bar Universal con Selector de Dispositivos en `activity_chat.xml`**:
  - Sustituir el botón de retroceso (`btnBackChat`) en el Chat por el botón / chip de estado de dispositivo activo (`btnDevicesHeader`), mostrando el icono del dispositivo (`ic_glasses` o `ic_headphones`), nombre y batería.
  - Al hacer clic, abre `DeviceManagementBottomSheet`.
- **`DeviceManagementBottomSheet` (Gestión de Dispositivos)**:
  - Lista de dispositivos vinculados con estado (conectado/desconectado).
  - Botón prominente *"Añadir Dispositivo Bluetooth"* (`+ Escanear y Añadir`).
  - Botón de *"Configurar"* que abre la interfaz correspondiente según el tipo:
    - Si es `SMART_GLASSES` $\rightarrow$ abre `SettingsActivity` (ajustes de display, HUD, patilla, etc.).
    - Si es `HEADPHONES` $\rightarrow$ abre `HeadphoneSettingsActivity`.
- **Botón Universal "Añadir Dispositivo" en Todas las Pantallas**:
  - Integrar el botón o acceso a dispositivos en las barras superiores de `NotesActivity`, `VoiceRecorderActivity`, `SettingsActivity` y `NotificationAppsActivity`.

### Fase 5: Pantalla de Configuración de Auriculares (`HeadphoneSettingsActivity`)
- Nueva actividad con diseño Material 3 obsidian:
  - Información del auricular: Nombre, MAC, tipo y estado de batería.
  - **Configuración de Gestos Táctiles**:
    - **1 Toque**: Selector desplegable con acciones disponibles (`Play/Pausa`, `Consulta al Agente`, `Ninguna`, etc.).
    - **2 Toques**: Selector desplegable (`Gemini Asistente`, `Gemini Live`, `Siguiente Canción`, etc.).
    - **3 Toques**: Selector desplegable (`Asistente del Teléfono`, `Leer Notificaciones`, `Canción Anterior`, etc.).
    - **Pulsación Larga**: Selector desplegable (`Crear Nota con IA`, `IA Local`, etc.).
  - **Opciones de Voz e IA**:
    - Switch para activar/desactivar respuestas habladas (TTS).
    - Switch para lectura automática de notificaciones entrantes.
    - Prueba de audio y voz del asistente.

### Fase 6: Pruebas, Verificación y Documentación
- Pruebas unitarias para `BluetoothDeviceManager`, clasificación de tipos de dispositivos y resolución de gestos en auriculares.
- Verificación de compilación completa con `rtk ./gradlew testDebugUnitTest`.
- Ejecución de `rtk codegraph sync`.
- Actualización completa de memoria y documentación: `docs/PROJECT_MEMORY.md`, `docs/ARCHITECTURE.md` y `README.md`.
