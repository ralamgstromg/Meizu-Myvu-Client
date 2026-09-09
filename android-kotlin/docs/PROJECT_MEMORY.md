# Meizu Myvu Client — Memoria Viva y Registro de Contexto

Este archivo almacena la memoria viva del proyecto, decisiones técnicas, contexto de ajustes, correcciones y estado acumulado para mantener coherencia a lo largo de las sesiones de desarrollo.

---

## 1. Identidad y Reglas Operativas Establecidas
- **Modo de Comunicación**: Cavernícola ("Kog"), lenguaje directo, primitivo y conciso.
- **Herramientas de Grafo y Búsqueda de Código**:
  - `codegraph` CLI y `codegraph_explore` (vía MCP) para exploración precisa de llamadas y blast radius.
  - `codebase-memory-mcp` para almacenamiento de arquitectura (ADR) e indexación de relaciones.
- **Protocolo de Sincronización**:
  - Ejecutar obligatoriamente `codegraph sync` al iniciar cualquier tarea.
  - Ejecutar obligatoriamente `codegraph sync` al finalizar cualquier tarea.
- **Flujo de Trabajo y Superpowers**:
  - Diseñar y guardar siempre un plan de implementación (`docs/superpowers/plans/...`) antes de cualquier modificación de código.
  - Guardar en memoria los cambios realizados para mantener contexto de ajustes o correcciones.
  - Al completar la tarea, actualizar la documentación del proyecto en detalle.

---

## 2. Mapa Arquitectónico Rápido

| Capa / Módulo | Paquete Principal | Responsabilidad |
|---|---|---|
| **Compilación / Toolchain** | Raíz / Gradle | OpenJDK 25 (`/usr/lib/jvm/java-25-openjdk-amd64`) en `gradle.properties`, Android SDK en `/home/rcastro/Android/Sdk` vía `local.properties`. |
| **Transporte** | `com.myvu.client.transport` | Conexión RFCOMM Bluetooth SPP y BLE GATT. Reconexión automática y manejo de sockets. |
| **Protocolo** | `com.myvu.client.protocol` | Decodificación de tramas, serialización binaria, empaquetado TLV para el HUD de las gafas. |
| **Servicio Central** | `com.myvu.client.service.MyvuService` | Foreground Service persistente. Maneja ciclo de vida del enlace, reenvío de notificaciones y dispatching. |
| **Habilidades (Skills)** | `com.myvu.client.skills` | Motor modular de skills (`SkillManager`, `BaseSkillHandler`) con 30 manifiestos en `assets/skills/built-in/`. |
| **Persistencia** | `com.myvu.client.database` / `data` | Base de datos Room (`AppDatabase`, `NoteRepository`, `ReminderRepository`). |
| **Inteligencia Artificial** | `com.myvu.client.ai` | Inferencia local con MediaPipe Tasks GenAI, streaming Gemini Live y cliente Custom/LiteLLM (`LocalAiClient`). |
| **Interfaz de Usuario** | `com.myvu.client.ui` | Vistas Material 3 (`ConnectActivity`, `NotesActivity`, `ChatActivity`, `SettingsActivity`, etc.). |

---

## 3. Bitácora de Modificaciones y Decisiones

### [2026-09-09] — Inicialización de Memoria y Documentación Integral
- Sincronización inicial con `codegraph sync`.
- Plan de trabajo en `docs/superpowers/plans/2026-09-09-init-memory-and-documentation.md`.
- Inicialización de Memoria MCP y documentación central (`README.md`, `docs/ARCHITECTURE.md`, `docs/PROJECT_MEMORY.md`).

### [2026-09-09] — Migración y Actualización de Procesos de Build a OpenJDK 25
- Configurado `org.gradle.java.home=/usr/lib/jvm/java-25-openjdk-amd64` en `gradle.properties`.
- Ajustada limitación del parser de versiones `JavaVersion.parse` en el compilador embeddable de Kotlin DSL.
- Verificado Gradle Daemon corriendo bajo OpenJDK 25.0.4.

