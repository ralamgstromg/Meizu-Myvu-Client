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



