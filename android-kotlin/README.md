# Meizu Myvu Client — Android Kotlin

[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.1.10-blue.svg)](https://kotlinlang.org/)
[![Android API](https://img.shields.io/badge/API-26%2B%20%28Target%2035%29-green.svg)](https://developer.android.com/)
[![Material Design](https://img.shields.io/badge/Material-3-purple.svg)](https://m3.material.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Cliente complementario nativo en Android (Kotlin) para gafas de realidad aumentada (AR) **Meizu Myvu Smart Glasses**. Permite sincronización bidireccional mediante Bluetooth SPP y BLE, visualización de notificaciones en el HUD, teleprompter de navegación, reporte del clima, grabación de voz, gestión de notas/recordatorios y asistente de inteligencia artificial (local con endpoints compatibles OpenAI/LiteLLM y en la nube con Gemini Live).

---

## 🌟 Características Principales

- **Conectividad Dual Bluetooth**:
  - Enlace de alta velocidad **Bluetooth SPP (RFCOMM)** para transmisión de paquetes binarios de pantalla HUD y audio.
  - Soporte **BLE (Bluetooth Low Energy)** para telemetría de baja energía (batería, estados de conexión).
  - Servicio en primer plano persistente (`MyvuService`) con reconexión automática resiliente.

- **Proyección en el HUD de las Gafas**:
  - **Espejo de Notificaciones**: Notificaciones de Android enviadas en tiempo real mediante `MirrorNotificationListener`.
  - **Teleprompter y Navegación**: Indicaciones paso a paso en pantalla HUD.
  - **Clima y Hora**: Sincronización automática de condiciones meteorológicas locales.

- **Inteligencia Artificial y Capacidad Agéntica**:
  - **Gemini + LiteLLM con Native Tool Calling**: Ejecución autónoma de herramientas del sistema mediante Function Calling directo en `/v1/chat/completions`.
  - **Bucle Agéntico ReAct**: Orquestador multi-paso (`AgenticToolExecutor`) para resolver tareas complejas consultando herramientas y sintetizando la respuesta para las gafas.
  - **Salidas Estructuradas (JSON Mode)**: Análisis 100% estricto de notas y reuniones con `response_format: json_object`.
  - **IA Local / Self-Hosted**: Inferencia local o en red privada mediante endpoints compatibles con OpenAI / LiteLLM (`LocalAiClient`).
  - **IA en la Nube**: Streaming conversacional con **Gemini Live**.
  - **Notas de Voz y Grabación**: Grabador integrado con análisis y transcripción (`VoiceRecorderActivity`).

- **Motor Modular de Habilidades (Skills Engine)**:
  - Sistema extensible de plugins definido por manifiestos `SKILL.md`.
  - 30 habilidades nativas expuestas dinámicamente como herramientas OpenAI/Gemini Schema (`SkillToolConverter`) listas para interactuar con el entorno (llamadas, calendario, WhatsApp, Telegram, calculadora, traducción, OCR, navegación HUD, etc.).

- **Botón Físico y Gestos Táctiles de Patillas ("Patas")**:
  - **Botón Físico de la Montura Inmutable**: Dedicado 100% y de forma fija al flujo de reconocimiento de voz **STT -> Modelo de IA configurado** (`ai().onTrigger(code)`), sin interceptación ni retrasos.
  - **Mapeo Personalizable de Patillas**: Soporte completo para todos los emisores táctiles de hardware (`key_event_sender`: 1, 2 y 4) y keycodes Flyme XR (210 Click/Tap, 211 Doble Tap, 212 Pulsación Larga, 206 Deslizar Adelante, 207 Deslizar Atrás; además de 200, 201, 202, 203, 237).
  - **Doble y Triple Toque Confiable con Síntesis Multi-Paquete (30-1100ms / 1350ms)**: Si el firmware de las gafas reporta toques simples (`210` o `200`) separados en ráfagas o paquetes distintos, el sistema utiliza marcas de tiempo de hardware (`_event_time_`) para sintetizar doble toque (`211`) o triple toque (`214`), tolerando demoras de transporte Bluetooth y suspensión profunda. Doble toque asignado por defecto a **Lanzar Gemini Asistente**.
  - **Deduplicación de Rebotes y Filtrado Antirruido**: Suprime micro-swipes parásitos simultáneos, consolida la pareja `200`+`203` de la patilla izquierda en un único toque, descarta rebotes eléctricos de contacto `< 30ms` sin alterar el acumulador y evita que acciones no configuradas (`NONE`) bloqueen toques posteriores por debounce.
  - **Lanzamiento Directo de Gemini con Activación de Micrófono y Protección contra Secuestro**: Despierta el teléfono con brillo total (`LockScreenHelper.wakeUpScreen`), descarta el keyguard y abre directamente la app oficial de Gemini (`com.google.android.apps.bard`) eliminando llamadas ambiguas al asistente clásico de Google (`googlequicksearchbox`). `AutoSendAccessibilityService` pulsa de forma autónoma el micrófono con gestos por coordenadas para Jetpack Compose, blindado contra falsos clics en widgets del escritorio.
  - **Modo Gemini Live (Conversación Continua)**: Configurable desde Ajustes (`gemini_live`). Lanza la app oficial de Gemini sobre la pantalla desbloqueada, preserva el canal de audio Bluetooth SCO permanentemente abierto durante la charla y orquesta la pulsación del botón *Live* mediante accesibilidad por coordenadas sin interferencias de la app de Google.
  - **Lanzador de Apps Instaladas**: Vincula cualquier gesto a cualquier app del teléfono (Spotify, WhatsApp, YouTube, Cámara) encendiendo la pantalla y quitando el bloqueo automáticamente.
  - Acciones del sistema: Gemini (App / Asistente), Gemini Live, Asistente del Teléfono, IA Local, Play/Pausa, Siguiente, Anterior, Modo Zen, Sincronización del Clima, Notificaciones y Teleprompter.
  - Sincronización dinámica de `set_music_tp_control_mode` para forzar reenvío de toques desde el launcher de las gafas.

- **Búsqueda Priorizada de Contactos y Fallback Semántico (`ContactHelper`)**:
  - **Prioridad Estricta al Nombre Secuencial**: Las búsquedas por voz priorizan el orden exacto de los nombres ingresados ($Q_0 \rightarrow Q_1$).
  - **Blindaje del Nombre de Pila**: Descarta de inmediato contactos que solo compartan el apellido (ej: `"Denis Castro"` para `"Matias Castro"`), previniendo desvíos accidentales de llamadas o mensajes.
  - **Fallback Semántico**: Mapeo inteligente de diminutivos y alias en español (`"mati"` $\leftrightarrow$ `"matias"`, `"juanca"` $\leftrightarrow$ `"juan carlos"`), parentescos familiares (`"papa"` $\leftrightarrow$ `"padre"`, `"hijo"`) y tolerancia fonética.

- **Rediseño Integral iOS Cupertino Pro Native & Modo Claro / Oscuro**:
  - **Identidad Gráfica y Launcher Adaptativo (`ic_launcher_hub`)**: Nuevo icono continuo squircle que fusiona el visor HUD holográfico AR, las ondas de sonido de auriculares, la estrella de asistencia neural de 4 puntas y la telemetría de wearables sobre gradiente Cupertino Azul/Índigo (`#007AFF` a `#5856D6`).
  - **Soporte Completo y Dinámico de Modo Claro / Modo Oscuro**: Paleta semántica estricta (`ios_system_bg`, `ios_card_bg`, `ios_label`, etc.) con reinflación instantánea sin retrasos mediante recreación limpia de ciclo de vida (`recreate()`), soporte nativo en `AndroidManifest.xml` (eliminado `|uiMode` de `configChanges`) y adaptación dinámica del contraste de la barra de estado y de navegación en `EdgeToEdgeHelper`.
  - **Companion Hub (`ChatActivity`)**:
    - Cabecera desahogada con título y subtítulo elípticos, estado en vivo y botones compactos sin colisiones ni recortes.
    - Carrusel horizontal de **Widgets de Wearables** con ancho ampliado de 205dp (Gafas AR con telemetría MicroOLED y Auriculares con gestos y monitoreo de batería).
    - Píldoras de acción rápida con insets normalizados (`android:insetTop="0dp"`, `android:insetBottom="0dp"`) que impiden desplazamientos y recortes de texto.
    - Stream de conversación estilo **iMessage** con esquinas asimétricas ergonómicas y avatar de IA.
    - Barra de entrada flotante con botones circulares para cámara, dictado por voz y envío.
    - Barra de navegación inferior **Cupertino de 5 pestañas** (Hub, Equipos, AR HUD, Traducir, Notas) con insets seguros que elevan la interfaz por encima de la barra de gestos de Android.

- **Plataforma Universal de Agente IA para Dispositivos Bluetooth**:
  - **Soporte Multi-Dispositivo**: Compatible con Gafas Inteligentes AR (MYVU), Auriculares / Audífonos Bluetooth (TWS, diadema, in-ear) y Wearables genéricos.
  - **Detección y Clasificación Automática (`BluetoothDeviceManager`)**: Reconoce y cataloga dispositivos emparejados en el sistema y permite escanear nuevos dispositivos Bluetooth en tiempo real.
  - **Catálogo Unificado de Acciones Comunes (`CommonDeviceActions`)**: 13 acciones transversales que cualquier dispositivo puede ejecutar (Lanzar Gemini, Gemini Live, Asistente de teléfono, notas de voz IA, leer notificaciones, Mi Día/Daily Briefing, teleprompter, control multimedia, etc.).
  - **Interfaces Propias y Diferenciadas de Configuración por Dispositivo**:
    - **Gafas AR (`GlassesSettingsActivity`)**: Control total del hardware propio de las gafas AR con persistencia dual en base de datos Room (`BluetoothDeviceEntity`) y `GlassesConfig`/`Prefs`:
      - Sincronización precisa de brillo HUD (niveles de hardware 1 a 5 con comando Flyme XR `set_brightness` en vivo y al guardar).
      - Volumen de audio de las gafas (0..15), Posición Standby de la pantalla (Centro, Superior, Inferior, Lateral), Tiempo de pantalla activa (3..60s) y Duración de visualización de notificaciones en el HUD (1..30s).
      - Modo de respuesta de IA exclusivo (Solo voz, Solo pantalla HUD, Voz y HUD).
      - Switches de escucha activa continua, activación por voz (Wake word) y enrutamiento forzado de micrófono SCO para Gemini.
      - Personalización profunda de gestos en las patillas táctiles ("patas": toque simple, doble toque, triple toque, deslizar adelante, deslizar atrás, pulsación larga) y comportamiento del **Botón de Acción de la montura** (1 toque corto muestra el HUD / Dashboard nativo de las gafas; mantener presionado invoca al Agente de IA configurado).
      - **Aislamiento Anticolisión de Botón Físico**: Desacoplamiento total entre el botón de la montura (`GlassGesture.ACTION_BUTTON`, keycodes `202`, `230` y `231`) y los sensores táctiles capacitivos. Cuenta con reclasificación del keycode `202` (emitido al pulsar 1 vez el botón de la montura) para desplegar el HUD nativo en vez de falsos disparos de doble toque/Google Assistant, consolidación de ruido mecánico `key_code: 200` (Down), ventana de supresión de 1200ms (`PHYSICAL_BUTTON_SUPPRESSION_MS`) y deduplicación bidireccional contra triggers concurrentes del firmware Flyme XR, eliminando por completo la ejecución de dos acciones consecutivas o disparos involuntarios de asistentes de terceros.
      - Guardado y lectura 100% confiables: restauración exacta de spinners y controles al reabrir la pantalla gracias al mapeo canónico de acciones y creación automática de entidades en Room.
    - **Auriculares Bluetooth (`HeadphoneSettingsActivity`)**: Personalización de 1, 2 y 3 toques, pulsación larga, selección entre el **Agente de Voz Aura (STT + API)** o asistentes externos (Gemini, Gemini Live, Asistente de teléfono), síntesis de voz (TTS), lectura automática de notificaciones y **toggle independiente de escucha activa / diálogo continuo**. Aislado 100% de la entidad de las gafas en Room DB, con fallback a dispositivos de audio del sistema y persistencia inmediata.
    - **Ajustes Globales Centralizados (`SettingsActivity`)**: Conserva de forma estrictamente limpia los parámetros generales del sistema (modelos y endpoints de IA, STT, TTS, telemetría climática, copias de seguridad en la nube, perfil de usuario, logs y auto-bloqueo tras acciones). Todos los parámetros exclusivos del visor y hardware de las gafas fueron migrados a su interfaz propia.
  - **Interruptor Maestro de Servicios en Segundo Plano (`swMasterService` / `swSettingsMasterService`)**: Switch Material 3 presente tanto en el Dashboard de Gafas como en Ajustes Generales que permite activar o suspender globalmente la ejecución de servicios en segundo plano (`MyvuService`, alarmas de watchdog y bucles de reintento de conexión Bluetooth) con un solo toque, ahorrando batería y evitando consumos innecesarios cuando no se usen los dispositivos.
  - **Garantía de IA 100% Autónoma (Standalone)**: Todas las funcionalidades de Inteligencia Artificial (Chat Aura con modelos locales/cloud, Libreta de Notas IA, Grabadora de Reuniones, Recordatorios y Resumen del Día "Mi Día") funcionan de manera autónoma directamente en el teléfono sin requerir gafas ni audífonos Bluetooth conectados, contando con acceso directo en el Dashboard (`btnOpenAiChat`) y en la barra superior del Chat.
  - **Desconexión Total Unificada (`TotalDisconnectHelper`)**: Botón universal de desconexión completa en Dashboard, menú lateral y panel de dispositivos que inactiva al 100% todos los servicios en segundo plano (`MyvuService`), cancela reconexión automática y watchdogs, libera canales de audio SCO, apaga sensores de salud y marca todos los periféricos en Room DB como desconectados.
  - **Protección Activa contra Fugas de Batería y Conexiones**: **Escucha activa deshabilitada por defecto en todos los dispositivos** (`activeListeningEnabled = false`) para evitar drenaje continuo del micrófono y DSP (~16.5%/h) con control individual por dispositivo, **control inteligente del servidor SPP y supresión del bucle RFCOMM** (respeta `CMD_SPP_SERVER_REQUEST_STATE_CLOSE` de las gafas con umbral de estabilidad de 30s en `RelaySupervisor`, erradicando el drenaje severo de ~48%/h), **entrega inmediata de notificaciones con latencia <50ms** vía canal BLE cuando el relay no está activo, **VAD adaptativo dinámico y watchdog de silencio** que finalizan la elocución a 1.2s del cese de voz (evitando esperar 20s), supresión de re-bursts cíclicos de inicialización en RFCOMM, watchdog automático de 5 minutos en canales de audio Bluetooth SCO para Gemini Live, receptor de pantalla apagada (`ACTION_SCREEN_OFF`) que suspende radios y libera canales de audio, timeout de seguridad de 12 segundos para búsquedas Bluetooth y apagado limpio de ejecutores en segundo plano.
  - **Configuración Aislada y Persistente en Room (`BluetoothDeviceEntity`)**: Cada dispositivo guarda de manera independiente sus capacidades y asociaciones de gestos.
  - **Gestos Táctiles Personalizables para Auriculares (`HeadphoneGestureManager`)**: Mapea 1 toque, 2 toques, 3 toques y pulsación larga en auriculares Bluetooth para ejecutar:
    - **Agente de Voz Aura (STT + API)** con transcripción inmediata y respuesta hablada directa en el auricular vía TTS.
    - Asistente Gemini (overlay y dictado directo).
    - Gemini Live (conversación continua manos libres).
    - Asistente del Teléfono (Google Assistant / Bixby).
    - Crear notas de voz con transcripción e IA.
    - Resumen del Día ejecutivo (`DailyBriefingService`).
    - Lectura en voz alta por TTS de notificaciones pendientes (`MirrorNotificationListener`).
    - Controles multimedia (play/pausa, siguiente, anterior).
  - **Manejo Granular de Notificaciones por Dispositivo (`DeviceNotificationMode`)**: Cada dispositivo Bluetooth permite configurar de forma personalizada el canal de entrega de alertas entrantes:
    - **Visual (HUD)**: Se proyecta en la pantalla microLED de las gafas inteligentes (silencioso, sin interrumpir con voz).
    - **Sonora (TTS)**: Se lee en voz alta por síntesis de voz (`TextToSpeechHelper`) hacia los audífonos o altavoces.
    - **Ambos**: Visualización simultánea en HUD y lectura por voz TTS.
    - **Desactivadas**: Silencia completamente las alertas para ese dispositivo específico.
  - **Enrutamiento Inteligente Multi-Dispositivo (`MirrorNotificationListener`)**: Analiza los periféricos activos conectados; si las gafas tienen habilitadas notificaciones visuales las envía al HUD, y si los audífonos o gafas tienen habilitada la lectura por voz reproduce *"De [App]: [Título]. [Texto]"* en los auriculares, funcionando tanto en conjunto como de forma autónoma.
  - **Motor de Audio TTS en Español (`TextToSpeechHelper`)**: Síntesis nativa para lectura de notificaciones, confirmaciones de comandos y resúmenes ejecutivos a través del auricular.
  - **Indicadores de Batería Reales en Tiempo Real (`BluetoothDeviceManager` & `InboundRouter`)**:
    - Extracción continua de nivel de batería desde el hardware de las gafas MYVU (`get_device_info`, `sync_glass_battery_info`), broadcasts de Android (`ACTION_BATTERY_LEVEL_CHANGED`), eventos HFP AT `+IPHONEACCEV` para auriculares Bluetooth, y consulta reflexiva a la API nativa de Android (`device.getBatteryLevel()`).
    - Persistencia automática en Room DB y actualización reactiva en `ChatActivity`, `GlassesSettingsActivity`, `HeadphoneSettingsActivity` y `DeviceManagementBottomSheet` sin porcentajes ficticios ni simulados.
  - **Chat Central como Interfaz Principal (`ChatActivity`)**: La actividad de inicio (`LAUNCHER`) es el Chat de Inteligencia Artificial, proporcionando interacción continua con el agente Aura y acceso directo a todas las habilidades.
  - **Menú de la Barra Lateral (Sidebar Navigation Drawer)**: Accesible desde el botón de menú hamburguesa superior, conecta de forma inmediata con:
    - 💬 Chat IA (Principal)
    - 📝 Notas de IA y Tareas
    - 🎙️ Grabadora de Voz y Reuniones
    - 🎧 Dispositivos Bluetooth (Emparejados / Escaneo)
    - 👓 Gafas AR (Dashboard y HUD)
    - ⚙️ Ajustes de IA y Perfil
    - 🖲️ Control Trackpad
    - 🔔 Filtro de Notificaciones
    - 📋 Logs y Actividad Unificada (Gafas, Bluetooth, Teléfono, IA)
  - **Sistema Unificado de Registro de Actividad y Diagnóstico Visual (`ActivityLogActivity`)**:
    - **Diagnóstico Visual Inmediato de Errores**:
      - Tarjetas de error destacadas con fondo suave tintado (`ios_red_tint`), borde de acento en rojo brillante (`#FF3B30`), badge rojo e icono de alerta `ic_ios_error`.
      - Tarjetas de advertencia con tinte suave y borde ámbar (`#FF9500`) con icono `ic_ios_warning`.
      - Trazas de excepción colapsables: botón interactivo *"Ver detalles del error ⌄"* que expande y contrae el stack trace completo bajo demanda sin saturar la vista.
    - **Píldoras de Métricas de Salud en Cabecera (Segmented Diagnostics Bar)**:
      - Fila superior interactiva con contadores en tiempo real: `[Todos: N]`, `[🚨 Errores: X]`, `[⚠️ Avisos: Y]`, `[👓 Gafas: Z]` y `[🎧 Audio BT: W]`.
      - Un solo toque en la píldora de errores filtra al instante la lista para una resolución de incidencias en segundos.
    - **Badges Semánticos Squircle por Dispositivo**: Identificación cromática clara de la fuente del evento (`[👓 Gafas MYVU]` en azul, `[🎧 Dispositivo BT]` en púrpura, `[🤖 Asistente IA]` en azul, `[📱 Teléfono / Sistema]` en gris).
    - **Pantalla Independiente Multidispositivo**: Centraliza la telemetría en tiempo real no solo de las gafas Meizu MYVU AR, sino también de auriculares/dispositivos Bluetooth vinculados, actividades del sistema operativo móvil y operaciones de agentes IA (Aura/Gemini/Groq/TTS).
    - **Control Maestro en Vivo**: Interruptor superior (`swActivityLogging`) para activar o suspender en caliente la captura de logs (`Prefs.setLoggingEnabled`), vaciando el buffer al apagarse para proteger privacidad y recursos de memoria.
    - **Filtros por Origen y Búsqueda en Vivo**: Chips interactivos de origen y barra de búsqueda en tiempo real con limpieza rápida (`btnClearSearch`).
    - **Exportación y Auditoría (`FileProvider`)**: Botón de compartir con icono Apple que genera un informe estructurado `myvu_activity_log.txt` con marcas de tiempo precisas (`yyyy-MM-dd HH:mm:ss.SSS`), distribuible mediante la hoja de compartir nativa de Android con fallback automático a texto plano.
    - **Limpieza Segura**: Diálogo Material/Apple de confirmación antes de purgar el buffer histórico en memoria.
    - **Acceso Global**: Disponible desde el menú lateral (`nav_logs`), botón directo en la cabecera de `ConnectActivity` (`btnOpenActivityLog`) y sección de logging en `SettingsActivity`.

  - **Patrón de Diseño Apple (iOS 18 Human Interface Guidelines - Cupertino Style)**:
    - **Inset Grouped Table Views**: Todas las pantallas de ajustes (`GlassesSettingsActivity`, `HeadphoneSettingsActivity`, `SettingsActivity`) estandarizadas con tarjetas agrupadas de esquinas redondeadas continuas (`16dp`/`14dp`), líneas divisorias finas de `0.5dp` (`ios_separator`) y cabeceras de sección en mayúsculas pequeñas.
    - **Selectores y Spinners Estilo Cupertino (`bg_ios_spinner`)**: Reemplazo de spinners planos sin flecha por componentes con fondo secundario y glifo de chevron desplegable `ic_ios_chevron_down` a la derecha.
    - **Controles Deslizantes con Iconos Inline**: Sliders de brillo de pantalla HUD y volumen flanqueados por iconos semánticos de mínimo y máximo (`ic_brightness_low`, `ic_brightness_high`) idénticos al Centro de Control de iOS.
    - **Normalización Ergonómica de Botones y Fuentes**: Botones táctiles con altura estándar de 48dp/52dp, insets normalizados (`insetTop="0dp"`, `insetBottom="0dp"`) que impiden deformaciones, desbordes o textos cortados al aumentar el tamaño de fuente del sistema por accesibilidad.
    - **Safe Area Insets Fluidos y Edge-to-Edge (`EdgeToEdgeHelper`)**: Integración dinámica de insets de ventana (`systemBars()` y `displayCutout()`) en todas las pantallas. En `ActivityLogActivity` y `NotesActivity`, las barras superiores se ajustan fluidamente a la barra de estado y al recorte del notch sin aplastar títulos ni controles (`minHeight="56dp"`), las listas mantienen `clipToPadding="false"` para evitar recortes, y los clusters flotantes (como el Speed Dial FAB de Notas) elevan sus márgenes automáticamente por encima de la barra de navegación por gestos del sistema Android.
    - **Paleta Semántica Dinámica iOS Day/Night**: Adaptación automática a modo claro (`#F2F2F7` / `#FFFFFF`) y modo oscuro (`#000000` / `#1C1C1E`) sin fondos estáticos o desajustes de contraste.
  - **Barra de Acceso Rápido a Funcionalidades de IA**: Botones de 1-toque en la pantalla principal para:
    - 📝 **Notas IA**: Abre la libreta y gestión de notas inteligentes.
    - 🎙️ **Grabar Reunión**: Dispara instantáneamente la grabación de reuniones con análisis de acuerdos y mapa mental.
    - ☀️ **Mi Día**: Sintetiza en voz y publica en chat el Daily Briefing ejecutivo.
    - ✅ **Mis Tareas**: Consulta rápida de pendientes.
    - 🎧 **Dispositivos**: Selector y gestor Bluetooth.

- **Interfaz Moderna**:
  - Construida con **Material Design 3 & Apple Cupertino HIG**, ViewBinding y soporte completo Edge-to-Edge (`EdgeToEdgeHelper`).
  - Modo pantalla de bloqueo (`LockScreenHelper`) para interactuar con las gafas sin desbloquear el teléfono.

---

## 🏗️ Arquitectura del Sistema

```mermaid
graph TD
    Glasses[Meizu Myvu AR Glasses] <--> |Bluetooth SPP / BLE| Transport[Transport Layer: BtConnection / BleManager]
    Transport <--> Protocol[Protocol Layer: TLV Codecs & Frame Handlers]
    Protocol <--> Service[Foreground Service: MyvuService]

    Service <--> Skills[Skills Engine: SkillManager]
    Service <--> Mirror[Notification Mirror Listener]
    Service <--> AutoSend[Accessibility Service: Auto-Send]

    Skills <--> AI[AI Engine: Local OpenAI-LiteLLM / Gemini Live]
    Skills <--> DB[(Room Database: Notes & Reminders)]
    Skills <--> External[System APIS: Contacts / Calendar / Location]

    Service <--> UI[UI Layer: Connect, Chat, ActivityLog, Notes, Settings]
```

### Módulos del Código (`app/src/main/java/com/myvu/client/`)

| Paquete | Descripción |
|---|---|
| `transport/` | Gestión de conexiones Bluetooth SPP (RFCOMM) y BLE GATT. |
| `protocol/` | Encoders/decoders TLV (Type-Length-Value), tramas binarias y protocolo de enlace. |
| `service/` | Servicios en segundo plano: `MyvuService`, `MirrorNotificationListener`, `AutoSendAccessibilityService`. |
| `skills/` | Orquestador de habilidades (`SkillManager`, `BaseSkillHandler`) y handlers de negocio en `handlers/`. |
| `ai/` | Motores de IA: `LocalAiClient` (OpenAI/LiteLLM compatible) y `GeminiClient` (remoto). |
| `recorder/` | Grabación de notas de audio y procesamiento de voz. |
| `reminder/` | Planificador de alarmas y recordatorios sincronizados con el HUD. |
| `database/` / `data/` | Entidades y repositorios Room (`NoteRepository`, `ReminderRepository`, `AppDatabase`). |
| `ui/` | Vistas principales (`ConnectActivity`, `ChatActivity`, `ActivityLogActivity`, `NotesActivity`, `SettingsActivity`). |

---

## 🧰 Catálogo de Habilidades (Skills Integradas)

Ubicadas en `app/src/main/assets/skills/built-in/`:

| Skill | Función |
|---|---|
| `ai-voice-recorder` | Graba audio de notas de voz y genera transcripciones/resúmenes. |
| `gemini-live-assistant` | Conversación interactiva con el asistente de Google Gemini en tiempo real. |
| `hud-navigation` | Proyección de direcciones y guiado GPS en el HUD de las gafas. |
| `smart-translate-hud` | Traducción en vivo proyectada en pantalla. |
| `smart-ocr-scanner` | Escaneo y reconocimiento de texto óptico. |
| `create-note` / `create-reminder` | Creación rápida de notas y recordatorios sincronizados. |
| `call-contact` | Marcación rápida de contactos del teléfono mediante comandos de voz. |
| `voip-call` | Llamadas VoIP por WhatsApp, Microsoft Teams o Google Chat/Meet. |
| `send-whatsapp` / `send-telegram` | Redacción y envío asistido de mensajes instantáneos. |
| `health-summary` | Resumen de pasos diarios, nivel de estrés, ritmo cardíaco y actividad física. |
| `unread-notifications` | Resumen de notificaciones pendientes leídas en el HUD. |
| `weather-forecast` | Consulta meteorológica en tiempo real proyectada en el display. |
| `google-search` / `duckduckgo-search` | Respuestas rápidas de motores de búsqueda. |
| `app-media-control` | Control de NewPipe, OpenTune, Spotify, YouTube Music, saltar canciones y qué está sonando. |
| `code-calculator-math` | Resolución de cálculos matemáticos al instante. |
| `daily-briefing` | Resumen ejecutivo matutino ("Mi Día"): clima, agenda, tareas pendientes, avisos VIP y batería de gafas. |
| `routine-macros` | Macros contextuales de una frase: Modo Reunión (Zen + vibración), Conducción (brillo alto), Gimnasio y Noche. |
| `spatial-memory` | Memoria espacial para recordar dónde estacionó el auto ("¿dónde estacioné?") con cálculo de distancia y rumbo. |
| `shopping-checklists` | Gestión manos libres de listas de compras ("agrega pan a las compras", "tacha pan", "ver compras"). |
| `quick-location-dispatch` | Envío instantáneo de coordenadas GPS con enlace de Google Maps a contactos vía WhatsApp o Telegram. |

---

## 🚀 Requisitos y Compilación

### Requisitos del Entorno
- **JDK**: OpenJDK 25 (`/usr/lib/jvm/java-25-openjdk-amd64`) para ejecución de Gradle Daemon y compilación.
- **Bytecode Compatibilidad**: Java 21 (`sourceCompatibility = JavaVersion.VERSION_21`, `jvmTarget = "21"`).
- **Android SDK**: API Level 35 (`compileSdk 35`, `minSdk 26`).
- **Gradle**: 8.14.3+ con wrapper `./gradlew`.

### Comandos de Compilación

```bash
# Compilar APK en modo Debug
./gradlew assembleDebug

# Ubicación del APK resultante:
# app/build/outputs/apk/debug/app-debug.apk

# Ejecutar pruebas unitarias
./gradlew test
```

Para generar versiones firmadas para producción, consultar la [Guía de Construcción y Despliegue](BUILD_INSTRUCTIONS.md).

---

## 📲 Instalación y Configuración en Android

Para instrucciones completas paso a paso, consulta la [Guía de Instalación y Configuración de Permisos](docs/ANDROID_SETUP_GUIDE.md).

### Instalación Rápida
```bash
# Instalar APK en el dispositivo y otorgar permisos estándar
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
```

### ⚙️ Permisos y Ajustes del Sistema Indispensables
1. **Permisos de la Aplicación** (`Ajustes -> Aplicaciones -> MyVU Client -> Permisos`):
   - **Dispositivos cercanos** (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`)
   - **Ubicación** (`ACCESS_FINE_LOCATION`)
   - **Contactos** (`READ_CONTACTS`)
   - **Teléfono y SMS** (`CALL_PHONE`, `SEND_SMS`)
   - **Cámara** (`CAMERA` - para linterna y OCR)
   - **Micrófono** (`RECORD_AUDIO`)
   - **Calendario** (`READ_CALENDAR`, `WRITE_CALENDAR`)
2. **Acceso a Notificaciones** (`Ajustes -> Notificaciones -> Acceso a notificaciones`): Activar **MyVU Client** para proyectar alertas y mensajes en el HUD.
3. **Servicio de Accesibilidad** (`Ajustes -> Accesibilidad -> MYVU Auto-Send Assistant`): Activar para permitir el envío automático de WhatsApp, Telegram y SMS sin pulsar la pantalla.
   - *Watchdog proactivo*: Si actualizas la app y Android apaga el servicio, la app te notificará inmediatamente y mostrará un banner de reactivación en el Dashboard.
   - *Auto-activación permanente vía ADB*: Si ejecutas este comando una única vez en tu PC con el móvil conectado:
     ```bash
     adb shell pm grant com.myvu.client android.permission.WRITE_SECURE_SETTINGS
     ```
     La app se reactivará **100% sola de forma automática e instantánea** cada vez que se actualice o se reinicie el teléfono, sin requerir intervención manual.
4. **Desbloqueo Extendido / Smart Lock** (`Ajustes -> Seguridad -> Desbloqueo extendido -> Dispositivos de confianza`): Añadir las gafas **MYVU** para permitir el envío de WhatsApp/Telegram y control de apps con el teléfono en el bolsillo sin requerir PIN.
5. **Asistente Digital Predeterminado** (`Ajustes -> Aplicaciones -> Aplicaciones predeterminadas -> Aplicación de asistente digital`): Seleccionar **MyVU Client**.
6. **Batería sin Restricciones** (`Ajustes -> Aplicaciones -> MyVU Client -> Batería`): Seleccionar **Sin restricciones** para evitar que Android mate el servicio en reposo.
7. **Escucha y Ahorro de Batería** (`Ajustes -> Escucha y Ahorro de Batería`): La **Escucha activa continua** y la **Activación por voz (Wake word)** vienen desactivadas por defecto para optimizar la batería de las gafas (~16.5% de ahorro estimado por hora). Además, el cliente incluye filtrado de tramas obsoletas en init burst, heartbeat BLE adaptativo con coalescencia a 20s, sensores de salud batched (60s) en reposo profundo y watchdog no-wakeup compatible con Android Doze Mode.
8. **Gestos Táctiles en Patilla (Touchpad)** (`Ajustes -> Gestos de la Patilla Táctil`):
   - **Doble Toque Predeterminado**: Lanza **Gemini (Asistente / App)** o **Gemini Live (Conversación)** despertando y desbloqueando el teléfono automáticamente.
   - **Enrutamiento Directo de Micrófono SCO**: Activa por defecto el micrófono integrado de las gafas Meizu MYVU (`AudioDeviceInfo.TYPE_BLUETOOTH_SCO`).
   - **Modo Escucha Inmediato y Auto-Click Dual**: En modo Asistente normal, la app abre Gemini y activa automáticamente el micrófono mediante el servicio de accesibilidad (`canPerformGestures="true"`, exploración BFS sin podas erróneas y doble despacho Compose Action + DispatchGesture por coordenadas) para que puedas hablar de inmediato. En modo **Gemini Live**, invoca directamente el overlay del Asistente del teléfono con micrófono habilitado, pulsa automáticamente el botón de conversación en vivo y mantiene el canal de audio SCO permanentemente abierto manos libres sin interrupción.
   - **Auto-Bloqueo de Pantalla Parametrizable**: Temporizador configurable en Ajustes (15s a 300s, por defecto **60s / 1 min**) que apaga y bloquea automáticamente la pantalla tras ejecutar acciones desde las gafas, protegiendo el móvil en el bolsillo y ahorrando batería.
   - **Detección Táctil Optimizada**: Debounce ágil a 200ms, ventana ampliada de toques (30ms a 1100ms), sincronización por timestamps de hardware de las gafas y síntesis inteligente de doble y triple toque entre paquetes Bluetooth que elimina falsos positivos por rebotes capacitivos.

---

## 📖 Documentación Adicional

- [Guía de Instalación y Permisos en Android](docs/ANDROID_SETUP_GUIDE.md): Paso a paso detallado para configuración de permisos estándar y especiales.
- [Arquitectura Técnica y Protocolos](docs/ARCHITECTURE.md): Explicación detallada de frames TLV, enlace Bluetooth y motor de habilidades.
- [Registro de Memoria y Contexto](docs/PROJECT_MEMORY.md): Bitácora de decisiones, historial de ajustes y memoria persistente.
- [Planes de Implementación](docs/superpowers/plans/): Planes arquitectónicos por feature o tarea.
- [Guía de Compilación de APK](BUILD_INSTRUCTIONS.md): Pasos para firmar y desplegar versiones de producción.

---

## 📄 Licencia

Este proyecto está bajo la Licencia Apache 2.0. Consulta el archivo `LICENSE` para más detalles.