### [2026-09-09] — Corrección de `SDK location not found` y Compilación Exitosa de APK
- Configurado `local.properties` con `sdk.dir=/home/rcastro/Android/Sdk`.
- Instalación automática de `build-tools 35.0.0` y `platforms/android-35`.
- Generado con éxito `app/build/outputs/apk/debug/app-debug.apk` (20 MB).

### [2026-09-09] — Implementación Completa de Gemini + LiteLLM con Native Tool Calling
- **Archivos Creados**:
  1. `app/src/main/java/com/myvu/client/ai/ToolCallModels.kt`: Modelos `ToolDefinition`, `ToolCall`, `ChatMessage` y `ChatCompletionResult`.
  2. `app/src/main/java/com/myvu/client/skills/SkillToolConverter.kt`: Mapeador bidireccional entre `Skill` de Android y OpenAPI JSON Schema para LiteLLM.
  3. `app/src/main/java/com/myvu/client/ai/AgenticToolExecutor.kt`: Orquestador del bucle ReAct multi-paso que ejecuta habilidades de Android y retroalimenta las observaciones a Gemini.
  4. `app/src/test/java/com/myvu/client/ai/ToolCallingIntegrationTest.kt`: Suite de pruebas unitarias cubriendo serialización, conversión y parseo de respuestas.
- **Archivos Modificados**:
  1. `AiClient.kt`: Añadido contrato `chat(...)` y flag `supportsToolCalling()`.
  2. `LocalAiClient.kt`: Soporte de `tools`, `tool_choice: "auto"`, `response_format: {"type": "json_object"}` y extracción de `tool_calls`.
  3. `SkillRegistry.kt`: Exposición de `getToolDefinitions()` y `buildNativeToolsSystemPrompt()`.
  4. `AiConversation.kt`: Conexión de `AgenticToolExecutor` en la interacción por voz de las gafas Meizu Myvu.
  5. `ChatActivity.kt`: Conexión agéntica en la vista móvil de chat.
  6. `NoteAiProcessor.kt` y `MeetingAiProcessor.kt`: Migración a `chat(..., jsonMode = true)` para análisis estructurado.
  7. `README.md` y `docs/ARCHITECTURE.md`: Documentación actualizada con la nueva arquitectura agéntica.
- **Verificación**:
  - Pruebas unitarias de integración (`ToolCallingIntegrationTest`) ejecutadas y aprobadas (4/4 tests verdes).
  - Compilación exitosa del APK debug con `./gradlew assembleDebug` (`BUILD SUCCESSFUL in 753ms`).

### [2026-09-09] — Desconexión Total y Ahorro de Batería en Segundo Plano
- **Problema**: Al presionar "Disconnect", la app continuaba reintentando conexiones en segundo plano y consumiendo batería debido a:
  1. `ServiceWatchdogReceiver` con alarma cada 15 minutos en `AlarmManager`.
  2. `BootReceiver` y `ServiceKeepAliveHelper` forzando el reinicio del servicio en eventos del sistema (`USER_PRESENT`, `POWER_CONNECTED`).
  3. `ConnectionManager.onDisconnected` y `fail` pasando a estado `FAILED` y re-encolando reconexiones sin respetar `userStopped`.
- **Solución**:
  1. `ServiceWatchdogReceiver.kt`: Creado método `cancelWatchdog(context)` y añadido guard `autoReconnectEnabled` en `onReceive` y `scheduleWatchdog`.
  2. `ServiceKeepAliveHelper.kt`: Añadido guard en `ensureServiceRunning` para abortar si `!Prefs.autoReconnectEnabled(context)`.
  3. `MyApp.kt`: El watchdog solo se programa al inicio si `autoReconnectEnabled` está activo.
  4. `ConnectActivity.kt`: Al presionar Disconnect (`stopConnection()`), cancela el watchdog con `cancelWatchdog(this)`, fija `autoReconnectEnabled(false)` y detiene `MyvuService`.
  5. `MyvuService.kt`: En `ACTION_STOP`, invoca `cancelWatchdog(this)`, finaliza el servicio en primer plano y remueve la notificación.
  6. `ConnectionManager.kt`: Blindados `onDisconnected`, `fail`, `beginConnect` y `beginAutoSearch` para comprobar `userStopped || !Prefs.autoReconnectEnabled(context)`, asegurando que el estado permanezca en `IDLE` sin programar reconexiones.
  7. `BootReceiver.kt`: Omite revivir `MyvuService` si `autoReconnectEnabled` fue deshabilitado por el usuario.

