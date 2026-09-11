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


- **Interfaz Moderna**:
  - Construida con **Material Design 3**, ViewBinding y soporte completo Edge-to-Edge (`EdgeToEdgeHelper`).
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

    Service <--> UI[UI Layer: Connect, Notes, Chat, Settings]
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
| `ui/` | Vistas principales (`ConnectActivity`, `ChatActivity`, `NotesActivity`, `SettingsActivity`). |

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