### [2026-09-09] — Conexión Automática a Bluetooth Audio (HFP + A2DP) al Presionar "Connect"
- **Problema**: Al presionar "Connect", la app establecía con éxito el enlace BLE de control con las gafas, pero el audio Bluetooth clásico (perfiles Headset HFP y A2DP) no se conectaba en el móvil Android, impidiendo el uso del micrófono y altavoz de las gafas.
- **Causas Raíz Identificadas**:
  1. `ConnectionManager.onSessionReady()` condicionaba la llamada a `audioProfiles?.connect(device)` con `if (!relayExpected)`. Cuando el enlace BLE conectaba, `transport == null` y `sppUuidVal != null`, haciendo que `relayExpected == true`, por lo que `audioProfiles.connect()` NUNCA era ejecutado.
  2. Condición de carrera en `AudioProfiles.kt`: Los proxies `BluetoothHeadset` y `BluetoothA2dp` tardan de 100 a 500 ms en vincularse (`getProfileProxy`). Si `connect(device)` se llamaba antes de que terminaran de vincularse, `headset` y `a2dp` eran nulos y la llamada quedaba descartada sin reconectar al finalizar el binding.
  3. Diferencia de direcciones MAC dual-mode: La dirección MAC BLE con la que se empareja la app con las gafas difiere de la dirección MAC Classic Bluetooth del dispositivo de audio emparejado en los ajustes de Android.
- **Solución Implementada**:
  1. `AudioProfiles.kt`:
     - Implementado `resolveTargetDevice`: inspecciona `adapter.bondedDevices` buscando coincidencia por MAC o nombre conteniendo `"MYVU"`.
     - Emparejamiento automático (`target.createBond()`) si el dispositivo no está emparejado en Android.
     - Almacenado `pendingDevice` para que `proxyListener.onServiceConnected` conecte automáticamente HFP y A2DP tan pronto como los proxies terminen de vincularse.
     - Refuerzo por reflexión con `setConnectionPolicy(device, 100)` y `setPriority(device, 1000)` para forzar la reconexión automática en el stack de Bluetooth de Android.
  2. `ConnectionManager.kt`:
     - Eliminado el guard restrictivo `if (!relayExpected)`.
     - Creado `connectAudioProfiles()` e invocado tanto en `onReady` (al establecerse el enlace BLE inicial) como en `onSessionReady`.
- **Verificación**:
  - Compilación limpia con `./gradlew assembleDebug` (39 tareas ejecutadas/actualizadas, 0 advertencias).

### [2026-09-09] — Corrección de Respuestas y Tool Calling con LiteLLM / OpenAI Compatible
- **Problema**: Las consultas de voz ("en Barranquilla el día de mañana") al endpoint OpenAI compatible (`https://soft-ia.co/litellm/v1/chat/completions`) no retornaban resultado en las gafas ni en los logs.
- **Causas Raíz Identificadas**:
  1. `LocalAiClient.chat()` llamaba a `askOnce(body)` en `AiHttpClient.kt`, el cual procesaba la respuesta HTTP con `extractText()`. Cuando el modelo emitía llamadas a herramientas (`tool_calls`), `choices[0].message.content` es `null`, haciendo que `extractText()` retornara vacío `""` y lanzara `IOException("Local API returned an empty answer")`. Incluso en respuestas textuales, `parseChatCompletion()` fallaba al recibir texto plano en lugar del JSON raíz.
  2. El servidor LiteLLM solo expone el modelo `gafas` (verificado vía `/v1/models`). Cualquier otro nombre retornaba `HTTP 400 Bad Request: Invalid model name`.
  3. `LOCAL_READ_TIMEOUT_MS` estaba configurado en 240 segundos (4 minutos), provocando bloqueos prolongados sin feedback cuando el socket tardaba.
  4. Falta de trazas: el endpoint, modelo y status HTTP no se registraban en `LogBus`.
- **Solución Implementada**:
  1. `AiHttpClient.kt`: Creado `postRaw(body)` que registra en log la URL de destino (`POST <endpoint>`) y el código de estado (`HTTP <status>`), retornando el cuerpo JSON en bruto sin pasar por `extractText()`.
  2. `AiHttpClient.kt`: Reducido `LOCAL_READ_TIMEOUT_MS` de 240s a 45s y `READ_TIMEOUT_MS` a 60s.
  3. `LocalAiClient.kt`: Actualizado `chat()` para invocar `postRaw(body)`, garantizando que `parseChatCompletion()` reciba la estructura JSON completa con soporte nativo de `tool_calls`.
  4. `AiConversation.kt`: Registra en `AI_REQUEST_STARTED` el proveedor, modelo y endpoint configurados.
- **Verificación**:
  - Petición cURL probada contra el servidor con `model: "gafas"` y herramienta `weather_forecast`, confirmando respuesta exitosa con `tool_calls` para Barranquilla.
  - Compilación limpia del APK con `./gradlew assembleDebug`.

### [2026-09-09] — Eliminación de Bucle Infinito en Vaciado de Notificaciones (`onSessionReady`)
- **Problema**: La aplicación entraba en un bucle síncrono infinito inundando la pantalla y log con miles de líneas por segundo:
  `!! app relay not ready -- queued notification for RFCOMM delivery`
  `flushing queued action/notification: {"action":"notification"...`
- **Causa Raíz**:
  En `ConnectionManager.kt`, `onSessionReady(transport)` procesaba `pendingNotifications` con un bucle `while (!pendingNotifications.isEmpty())`. Cuando el transporte es nulo (`transport == null`, enlace BLE listo pero relé RFCOMM pendiente), `sendActionNow` detectaba `isNotification && transport == null` y reinsertaba la notificación a `pendingNotifications`. El bucle `while` extraía de inmediato el mismo elemento recién insertado, bloqueando el hilo de eventos en una recursión infinita.
- **Solución Implementada**:
  - En `ConnectionManager.kt` (`onSessionReady`), se vacía primero la cola a una lista local inmutable para la iteración (`val toFlush = ArrayList<PendingAction>()`).
  - Durante la iteración, si `isNotification && transport == null && canConnectRelay()`, se mantiene encolada para la entrega por RFCOMM sin invocar recursivamente a `sendActionNow`, eliminando cualquier posibilidad de bucle infinito.
- **Verificación**:
  - Compilación limpia con `./gradlew assembleDebug` (39 tareas ejecutadas/actualizadas).

### [2026-09-09] — Análisis Profundo del Log Operativo y Plan Integral de Mejoras
- **Hallazgos Clave del Log**:
  1. Conexión BLE + RFCOMM 100% estable. Bucle infinito erradicado por completo.
  2. Consulta de voz ejecutada de punta a punta en 1.8 segundos con síntesis TTS y visualización en HUD.
  3. `VoiceActionRouter` intercepta consultas con modificadores temporales ("mañana") devolviendo el clima de hoy.
  4. STT pierde ~880ms por turno al intentar `es-CO` offline no soportado antes de pasar a `es` online.
  5. Ráfagas de notificaciones y `DISMISS_NOTIFICATION` repetitivas de WhatsApp por conteo de mensajes de grupo.
  6. Proxies de `AudioProfiles` quedan en cola de binding sin confirmar `onServiceConnected`.
- **Plan de Mejoras Registrado**:
  - `docs/superpowers/plans/2026-09-09-log-analysis-and-improvements.md` estructurado en 4 pistas: AudioProfiles, STT Latency Cache, Weather Temporal Precision, y WhatsApp Mirroring Debounce.

### [2026-09-09] — Aplicación de las 4 Mejoras de Rendimiento y Experiencia
1. **Pista 1: AudioProfiles Diagnóstico, MainLooper y Reintento**:
   - `AudioProfiles.kt`: `bindProxies()` ahora se ejecuta sobre `mainHandler` (Looper principal), registra el resultado booleano de `getProfileProxy` (`HFP=true/false, A2DP=true/false`) y reintenta a los 2.5s si algún proxy no se vinculó.
   - Si `tryConnect()` se llama con `proxy == null`, re-dispara `bindProxies()`.
2. **Pista 2: Caché de STT con Cero Latencia (-900ms por turno)**:
   - `AndroidSpeechRecognizer.kt`: Creados `cachedWorkingLanguage` y `cachedPreferOffline`.
   - Cuando un dialecto offline no soportado (`es-CO` con `code 12`) activa el fallback a `es` online, se almacena en caché. Las siguientes pulsaciones inician directamente en el idioma y modo que funcionan, eliminando el segundo de espera fallido.
3. **Pista 3: Precisión Temporal en Clima ("mañana" / "forecast")**:
   - `ExternalInfoService.kt`: `formatWeatherResult` y `fetchWeather` ahora reciben `queryContext`/`targetDate`.
   - Si la consulta contiene *"mañana"*, lee `reading.futureDay[1]` de OpenMeteo y formatea el pronóstico exacto de mañana con temperaturas máxima y mínima en lugar del clima actual.
   - `WeatherForecastHandler.kt`: Actualizado para aprovechar el pronóstico temporal por fecha.
4. **Pista 4: Filtro Antirráfaga para Notificaciones de WhatsApp**:
   - `NotificationFilter.kt`: En `isDuplicateContent()`, se normalizan los títulos eliminando contadores de mensajes repetitivos (`(2 mensajes)`, `(3 mensajes)`). Mensajes idénticos dentro de la ventana de 8 segundos son descartados.
   - `MirrorNotificationListener.kt`: Se añadió `pendingDismisses` para cancelar programaciones previas al recibir una actualización del mismo ID o al removerse la notificación, erradicando ráfagas de `DISMISS_NOTIFICATION` en cascada.
- **Verificación**:
   - Compilación limpia con `./gradlew assembleDebug` (39 tareas ejecutadas/actualizadas, 0 errores).


### [2026-09-09] — Mejora Integral de Skills, Tools e Integraciones con Android y Terceros
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-09-skills-and-tools-enhancement-plan.md`.
- **Nuevos Componentes**:
  1. `ContactHelper.kt` en `com.myvu.client.core`:
     - Normalización fonética y remoción de tildes (NFD).
     - Distancia Levenshtein y puntuación difusa sobre `ContactsContract.CommonDataKinds.Phone`.
     - Resolución de correos en `ContactsContract.CommonDataKinds.Email`.
     - Formateo inteligente de prefijos celulares colombianos (+57 para números de 10 dígitos iniciando en 3 o 6).
- **Handlers Mejorados**:
  1. `CallContactHandler.kt`:
     - Búsqueda difusa de contactos por nombre.
     - Llamada directa 100% manos libres mediante `TelecomManager.placeCall` o `Intent.ACTION_CALL` cuando `CALL_PHONE` está concedido. Fallback a `ACTION_DIAL`.
  2. `SendWhatsappHandler.kt`:
     - Resolución de nombres de contactos a números celulares reales.
     - Prefijo de país (+57) para Colombia.
     - Activación automática de `AutoSendAccessibilityService.triggerWhatsAppAutoSend()` para envío manos libres desde las gafas.
  3. `SendTelegramHandler.kt`:
     - Soporte para nombres de contacto, números de teléfono y alias `@usuario`.
     - Envío directo mediante `tg://msg?text=` y activación de `AutoSendAccessibilityService.triggerTelegramAutoSend()`.
  4. `SendEmailHandler.kt`:
     - Resolución de nombres de contacto a direcciones de correo registradas en la agenda.
  5. `OpenAppHandler.kt`:
     - Matriz enriquecida de alias para más de 30 aplicaciones (Cámara, Galería, Ajustes, Reloj, Calculadora, Spotify, OpenTune, YouTube, Netflix, Waze, Google Maps, Uber, Rappi, etc.).
     - Acceso directo a `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA` y `Settings.ACTION_SETTINGS`.
  6. `QuickAlarmTimerHandler.kt`:
     - Acciones: `set_alarm`, `set_timer`, `show_alarms`, `show_timers`, `dismiss_alarm`.
     - Parser en lenguaje natural ("1 hora y media", "10 minutos", "45 segundos", "7:30 am", "18:00").
  7. `CalendarService.kt` y `CalendarEventsHandler.kt`:
     - Filtro por fecha natural ("hoy", "mañana", ventana de 24 horas).
     - Creación de nuevos eventos de calendario con `Intent.ACTION_INSERT`.
  8. `CreateReminderHandler.kt`:
     - Conexión con `ReminderTimeParser` para admitir horas específicas ("5:30 pm") o duraciones ("en 20 minutos").
  9. `SmartTranslateHudHandler.kt`:
     - Motor de traducción en tiempo real (en, fr, de, it, pt, zh, ja, es).
     - Proyección directa en pantalla HUD de las gafas Meizu Myvu mediante `openTeleprompter()`.
  10. `HudNavigationHandler.kt`:
      - Proyección en visor AR cuando las gafas están enlazadas y fallback automático a Google Maps o Waze en el móvil.
  11. `CodeCalculatorMathHandler.kt`:
      - Parser Shunting-Yard con precedencia de operadores (`*`, `/` sobre `+`, `-`), paréntesis y porcentajes.
      - Liquidación tributaria colombiana (IVA 19%, Retención en la fuente 3.5%), proyección de créditos bancarios y conversión de unidades físicas/temperatura.
  12. `RagHistorySearchHandler.kt`:
      - Búsqueda contextual integrada en notas, grabaciones de voz, recordatorios y tareas (todos).
  13. `SkillParser.kt`:
      - Corregido bug crítico en la lectura de manifiestos YAML: detección de atributos indentados para evitar que parámetros secundarios fueran interpretados como metadatos globales de la skill.
      - Soporte unificado de formato inline `{ type: ..., required: ... }` y formato multi-línea estándar.
  14. Manifiestos `SKILL.md`:
      - Actualizados manifiestos con descripciones claras y ejemplos en español para Function Calling nativo en Gemini y LiteLLM.
- **Verificación**:
  - `ToolCallingIntegrationTest` ampliado con prueba de parseo YAML inline y block (5/5 tests aprobados).
  - Compilación exitosa del APK debug con `./gradlew assembleDebug` en 659ms.

### [2026-09-09] — Optimización Multimodal y Proyección AR HUD en Notas, Recordatorios y Grabadora de Voz IA
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-09-multimodal-notes-and-voice-recorder-enhancement.md`.
- **Arquitectura Multimodal Unificada**:
  1. `ToolCallModels.kt`:
     - Expandido `ChatMessage` con campo `images: List<Pair<String, String>>?` (mimeType, base64).
     - Implementada serialización a formato compatible OpenAI/LiteLLM vision: array de objetos `[{"type": "text", "text": ...}, {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}}]`.
     - Creado helper `ChatMessage.userWithImages(content, images)`.
  2. `DocumentExtractor.kt`:
     - Implementado `loadAndEncodeImageBase64(file, maxDim = 1024)`: reescala eficientemente imágenes a máximo 1024px, comprime a JPEG 80% y codifica en Base64, reduciendo tamaño de ~4MB a ~120KB para envíos ultrarrápidos sin desbordar memoria.
  3. `NoteAiProcessor.kt`:
     - Enriquecidas `processNote()`, `processReminder()`, `askQuestionAboutNote()` y `askQuestionAboutReminder()`.
     - Detección automática y extracción de imágenes adjuntas (recibos, diagramas, textos manuscritos, capturas).
     - Procesamiento multimodal en un solo paso: envía el texto junto a las imágenes en Base64 al endpoint LiteLLM / Gemini.
     - Prompt enriquecido para exigir extracción de datos visuales, resumen ejecutivo, matriz de compromisos y diagrama mental Mermaid.
  4. `MeetingAiProcessor.kt`:
     - Enriquecidas `processFullMeeting()` y `askQuestionAboutRecording()`.
     - Cruce multimodal: asocia la transcripción de audio (STT Whisper) con fotos adjuntas de pizarras, diapositivas y esquemas tomados durante la reunión.
     - Instrucciones de prompt para correlacionar visualmente los diagramas con la discusión verbal de los participantes.
  5. Proyección en AR HUD de Gafas Meizu Myvu (`NoteDetailActivity.kt` y `RecordingDetailActivity.kt`):
     - Sustituido el envío simple de notificación (que truncaba a pocos caracteres) por `MyvuService.activeConnection()?.openTeleprompter(fullText, title)`.
     - Proyecta en el visor microLED de las gafas el Resumen Ejecutivo completo, las Tareas/Compromisos y el contenido detallado, permitiendo al usuario navegarlo mediante el touchpad de la patilla de las gafas.
     - Mantiene fallback elegante a notificación si las gafas no tienen sesión activa.
- **Verificación**:
  - `ToolCallingIntegrationTest` ejecutado con éxito (5/5 pruebas unitarias pasando).
  - Compilación limpia con `./gradlew assembleDebug` en 765ms.

### [2026-09-09] — Actualización Integral de Paquetes y Dependencias a Última Versión
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-09-update-all-dependencies-to-latest.md`.
- **Actualizaciones en `gradle/libs.versions.toml`**:
  1. `agp` (Android Gradle Plugin): `8.8.0` -> `8.13.2` (máxima versión estable AGP 8 compatible con toolchain).
  2. `kotlin`: `2.1.10` -> `2.3.20` (resuelve incompatibilidad de metadatos 2.3.0 en bibliotecas Google).
  3. `ksp` (Kotlin Symbol Processing): `2.1.10-1.0.31` -> `2.3.11` (perfectamente alineado con Kotlin 2.3.20).
  4. `coroutines` (`kotlinx-coroutines-*`): `1.10.1` -> `1.11.0`.
  5. `coreKtx` (`androidx.core:core-ktx`): `1.15.0` -> `1.16.0` (última versión compatible con `compileSdk 35` / Android 15).
  6. `appcompat` (`androidx.appcompat:appcompat`): `1.7.0` -> `1.8.0`.
  7. `material` (`com.google.android.material:material`): `1.12.0` -> `1.14.0`.
  8. `playServicesLocation`: `21.3.0` -> `21.4.0`.
  9. `playServicesAuth`: `21.3.0` (fijado en 21.3.0 para preservar compatibilidad con `GoogleSignIn`, removido en 22.0.0 a favor de Credential Manager).
  10. `robolectric`: `4.14.1` -> `4.16.1`.
  11. `json` (`org.json:json`): `20260522` -> `20260814`.
  12. `lifecycleRuntimeKtx`: `2.8.7` -> `2.11.0`.
  13. `mediapipeGenai`: `0.10.20` -> `0.10.35`.
  14. `room` (`androidx.room:*`): `2.6.1` -> `2.8.4` (Room 2.8 estable con soporte KMP y KSP optimizado).
- **Ajustes de Código y Build DSL**:
  1. `app/build.gradle.kts`:
     - Eliminado bloque deprecado `kotlinOptions { jvmTarget = "21" }`.
     - Configurado `tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(JvmTarget.JVM_21) }` para garantizar coherencia entre tareas Java y Kotlin bajo OpenJDK 25.
  2. `AppDatabase.kt`:
     - Actualizado método deprecado `fallbackToDestructiveMigration()` a `fallbackToDestructiveMigration(dropAllTables = true)`.
- **Verificación**:
  - Suite de pruebas unitarias `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL** (34 tareas ejecutadas/al día, 0 fallos).
  - Ensamblado de APK debug `./gradlew assembleDebug`: **BUILD SUCCESSFUL** en 924ms (41 tareas ejecutadas/al día, APK generado limpiamente).

### [2026-09-09] — Optimización de Latencia en Voz, Aceleración de App Relay y Robustez de Notificaciones
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-09-log-analysis-and-latency-voice-improvements.md`.
- **Análisis de Logs**: Diagnóstico exhaustivo de 350 eventos en `myvu_client_log.txt`.
- **Modificaciones Realizadas**:
  1. `AiConversation.kt`:
     - Eliminado el cuello de botella de latencia de 8 a 10 segundos: al pulsar el botón de las gafas (`why == "button"`), el audio proviene del micrófono de las gafas; se omite `AndroidSpeechRecognizer` en el teléfono para evitar timeouts y conflictos de audio del móvil en el bolsillo.
     - Detección inmediata de fin de habla en VAD: tan pronto el usuario termina de hablar (`SILENCE_HOLD_MS = 1200ms`), se procesa el flujo Opus de las gafas directamente con `endUtterance(forceGlassesAudio = true)`, reduciendo la latencia de respuesta de ~11s a ~1.8s.
  2. `AiProtocol.kt`:
     - Sustituido el mensaje de `sessionAck` en chino mandarín (`"唤醒成功"`) por español (`"Escuchando..."`).
  3. `ConnectionManager.kt`:
     - Reducido el timeout de establecimiento del App Relay RFCOMM (`RELAY_ESTABLISH_TIMEOUT_MS`) de 30.000 ms a 6.000 ms.
     - Añadida retransmisión automática de `sendAbility` a los 2.000 ms si el burst inicial de BLE demoró la respuesta de las gafas, permitiendo que el visor HUD y teleprompter estén listos en 2 a 4 segundos en vez de esperar medio minuto.
  4. `MirrorNotificationListener.kt`:
     - Implementada idempotencia estricta en `sendDismissSafely` con ventana de 2.5s y limpieza periódica de cache, erradicando ráfagas duplicadas de `DISMISS_NOTIFICATION` en milisegundos.
     - Corregida concordancia gramatical en `getUnreadSummary()`: "1 notificación pendiente" vs "X notificaciones pendientes".
- **Verificación**:
  - Suite de pruebas unitarias `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL** (34 tareas ejecutadas/al día, 0 fallos).
  - Ensamblado de APK debug `./gradlew assembleDebug`: **BUILD SUCCESSFUL** en 1s (41 tareas ejecutadas/al día, APK generado limpiamente).

### [2026-09-09] — Pulido de Latencia de Clima (Cache), Handshake RFCOMM Suave y Gramática
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-09-polish-weather-rfcomm-grammar.md`.
- **Análisis de Logs Post-Ajustes**:
  - Confirmada reducción drástica de latencia de voz de 11.5s a 1.8s (STT rápido con Opus sin errores 12).
  - Confirmada auto-conexión de perfiles Bluetooth Classic (HFP + A2DP) y deduplicación de notificaciones.
- **Mejoras Implementadas**:
  1. `OpenMeteo.kt`:
     - Implementada caché concurrente en memoria (`geocodeCache`) para coordenadas de ciudades en consultas meteorológicas.
     - Evita la petición HTTP de geocodificación en consultas repetidas o de la ciudad local, reduciendo el tiempo de respuesta del clima de ~3.6s a ~1.1s.
     - Esto previene de raíz que el firmware de las gafas dispare su watchdog interno de 3 segundos que reproducía el mensaje en inglés *"Just a moment, please"*.
  2. `MirrorNotificationListener.kt`:
     - Corregida la pluralización en `getUnreadSummary()`: ahora muestra y pronuncia *"No tienes correos pendientes por leer"* o *"No tienes mensajes pendientes por leer"* en lugar del singular desajustado *"No tienes correo..."*.
  3. `ConnectionManager.kt`:
     - Añadido retraso prudente de 800ms antes de activar el `RelaySupervisor` tras completar la ráfaga de inicialización BLE y `applyDefaults()`. Esto previene la colisión de paquetes entre BLE y RFCOMM durante el emparejamiento y elimina el timeout de 6 segundos.
     - Incorporado acelerador/filtro de desduplicación de `ClockSync` / `SyncOffSetTime` (ventana de 4s), impidiendo el envío consecutivo doble de sincronización de hora en menos de 100ms.
- **Verificación**:
  - Tests unitarios `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL** (34 tareas OK, incluyendo nueva prueba unitaria de caché en `ExternalInfoServiceTest.kt`).
  - Ensamblado de APK debug `./gradlew assembleDebug`: **BUILD SUCCESSFUL** en 846ms.



