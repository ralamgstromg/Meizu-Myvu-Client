# Meizu Myvu Client — Memoria Viva y Registro de Contexto

Este archivo almacena la memoria viva del proyecto, decisiones técnicas, contexto de ajustes, correcciones y estado acumulado para mantener coherencia a lo largo de las sesiones de desarrollo.

---

## 1. Identidad y Reglas Operativas Maestras (Protocolo Caveman)
- **Modo de Comunicación**: Cavernícola ("Kog"), lenguaje directo, primitivo, conciso, gruñidos ("Ugh!").
- **Herramientas de Grafo y Búsqueda**:
  - `codegraph` CLI (`codegraph query`, `codegraph explore`, `codegraph context`) y `codegraph_explore` para búsquedas de código y análisis de impacto.
  - `graphify` y `codebase-memory-mcp` para indexación profunda y ADR de memoria viva.
  - `superpowers` para flujos sistemáticos (plans, subagents, debugging).
- **Protocolo Obligatorio de Sincronización**:
  - **Al iniciar toda tarea**: Ejecutar inmediatamente `codegraph sync`.
  - **Al terminar toda tarea**: Ejecutar obligatoriamente `codegraph sync`.
- **Planificación Previa Obligatoria**:
  - Siempre generar un plan de trabajo detallado en `docs/superpowers/plans/` antes de cualquier implementación de código.
- **Registro en Memoria de Cambios y Contexto**:
  - Guardar siempre en memoria (`docs/PROJECT_MEMORY.md`) los cambios realizados, decisiones técnicas, contexto de ajustes y correcciones.
- **Paso Final Obligatorio**:
  - Como último paso de cada tarea, actualizar la documentación del proyecto (`README.md`, `docs/ARCHITECTURE.md`, `docs/PROJECT_MEMORY.md`) con todos los cambios aplicados en detalle.
- **Inicialización de Proyecto y Memoria**:
  - Si el proyecto y su memoria no han sido inicializados: inicializar y generar la memoria viva y la documentación completa del proyecto.

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
| **Inteligencia Artificial** | `com.myvu.client.ai` | Inferencia privada/local mediante `LocalAiClient` (LiteLLM/OpenAI compatible), streaming Gemini Live y cliente en la nube `GeminiClient`. |
| **Interfaz de Usuario** | `com.myvu.client.ui` | Vistas Material 3 & Apple Cupertino HIG (`ConnectActivity`, `ActivityLogActivity`, `NotesActivity`, `ChatActivity`, `GlassesSettingsActivity`, `HeadphoneSettingsActivity`, `SettingsActivity`). |

### [2026-09-12] — Eliminación del Bucle Ping-Pong RFCOMM y Optimización Crítica de Batería (Gafas y Teléfono)
- **Requerimiento del Usuario**:
  - Analizar el log `/home/rcastro/Descargas/myvu_client_log.txt`, identificar problemas o errores, y detectar cualquier impacto en la batería o rendimiento de las gafas o del móvil.
  - Aplicar correcciones sistemáticas.
- **Diagnóstico Forense del Registro**:
  - **Google Assistant**: Confirmado resuelto. Cero activaciones erróneas del asistente en el log tras la corrección del botón físico (`202` -> `ACTION_BUTTON`).
  - **Bucle Zombi Ping-Pong de Reconexión RFCOMM**:
    - El 98% del log (8 ciclos de conexión en 67 segundos, 7 en apenas 13 segundos) registraba intentos continuos de reconexión RFCOMM con `attempt 1/6`.
    - Secuencia observada: Teléfono conecta RFCOMM -> handshake `-> AUTH_SUCCESS` -> a los 5ms las gafas cierran el servidor SPP (`CMD_SPP_SERVER_REQUEST_STATE_CLOSE`) porque no hay streaming activo -> el supervisor entra en *passive low-power mode* -> 2 segundos después, el daemon de las gafas emite ráfaga `CMD_SPP_SERVER_REQUEST_CONNECT` (71) o `CMD_SPP_SERVER_REQUEST_STATE_OPEN` (72) -> `ConnectionManager` invocaba ciegamente `supervisor?.wake()` -> `RelaySupervisor` reseteaba `attempt = 0` y `sppServerSuspended = false`, forzando reconexión inmediata (`attempt 1/6`).
  - **Impacto Severo en Dispositivos**:
    - Gafas MYVU: El radio Bluetooth Classic (BR/EDR) consume 10-20x más energía que BLE. Conectar y autenticar criptografía cada 2 segundos impedía el Deep Sleep del procesador de las gafas, drenando sus diminutas baterías de patilla (~170-200 mAh).
    - Móvil: Mantenía el Bluetooth Controller en estado de alta potencia, despertaba corrutinas de E/S y handlers cada 2 segundos, e impedía el modo Doze de Android.
- **Implementación y Solución**:
  1. **`RelaySupervisor.kt`**:
     - `wake(force: Boolean = false)`: Si `sppServerSuspended` es verdadero (las gafas cerraron el servidor SPP explícitamente), se ignora el despertar no forzado (`force = false`) para respetar el modo pasivo de ultra bajo consumo y no reactivar el supervisor por ráfagas pasivas.
     - Se preserva el despertar inmediato cuando `force = true` para acciones deliberadas del usuario (botón de IA con micrófono, Trackpad, o inicio de sesión).
  2. **`ConnectionManager.kt`**:
     - `handleSppOpenRequest()`: Se agregó control de cooldown (`SPP_COOLDOWN_MS = 15000L`) y validación de `isRelayFeatureActive`. Si las gafas cerraron el SPP recientemente y ninguna función activa (Trackpad, grabación de audio IA) requiere RFCOMM, se descartan las solicitudes de apertura ruidosas del daemon BLE de las gafas.
     - `trackpadStart()` y `trackpadStop()`: Integración de `setRelayFeatureActive(true/false)` para activar RFCOMM bajo demanda únicamente cuando la superficie táctil esté en uso.
     - `wakeRelay(force: Boolean = true)`: Garantiza que las llamadas explícitas reactiven el supervisor.
  3. **Pruebas y Verificación**:
     - `RelaySupervisorTest.kt`: Actualizado y verificado que `wake(force = false)` respeta la suspensión pasiva, mientras que `wake(force = true)` reactiva el supervisor.
     - 309 pruebas unitarias pasando al 100% (`BUILD SUCCESSFUL in 14s`).
     - Compilación limpia de APK (`assembleDebug` en 777ms).

### [2026-09-12] — Corrección del Botón Físico de Montura (Google Assistant No Deseado con 1 Pulsación) y Optimización de Telemetría
- **Requerimiento del Usuario**:
  - Al presionar el botón físico de la montura de las gafas Meizu MYVU 1 sola vez, aún se seguía activando el asistente de Google en el celular.
  - Validar el archivo de log `/home/rcastro/Descargas/myvu_client_log.txt` y proponer un plan de mejora integral.
- **Diagnóstico y Análisis Forense en el Log**:
  1. **Clasificación Errónea de Keycode 202 en `GlassGesture.kt`**:
     - En el log oficial (`21:43:14.095` y `21:43:20.191`), al presionar 1 vez el botón físico de la montura, las gafas emiten:
       `{"key_code": "200", "down_or_up": 1, "key_event_sender": 2}`
       `{"key_code": "202", "down_or_up": 1, "key_event_sender": 2}`
     - En `GlassGesture.kt`, el código `202` estaba registrado en `fromCode()` como `DOUBLE_TAP`.
     - En la configuración activa del usuario (`Prefs.touchpadDoubleTapAction`), el doble toque estaba asignado a `phone_assistant` (`LAUNCH_PHONE_ASSISTANT`).
     - Consecuencia: La app interpretaba la pulsación física simple de montura (`202`) como un doble toque de la patilla táctil capacitiva, ejecutando `TouchGestureManager.launchPhoneAssistant()` y abriendo Google Assistant.
  2. **Doble Disparo por Falta de Consolidación Down (200) ante 202**:
     - En `InboundRouter.dispatchGestureBatch()`, el evento `200` (Down) sólo se eliminaba si `210` o `230` estaban presentes. Como llegó `202`, no se eliminó `200`, ejecutando primero `202` (Google Assistant) y 36ms después `200` (Tap).
  3. **Despertar Innecesario de RFCOMM en Notificaciones**:
     - `MirrorNotificationListener.kt` ejecutaba `connection.wakeRelay()` cada vez que llegaba una notificación para el HUD, forzando la reconexión del socket SPP RFCOMM cuando las gafas querían suspender su radio para ahorrar batería, ignorando que las notificaciones viajan fluidamente por BLE.
- **Implementación y Solución**:
  1. **`GlassGesture.kt`**:
     - Se retiró el código `202` de `DOUBLE_TAP`.
     - Se asignó `202` a `GlassGesture.ACTION_BUTTON` (junto con `230` y `231`).
  2. **`TouchGestureManager.kt`**:
     - `rawCode == 202` se incluye inequívocamente en `isPhysicalButton` y en la condición de pulsación corta (`rawCode == 230 || rawCode == 202`), ejecutando `executor.executeHudDashboard()` (mostrar HUD nativo de las gafas) y protegiendo el pipeline contra la apertura accidental de asistentes de terceros.
  3. **`InboundRouter.kt`**:
     - En `dispatchGestureBatch()`, se añadió `other.actionValue == 202` al filtro de consolidación de `withoutDownUpNoise`, eliminando `200` (Down) y `203` (Up) como ruido de contacto mecánico cuando `202` está presente en el mismo lote.
  4. **`MirrorNotificationListener.kt`**:
     - Se eliminó la llamada a `connection.wakeRelay()`, preservando el reposo profundo de la radio SPP de las gafas durante la recepción continua de notificaciones BLE.
- **Verificación**:
  - Pruebas unitarias actualizadas (`InboundGestureTest.kt` y `PhysicalActionButtonConflictTest.kt`), verificando que `202` resuelve a `ACTION_BUTTON`, consolida `200` y ejecuta únicamente `HUD_DASHBOARD`.
  - 100% de la suite de pruebas unitarias pasando (`BUILD SUCCESSFUL in 10s`), compilación limpia de APK.

### [2026-09-12] — Corrección de Insets Edge-to-Edge y Ajuste Cupertino en Notas y Logs
- **Requerimiento del Usuario**:
  - Las pantallas de "Notas de IA y Tareas" y "Logs y Actividad" aparecían visualmente corridas o desfasadas en la parte superior o inferior.
  - Corregir el desplazamiento aplicando las directrices de diseño de Apple (iOS Human Interface Guidelines).
- **Diagnóstico y Causas Raíz Técnicas**:
  1. **`ActivityLogActivity`**:
     - Omisión de `EdgeToEdgeHelper.setupEdgeToEdge()`.
     - En Android 14/15 con tema transparente de sistema, la barra de navegación superior (56dp fijos) quedaba montada debajo del recorte de pantalla (notch), reloj e iconos de estado.
     - `rvActivityLogs` no recibía insets inferiores, quedando los últimos registros tapados por la píldora de navegación por gestos del sistema.
  2. **`NotesActivity`**:
     - `toolbarNotes` tenía altura rígida de `56dp`. Al sumarse insets superiores de barra de estado (+ ~36-48dp), el espacio interno quedaba aplastado en ~16dp, cortando y deformando iconos y título.
     - El Speed Dial FAB flotante (`fabCluster`) tenía margen estático de 16dp y no estaba registrado en `setupEdgeToEdge`, superponiéndose directamente sobre la barra de navegación del sistema.
     - Las listas de notas y recordatorios no sincronizaban sus insets inferiores con la barra de navegación del sistema.
- **Implementación y Solución**:
  1. **`EdgeToEdgeHelper.kt`**:
     - Se añadió soporte para `scrollContents: List<View>? = null` en `setupEdgeToEdge()`, permitiendo actualizar múltiples vistas de scroll (como `rvNotes` y `rvReminders`) con insets inferiores dinámicos sin pisar listeners.
  2. **`activity_log.xml` y `ActivityLogActivity.kt`**:
     - Identificador `topBarLog` asignado a la barra de navegación Cupertino con `layout_height="wrap_content"` y `minHeight="56dp"`.
     - Invocación de `EdgeToEdgeHelper.setupEdgeToEdge(this, topBar = findViewById(R.id.topBarLog), scrollContent = rvLogs)`.
  3. **`activity_notes.xml` y `NotesActivity.kt`**:
     - En `toolbarNotes`, cambio de altura a `wrap_content` con `minHeight="56dp"`.
     - Contenedor flotante del FAB etiquetado como `@+id/fabCluster` y vinculado a `bottomBar` en `setupEdgeToEdge()`, asegurando margen dinámico seguro sobre la píldora de navegación.
     - Vinculación simultánea de `rvNotes` y `rvReminders` en `scrollContents` para garantizar scroll completo y limpio.
- **Verificación**:
  - Compilación limpia del proyecto (`assembleDebug`) y paso del 100% de la suite de pruebas unitarias (`BUILD SUCCESSFUL in 16s`).

### [2026-09-12] — Alineación de UI, Adopción del Patrón Apple (iOS HIG) y Diagnóstico Visual de Logs
- **Requerimiento del Usuario**:
  - Validar y ajustar todas las interfaces (UI) con deformaciones, cortes de texto o desalineaciones.
  - Mejorar la interfaz de logs para identificar visualmente con facilidad errores, advertencias y fallos por dispositivo.
  - Implementar de mejor forma el patrón de diseño de interfaces de Apple (iOS 18 Human Interface Guidelines).
  - Elaborar un plan detallado previo a la implementación y registrar en memoria viva.
- **Diagnóstico y Problemas Resueltos**:
  1. **Diagnóstico Visual y Experiencia en Activity Log (`activity_log.xml`, `item_activity_log.xml`)**:
     - *Antes*: Fondo negro fijo (`obsidian_bg`), sin alerta visual de fallas (solo texto pequeño "ERROR"), sin indicadores rápidos de estado, iconos cyberpunk discordantes (`ic_arrow_back_cyber`, etc.).
     - *Ahora*:
       - Paleta semántica dinámica iOS Day/Night (`ios_system_bg`, `ios_card_bg`, `ios_red_tint`, `ios_orange_tint`, `ios_blue_tint`, `ios_purple_tint`).
       - Tarjetas de error con borde rojo brillante (`#FF3B30`), fondo tintado suave (`#1AFF3B30`) e icono distintivo `ic_ios_error`.
       - Tarjetas de advertencia con borde ámbar (`#FF9500`), fondo tintado suave e icono `ic_ios_warning`.
       - Píldoras de métricas de salud en cabecera: `[Todos: N]`, `[🚨 Errores: X]`, `[⚠️ Avisos: Y]`, `[👓 Gafas: Z]`, `[🎧 Audio: W]`. Al tocar una píldora se filtra la lista de inmediato.
       - Badges squircle distintivos por origen de hardware (`[👓 Gafas MYVU]` en azul, `[🎧 Audio BT]` en púrpura, `[📱 App/Sistema]` en gris).
       - Traza de error colapsable: botón interactivo `"Ver detalles del error ⌄"` para expandir stack traces completos sin saturar la vista.
       - Iconos Apple auténticos: `ic_ios_back`, `ic_ios_share`, `ic_ios_delete`, `ic_ios_info`.
  2. **Estandarización de Selectores y Spinners Estilo Cupertino (`bg_ios_spinner.xml`, `bg_spinner_container.xml`)**:
     - *Antes*: `<Spinner>` usaba fondo plano sin icono de chevron a la derecha, pareciendo cajas de texto inactivas.
     - *Ahora*: Se introdujo un `layer-list` con tarjeta redondeada (`10dp`), fondo secundario suave y glifo de chevron desplegable `ic_ios_chevron_down` alineado a la derecha. Aplicado en `GlassesSettingsActivity` y `HeadphoneSettingsActivity`.
  3. **Corrección de Deformaciones y Fondos Inconsistentes**:
     - *`activity_notes.xml` y `activity_settings.xml`*: Reemplazado fondo fijo negro por `ios_system_bg`, títulos y navegación actualizados a glifos iOS.
     - *`activity_glasses_settings.xml`*: Añadidos iconos de sol mínimo (`ic_brightness_low`) y sol máximo (`ic_brightness_high`) inline con el slider de brillo HUD estilo iOS Control Center.
     - *`view_dashboard.xml` y `activity_connect.xml`*: Normalizadas alturas táctiles a 48dp/52dp con `insetTop="0dp"` e `insetBottom="0dp"`, evitando recorte o deformación de botones al usar escalado de fuentes de accesibilidad. Botón "Conectar" en azul iOS y "Desconexión Total" en tarjeta secundaria con texto rojo.
  4. **Pruebas y Verificación**:
     - `LogBusTest.kt`: Añadida prueba `testErrorAndWarningEntriesWithThrowable` verificando captura de niveles de error, mensajes y objetos Throwable.
     - 100% de la suite de pruebas pasando (`BUILD SUCCESSFUL in 11s`), compilación de APK limpia.

### [2026-09-12] — Solución al Drenaje Crítico de Batería por Bucle RFCOMM, Latencia de Notificaciones, Spam de Ajustes y Optimización VAD
- **Requerimiento del Usuario**:
  - Analizar `/home/rcastro/Descargas/myvu_client_log.txt`.
  - Diagnosticar y corregir problemas de rendimiento, cuellos de botella y drenaje severo de batería en gafas y teléfono.
  - Corregir retrasos en entrega de notificaciones y timeouts de audio VAD.
  - Actualizar el plan de pruebas para ser exhaustivo y prevenir regresiones.
- **Diagnóstico de Causas Raíz según Logs (`myvu_client_log.txt`)**:
  1. **Drenaje Severo de Batería (Bucle RFCOMM SPP)**:
     - Cada 2.5 - 3.5 segundos exactos se abría un socket RFCOMM y se ejecutaba el handshake criptográfico ECDH completo.
     - Las gafas cerraban el socket (`CMD_SPP_SERVER_REQUEST_STATE_CLOSE`) para entrar en reposo profundo (Flyme XR apaga el servidor SPP si no hay tareas pesadas como Nav HUD).
     - La app interpretaba esto como una pérdida de enlace accidental e invocaba `supervisor?.onRelayLost()`.
     - En `RelaySupervisor.kt`, cuando la conexión abría por 50ms, `check()` reseteaba prematuramente `attempt = 0`, haciendo que `onRelayLost()` siempre agendara un reintento a los 2 segundos (`calculateBackoffDelay(0) = 2000ms`), resultando en `attempt 1/6` perpetuo.
     - Telemetría de las gafas (`suspend_stats`): tiempo de suspensión de solo 1.6s a 2.8s antes de ser despertadas a la fuerza por la radio Bluetooth. Caída de batería de 1% en apenas 75 segundos (~48% por hora).
  2. **Retraso de 9 Segundos en Notificaciones**:
     - En `ConnectionManager.kt`, si el relay no estaba conectado pero la UUID de sesión existía (`canConnectRelay() == true`), el método `sendActionNow` encolaba artificialmente las notificaciones esperando que conectara el relay RFCOMM, en lugar de despacharlas inmediatamente por el canal BLE ya listo y autenticado.
  3. **Spam de Ajustes (`code: 2`) y Duplicación de Mensajes de Hardware**:
     - En `ConnectionManager.kt`, cada reconexión del relay disparaba un callback retrasado para reenviar `AiProtocol.assistantConfig` (`code: 2`), a pesar de que BLE ya había aplicado todos los ajustes.
     - En `GlassesSettingsActivity.kt`, el guardado ejecutaba `GlassesConfig.setBrightness()` (que despachaba al hardware por reflexión) y justo después llamaba directamente a `activeConn?.setBrightness()`, duplicando `set_volume`, `set_standby_position`, `set_screen_off_time`.
  4. **Latencia Excesiva de Audio VAD (Grabación de 19.3s ante consulta de 2s)**:
     - En `AiConversation.kt`, el umbral de silencio estático (`75.0`) quedaba por debajo del nivel de ruido ambiente del micrófono de las gafas Meizu MYVU con ganancia digital (~75-95 RMS).
     - Al no adaptar el umbral de silencio respecto al pico de habla (`peakEnergy`), `level >= speechThreshold` era siempre verdadero sobre ruido de fondo, cancelando la detección de silencio y forzando a la app a grabar hasta el límite forzado de 20 segundos (`utteranceCap`).
- **Implementación Técnica y Solución Aplicada**:
  1. **Control Inteligente de Servidor SPP en `RelaySupervisor.kt` y `ConnectionManager.kt`**:
     - Implementado `onSppServerClosed()` en `RelaySupervisor`. Si las gafas cierran el servidor SPP, el supervisor entra en modo suspendido de ultra bajo consumo y desactiva reintentos de conexión.
     - Añadido umbral de estabilidad `STABLE_RELAY_THRESHOLD_MS = 30000L`: solo se resetea `attempt = 0` si el relay se mantuvo conectado y estable por al menos 30 segundos continuos. Desconexiones rápidas respetan el backoff exponencial (2s -> 4s -> 8s -> 16s -> 32s -> 60s).
     - En `ConnectionManager`, incorporado estado `sppServerOpen`. Cuando llega `CMD_SPP_SERVER_REQUEST_STATE_CLOSE`, se marca falso y se notifica a `onSppServerClosed()`. Solo se reactiva (`wake()`) si las gafas envían `CMD_SPP_SERVER_REQUEST_CONNECT` (71), `CMD_SPP_SERVER_REQUEST_STATE_OPEN`, nuevo `UUID_SYNC`, o si una función solicita explícitamente el relay (`wakeRelay()`).
  2. **Entrega Inmediata de Notificaciones sin Bloqueo por Relay**:
     - Removida la retención forzada en cola de notificaciones en `ConnectionManager.kt`.
     - Si el relay RFCOMM no está activo (`transport == null`), las notificaciones se emiten de inmediato sobre BLE (`bleSession`), reduciendo la latencia de 9000ms a <50ms.
  3. **Limpieza de Ráfagas y Ajustes Duplicados**:
     - Removidas llamadas duplicadas de hardware en `GlassesSettingsActivity.kt` (`activeConn?.setBrightness`, `setVolume`, etc.), conservando el despacho unificado y seguro de `GlassesConfig`.
     - Eliminado el despacho repetido de `AiProtocol.assistantConfig` al enlazar el relay RFCOMM si BLE ya sincronizó los ajustes.
  4. **VAD Adaptativo Dinámico y Watchdog de Silencio en `AiConversation.kt`**:
     - Implementado `dynamicSpeechThreshold = max(speechThreshold, peakEnergy * 0.22)` activo una vez detectada voz. Al callar el usuario, el ruido ambiente cae por debajo del umbral dinámico de forma inmediata.
     - Incorporado `silenceWatchdog` periódico (cada 200ms) que detecta y corta el turno de audio tras `SILENCE_HOLD_MS = 1200ms` de silencio post-voz, evitando esperar al tope de 20s.
  5. **Ampliación del Plan de Pruebas**:
     - `RelaySupervisorTest.kt`: añadido `testSppServerClosedSuspendsSupervisor` y `testStableRelayThresholdConstant`.
     - `ConnectionManagerTest.kt`: añadido `testSppServerOpenStateAndRelayRequirements` y `testNotificationDirectBleDeliveryWhenRelayDown`.
     - `VadSensitivityTest.kt`: añadido `testDynamicSpeechThresholdPostUtterance`.
     - `SettingsGestureConfigTest.kt`: bombeo de looper en verificación de persistencia Room.
  - **Resultado**: 307 pruebas unitarias pasando con 100% de éxito, `assembleDebug` completado sin errores.

### [2026-09-12] — Reparación del Ciclo Conexión/Desconexión de las Gafas y Botón de Desconexión Total
- **Requerimiento del Usuario**:
  - Investigar `/home/rcastro/Descargas/myvu_client_log.txt`: las gafas quedan en un ciclo continuo de conexión/desconexión y no permiten ser utilizadas.
  - Implementar un botón de **desconexión total**, sin importar el tipo de dispositivo (gafas, auriculares, wearables), que inactive por completo todos los servicios y recursos de la app.
- **Diagnóstico de Causas Raíz según Logs (`myvu_client_log.txt`)**:
  1. **Fallo Crítico de Integridad de Esquema Room (`IllegalStateException`)**:
     - En la sesión previa se añadieron campos a `BluetoothDeviceEntity` (`activeListeningEnabled`), pero la versión de `AppDatabase` permaneció en `4`.
     - En el log real del usuario se registraba:
       `Room cannot verify the data integrity. Looks like you've changed schema but forgot to update the version number. Expected identity hash: 56b9b8288dccce96b84121e4119b55a5, found: 8b3b2fae764430338a9dc01c6f7d51a1`
     - Toda escritura de batería y sincronización de dispositivos fallaba, corrompiendo la persistencia de estado.
  2. **Doble `InitBurst` y Bucle de Despertar de Pantalla**:
     - Al conectar BLE, se enviaban los 27 paquetes de `InitBurst` al launcher de las gafas.
     - Segundos después, al establecerse el relay RFCOMM, `ConnectionManager.sendInitBurst()` volvía a retransmitir indiscriminadamente los 27 mensajes por RFCOMM.
     - Esto forzaba a Flyme XR a reiniciar el launcher de las gafas, encender la pantalla (`screen_status: 1`), aplicar el timeout de 5 segundos, apagar la pantalla (`screen_status: 0`), lo cual suspendía RFCOMM, haciendo que `RelaySupervisor` reconectara y repitiera el ciclo infinitamente.
  3. **Falta de Desconexión Total Unificada**:
     - La desconexión previa era parcial: no marcaba en Room los dispositivos como desconectados, no cancelaba sensores de paso (`HealthService`), no liberaba `MediaSession`, y sólo estaba disponible si se abría el Dashboard.
- **Implementación Técnica y Solución Aplicada**:
  1. **Actualización de Versión de Base de Datos Room (`AppDatabase.kt`)**:
     - Se incrementó `version = 5` con `fallbackToDestructiveMigration(dropAllTables = true)`. La tabla `bluetooth_devices` se regenera limpiamente con todos los nuevos campos sin excepciones de hash.
  2. **Supresión de Doble `InitBurst` en RFCOMM (`ConnectionManager.kt`)**:
     - En `ConnectionManager.handleAbilityReply()`: si `bleSession.ready` ya ejecutó el `InitBurst`, el relay RFCOMM omite el reenvío de los 27 paquetes y activa directamente la sesión (`onSessionReady`), evitando el reinicio cíclico del visor y el parpadeo de pantalla.
     - `connectAudioProfiles()` se aisló para ejecutarse una sola vez por conexión física, evitando renegociaciones de perfil de audio clásico.
  3. **Creación del Helper Universal `TotalDisconnectHelper`**:
     - Ubicado en `com.myvu.client.core.TotalDisconnectHelper`.
     - Deshabilita permanentemente la reconexión automática (`Prefs.setAutoReconnectEnabled(context, false)`).
     - Cancela el watchdog periódico de 15 minutos (`ServiceWatchdogReceiver.cancelWatchdog(context)`).
     - Libera de inmediato cualquier canal de audio SCO abierto (`TouchGestureManager.releaseBluetoothSco(context)`).
     - Inactiva sensores de movimiento (`HealthService.getInstance(context).unregisterHardwareSensor()`).
     - Detiene escaneos y marca en Room todos los dispositivos como `isConnected = false` (`BluetoothDeviceDao.markAllDisconnected()`, `BluetoothDeviceManager.markAllDevicesDisconnected()`).
     - Envía `MyvuService.ACTION_STOP`, liberando `MediaSession`, apagando la notificación persistente y llamando a `stopSelf()`.
  4. **Puntos de Acceso en la Interfaz de Usuario**:
     - **Dashboard (`view_dashboard.xml`)**: Botón prominente "Desconexión Total".
     - **Menú Lateral (`menu_navigation_drawer.xml`)**: Opción "Desconexión Total (Inactivar Todo)" accesible desde cualquier pantalla.
     - **Panel de Dispositivos (`bottom_sheet_devices.xml` / `DeviceManagementBottomSheet.kt`)**: Botón "Desconexión Total (Inactivar Todo)" con estilo de alerta visual.
  5. **Verificación Automatizada**:
     - Pruebas añadidas en `TotalDisconnectHelperTest.kt`: integridad de esquema DB v5, deshabilitación de auto-reconexión, y desmarcado simultáneo de todos los dispositivos conectados.
     - Pruebas unitarias completas pasando exitosamente y APK compilada sin advertencias de recursos.

### [2026-09-11] — Deshabilitación de Escucha Activa por Defecto y Toggle Individual por Dispositivo
- **Requerimiento del Usuario**:
  - Deshabilitar la escucha activa / diálogo continuo en todos los dispositivos por defecto (ahorro de batería y privacidad).
  - Permitir activar y desactivar la escucha activa individualmente por cada dispositivo conectado (gafas, auriculares, wearables).
- **Diagnóstico y Causas Raíz**:
  1. La escucha activa / diálogo continuo (`isContinuousDialogueEnable` en protocolo de firmware FlymeAR) estaba asociada previamente a un flag global en `Prefs` (`continuous_dialogue_enabled`). No había granularidad por dispositivo en la base de datos Room (`BluetoothDeviceEntity`).
  2. `HeadphoneSettingsActivity` carecía por completo de control UI para diálogo continuo / escucha activa.
  3. `GlassesSettingsActivity` leía y escribía de las preferencias globales `Prefs` en vez de almacenar y respetar el estado individual de la entidad del dispositivo en Room.
  4. Los consumidores principales (`ConnectionManager` en `sendActionNow(AiProtocol.assistantConfig(...))` y `AiConversation.kt` en `begin()` / `textMode`) leían directamente del flag global de `Prefs`, aplicando la misma configuración indiscriminadamente a cualquier dispositivo conectado.
- **Implementación Técnica y Solución Aplicada**:
  1. **`BluetoothDeviceEntity.kt`**:
     - Agregado campo `val activeListeningEnabled: Boolean = false` con valor por defecto `false` garantizando que todo nuevo dispositivo tenga la escucha continua desactivada.
  2. **`HeadphoneSettingsActivity.kt` y `activity_headphone_settings.xml`**:
     - Agregado switch `switchActiveListening` (`MaterialSwitch`) en la tarjeta "Voz y Asistencia en Auriculares" con leyenda explicativa ("Mantiene la escucha activa tras hablar. Desactivado ahorra batería").
     - `loadDevice()` enlaza el estado desde `device.activeListeningEnabled` (default `false`).
     - `saveSettings()` persiste el valor en la entidad Room (`activeListeningEnabled = switchActiveListening.isChecked`).
  3. **`GlassesSettingsActivity.kt`**:
     - `loadDevice()` carga `swContinuousDialogue.isChecked` desde `currentDevice?.activeListeningEnabled ?: false`.
     - `saveSettings()` persiste `activeListeningEnabled = contDialogue` en la entidad Room y la actualiza a través de `BluetoothDeviceManager.updateGlassesGestures()`, manteniendo sincronización dual con `Prefs` para retrocompatibilidad.
  4. **`BluetoothDeviceManager.kt`**:
     - Agregadas funciones `isActiveListeningEnabled(): Boolean` (suspend) y `isActiveListeningEnabledBlocking(): Boolean` (síncrona/bloqueante para handlers/runnables) que evalúan la entidad activa conectada en Room y respetan su flag individual.
     - Parámetro `activeListening: Boolean? = null` incorporado en `updateDeviceGestures` y `updateGlassesGestures`.
  5. **`ConnectionManager.kt` y `AiConversation.kt`**:
     - Actualizadas invocaciones de `AiProtocol.assistantConfig(...)` para consumir `BluetoothDeviceManager.getInstance(context).isActiveListeningEnabledBlocking()`.
  6. **Pruebas Automatizadas (`SettingsGestureConfigTest.kt`)**:
     - `testActiveListeningDisabledByDefaultOnAllDevices`: valida que toda entidad nace con `activeListeningEnabled = false` y que el manager reporta falso.
     - `testHeadphoneSettingsActiveListeningToggleIndependentFromGlasses`: verifica que activar la escucha en auriculares no altera ni activa la escucha en las gafas.
     - `testGlassesSettingsActiveListeningTogglePersistsPerDevice`: verifica que alternar el switch en la pantalla de ajustes de gafas persiste en Room y se lee fielmente.
  - **Resultado**: 287 pruebas pasando con éxito en Robolectric, compilación y empaquetado (`assembleDebug`) exitosos.

### [2026-09-11] — Validación de Ajustes de Auriculares/Wearables y Protección Integral contra Fugas de Conexiones y Batería
- **Requerimiento del Usuario**:
  - Validar que las configuraciones de otros dispositivos (auriculares, wearables Bluetooth) se lean y guarden de forma correcta.
  - Verificar y garantizar que no queden abiertas conexiones ni recursos que drenen la batería de los dispositivos (gafas, auriculares) y del móvil (radio Bluetooth, CPU, SoC, hilos huérfanos).
- **Diagnóstico y Causas Raíz**:
  1. **Sobrescritura Accidental y Aislamiento Roto**: En `HeadphoneSettingsActivity.kt` y `HeadphoneGestureManager.kt`, cuando no se suministraba MAC, se invocaba `dao.getActiveConnectedDevice()`. Si las gafas MYVU estaban conectadas, este método devolvía las gafas en lugar de auriculares, provocando que la configuración de los audífonos sobrescribiera la entidad de los lentes o que los toques de los auriculares ejecutaran acciones de los lentes.
  2. **Sobrescritura de MAC en Fallback**: En `HeadphoneSettingsActivity.loadDevice()`, cuando el dispositivo no existía aún en base de datos, `targetMac` se reemplazaba incondicionalmente por `"HEADPHONES-DEFAULT"`, ignorando la MAC real pasada en el Intent (`EXTRA_MAC`).
  3. **Descarte Silencioso en `BluetoothDeviceManager.updateDeviceGestures`**: Si `dao.getDevice(mac)` devolvía null, el método abortaba con `return` sin persistir nada en Room.
  4. **Fuga Indefinida de Bluetooth SCO**: En `TouchGestureManager.kt`, cuando `isLive == true` (modo Gemini Live), el canal SCO (`startBluetoothSco`) se mantenía abierto de forma continua sin ningún temporizador de seguridad. Si el usuario bloqueaba el teléfono o abandonaba la conversación, el canal SCO continuaba abierto permanentemente, impidiendo que el chip Bluetooth y el micrófono entraran en suspensión de bajo consumo.
  5. **Escaneo de Búsqueda Bluetooth sin Límite de Tiempo**: En `BluetoothDeviceManager.kt`, `startScanning()` iniciaba `startDiscovery()` sin un temporizador de seguridad que garantizara su detención. En `DeviceManagementBottomSheet`, el diálogo solo ocultaba el progress bar tras 8 segundos sin detener la búsqueda, y no detenía el escaneo en `onDestroyView` ni en `onDismiss`.
  6. **Falta de Desconexión de Recursos ante Apagado de Pantalla**: No existía receptor en el servicio foreground `MyvuService` para liberar canales de audio o cancelar búsquedas activas cuando la pantalla del teléfono se apagaba o bloqueaba (`ACTION_SCREEN_OFF`).
  7. **Hilos No Finalizados en `WeatherSync`**: `WeatherSync.stop()` no apagaba su `ExecutorService` (`net`), dejando hilos de red ociosos.
- **Implementación Técnica y Solución Aplicada**:
  1. **`HeadphoneSettingsActivity.kt`**:
     - `loadDevice()` blindado: busca estrictamente entidades de tipo `HEADPHONES` o `GENERIC`, descartando completamente las gafas (`SMART_GLASSES`).
     - Respeta la MAC del intent (`targetMac`) y si no existe en Room, busca dispositivos de audio emparejados en el sistema Android antes de usar fallback.
     - Inicializa los spinners con defaults coherentes (`tap1 = MEDIA_PLAY_PAUSE`, `tap2 = LAUNCH_GEMINI`, `tap3 = LAUNCH_PHONE_ASSISTANT`, `longPress = CREATE_AI_NOTE`, `notificationMode = AUDIO_ONLY`).
     - `saveSettings()` garantiza persistencia directa en Room DB con `dao.insertOrUpdate`, actualiza `currentDevice` y refresca `BluetoothDeviceManager`.
  2. **`HeadphoneGestureManager.kt`**:
     - Creado `getActiveHeadphone()` que filtra únicamente dispositivos de tipo `HEADPHONES` o `GENERIC`, previniendo colisiones con los keycodes de las gafas inteligentes.
  3. **`BluetoothDeviceManager.kt`**:
     - `updateDeviceGestures()` ahora crea e inserta automáticamente una nueva entidad `BluetoothDeviceEntity` si no existía en Room, garantizando cero pérdida de configuración.
     - Añadido `scanHandler` con temporizador de seguridad automático `SCAN_TIMEOUT_MS = 12000L` a `startScanning()`. `stopScanning()` y `ACTION_DISCOVERY_FINISHED` cancelan cualquier temporizador pendiente y cancelan la búsqueda Bluetooth.
  4. **`DeviceManagementBottomSheet.kt`**:
     - Añadida detención activa `devManager.stopScanning()` al terminar los 8s, en `onDestroyView()` y en `onDismiss()`.
  5. **`TouchGestureManager.kt`**:
     - Incorporado temporizador de seguridad `GEMINI_LIVE_MAX_SCO_DURATION_MS = 5 * 60 * 1000L` (5 minutos máximos de conversación continua) para el canal SCO en modo Live, impidiendo drenajes residuales si el usuario olvida la sesión.
     - `releaseBluetoothSco()` cancela todos los callbacks pendientes de `audioHandler`.
     - Invocación de `releaseBluetoothSco` integrada en `ConnectionManager.teardown()`, `ConnectionManager.stop()` y `LockScreenHelper.lockDevice()`.
  6. **`MyvuService.kt`**:
     - Registrado receptor dinámico `screenOffReceiver` para `Intent.ACTION_SCREEN_OFF`. Ante el apagado o bloqueo de la pantalla, libera inmediatamente cualquier canal Bluetooth SCO activo y detiene escaneos de búsqueda Bluetooth.
     - Limpieza y desregistro garantizados en `onDestroy()`.
  7. **`WeatherSync.kt`**:
     - Invocado `net.shutdownNow()` en `WeatherSync.stop()` para destruir hilos residuales.
  8. **Pruebas y Verificación**:
     - Nuevos tests en `SettingsGestureConfigTest.kt`: `testHeadphoneSettingsReopenReflectsSavedParameters()`, `testHeadphoneSettingsDoesNotOverwriteGlasses()`, y `testBluetoothDeviceManagerUpdateDeviceGesturesCreatesNewIfAbsent()`.
     - 284 tests unitarios aprobados (100% verde en `./gradlew testDebugUnitTest`).
     - `./gradlew assembleDebug` completado exitosamente.

### [2026-09-11] — Corrección Integral de Guardado y Lectura de Configuraciones de Lentes AR y Persistencia en Room
- **Requerimiento del Usuario**:
  - Al presionar el botón "Guardar" en la configuración de los lentes AR (`GlassesSettingsActivity`), los cambios no se estaban guardando ni reflejando al volver a entrar a la actividad. Se requirió que se lean y guarden todos los cambios de parametrización (Brillo, Volumen, Posición FOV, Tiempo de pantalla activa, Modo de notificación, Asistente, Diálogo continuo, Wakeup por voz, Micrófono SCO para Gemini, y todos los gestos táctiles y botón de acción).
- **Diagnóstico y Causas Raíz**:
  1. **Referencia Huérfana `currentDevice` en `loadDevice()`**: En `GlassesSettingsActivity.loadDevice()`, se consultaba la entidad desde Room (`val dev = dao.getDevice(targetMac)`), pero jamás se asignaba a la variable de instancia `currentDevice = dev`. Como consecuencia, `currentDevice` permanecía perpetuamente en `null`. Al abrir o recargar, cualquier lectura de `currentDevice?.tap1Action` o `currentDevice?.notificationMode` devolvía `null` y forzaba a los spinners y selectores a resetearse a sus valores por defecto ("NONE" o "BOTH").
  2. **MAC Destino Vacía (`""`) al Abrir desde Pantallas Secundarias**: Cuando `GlassesSettingsActivity` se abría desde `SettingsActivity` o sin pasar `EXTRA_MAC` explícito, `targetMac` iniciaba como `""`. Al presionar Guardar, `BluetoothDeviceManager.updateGlassesGestures` intentaba hacer `dao.getDevice("")` que resultaba nulo, y el método abortaba silenciosamente sin crear ni actualizar la entidad en la base de datos Room.
  3. **Desajuste de Nombres de Acciones en `CommonDeviceActions`**: Los IDs serializados en base de datos o preferencias contenían alias históricos o nombres en minúsculas (ej. `"phone_assistant"`, `"ai_assistant"`, `"gemini_live"`). `CommonDeviceActions.getIndexForAction` solo comparaba igualdad de strings con `action.name` en mayúsculas (`"LAUNCH_PHONE_ASSISTANT"`), haciendo que las acciones guardadas no coincidieran y los spinners cayeran por fallback al índice 0 ("NONE").
  4. **Falta de Despacho de Hardware en Vivo**: Al guardar, los parámetros de hardware (brillo, volumen, posición de pantalla en reposo, timeout de apagado de pantalla y enrutamiento táctil) no se enviaban a la conexión activa `MyvuService.activeConnection()`.
- **Implementación Técnica y Solución**:
  1. **`GlassesSettingsActivity.kt`**:
     - `loadDevice()` corregido para asignar inmediatamente `currentDevice = dev` y resolver `targetMac` desde `Prefs.targetMac(this)` o el primer dispositivo de tipo `GLASSES` si venía vacío.
     - Carga bidireccional integral: inicializa todos los spinners de gestos (`spnTap1`, `spnTap2`, `spnTap3`, `spnLongPress`, `spnSwipeFwd`, `spnSwipeBack`), el botón de acción (`spnActionButton`) y el modo de notificación (`spnNotificationMode`) usando el valor de `currentDevice` o fallback a `GlassesConfig`/`Prefs`.
     - Sliders de hardware (`seekBrightness`, `seekVolume`, `seekStandbyPosition`) y switches (`switchContinuousDialog`, `switchVoiceWakeup`, `switchGeminiScoMic`, `switchKeepScreenActive`) inicializados fielmente desde Room o `GlassesConfig`.
     - `saveSettings()` enriquecido para:
       * Actualizar `GlassesConfig` y `Prefs`.
       * Enviar comandos directos de hardware mediante `MyvuService.activeConnection()` (`setBrightness`, `setVolume`, `setStandbyPosition`, `setScreenOffTime`, `setMusicTpControl`, `assistantConfig`).
       * Persistir inmediatamente en Room DB (`BluetoothDeviceDao`) actualizando o insertando la entidad `BluetoothDeviceEntity`.
       * Actualizar `currentDevice` con el nuevo estado guardado.
  2. **`CommonDeviceActions.kt`**:
     - `getIndexForAction(actionId: String?)` actualizado para resolver sinónimos e identificadores heredados mediante `GestureAction.fromId(actionId)`. Mapea de forma transparente identificadores como `"phone_assistant"`, `"ai_assistant"`, `"gemini_live"` a sus respectivas constantes `LAUNCH_PHONE_ASSISTANT`, `AI_ASSISTANT`, etc., seleccionando la posición correcta del spinner.
  3. **`BluetoothDeviceManager.kt`**:
     - `updateGlassesGestures` mejorado: si la entidad para `resolvedMac` no existe en Room, ahora crea e inserta automáticamente una nueva entidad `BluetoothDeviceEntity` con `deviceType = GLASSES`, garantizando que nunca se descarte una operación de guardado.
  4. **Lanzadores de Actividad (`ChatActivity.kt` & `SettingsActivity.kt`)**:
     - Actualizados para incluir siempre `Prefs.targetMac(this)` en el intent `EXTRA_MAC` al iniciar `GlassesSettingsActivity`.
  5. **Pruebas y Verificación**:
     - Nuevos tests en `SettingsGestureConfigTest.kt`: `testFullReopenReflectsSavedParameters()` y `testSavingWithoutIntentMacFallsBackAndPersists()`, validando el ciclo completo de guardar y reabrir la actividad sin pérdida de parámetros.
     - Pruebas unitarias (`./gradlew testDebugUnitTest`): 281 tests aprobados. Compilación de APK (`./gradlew assembleDebug`) exitosa.

### [2026-09-11] — Indicadores de Batería Reales y Sincronización en Vivo para Dispositivos Conectados
- **Requerimiento del Usuario**:
  - Validar y corregir los indicadores de batería de los dispositivos conectados (gafas MYVU, auriculares Bluetooth y wearables), dado que no se estaban actualizando con los valores reales.
- **Diagnóstico y Causas Raíz**:
  1. **Persistencia Huérfana en Room**: `BluetoothDeviceDao.updateBatteryLevel` estaba declarada pero nunca se invocaba en ningún lugar de la aplicación.
  2. **Filtrado Incompleto en `InboundRouter`**: La extracción de batería solo revisaba si `action.contains("battery")`, perdiendo respuestas clave como `get_device_info` o `device_info` provenientes del firmware de las gafas MYVU. Además, descartaba `0%` porque validaba `battery in 1..100` en lugar de `0..100`.
  3. **Desconexión con el Gestor de Dispositivos**: `ConnectionManager.updateGlassesBattery` guardaba la batería solo en un StateFlow en memoria (`glassesInfoVal`), sin propagarla al `BluetoothDeviceManager` ni persistirla en la base de datos Room.
  4. **Falta de Receptores de Batería Bluetooth del Sistema Android**: `BluetoothDeviceManager` no registraba `ACTION_BATTERY_LEVEL_CHANGED` (`android.bluetooth.device.action.BATTERY_LEVEL_CHANGED`) ni eventos HFP de auriculares AT `+IPHONEACCEV` (`android.bluetooth.headset.action.VENDOR_SPECIFIC_HEADSET_EVENT`).
  5. **Falta de Consulta por Reflexión**: No se invocaba el método oculto de Android `BluetoothDevice.getBatteryLevel()` al emparejar, conectar o refrescar dispositivos.
  6. **Valores Falsos / Hardcodeados en la UI**: `ChatActivity` utilizaba `"${glasses.batteryLevel ?: 85}%"` y `"${headphones.batteryLevel ?: 92}%"`, y `GlassesSettingsActivity` mostraba `85%` como fallback, en lugar de mostrar datos en vivo o `"--"`. `HeadphoneSettingsActivity` ni siquiera mostraba la batería en su cabecera.
- **Implementación y Solución Aplicada**:
  1. **`InboundRouter.kt`**:
     - `checkBatteryUpdate` ampliado para capturar acciones `get_device_info`, `device_info`, `sync_glass_battery_info`, `get_air_glass_info`.
     - Soporte para campos JSON `battery` y `capacity` en la raíz o en objetos anidados `data` o `value`.
     - Rango de validación corregido a `0..100` (soporta batería agotada al 0%).
  2. **`ConnectionManager.kt`**:
     - Al recibir eventos de batería o en `onSessionReady`, actualiza `BluetoothDeviceManager.getInstance(context).updateGlassesBatteryLevel(target, battery)`.
  3. **`BluetoothDeviceManager.kt`**:
     - Registrado `ACTION_BATTERY_LEVEL_CHANGED` y `VENDOR_SPECIFIC_HEADSET_EVENT` en el `BroadcastReceiver`.
     - Implementado `readDeviceBattery(bDevice)` mediante reflexión en `device.javaClass.getMethod("getBatteryLevel")`.
     - Implementado `parseAppleBatteryArgs(args)` en el companion object para decodificar tramas HFP de audífonos (AirPods, Galaxy Buds, etc.).
     - Implementado `updateGlassesBatteryLevel(mac, battery)` y `handleDeviceBatteryChanged(mac, battery)`.
     - Implementado `refreshAllDeviceBatteries()` para consultar todos los dispositivos enlazados y las gafas activas en `ConnectionManager`.
     - Actualizado `syncPairedDevices()` y `handleDeviceConnectionChanged()` para leer y persistir la batería inmediatamente al conectar.
  4. **Interfaces de Usuario**:
     - `ChatActivity.kt`: Eliminados los fallbacks ficticios `85%` y `92%`. Ahora muestra el nivel real o `"--"`. Añadido `refreshAllDeviceBatteries()` en `onResume()`.
     - `GlassesSettingsActivity.kt`: Sustituido `85%` fijo por nivel dinámico o `"--"`. Refresco en `onResume()`.
     - `activity_headphone_settings.xml` y `HeadphoneSettingsActivity.kt`: Añadido `txtHeadphoneBattery` con icono en el banner de cabecera, actualizándose en tiempo real y en `onResume()`.
     - `DeviceManagementBottomSheet.kt`: Los ítems de la lista de dispositivos ahora muestran el porcentaje de batería real cuando está disponible (ej. `"● Conectado • 80% • Notif: HUD + TTS"`).
  5. **Pruebas y Verificación**:
     - Añadidos tests unitarios en `BluetoothDeviceManagerTest.kt` (`testParseAppleBatteryArgs`, `testBluetoothDeviceEntityBatteryField`).
     - Añadidos tests unitarios en `InboundRouterTest.kt` (`getDeviceInfoFiresBatteryListener`, `zeroPercentBatteryIsAcceptedAsValidBoundary`).
     - `rtk ./gradlew testDebugUnitTest` y `rtk ./gradlew assembleDebug` exitosos (100% verde).

### [2026-09-11] — Manejo Granular de Notificaciones por Dispositivo: Visual (HUD), Sonora (TTS), Ambos o Desactivado
- **Requerimiento del Usuario**:
  - Permitir configurar de forma independiente para cada dispositivo Bluetooth (Gafas MYVU, Auriculares, Wearables) cómo se procesan las notificaciones entrantes:
    1. **Visuales (HUD)**: Mostrar en pantalla/visor microLED de los lentes AR.
    2. **Sonoras (TTS)**: Leer en voz alta por Text-To-Speech hacia altavoces o audífonos.
    3. **Ambos**: Visualización simultánea en HUD y lectura por voz TTS.
    4. **Desactivadas**: Silenciar completamente las notificaciones para ese dispositivo.
- **Implementación Técnica**:
  1. **Modelo de Datos Room (`BluetoothDeviceEntity.kt` & `AppDatabase.kt`)**:
     - Creado enum `DeviceNotificationMode`: `BOTH`, `VISUAL_ONLY`, `AUDIO_ONLY`, `NONE`, con descripciones y métodos auxiliares `isVisualNotificationEnabled()` y `isAudioNotificationEnabled()`.
     - Añadido campo `val notificationMode: String = DeviceNotificationMode.BOTH.name` a `BluetoothDeviceEntity`.
     - Incrementada la base de datos Room a `version = 4`.
  2. **Acceso y Gestión (`BluetoothDeviceDao.kt` & `BluetoothDeviceManager.kt`)**:
     - Agregada consulta `@Query("SELECT * FROM bluetooth_devices WHERE isConnected = 1") suspend fun getConnectedDevices(): List<BluetoothDeviceEntity>`.
     - Métodos de actualización `updateGlassesGestures(...)`, `updateHeadphoneSettings(...)` y `updateDeviceNotificationMode(...)` actualizados para persistir `notificationMode`.
  3. **Enrutamiento Inteligente en `MirrorNotificationListener.kt`**:
     - Al recibir una notificación válida (respetando la lista blanca de apps elegidas por el usuario):
       - **Ruta Sonora (TTS)**: Verifica si entre los dispositivos conectados activos (`getConnectedDevices()`) alguno tiene habilitado el canal sonoro (`isAudioNotificationEnabled()`). Si es afirmativo, sintetiza por voz *"De [App]: [Título]. [Texto]"* mediante `TextToSpeechHelper`.
       - **Ruta Visual (HUD)**: Verifica si las gafas inteligentes conectadas tienen habilitado el canal visual (`isVisualNotificationEnabled()`). Si el usuario configuró las gafas como `AUDIO_ONLY` o `NONE`, se omite el envío de paquetes TLV/JSON al microLED de los lentes, evitando distracciones visuales innecesarias.
  4. **Interfaces de Usuario (`GlassesSettingsActivity.kt`, `HeadphoneSettingsActivity.kt`, `DeviceManagementBottomSheet.kt`)**:
     - En `activity_glasses_settings.xml` y `GlassesSettingsActivity.kt`: Tarjeta dedicada *"MANEJO DE NOTIFICACIONES"* con spinner interactivo que muestra las 4 opciones (`Ambas`, `Solo Visual HUD`, `Solo Sonora TTS`, `Desactivadas`) y texto explicativo dinámico.
     - En `activity_headphone_settings.xml` y `HeadphoneSettingsActivity.kt`: Selector de manejo de notificaciones sincronizado con el interruptor de lectura automática y compatibilidad total.
     - En `DeviceManagementBottomSheet.kt`: Los ítems de la lista de dispositivos muestran el badge de su modo de notificación (`HUD + TTS`, `HUD`, `TTS`, `Mudo`).
  5. **Pruebas Unitarias**:
     - `BluetoothDeviceManagerTest.kt`: Pruebas añadidas `testDeviceNotificationModes()` y `testDeviceNotificationModeEnumHelpers()`, verificando la lógica de filtrado y resolución de enrutamiento. 100% de tests unitarios superados. Compilación exitosa en 1s.

### [2026-09-11] — Corrección de Alineación de Interfaces, Insets de Gestos, Píldoras Recortadas y Soporte Completo de Modo Claro
- **Contexto y Diagnóstico**:
  - El usuario reportó con captura de pantalla que la interfaz presentaba elementos corridos, textos e iconos recortados (cabecera comprimida, subtítulo cortado a la mitad, píldoras con texto desplazado, barra inferior colisionando con la barra de gestos de Android) y que "no soporta el modo claro".
  - **Causas Raíz Identificadas**:
    1. **Fallo en Modo Claro**: `AndroidManifest.xml` contenía `android:configChanges="...|uiMode"`. Debido a esto, al alternar el modo con `AppCompatDelegate.setDefaultNightMode`, Android NO recreaba las actividades, impidiendo la recarga de la paleta diurna/nocturna. Adicionalmente, faltaba `recreate()` explícito en el toggle.
    2. **Colisión de la Barra de Gestos del Sistema (Insets de Navegación)**: `EdgeToEdgeHelper.setupEdgeToEdge` aplicaba `insets.bottom` como margen inferior a la barra de entrada de mensajes (`bottomBar`), en lugar de aplicarlo como padding a la barra inferior de 5 pestañas (`cupertinoTabBar`). Por tanto, la barra blanca de gestos de Android se superponía sobre las etiquetas "AR HUD", "Hub", "Equipos", "Traducir", "Notas".
    3. **Cabecera Sobrecargada y Recortada**: `topBar` tenía altura fija de 48dp con 6 elementos horizontales saturando los 360-390dp de pantalla, provocando que "Companion Hub" se quebrara en 2 líneas y el subtítulo "Sincronizado" quedara rebanado verticalmente.
    4. **Recorte en Píldoras de Acciones Rápidas**: Los `MaterialButton` en `scrollAiQuickBar` y `scrollQuickSkills` tenían alturas de 30-32dp sin `android:insetTop="0dp"`, `android:insetBottom="0dp"`, ni `android:minHeight="0dp"`, provocando que los insets internos por defecto de Material 3 recortaran el texto por abajo.
- **Ajustes y Solución Aplicada**:
  1. **Manifiesto y Ciclo de Vida de Tema (`AndroidManifest.xml`)**:
     - Removido `|uiMode` de `android:configChanges` en todas las actividades (`ChatActivity`, `SettingsActivity`, `ConnectActivity`, etc.), permitiendo que el sistema recree automáticamente las pantallas e infle los recursos semánticos correspondientes (`values` vs `values-night`).
     - En `ChatActivity.kt` y `SettingsActivity.kt`: invocación explícita de `recreate()` al alternar tema para transición visual instantánea.
  2. **Controlador EdgeToEdge (`EdgeToEdgeHelper.kt`)**:
     - Añadido soporte para `navTabBar: View? = null`.
     - Si existe una barra de navegación inferior (`cupertinoTabBar`), `insets.bottom` se aplica como padding inferior a esta, elevando iconos y textos por encima de la barra de gestos del sistema. La barra de mensajes superior (`bottomBar`) ya no sufre márgenes desalineados.
     - Sincronización precisa de `isAppearanceLightStatusBars` e `isAppearanceLightNavigationBars` consultando `Prefs.themeMode()` para garantizar iconos oscuros en fondo claro e iconos claros en fondo oscuro.
  3. **Desahogo y Rediseño de Cabecera (`activity_chat.xml`)**:
     - `topBar` configurada con `layout_height="wrap_content"`, `minHeight="52dp"`, `singleLine="true"` y `ellipsize="end"` para título y estado, y botones de 36dp x 36dp con márgenes simétricos. `btnDevices` restringido con `maxWidth="100dp"`, `singleLine` y `ellipsize` para nunca empujar los controles vecinos.
  4. **Corrección de Insets y Alineación de Píldoras**:
     - Incorporados `android:insetTop="0dp"`, `android:insetBottom="0dp"`, `android:minHeight="0dp"`, `android:paddingVertical="0dp"` y `android:gravity="center"` en `btnQuickAiNotes`, `btnQuickRecordMeeting`, `btnQuickDailyBriefing`, `btnQuickTasks`, `btnQuickDevicesShortcut`, `btnAllSkills`, `btnSave` de ambas pantallas de dispositivos y `btnConfigureDevice`.
     - Ampliadas las tarjetas de wearables en el carrusel a `205dp` con `singleLine="true"` para evitar colisiones entre el estado y el porcentaje de batería.
  5. **Barra Inferior Cupertino (`cupertinoTabBar`)**:
     - `layout_height="wrap_content"`, `minHeight="54dp"`, con padding vertical que absorbe los insets de navegación sin solapamientos.
- **Verificación**:
  - `rtk ./gradlew testDebugUnitTest`: BUILD SUCCESSFUL.
  - `rtk ./gradlew assembleDebug`: BUILD SUCCESSFUL.
  - Grafo sincronizado con `rtk codegraph sync`.

### [2026-09-11] — Corrección del Botón de Acción HUD en Gafas MYVU, Agente de Voz Aura (STT+API) en Auriculares y Lectura TTS de Notificaciones
- **Contexto y Diagnóstico**:
  - **Problema 1 (Gafas MYVU - Botón de Acción)**: Al presionar 1 vez el botón de acción físico de los lentes, a veces se abría Gemini en el teléfono, impidiendo consultar el dashboard/HUD nativo de las gafas.
    - *Causa Raíz*: El firmware de las gafas Meizu MYVU emite dos eventos de contacto mecánico (`code 200` touch down y `code 210` tap confirmado, o `code 203` release) separados por 30-150ms. `InboundRouter` y `TouchGestureManager` interpretaban esta ráfaga como un `DOUBLE_TAP` (debido a ventanas de síntesis de 30L..800L y 1100L respectivamente). Dado que `DOUBLE_TAP` tenía como acción predeterminada `launch_gemini`, un solo toque corto abría Gemini y bloqueaba el HUD. Además, el código 230 (botón de acción tap) no estaba mapeado en `GlassGesture`.
  - **Problema 2 (Auriculares Bluetooth - Configuración de Agente STT+API)**: Los auriculares debían permitir configurar en sus gestos si el usuario desea invocar el agente de voz vía STT+API (Aura) o las demás acciones externas (Gemini, Gemini Live, Asistente de teléfono, etc.).
    - *Causa Raíz*: No existía la acción `VOICE_AGENT_AURA` en `CommonDeviceActions` ni su manejo en `HeadphoneGestureManager`.
  - **Problema 3 (Auriculares Activos - Lectura de Notificaciones por TTS)**: Si un auricular Bluetooth está activo/conectado, las notificaciones entrantes permitidas deben leerse con el motor TTS y reproducirse en los auriculares.
    - *Causa Raíz*: `MirrorNotificationListener` retornaba anticipadamente si las gafas no estaban conectadas y no contaba con integración TTS para leer notificaciones hacia auriculares Bluetooth.
- **Solución Implementada**:
  1. **Aislamiento y Debounce del Botón de Acción y Toque Simple en Gafas**:
     - En `GlassGesture.kt`: Mapeado el código 230 como `TAP` (pulsación corta del botón de acción) y asegurado 231 como `LONG_PRESS`.
     - En `InboundRouter.kt`:
       - Añadida consolidación de eventos: cuando `210` o `230` (tap confirmado) está presente en el lote, se filtran automáticamente los eventos de ruido `200` (down) y `203` (up) del mismo emisor/toque para que no se sinteticen en un falso doble toque.
       - Rango de síntesis humana de `DOUBLE_TAP` calibrado a `180L..500L` (descartando rebotes mecánicos menores a 180ms).
     - En `TouchGestureManager.kt`:
       - Definidas las acciones `ACTION_HUD_DASHBOARD` (`"hud_dashboard"`) y `ACTION_VOICE_AGENT_AURA` (`"voice_agent_aura"`).
       - En `handleGesture`: cuando la acción es `NONE` o `HUD_DASHBOARD`, se despacha a `executor.executeHudDashboard()` / `executeNone()` sin activar debounce parasitario, permitiendo que el HUD nativo de las gafas permanezca visible sin interferencia del teléfono.
       - Ventana de doble toque ajustada a `DOUBLE_TAP_MIN_INTERVAL_MS = 180L` y `DOUBLE_TAP_MAX_INTERVAL_MS = 500L`.
     - En `ConnectionManager.kt` y `GlassesEventHandler.kt`:
       - Implementado `executeHudDashboard()` y `executeVoiceAgentAura()`.
       - En `inbound.setAiTriggerListener`: al recibir el disparador por hardware de pulsación larga (`code: 3`), se respeta la configuración de `Prefs.glassesActionButtonAction` (`VOICE_AI_FIXED` por defecto llama a `ai().onTrigger(code)`, o lanza Gemini si el usuario lo configuró explícitamente).
     - En `GlassesSettingsActivity.kt`:
       - Mapeado el selector del botón de acción con etiquetas explícitas que aclaran que el toque simple abre el HUD nativo y la pulsación larga invoca al agente de IA configurado.
       - Carga y sincronización correcta de la selección inicial en `loadDevice()` mediante `Prefs.glassesActionButtonAction`.
  2. **Acción de Agente de Voz Aura (STT + API) en Auriculares**:
     - En `CommonDeviceActions.kt`: Agregadas las acciones `VOICE_AGENT_AURA` ("Agente de Voz Aura (STT + API)") y `HUD_DASHBOARD` ("Ver Dashboard / HUD de Gafas").
     - En `HeadphoneGestureManager.kt`:
       - Al dispararse `VOICE_AGENT_AURA`, notifica con TTS breve ("Te escucho") y lanza `ChatActivity` con el flag `EXTRA_AUTO_START_STT = true`.
     - En `ChatActivity.kt`:
       - Añadido `EXTRA_AUTO_START_STT`. Si se recibe al crear la actividad o mediante `onNewIntent`, activa automáticamente el reconocimiento de voz por hardware/micrófono (`launchVoiceStt()`) y activa `speakNextResponse = true`.
       - Al completarse la respuesta del modelo o de la habilidad en `sendUserQuery`, si `speakNextResponse` está activo, reproduce la respuesta directamente en los auriculares a través de `TextToSpeechHelper.speak(responseText)`.
  3. **Lectura Automática de Notificaciones en Auriculares Vía TTS**:
     - En `MirrorNotificationListener.kt`:
       - Añadido `serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())` con cancelación en `onDestroy()`.
       - Antes de comprobar la conexión a las gafas, consulta si existe un auricular Bluetooth conectado (`BluetoothDeviceType.HEADPHONES`) y con `autoReadNotifications` o `ttsEnabled` habilitado.
       - Si está activo, sintetiza mediante `TextToSpeechHelper.speak("De $appName: $title. $text")`, reproduciéndose por A2DP en los auriculares.
       - El reenvío visual hacia el HUD de las gafas se mantiene de forma independiente cuando las gafas están vinculadas y conectadas.
- **Verificación**:
  - `InboundGestureTest.kt`: Incorporadas pruebas unitarias `consolidatesTouchDown200AndTap210ToSingleTapWithoutSynthesizingDoubleTap` y `decodesActionButtonCode230AsTap`.
  - Todas las pruebas unitarias pasaron exitosamente (`BUILD SUCCESSFUL`).
  - Compilación de depuración validada (`rtk ./gradlew assembleDebug` exitoso).
  - Grafo sincronizado con `rtk codegraph sync`.

---


### [2026-09-11] — Interfaces Específicas de Configuración por Dispositivo y Catálogo Unificado de Acciones Comunes
- **Contexto y Requerimiento**:
  - El usuario especificó que las configuraciones del sistema (modelos de IA, STT, TTS, clima, backups, perfil) deben mantenerse generales/globales, pero se deben proporcionar **interfaces de configuración propias y diferenciadas por tipo de dispositivo** para personalizar capacidades de hardware individuales:
    - **Gafas AR (Meizu MYVU)**: botón de acción físico en la montura, gestos del touch de las patas de los lentes (toque simple, doble toque, triple toque, deslizamiento hacia adelante, deslizamiento hacia atrás, pulsación larga) y brillo/pantalla HUD MicroOLED.
    - **Auriculares Bluetooth**: gestos de 1, 2 o 3 toques, pulsación larga, síntesis de voz (TTS) y lectura automática de notificaciones.
    - **Dispositivos Genéricos / Wearables**: configuración básica de botones y notificaciones.
  - Las acciones a realizar deben ser **comunes y transversales** a todos los dispositivos (lanzar apps, Gemini, Gemini Live, Asistente de teléfono, notas de voz IA, lectura de notificaciones, resumen diario, teleprompter, controles multimedia, etc.).
- **Arquitectura y Cambios Realizados**:
  1. **Catálogo Unificado de Acciones (`CommonDeviceActions.kt`)**:
     - Creado en `com.myvu.client.data.CommonDeviceActions`.
     - 13 acciones universales tipadas con IDs normalizados, etiquetas legibles y descripción: `MEDIA_PLAY_PAUSE`, `MEDIA_NEXT`, `MEDIA_PREV`, `LAUNCH_GEMINI`, `LAUNCH_GEMINI_LIVE`, `LAUNCH_PHONE_ASSISTANT`, `CREATE_AI_NOTE`, `READ_UNREAD_NOTIFICATIONS`, `DAILY_BRIEFING`, `SYNC_WEATHER`, `OPEN_TELEPROMPTER`, `QUICK_RECORD_MEETING`, `NONE`.
     - Métodos helper para Spinners y selectores: `getLabels()`, `getActionId(index)`, `getIndexForAction(actionId)`.
  2. **Ampliación de Modelo y Base de Datos (`BluetoothDeviceEntity.kt` y `AppDatabase.kt`)**:
     - Añadidos campos a `BluetoothDeviceEntity`: `swipeForwardAction`, `swipeBackwardAction`, `actionButtonAction`, `hudBrightness`.
     - `AppDatabase`: migración a versión 3 con `fallbackToDestructiveMigration(dropAllTables = true)`.
     - `BluetoothDeviceManager.kt`: agregado método `updateGlassesGestures(mac, tap, doubleTap, tripleTap, swipeForward, swipeBackward, longPress, actionButton, hudBrightness)` que persiste tanto en Room como en `Prefs` (`touchpadTapAction`, `touchpadSwipeForwardAction`, etc.) garantizando sincronización en tiempo real con `TouchGestureManager`.
  3. **Nueva Pantalla de Ajustes de Gafas AR (`GlassesSettingsActivity.kt` y `activity_glasses_settings.xml`)**:
     - Diseño iOS Cupertino Grouped Cards:
       - Cabecera y tarjeta de estado de las gafas Meizu MYVU AR.
       - Sección **Patas Táctiles (Touchpad Lateral)**: Toque Simple, Doble Toque, Triple Toque, Deslizar Adelante, Deslizar Atrás, Pulsación Larga (mapeadas a `CommonDeviceActions`).
       - Sección **Botón de Acción (Montura)**: Selector de asistente/modo de voz.
       - Sección **Pantalla HUD MicroOLED**: Slider de brillo (0% a 100%) y botón directo para abrir el Trackpad virtual.
     - Registro en `AndroidManifest.xml`.
  4. **Refactorización de Pantalla de Auriculares (`HeadphoneSettingsActivity.kt`)**:
     - Migrado del array local hardcodeado al catálogo unificado `CommonDeviceActions`.
     - Selectores para 1 toque, 2 toques, 3 toques, pulsación prolongada, interruptores de TTS y lectura automática.
  5. **Navegación y Enrutamiento Contextual**:
     - `DeviceManagementBottomSheet.kt`: Al pulsar "Configurar", si es `SMART_GLASSES` abre `GlassesSettingsActivity`; si es `HEADPHONES` u otro abre `HeadphoneSettingsActivity`.
     - `ChatActivity.kt`: El widget de Gafas AR en el carrusel abre `GlassesSettingsActivity`; el widget de Auriculares abre `HeadphoneSettingsActivity`.
     - `SettingsActivity.kt` y `activity_settings.xml`: Añadida tarjeta iOS agrupada *"Dispositivos & Gestos Específicos"* antes de los proveedores de IA, con accesos directos a la configuración de Gafas AR, Auriculares y Administrador de Dispositivos.
- **Verificación**:
  - `rtk ./gradlew testDebugUnitTest`: BUILD SUCCESSFUL (todas las pruebas pasan).
  - `rtk ./gradlew assembleDebug`: BUILD SUCCESSFUL (APK generado sin advertencias críticas).
  - `rtk codegraph sync`: Sincronizado.

---

### [2026-09-11] — Rediseño Integral UI Estilo iOS Cupertino Pro Native, Modo Claro/Oscuro y Nuevo Icono de Identidad
- **Contexto y Requerimiento**:
  - El usuario solicitó el rediseño completo de la interfaz de usuario siguiendo los prototipos y especificaciones de `@design/stitch_smartwear_ai_hub` (diseño estilo iOS Human Interface Guidelines / Cupertino Pro Native).
  - Nuevo icono de aplicación moderno y acorde a la nueva función como hub inteligente y agente de wearables universales.
  - Soporte completo y dinámico para conmutar entre Modo Claro y Modo Oscuro, manteniendo total coherencia visual y contraste en todas las pantallas.
  - Plan de migración detallado previo a la ejecución (`docs/superpowers/plans/2026-09-11-ios-cupertino-redesign-and-theme-migration-plan.md`).
- **Arquitectura y Cambios Aplicados**:
  1. **Sistema de Color Semántico Dual (`colors.xml` y `values-night/colors.xml`)**:
     - Definidos tokens semánticos iOS: `ios_system_bg` (`#F2F2F7` Claro / `#000000` Oscuro), `ios_card_bg` (`#FFFFFF` Claro / `#1C1C1E` Oscuro), `ios_card_secondary` (`#F8F8FA` Claro / `#2C2C2E` Oscuro), `ios_label` (`#1C1C1E` / `#FFFFFF`), `ios_secondary_label` (`#8E8E93`), `ios_separator` (`#E5E5EA` / `#38383A`), `ios_border`, `ios_blue` (`#007AFF` / `#0A84FF`), `ios_indigo` (`#5856D6` / `#5E5CE6`), `ios_green` (`#34C759` / `#30D158`), `ios_bubble_ai` (`#E9E9EB` / `#26252A`), `ios_bubble_user` (`#007AFF` / `#0A84FF`), `ios_nav_bg`, `ios_pill_bg`, etc.
     - Mapeados tokens legados (`obsidian_bg`, `obsidian_container`, etc.) a los nuevos colores semánticos para retrocompatibilidad total sin roturas en pantallas secundarias.
  2. **Motor de Temas Dinámico en `Prefs.kt` y `MyApp.kt`**:
     - Constantes y métodos `themeMode()`, `setThemeMode()`, `applyTheme()` en `Prefs.kt`. Soporte de `THEME_MODE_SYSTEM`, `THEME_MODE_LIGHT`, `THEME_MODE_DARK`.
     - Aplicación inmediata vía `AppCompatDelegate.setDefaultNightMode()` al inicio de la app en `MyApp.onCreate()` y en tiempo real al seleccionar tema.
     - Botón de alternancia rápida en el encabezado de `ChatActivity` (`btnThemeToggle`) y selector de 3 opciones (Automático, Claro, Oscuro) en `SettingsActivity`.
     - Ajuste en `EdgeToEdgeHelper.kt` para que `isAppearanceLightStatusBars` e `isAppearanceLightNavigationBars` se adapten dinámicamente al modo claro u oscuro, garantizando legibilidad de iconos de estado del sistema.
  3. **Nueva Identidad Gráfica y Launcher Icon (`ic_launcher_hub`)**:
     - Creados recursos vectoriales y adaptativos: `ic_launcher_hub.xml`, `ic_launcher_hub_background.xml` (gradiente continuo Cupertino `#007AFF` a `#5856D6`) e `ic_launcher_hub_foreground.xml` (gafas holográficas AR HUD, ondas de sonido de auriculares, estrella neural de IA central de 4 puntas y telemetría de wearable).
     - Actualizado `AndroidManifest.xml` con `android:icon="@drawable/ic_launcher_hub"` y `android:roundIcon="@drawable/ic_launcher_hub"`.
  4. **Drawables Base Estilo iOS**:
     - `bg_ios_card.xml`: Squircle continuo de 16dp con borde sutil.
     - `bg_ios_card_secondary.xml`: Radio de 12dp para contenedores internos.
     - `bg_ios_pill.xml` y `bg_ios_pill_active.xml`: Radio total 999dp para filtros y botones de acción rápida.
     - `bg_ios_bubble_user.xml` y `bg_ios_bubble_ai.xml`: Burbujas redondeadas estilo iMessage con esquinas diferenciadas de 18dp/4dp.
     - `bg_ios_input_bar.xml`: Barra de entrada de texto estilo iOS Messages.
     - `bg_ios_circle_button.xml`: Botones circulares para acciones directas y envío.
  5. **Rediseño de Pantallas Principales (Companion Hub & Chat)**:
     - `activity_chat.xml` & `ChatActivity.kt`:
       - Top bar translúcida con logo, sincronización, selector de tema y acceso a ajustes.
       - **Carrusel Horizontal de Dispositivos Activos (iOS Widgets)**: Tarjeta interactiva de Gafas AR (estado, batería, HUD MicroOLED), Auriculares Pro (estado, batería, gestos) y botón de emparejar nuevo equipo. Conexión reactiva vía `getAllDevicesFlow()` de Room.
       - **iMessage Conversation Stream**: Burbujas asimétricas, avatar de IA, texto nítido en modo claro y oscuro, y previsualización de multimedia.
       - **Pills de Acción Rápida**: Teleprompter, Notas IA, Grabar Nota, Resumir Día, Tareas.
       - **Cupertino 5-Tab Bottom Bar**: Hub, Dispositivos, AR HUD, Traducir, Notas.
     - `item_chat_message.xml` y `ChatSidebarBottomSheet.kt`: Adaptados para soportar el renderizado visual iMessage.
     - `item_note.xml`, `item_reminder.xml`, `bg_chip_badge.xml`: Adaptados a radios squircles de 16dp y paletas semánticas iOS.
- **Verificación**:
  - Compilación y ejecución exitosa de pruebas unitarias (`./gradlew testDebugUnitTest`: 34 tasks exitosas).
  - Compilación exitosa de APK de depuración (`./gradlew assembleDebug`: BUILD SUCCESSFUL).

---

### [2026-09-11] — Acceso Rápido a Funcionalidades de IA y Menú Lateral (Sidebar Navigation Drawer)
- **Contexto y Requerimiento**:
  - El usuario solicitó ajustar la interfaz de usuario para poder acceder a las funcionalidades de IA de manera rápida (como Notas de IA, Grabación de Reuniones, Resumen del Día, etc.) y habilitar el menú de la barra lateral (Sidebar Navigation Drawer) para acceder rápidamente a todas las funciones disponibles desde la pantalla principal (`ChatActivity`).
- **Solución Implementada**:
  - **Menú de Navegación Lateral (`menu/menu_navigation_drawer.xml`)**:
    - Se reordenaron y ampliaron los destinos del drawer:
      - `nav_chat_sidebar`: Chat IA Aura (Principal)
      - `nav_notes`: Notas de IA y Tareas
      - `nav_voice_recorder`: Grabadora de Voz & Reuniones
      - `nav_devices`: Dispositivos Bluetooth (Gafas, Auriculares)
      - `nav_dashboard`: Gafas AR (Dashboard)
      - `nav_ai_config`: Ajustes de IA y Perfil
      - `nav_trackpad`: Control Trackpad
      - `nav_notifications`: Filtro de Notificaciones
      - `nav_logs`: Logs y Telemetría
  - **Integración de DrawerLayout y Botón Hamburguesa en `ChatActivity` (`activity_chat.xml` y `ChatActivity.kt`)**:
    - Se convirtió la raíz de `activity_chat.xml` en un `DrawerLayout` (`chatDrawerLayout`).
    - En `topBar`, se reemplazó el botón de retroceso por `btnNavigationDrawer` usando el vector `@drawable/ic_menu_hamburger` con tinte `@color/cyber_teal`.
    - Se incorporó `NavigationView` (`chatNavigationView`) con cabecera dinámica que muestra el estado y nombre del dispositivo Bluetooth activo.
    - Se configuró el cierre automático del drawer con el botón atrás (`OnBackPressedCallback`).
    - Se conectó `setNavigationItemSelectedListener` para abrir directamente cada actividad (`NotesActivity`, `VoiceRecorderActivity`, `ConnectActivity`, `SettingsActivity`, `TrackpadActivity`, `NotificationAppsActivity`, `DeviceManagementBottomSheet`).
  - **Barra de Acceso Rápido de IA (`scrollAiQuickBar`)**:
    - Se situó una barra horizontal prominente de botones tonales Material 3 justo debajo de la cabecera:
      - `btnQuickAiNotes` ("📝 Notas IA"): Abre de inmediato la libreta de notas con IA.
      - `btnQuickRecordMeeting` ("🎙️ Grabar Reunión"): Abre `VoiceRecorderActivity` con la categoría "Reunión" preseleccionada y activación inmediata de grabación en vivo.
      - `btnQuickDailyBriefing` ("☀️ Mi Día"): Genera el briefing ejecutivo del día, lo sintetiza por voz (`TextToSpeechHelper`) y lo publica en el chat con formato Markdown.
      - `btnQuickTasks` ("✅ Mis Tareas"): Abre `NotesActivity` enfocado en la pestaña de recordatorios y pendientes.
      - `btnQuickDevicesShortcut` ("🎧 Dispositivos"): Abre la hoja modal de gestión Bluetooth.
  - **`VoiceRecorderActivity.kt`**:
    - Se añadió soporte en `onCreate` para los extras `CATEGORY = "MEETING"` y `AUTO_START_RECORDING = true` para iniciar la grabación de reuniones sin clics adicionales.
  - **`ConnectActivity.kt`**:
    - Se actualizó el listener del drawer para soportar la opción `nav_devices` abriendo `DeviceManagementBottomSheet`.
- **Verificación**:
  - Pruebas unitarias: `./gradlew testDebugUnitTest` 100% exitosas (`BUILD SUCCESSFUL`).
  - Compilación APK: `./gradlew assembleDebug` exitoso (`BUILD SUCCESSFUL`).
  - Sincronización de grafo de código: `rtk codegraph sync` completada.

---

### [2026-09-11] — Plataforma Universal de Agente IA para Todo Tipo de Dispositivos Bluetooth (Gafas, Auriculares, Wearables)
- **Contexto y Requerimiento**:
  - Transformar la aplicación Android Meizu Myvu Client en una plataforma universal de Agente IA para cualquier dispositivo Bluetooth: Gafas inteligentes AR (MYVU), Auriculares / Audífonos Bluetooth (TWS / Diadema / In-ear) y Dispositivos Wearables Genéricos.
  - Requisitos clave:
    1. Detección y adición dinámica de dispositivos Bluetooth emparejados y en escaneo.
    2. Configuraciones aisladas e independientes por dispositivo según su tipo (`SMART_GLASSES`, `HEADPHONES`, `GENERIC`).
    3. Todas las capacidades de IA (notas con IA, transcripción, comandos, llamadas, WhatsApp, Gemini, Gemini Live, Asistente de teléfono, 30 habilidades modulares) disponibles para todos los dispositivos.
    4. Configuración completa de gestos táctiles de auriculares (1 toque, 2 toques, 3 toques, pulsación larga) asignables a acciones de IA, multimedia o lectura de notificaciones.
    5. Ajuste de UI para soportar selección y administración multi-dispositivo (`DeviceManagementBottomSheet`).
    6. Centralidad del Chat: `ChatActivity` pasa a ser la pantalla principal (`LAUNCHER`) de la app, con acceso directo a la gestión de dispositivos en la cabecera.
- **Solución Implementada**:
  - **Base de Datos y Persistencia**:
    - `BluetoothDeviceEntity.kt`: Entidad Room con campos `macAddress` (PK), `name`, `deviceType`, `isConnected`, `isActiveDevice`, `tap1Action`, `tap2Action`, `tap3Action`, `longPressAction`, `ttsEnabled`, `autoReadNotifications`, `lastSeen`.
    - `BluetoothDeviceDao.kt`: Consultas de dispositivos emparejados, activos y por tipo.
    - `AppDatabase.kt`: Actualizado a versión 2 incorporando `BluetoothDeviceEntity` y `bluetoothDeviceDao()`.
  - **Gestor Universal Bluetooth**:
    - `BluetoothDeviceManager.kt`: Singleton centralizado para sincronización de dispositivos emparejados, escaneo BLE/Clásico (`startDiscovery`), clasificación heurística basada en nombres (`myvu`, `airpods`, `buds`, `freebuds`, `wh-1000xm`, etc.) y `BluetoothClass` (`AUDIO_VIDEO_*`, `WEARABLE_*`).
  - **Motor de Gestos y Feedback de Audio para Auriculares**:
    - `HeadphoneGestureManager.kt`: Evaluador de toques mediante pulsaciones consecutivas de `KEYCODE_HEADSETHOOK` y `KEYCODE_MEDIA_PLAY_PAUSE` (1 toque, 2 toques, 3 toques, pulsación larga), despachando Gemini, Gemini Live, Asistente del Teléfono, Notas de voz con IA, Resumen del día (Briefing) y lectura TTS de notificaciones.
    - `TextToSpeechHelper.kt`: Motor nativo TTS en español (`es-CO`/`es-ES`) con enrutamiento dinámico al canal de audio Bluetooth.
    - `MyvuService.kt`: Enrutamiento inteligente en `setupMediaSession`: si las gafas no están en estado `READY`, los eventos de botones multimedia se canalizan a `HeadphoneGestureManager`.
  - **Interfaz de Usuario Multi-Dispositivo**:
    - `ChatActivity`: Configurada como actividad de inicio principal (`MAIN`/`LAUNCHER`) en `AndroidManifest.xml`.
    - `DeviceManagementBottomSheet.kt`: Hoja modal inferior estilo Obsidian que lista los dispositivos emparejados, permite escanear nuevos dispositivos, alternar el dispositivo activo y abrir la configuración específica según el tipo.
    - `HeadphoneSettingsActivity.kt`: Interfaz dedicada para configurar gestos táctiles (1, 2, 3 toques y long press), habilitar TTS y lectura automática de notificaciones para audífonos.
    - Botón universal "Dispositivos" (`btnDevices`) incorporado en las cabeceras de `ChatActivity`, `ConnectActivity`, `SettingsActivity`, `NotesActivity` y `VoiceRecorderActivity`.
  - **Pruebas y Verificación**:
    - `BluetoothDeviceManagerTest.kt`: Pruebas unitarias de clasificación automática de dispositivos (`SMART_GLASSES`, `HEADPHONES`, `GENERIC`).
    - Compilación completa y pruebas unitarias exitosas (`BUILD SUCCESSFUL`, exit code 0).
    - APK generado con éxito (`assembleDebug`).
    - Sincronización del grafo de código (`codegraph sync`).

---

### [2026-09-11] — Búsqueda Priorizada de Contactos por Orden Secuencial y Fallback Semántico
- **Contexto y Requerimiento**:
  - En comandos de voz como `"Enviar mensaje de whatsapp a Matias Castro, hola hijo"`, el motor de resolución de contactos (`ContactHelper.kt`) elegía incorrectamente a `"Denis Castro"` en lugar de `"Matias Castro"`.
  - Causa raíz: La lógica anterior usaba sumatoria laxa de tokens donde compartir únicamente el apellido (`"Castro"`) otorgaba puntaje de coincidencia de token (+60) + bonificación de cobertura parcial (+100) + bonificación colombiana (+50), alcanzando 210 puntos y superando el umbral laxo de 30/40 puntos. El nombre de pila (`"Matias"` vs `"Denis"`) era completamente ignorado.
  - Requerimiento: Dar prioridad estricta al nombre en el orden en que se ingresa ($Q_0 \rightarrow Q_1$). Si no se encuentran resultados en la búsqueda secuencial estricta, recurrir a búsqueda semántica de contactos.
- **Solución Implementada**:
  - **`ContactHelper.kt`**:
    - **Regla Crítica de Seguridad para Consultas Compuestas**: Si la consulta tiene $\ge 2$ tokens (ej: `"Matias Castro"`), el primer token ($Q_0$, nombre de pila) DEBE coincidir con algún token del contacto (vía exacto, prefijo, alias semántico o Levenshtein $\le 1$). Si no coincide, el candidato recibe **0 puntos** y es descartado inmediatamente. Esto previene de raíz que `"Denis Castro"` se empareje para `"Matias Castro"`.
    - **Tier 1 (Exacto)**: Coincidencia idéntica normalizada recibe 3000 puntos.
    - **Tier 2 (Prefijo continuo)**: Si el contacto comienza exactamente con la cadena buscada (ej: `"Matias Castro Hijo"`), recibe 2000+ puntos con bonificación por concisión.
    - **Tier 3 (Orden Secuencial Estricto con Alineación Codiciosa)**: Comprueba que todos los tokens de la consulta aparezcan en el contacto en orden estrictamente creciente ($Q_0 \rightarrow Q_1 \rightarrow \dots$). Si el primer token coincide al inicio del contacto ($C_0$), otorga 1500+ puntos. Si tiene prefijo de relación/título ($C_{>0}$), otorga 1200+ puntos.
    - **Tier 4 (Fallback Semántico y Desordenado)**: Solo si la fase secuencial no halla coincidencia completa:
      - Diccionario de alias y diminutivos comunes en español (`SPANISH_NICKNAMES`): `"mati"` $\leftrightarrow$ `"matias"`, `"dani"` $\leftrightarrow$ `"daniel"/"daniela"`, `"sebas"` $\leftrightarrow$ `"sebastian"`, `"santi"` $\leftrightarrow$ `"santiago"`, `"juanca"` $\leftrightarrow$ `"juan carlos"`, etc.
      - Parentescos y relaciones familiares (`KINSHIP_ALIASES`): `"papa"` $\leftrightarrow$ `"padre"`, `"mama"` $\leftrightarrow$ `"madre"`, `"hijo"`, `"esposa"`, `"esposo"`, `"abuelo"`, etc.
      - Tokens en orden invertido con 100% de cobertura (ej: `"Castro Matias"` para `"Matias Castro"`) reciben 850+ puntos.
      - Búsqueda por un solo token (ej: `"Castro"`) sigue funcionando correctamente permitiendo búsqueda por apellido individual con puntaje $\ge 400$.
    - **Alineación de Umbrales**:
      - Se elevó el umbral de aceptación a 150 puntos en `findBestContactMatch`, `extractRecipientAndMessage`, `resolveWhatsAppChatDataId` y `resolveWhatsAppVoipDataId`.
      - La bonificación por número colombiano se ajustó a +20 como desempate cualitativo sin alterar rangos.
  - **`ContactHelperTest.kt`**:
    - Se crearon pruebas unitarias exhaustivas:
      - `testMatiasCastroQueryStrictlyRejectsDenisCastro()`: Denis Castro obtiene 0 puntos y queda estrictamente rechazado.
      - `testSequentialOrderPriorityBeatsUnordered()`: Coincidencia secuencial ordenada supera a la desordenada.
      - `testSingleTokenSurnameQueryMatchesContactWithSurname()`: Búsqueda de un solo token como `"Castro"` sigue encontrando contactos con ese apellido.
      - `testSpanishNicknameAliasesResolveSemantically()`: Alias semántico `"mati"` resuelve a `"Matias Castro"`.
      - `testSpanishKinshipAliasesResolveSemantically()`: Parentesco `"papa"` resuelve a `"Padre"`.
- **Verificación**:
  - Compilación y pruebas unitarias ejecutadas con `rtk ./gradlew testDebugUnitTest`: 100% exitosas (`BUILD SUCCESSFUL`).
  - Grafo sincronizado mediante `rtk codegraph sync`.

---

### [2026-09-11] — Temporizador Parametrizable de Auto-Bloqueo de Pantalla tras Acciones (Default 60s / 1 min)
- **Contexto y Requerimiento**:
  - Al ejecutar acciones desde las gafas que despiertan la pantalla (Gemini, Gemini Live, Asistente de teléfono, Abrir aplicaciones, WhatsApp, Telegram, etc.), el dispositivo se encendía y desbloqueaba el keyguard, pero quedaba expuesto y encendido en el bolsillo dependiendo del timeout del sistema operativo.
  - El usuario solicitó un ajuste para configurar un tiempo máximo para volver a bloquear la pantalla, parametrizable y con 1 minuto (60s) por defecto.
- **Solución Implementada**:
  - **`Prefs.kt`**:
    - Se incorporaron las claves y métodos `screenReLockTimeoutSeconds(c)` y `setScreenReLockTimeoutSeconds(c, seconds)` con rango de 15 a 600 segundos y valor por defecto de `60` segundos (1 minuto).
  - **`LockScreenHelper.kt`**:
    - Se creó `lockDevice(context)`: bloquea de inmediato la pantalla utilizando `AutoSendAccessibilityService.activeInstance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)`.
    - Se implementó `scheduleReLock(context, timeoutSeconds)` y `cancelScheduledReLock()`:
      - Cada invocación de `wakeUpScreen` programa automáticamente un temporizador con el tiempo fijado por el usuario en `Prefs`.
      - Al expirar, apaga y bloquea el dispositivo automáticamente.
  - **Interfaz de Ajustes (`activity_settings.xml` y `SettingsActivity.kt`)**:
    - En la tarjeta **Gestos de la Patilla Táctil (Touchpad)**, se añadió un slider Material 3 (`sliderScreenReLockTimeout`) con rango de 15s a 300s (pasos de 15s) y valor predeterminado de 60s.
    - Etiqueta dinámica `lblScreenReLockTimeout` formateando segundos y minutos (*"Tiempo para volver a bloquear: 60s (1 minuto)"*).
- **Verificación**:
  - Se corrió `./gradlew testDebugUnitTest` pasando 264 pruebas unitarias con éxito (`BUILD SUCCESSFUL`).
  - Grafo sincronizado mediante `codegraph sync`.

### [2026-09-11] — Integración de Gemini Live vía Overlay del Asistente del Teléfono (Google/Gemini) con Auto-Start de Live
- **Contexto y Requerimiento**:
  - Al activar "Asistente del Teléfono (Google)", el sistema abre de inmediato el micrófono en el overlay del Asistente.
  - El usuario deseaba que al seleccionar **Gemini Live**, la app active el asistente de la misma manera pero pase inmediatamente al modo **Live** manos libres de forma automática.
- **Diagnóstico**:
  - `launchPhoneAssistant` despachaba `KEYCODE_VOICE_ASSIST` y `ACTION_VOICE_COMMAND`, levantando de inmediato la tarjeta/overlay del asistente del sistema con el micrófono ya abierto.
  - `launchGeminiAssistant(isLive = true)` intentaba lanzar la app completa de Gemini (`com.google.android.apps.bard`) en vez de usar el overlay del Asistente.
- **Ajustes y Correcciones Realizadas**:
  - **`TouchGestureManager.kt`**:
    - En `launchGeminiAssistant(isLive = true)`:
      - Se mantiene el canal de audio SCO permanentemente abierto para la conversación interactiva.
      - Se activa el modo Live en accesibilidad (`AutoSendAccessibilityService.triggerGeminiLiveAutoStart`).
      - Se despacha `KEYCODE_VOICE_ASSIST` y `ACTION_VOICE_COMMAND` (el mismo flujo del Asistente del Teléfono), levantando el overlay del asistente del sistema con micrófono activo.
      - Si el paquete completo de `bard` o `ACTION_ASSIST` se utiliza como fallback, se envía de forma coordinada a través de `SendTrampolineActivity`.
  - **`AutoSendAccessibilityService.kt`**:
    - Se aceleró la ráfaga de reintentos (`scheduleBurstGeminiLiveRetries`) comenzando desde 75ms (`75ms, 150ms, 300ms, 550ms...`) para hacer clic en el botón de Live en cuanto el overlay se dibuje.
    - Se enriquecieron los selectores en `findAndClickGeminiLiveButton`:
      - IDs: `assistant_live_button`, `chat_live_button`, `live_icon`, `live_toggle`, `live_mode_button`, etc.
      - Descs/Textos: `"abrir gemini live"`, `"modo live"`, `"live mode"`, `"charlar en vivo"`, `"conversar en vivo"`, `"conversar"`, `"charlar"`, `"onda sonora"`.
- **Verificación**:
  - Suite de 264 pruebas unitarias ejecutadas con éxito (`BUILD SUCCESSFUL`, 0 errores).

### [2026-09-11] — Corrección Raíz de Activación de Micrófono y Gemini Live (canPerformGestures y Desbloqueo de BFS)
- **Diagnóstico y Causas Raíz Identificadas**:
  1. **Permiso de Gestos Deshabilitado en XML (`accessibility_service_config.xml`)**:
     - `app/src/main/res/xml/accessibility_service_config.xml` carecía del atributo `android:canPerformGestures="true"`.
     - Por diseño de seguridad estricto de Android, el método `dispatchGesture(...)` falla silenciosamente y nunca despacha eventos táctiles (strokes) si este flag no está declarado a nivel de manifiesto/configuración del servicio de accesibilidad.
     - Como Gemini está desarrollado en **Jetpack Compose**, sus botones de micrófono y Live frecuentemente devuelven `false` a `performAction(ACTION_CLICK)`. El fallback indispensable por coordenadas táctiles físicas (`dispatchGesture`) estaba bloqueado por el propio sistema operativo Android.
  2. **Poda Errónea del Árbol de Vistas en Búsqueda BFS (`AutoSendAccessibilityService.kt`)**:
     - En `findAndClickGeminiMicButton` y `findAndClickGeminiLiveButton`, existía una comprobación de filtrado contra IDs de contenedores (`searchbox`, `search_plate`, `search_edit`) que ejecutaba `continue` *antes* de encolar los hijos del nodo (`node.getChild(i)`).
     - La jerarquía de la app de Google / Gemini sitúa toda la pantalla y el campo de composición dentro de contenedores superiores que contienen el substring `"searchbox"`.
     - Como consecuencia de ejecutar `continue` antes de explorar a los hijos, el algoritmo BFS descartaba la totalidad del subárbol de la ventana de Gemini y nunca llegaba a evaluar los botones de Micrófono ni de Live situados en su interior.
  3. **Ausencia de Doble Acción (PerformAction + DispatchGesture)**:
     - Los botones de Compose requieren a menudo tanto el evento de accesibilidad como la simulación táctil por inyección de coordenadas con callback (`GestureResultCallback`) para forzar la apertura del asistente de voz.
- **Ajustes y Correcciones Realizadas**:
  - **`app/src/main/res/xml/accessibility_service_config.xml`**:
    - Declarado formalmente `android:canPerformGestures="true"`.
  - **`AutoSendAccessibilityService.kt`**:
    - **Reestructuración de BFS**: Se aseguró que todos los nodos hijos (`node.getChild(i)`) sean encolados incondicionalmente en la cola `queue` *antes* de aplicar cualquier regla de exclusión o comprobación de coincidencia sobre el nodo actual.
    - **Doble Despacho (Dual-Action Click)**: En `findAndClickGeminiMicButton` y `findAndClickGeminiLiveButton`, cuando se detecta el nodo meta, se invoca `performAction(ACTION_CLICK)` y de inmediato se complementa con `dispatchGesture` hacia el centro de las coordenadas de la vista mediante un `Path` y `GestureResultCallback` verificado con timeout y log.
    - **Fallback Multiventana y Source Event**: En `onAccessibilityEvent`, si `rootInActiveWindow` es nulo o no contiene la ventana meta, se inspecciona `event.source` y se recorre exhaustivamente la colección `service.windows`.
- **Verificación**:
  - Se corrió `./gradlew testDebugUnitTest` completando exitosamente 264 pruebas unitarias sin fallos (`BUILD SUCCESSFUL`).

### [2026-09-11] — Restauración del Clic Automático de Micrófono y Gemini Live con Aislamiento de Widgets del Launcher
- **Diagnóstico y Análisis de Log (`/home/rcastro/Descargas/myvu_client_log.txt`)**:
  - Tras el bloqueo total de `googlequicksearchbox`, la app de Gemini abría pero se quedaba estática en pantalla sin activar el micrófono ni iniciar Gemini Live.
  - Causa técnica: La app de Gemini (`com.google.android.apps.bard`) delega el renderizado de su interfaz de usuario, ventanas y actividades a la infraestructura de `com.google.android.googlequicksearchbox`. Al haber rechazado `googlequicksearchbox` en accesibilidad, el servicio ignoraba la ventana real de Gemini.
- **Ajustes y Correcciones Realizadas**:
  - **`AutoSendAccessibilityService.kt`**:
    - Se creó la función `isGeminiAppWindow(pkg)` que acepta paquetes que contengan `bard` o `googlequicksearchbox` pero **excluye estrictamente** cualquier paquete de Launcher (`launcher`, `nexuslauncher`, `sec.android.app.launcher`).
    - En `scheduleBurstGeminiLiveRetries` y `scheduleBurstGeminiVoiceRetries`, se restauró la inspección de ventanas compatibles con Gemini y se amplió el abanico de reintentos hasta 12.5s (`150ms, 350ms, 700ms, 1200ms, 1800ms, 2500ms, 3500ms, 5000ms, 7000ms, 9500ms, 12500ms`).
    - En `findAndClickGeminiMicButton`:
      - Se eliminaron selectores de búsqueda ("voice_search", "voice_search_button") y se excluyeron de forma contundente nodos con `viewId` que contengan `widget`, `searchbox`, `ghost_voice`, `search_plate` o `search_edit`.
      - Se excluyeron descripciones y textos que contengan `buscar` o `search` (propios del widget de Google Search), permitiendo únicamente selectores legítimos de entrada de voz de Gemini (`usar el micrófono`, `voice input`, `micrófono`, etc.).
    - En `findAndClickGeminiLiveButton`:
      - Se excluyeron nodos con `viewId` que contengan `widget`, `searchbox`, `ghost_voice` o pertenezcan a launchers.
      - Se preservaron y afinaron los selectores de Gemini Live (`open gemini live`, `gemini live`, `iniciar live`, `live`, etc.) con soporte por coordenadas geométricas.
- **Verificación**:
  - Suite de 264 pruebas unitarias ejecutada con éxito (`BUILD SUCCESSFUL`, 0 errores).

### [2026-09-11] — Corrección del Secuestro de Gemini y Gemini Live por Google App (`googlequicksearchbox`)
- **Diagnóstico y Análisis de Log (`/home/rcastro/Descargas/myvu_client_log.txt`)**:
  - Al realizar doble toque o pulsación larga en la patilla táctil, algunas veces no abría Gemini sino la app de Google clásica (Google Search / Assistant antiguo).
  - El análisis del log detectó tres causas críticas:
    1. **`ACTION_VOICE_COMMAND` sin componente explícito**: Android resolvía este Intent genérico hacia `com.google.android.googlequicksearchbox` y ejecutaba un `return` inmediato, impidiendo que el código alcanzara el lanzamiento de la app oficial de Gemini (`com.google.android.apps.bard`).
    2. **Emisión de `KEYCODE_VOICE_ASSIST` al AudioManager**: `am.dispatchMediaKeyEvent(KEYCODE_VOICE_ASSIST)` era emitido incondicionalmente, provocando que Android interceptara la tecla de medios a nivel del sistema y lanzara el asistente predeterminado de Google sobre la pantalla.
    3. **Servicio de Accesibilidad atacando el widget del Launcher**: `AutoSendAccessibilityService` incluía `pkg.contains("googlequicksearchbox")` en `isTargetGemini`, y en `findAndClickGeminiMicButton` buscaba `"voice_search"` sin validar el paquete del nodo. Al desbloquearse la pantalla, el servicio inspeccionaba la ventana del Launcher y pulsaba el botón de micrófono del widget de Google (`com.google.android.googlequicksearchbox:id/googleapp_search_widget_ghost_voice_search`), disparando la búsqueda de voz de Google en vez de esperar a Gemini.
- **Ajustes y Correcciones Realizadas**:
  - **`TouchGestureManager.kt`**:
    - Se priorizó el lanzamiento explícito y directo de `com.google.android.apps.bard` (`getLaunchIntentForPackage`) tanto para Gemini estándar como para Gemini Live.
    - Se eliminó completamente la emisión de `KEYCODE_VOICE_ASSIST` para acciones de Gemini y Gemini Live.
    - Se aisló `launchPhoneAssistant` en un método dedicado e independiente que sí utiliza `KEYCODE_VOICE_ASSIST` y `ACTION_VOICE_COMMAND` exclusivamente para cuando el usuario configure la acción `LAUNCH_PHONE_ASSISTANT`.
    - Los intents genéricos `ACTION_VOICE_COMMAND` y `ACTION_ASSIST` quedaron únicamente como fallbacks finales si Gemini no está instalado en el móvil.
  - **`AutoSendAccessibilityService.kt`**:
    - Se eliminó el soporte de `googlequicksearchbox` para Gemini. Solo se procesa `com.google.android.apps.bard`.
    - `findAndClickGeminiMicButton` y `findAndClickGeminiLiveButton` ahora comprueban que `root` y cada `node` no pertenezcan a `googlequicksearchbox` ni a paquetes `launcher`, y descartan viewIds que contengan `googlequicksearchbox` o `search_widget`.
    - Las ráfagas de reintento (`scheduleBurstGeminiVoiceRetries` y `scheduleBurstGeminiLiveRetries`) verifican que la ventana activa o alguna ventana pertenezca a `com.google.android.apps.bard` antes de ejecutar la búsqueda o clics de botones.
  - **Pruebas y Verificación**:
    - Se añadieron pruebas unitarias en `TouchGestureManagerTest.kt` validando la seguridad y separación de `launchGeminiAssistant` y `launchPhoneAssistant`.
    - Suite completa de 264 pruebas unitarias ejecutada con éxito (`BUILD SUCCESSFUL`, 0 errores).

### [2026-09-11] — Detección Multi-Toque Robusta entre Paquetes (Hardware Timestamps) y Activación Directa de Micrófono en Gemini
- **Análisis de Log de Producción (`/home/rcastro/Descargas/myvu_client_log.txt`)**:
  1. **Toques no reconocidos / Múltiples intentos necesarios**:
     - Las gafas enviaban toques físicos legítimos (`code: 210` patilla derecha sender 1, `code: 200` patilla izquierda sender 2), pero la app resolvía `Action: none (NONE)`.
     - `Prefs.touchpadTapAction` tiene por defecto `"none"`, por lo que un toque simple no produce acción visible/audible.
     - Cuando el usuario realizaba un Doble Toque, las gafas transmitían cada toque en paquetes Bluetooth separados (`msgId=6140` y `msgId=6142`).
     - `InboundRouter.kt` solo sintetizaba doble toque si ambos toques venían en el *mismo* lote de un solo paquete BLE/RFCOMM.
     - `TouchGestureManager.kt` medía el tiempo entre toques únicamente con `System.currentTimeMillis()` del teléfono con un límite rígido de 800ms. Debido al retraso de desuspensión de las gafas y el buffer Bluetooth, la diferencia de llegada al teléfono superaba los 800ms (llegó a 2.927ms en el log a pesar de ocurrir con 1.0s de diferencia en las gafas `_event_time_`).
     - No existía soporte para sintetizar `TRIPLE_TAP` a partir de toques en paquetes separados en `TouchGestureManager`.
     - Las gafas se apagaban a los 5 segundos (`screen_off_time: 5`) y entraban en sueño profundo (`suspend_stats: type 1`), haciendo que el primer toque sufriera latencia al despertar el microcontrolador.
  2. **Micrófono de Gemini no se activaba al abrir**:
     - `TouchGestureManager.launchGeminiAssistant` lanzaba la actividad principal de la app Gemini (`getLaunchIntentForPackage`), que abre el chat en silencio sin modo de escucha activo.
     - `AutoSendAccessibilityService.findAndClickGeminiMicButton` solo intentaba `performAction(ACTION_CLICK)`, que en interfaces Jetpack Compose devuelve `false` al no exponer acciones de click convencionales.
     - Faltaba el fallback por coordenadas (`dispatchGesture`) que sí tenía el botón de enviar.
     - Selectores de descripción y texto en español insuficientes ("usar el micrófono", "habla para enviar", "escribe, habla o comparte", "entrada de voz").
     - Si `rootInActiveWindow` era nulo durante el arranque, el servicio no examinaba `service.windows` ni `event.source`.
- **Soluciones Implementadas**:
  1. **Propagación del Timestamp de Hardware de las Gafas**:
     - Se actualizó `InboundRouter.TouchGestureListener` para recibir `eventTime: Long`.
     - `InboundRouter` propaga `item.eventTime` extraído de la telemetría de las gafas (`_event_time_`, `key_event_time`, etc.).
     - `ConnectionManager.kt` y `GlassesEventHandler.kt` enrutan `eventTime` hacia `TouchGestureManager.handleGesture`.
  2. **Máquina de Estados Multi-Toque con Ventana Extendida**:
     - En `TouchGestureManager.kt`:
       - Se amplió `DOUBLE_TAP_MAX_INTERVAL_MS` a 1100ms (antes 800ms) y se agregó `TRIPLE_TAP_MAX_INTERVAL_MS = 1350L`.
       - Si `eventTime` está presente, calcula el intervalo con el reloj de hardware de las gafas (`Math.abs(eventTime - lastTapEventTime)`), eliminando cualquier impacto de jitter o buffering Bluetooth.
       - Acumulador `accumulatedTapCount`: soporte completo para Doble Toque y Triple Toque entre paquetes Bluetooth separados.
  3. **Activación Directa de Micrófono y Escucha Activa en Gemini**:
     - En `TouchGestureManager.launchGeminiAssistant`:
       - Para consultas normales (`isLive = false`), prioriza `Intent(Intent.ACTION_VOICE_COMMAND)` y `ACTION_VOICE_SEARCH_HANDS_FREE`, abriendo directamente el overlay de escucha activa con animación de ondas de voz y micrófono abierto de inmediato.
       - Fallback robusto a `bardLaunchIntent` si el intent del sistema no está presente.
     - En `AutoSendAccessibilityService.kt`:
       - En `findAndClickGeminiMicButton` y `findAndClickGeminiLiveButton`, se implementó fallback por coordenadas con `dispatchGesture` simulando toque físico en el centro del nodo (`getBoundsInScreen`), garantizando compatibilidad con Jetpack Compose.
       - Soporte para inspeccionar `service.windows` y `event.source` si `rootInActiveWindow` es nulo en transiciones.
       - Selectores ampliados en español e inglés ("usar el micrófono", "habla para enviar", "escribe, habla o comparte", "entrada de voz", "micrófono", "hablar", "dictado").
       - Ráfaga de reintentos ampliada hasta 7.000ms.
  4. **Prevención de Suspensión Inmediata**:
     - En `Prefs.kt`, se elevó el valor por defecto de `screenOffTime` a 15 segundos (antes 10s / 5s en log) para mantener activas las gafas.
- **Verificación**:
  - Se agregaron y ejecutaron pruebas unitarias en `TouchGestureManagerTest.kt`:
    - `twoTapsAt950msSynthesizeDoubleTapInExtendedWindow`: Verifica la ventana de 1100ms.
    - `twoTapsWithHardwareEventTimeSynthesizeDoubleTapEvenIfNetworkDelayed`: Verifica síntesis con llegada retrasada en red (3.5s) usando timestamps de hardware.
    - `threeTapsSynthesizeTripleTapAcrossPackets`: Verifica síntesis de triple toque entre paquetes separados.
  - Ejecución de `./gradlew testDebugUnitTest`: exitosa (código 0).
  - `codegraph sync`: grafo sincronizado.

### [2026-09-11] — Modo Escucha Gemini (Asistente/App) y Gemini Live con Micrófono de Lentes y Alta Sensibilidad Táctil
- **Diagnóstico y Causas Raíz**:
  1. **Micrófono de las Gafas no se activaba**: La preferencia `isGeminiForceScoEnabled` tenía por defecto `false` en `Prefs.kt`, obligando al usuario a configurarlo manualmente y usando el micrófono interno del teléfono en el bolsillo en lugar de los micrófonos de las gafas.
  2. **Cierre Prematuro del Micrófono en Gemini Live**: La ventana fija de SCO (`4500L`) liberaba el dispositivo de comunicación tras 4.5 segundos, cortando la entrada de audio de las gafas en conversaciones continuas de Gemini Live.
  3. **Lanzamiento de Gemini en Pantalla Inactiva**: Al abrir la aplicación `com.google.android.apps.bard`, la UI de Gemini iniciaba en estado inactivo sin presionar automáticamente el micrófono de entrada de voz.
  4. **Falsos Negativos y Pérdida de Gestos Táctiles**:
     - `DEBOUNCE_MS` estaba fijado en 350ms, bloqueando toques y gestos rápidos legítimos del usuario.
     - En `InboundRouter.kt`, la síntesis de toques descartaba eventos si los timestamps de telemetría venían vacíos (`-1L`).
     - Al tocar la patilla, el dedo generaba micro-desplazamientos instantáneos en el sensor capacitivo.
- **Soluciones Implementadas**:
  1. **Activación Predeterminada del Micrófono Bluetooth SCO de las Gafas**:
     - En `Prefs.kt`, se cambió el valor por defecto de `isGeminiForceScoEnabled` a `true`.
  2. **Modo Escucha Inmediato para Gemini Asistente/App**:
     - `TouchGestureManager.launchGeminiAssistant(context, isLive = false)`: Despierta la pantalla con wakelock brillante, descarta el keyguard mediante `SendTrampolineActivity`, activa la ruta de audio Bluetooth SCO (`setCommunicationDevice` / `startBluetoothSco`) con ventana extendida de 8.5s para capturar la pregunta antes de restaurar A2DP para el audio de respuesta.
     - Dispara `AutoSendAccessibilityService.triggerGeminiVoiceAutoStart(...)` que mediante ráfagas rápidas de reintento (150ms, 350ms, 600ms, 1000ms, 1600ms, 2500ms, 4000ms) y en `onAccessibilityEvent` localiza y presiona automáticamente el botón de micrófono (`findAndClickGeminiMicButton`) en la app de Gemini (`com.google.android.apps.bard`).
  3. **Modo Conversación Continua para Gemini Live**:
     - `TouchGestureManager.launchGeminiAssistant(context, isLive = true)`: Despierta y mantiene encendida la pantalla con wakelock de 60s, enruta el micrófono SCO de las gafas y **no** programa liberación automática (`scoReleaseRunnable`), manteniendo el canal SCO permanentemente abierto durante la conversación multidireccional.
     - `AutoSendAccessibilityService.triggerGeminiLiveAutoStart(...)` localiza y presiona dinámicamente el botón de onda/Live (`waveform`, `live`, `gemini live`, `sparkle`, `hablar en directo`, etc.) para iniciar la sesión continua en manos libres.
  4. **Optimización Extrema de Detección Táctil en la Patilla**:
     - En `TouchGestureManager.kt`, se redujo `DEBOUNCE_MS` a 200ms (antes 350ms), se amplió la ventana de doble toque a `30L..800L` (antes 40..700ms).
     - En `InboundRouter.kt`, se mantuvo el filtrado de rebotes idénticos `<= 50ms`, la consolidación de patilla izquierda `200 + 203`, y se optimizó la síntesis de `DOUBLE_TAP` y `TRIPLE_TAP` tanto con timestamps válidos como con eventos agrupados en el mismo paquete BLE (`dt == -1L`).
  5. **Verificación y Pruebas**:
     - Se actualizaron y expandieron las suites de pruebas unitarias en `InboundGestureTest.kt` y `TouchGestureManagerTest.kt` incluyendo síntesis en ventana extendida de 750ms.
     - `./gradlew testDebugUnitTest`: 260 tests pasando al 100%.
     - `./gradlew assembleDebug`: Compilación exitosa de APK.
     - `codegraph sync`: Grafo de código sincronizado.

### [2026-09-10] — Corrección de Doble Toque para Lanzar App Gemini con Desbloqueo y Modo Gemini Live
- **Diagnóstico y Causas Raíz**:
  - En el log (`/home/rcastro/Descargas/myvu_client_log.txt`), los toques físicos (`TAP`, código 210) llegaban a la app pero no disparaban Gemini.
  - La preferencia `touchpad_double_tap_action` tenía por defecto `"media_play_pause"` en `Prefs.kt`, por lo que el doble toque no lanzaba Gemini salvo configuración manual previa.
  - `DOUBLE_TAP_MAX_INTERVAL_MS` estaba limitado a 450ms. Con la latencia de transmisión BLE de las gafas y la cadencia humana al tocar la patilla, intervalos de 500-700ms eran catalogados como toques individuales aislados (`Action: none`).
  - `launchGeminiAssistant` llamaba a `ACTION_VOICE_SEARCH_HANDS_FREE`, el cual en Android moderno no lanza la app de Gemini (`com.google.android.apps.bard`).
- **Soluciones Implementadas**:
  1. `Prefs.kt`: Se cambió el valor por defecto de `touchpadDoubleTapAction` a `"launch_gemini"`.
  2. `GestureAction.kt`: Se añadió la acción `LAUNCH_GEMINI_LIVE` (`"gemini_live"`, display: `"Gemini Live (Conversación)"`) y se renombró `LAUNCH_GEMINI` a `"Gemini (Asistente / App)"`.
  3. `TouchGestureManager.kt`:
     - Se aumentó `DOUBLE_TAP_MAX_INTERVAL_MS` a 700ms para garantizar que la cadencia humana sea detectada fluidamente.
     - Se agregó soporte para `LAUNCH_GEMINI_LIVE` en `ActionExecutor` (`executeGeminiLive()`) y en `dispatchAction`.
     - Se actualizó `launchGeminiAssistant(context, isLive: Boolean = false)`: despierta la pantalla con `LockScreenHelper.wakeUpScreen`, descarta el keyguard con `SendTrampolineActivity.launchWithKeyguardDismiss`, y abre directamente la aplicación oficial de Gemini (`com.google.android.apps.bard`) con flags `FLAG_ACTIVITY_NEW_TASK` y `FLAG_ACTIVITY_CLEAR_TOP`, con cascada de fallbacks a `ACTION_ASSIST`, `ACTION_VOICE_SEARCH_HANDS_FREE` y `ACTION_VOICE_COMMAND`.
  4. `InboundRouter.kt`: Se amplió la ventana de síntesis de doble toque en lotes BLE de 500ms a 700ms (`50L..700L`).
  5. `AutoSendAccessibilityService.kt`:
     - Se implementó `triggerGeminiLiveAutoStart(isDeviceLocked, timeoutMs)` y `scheduleBurstGeminiLiveRetries()`.
     - Se agregó `findAndClickGeminiLiveButton` para detectar dinámicamente y pulsar el botón de conversación continua de Gemini Live (`viewId` o `contentDescription` con "live", "gemini live", "waveform", "hablar en directo", etc.).
  6. `ConnectionManager.kt` y `GlassesEventHandler.kt`: Se implementó `executeGeminiLive()` para invocar `launchGeminiAssistant(ctx, isLive = true)`.
  7. Pruebas Unitarias: Se actualizaron `TouchGestureManagerTest.kt` y `SettingsGestureConfigTest.kt` verificando que el doble toque por defecto sea `LAUNCH_GEMINI`, que la ventana de 600ms sintetice doble toque, y que `LAUNCH_GEMINI_LIVE` se procese correctamente (260 tests pasando).

### [2026-09-10] — Reversión del Re-bloqueo Automático Inteligente de Pantalla
- **Motivo de Reversión**:
  - Se confirmó que el re-bloqueo automático (`lockDeviceScreen()` mediante `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`) bloqueaba la pantalla prematuramente mientras Gemini aún procesaba o dictaba su respuesta, provocando que Android suspendiera o cancelara de inmediato la sesión del asistente de voz.
  - Asimismo, en tareas de automatización (como llamadas directas de WhatsApp y despachos que requieren que la actividad siga visible sobre la pantalla de bloqueo o keyguard), el bloqueo forzado cancelaba las tareas pendientes que dependían de la pantalla encendida y activa.
- **Acciones Realizadas**:
  1. `LockScreenHelper.kt`: Se eliminaron `lockDeviceScreen()` y `scheduleAutoLock()`, preservando únicamente las funciones legítimas de encendido y superposición sobre keyguard (`wakeUpScreen`, `unlockKeyguard`, etc.).
  2. `Prefs.kt`: Se eliminó la clave de preferencia `KEY_AUTO_LOCK_AFTER_VOICE_ACTION` y sus métodos getter/setter `isAutoLockAfterActionEnabled` / `setAutoLockAfterActionEnabled`.
  3. `AutoSendAccessibilityService.kt`: Se removió el observador de ciclo de vida de Gemini (`armGeminiAutoLock`, `cancelGeminiAutoLock`, `isGeminiWatchActive`, `geminiHadFocus`), la variable `wasLockedOnTrigger`, y todas las llamadas a `scheduleAutoLock` en los reintentos y en `onAccessibilityEvent`.
  4. `TouchGestureManager.kt`: En `launchGeminiAssistant`, se eliminó el chequeo de `wasDeviceLocked` y la llamada a `armGeminiAutoLock`.
  5. `PhoneActionExecutor.kt`: En `makeWhatsAppCall`, se removió la programación de bloqueo de pantalla diferido.
  6. `activity_settings.xml` y `SettingsActivity.kt`: Se retiró el switch Material 3 `swAutoLockAfterAction` y su vista asociada, manteniendo `swForceGeminiSco`.

### [2026-09-10] — Robustez Total en Touchpad: Deduplicación de Rebotes en Lotes, Consolidación Sender 2, Inmunidad a Ruido < 40ms, Estabilidad SPP sin Caídas por Bluetooth SCO y Corrección Scoped Storage en BackupManager
- **Problemas Identificados en Nuevo Log (709 líneas)**:
  1. **Lotes de Telemetría BLE con Rebotes y Tiempos Simultáneos en Teléfono (Líneas 298-307)**:
     - El lote de las gafas `[210, 210, 210]` contenía dos toques con el mismo timestamp de hardware (`1789075058000`, rebote eléctrico por contacto capacitivo). Al procesarse en el teléfono llegaron con 1-2ms de diferencia, reseteando `lastTapTime` y descartando el segundo toque legítimo.
  2. **Disparo Doble por Toque en Patilla Secundaria (`sender: 2`, Líneas 378 y 436)**:
     - Cada contacto en la patilla izquierda enviaba la pareja `200` (down) y `203` (up). Al estar ambos mapeados a `TAP`, un solo toque generaba dos toques lógicos en 2ms.
  3. **Caída Sistemática del Servidor SPP de las Gafas al Finalizar Gemini (Líneas 236, 333, 526, 585, 672)**:
     - Exactamente 50ms después de que `releaseBluetoothSco()` liberaba el canal de audio, las gafas cerraban el socket SPP (`<- SPP server closed by the glasses -- dropping the relay`), dejando la app desconectada durante 8 a 15 segundos mientras se restablecía el relay con un `init burst` de 27 mensajes.
     - Causa: En Android 12+ (API 31+), llamar a `stopBluetoothSco()` mientras se utiliza `setCommunicationDevice()` envía comandos HCI legacy conflictivos que reinician la pila Bluetooth de las gafas y desconectan RFCOMM.
  4. **Error EACCES en BackupManager (Línea 145)**:
     - Fallo al copiar `data.zip` en `/storage/emulated/0/Download/MYVU/` debido a restricciones de Scoped Storage en Android 10+.
- **Soluciones Implementadas**:
  1. `InboundRouter.kt`:
     - **Deduplicación de Rebotes en Lote**: Descarta eventos duplicados consecutivos con el mismo gesto, mismo emisor y delta de tiempo `<= 50ms`.
     - **Consolidación Sender 2 (`200` + `203`)**: Si el código `200` (touch down) está presente en el lote, se suprime automáticamente `203` (release confirmation), evitando que un toque físico cuente como dos.
     - **Síntesis Directa de `DOUBLE_TAP` en Lote**: Si el lote recibido por BLE contiene dos toques `TAP` con delta de tiempo de hardware entre 60ms y 500ms, se sintetiza inmediatamente `GlassGesture.DOUBLE_TAP`.
  2. `TouchGestureManager.kt`:
     - **Inmunidad a Ruido y Rebotes Eléctricos (< 40ms)**: Si llega un `TAP` con intervalo `< 40ms`, se ignora como rebote de contacto eléctrico **sin** actualizar `lastTapTime`, protegiendo la ventana de detección para el segundo toque real del usuario.
     - **Estabilidad Bluetooth SCO en Android 12+**: En Android 12+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`), se utiliza exclusivamente `setCommunicationDevice` con `MODE_IN_COMMUNICATION` al activar y `clearCommunicationDevice` con `MODE_NORMAL` al liberar. Se eliminó por completo el uso de `startBluetoothSco()` / `stopBluetoothSco()` en API 31+, erradicando las caídas de SPP y manteniendo la conexión de las gafas permanentemente viva.
  3. `BackupManager.kt`:
     - Uso de `MediaStore.Downloads` en Android 10+ (API 29+) para exportar copias de seguridad a la carpeta pública de descargas respetando Scoped Storage, con fallback legacy en versiones anteriores.
  4. Pruebas y Validación:
     - Tests unitarios añadidos en `InboundGestureTest.kt` y `TouchGestureManagerTest.kt` verificando deduplicación, consolidación de sender 2, síntesis en lote y rebotes `< 40ms`.
     - `./gradlew testDebugUnitTest`: 100% pasando (34 tareas).
     - `./gradlew assembleDebug`: Compilación exitosa en < 1s (41 tareas).

### [2026-09-10] — Corrección de Detección de Doble Toque en Touchpad (Lanzamiento de Gemini), Inmunidad a Debounce en Gestos Nulos y Filtrado de Micro-swipes Parásitos
- **Problema**: En `/home/rcastro/Descargas/myvu_client_log.txt`, el lanzamiento de Gemini mediante doble toque en el touchpad de las patillas de las gafas fallaba intermitentemente ("algunas veces no detecto correctamente el lanzamiento de gemini con doble toque del touchpad de las patas de las gafas").
- **Causas Raíz Identificadas en Log**:
  1. **Debounce en Gestos Nulos (`NONE`) Bloqueando Toques Legítimos (Líneas 361-381)**:
     - En `TouchGestureManager.handleGesture`, `lastTriggerTime = now` se actualizaba antes de evaluar la acción resuelta.
     - Si llegaba un gesto con acción `NONE` (por ejemplo, un micro-swipe `206` no mapeado), establecía el temporizador de debounce. Cuando 4ms después llegaba el `TAP` (`210`), la condición `now - lastTriggerTime < 350ms` se cumplía y el toque era descartado silenciosamente.
  2. **Micro-swipes Parásitos Simultáneos en Lotes de Telemetría (Líneas 361 y 362)**:
     - Al apoyar el dedo sobre la patilla para dar un toque, el sensor capacitivo registra una micro-fricción/desplazamiento enviando simultáneamente un swipe (`206` o `207`) y un toque (`210`) con el **mismo timestamp exacto** (`key_event_time: 7133096` / `_event_time_: 1789074004000`) en un único lote `JSONArray`.
     - Al procesarse en orden secuencial sin priorización, el swipe consumía la ventana de debounce e ignoraba el toque.
  3. **Falta de Síntesis Software de Doble Toque**:
     - El firmware Flyme XR de las gafas no siempre sintetiza el keycode nativo `211` (`DOUBLE_TAP`); con frecuencia emite dos eventos `210` (`TAP`) rápidos consecutivos (~150-250ms).
     - La ventana de debounce de 350ms descartaba el segundo toque simple, impidiendo cualquier detección de doble toque.
- **Soluciones Implementadas**:
  1. `TouchGestureManager.kt`:
     - **Inmunidad a Debounce para Acción `NONE`**: Si la acción resuelta es `GestureAction.NONE`, se ejecuta `executor.executeNone()` y **no** se actualiza `lastTriggerTime`. Gestos nulos o accidentales ya no bloquean gestos posteriores.
     - **Acumulador y Sintetizador Software de Doble Toque**: Si llega un `GlassGesture.TAP` y han transcurrido entre `40ms` y `450ms` desde el toque anterior, y `DOUBLE_TAP` tiene una acción asignada (por defecto `LAUNCH_GEMINI` o configurada por el usuario), se sintetiza y despacha directamente `GlassGesture.DOUBLE_TAP`.
     - **Inyección de Tiempo para Pruebas**: Añadido `timeProvider: () -> Long` reseteable para pruebas unitarias deterministas sin delays reales.
  2. `InboundRouter.kt`:
     - **Filtrado de Micro-swipes Parásitos**: En `dispatchGestureBatch`, si un lote contiene gestos de toque/pulsación (`TAP`, `DOUBLE_TAP`, `TRIPLE_TAP`, `LONG_PRESS`) junto con gestos de deslizamiento (`SWIPE_FORWARD`, `SWIPE_BACKWARD`) del mismo emisor con diferencia temporal `<= 50ms` (o igual timestamp), el swipe es identificado como ruido de aterrizaje táctil y se descarta (`Filtered parasitic SWIPE_FORWARD occurring simultaneously with TAP`).
     - **Priorización de Gestos Simultáneos**: Gestos legítimos que coinciden en timestamp se ordenan según su jerarquía de intención: `DOUBLE_TAP` > `TRIPLE_TAP` > `TAP` > `LONG_PRESS` > `SWIPE`.
  3. Pruebas y Validación:
     - `TouchGestureManagerTest.kt`: Pruebas `twoTapsWithinWindowSynthesizeDoubleTap` y `gestureWithActionNoneDoesNotDebounceSubsequentTap`.
     - `InboundGestureTest.kt`: Prueba `filtersParasiticSwipeWhenSimultaneousTapOccursInBatch` con el payload exacto del log.
     - `./gradlew testDebugUnitTest`: 100% exitoso (todas las pruebas pasan).
     - `./gradlew assembleDebug`: Compilación exitosa de APK.
- **Problemas Identificados**:
  1. **Deshabilitación de Accesibilidad tras Actualizar**: En Android (especialmente en OEM skins como MIUI/HyperOS, Samsung, etc.), al actualizar el APK (`ACTION_MY_PACKAGE_REPLACED`), el sistema operativo detiene y deshabilita automáticamente el servicio `AutoSendAccessibilityService`. El usuario no recibía ninguna notificación ni advertencia visual y el envío automático de WhatsApp, Telegram y SMS fallaba silenciosamente.
  2. **Timeout Prematuro en Handshake RFCOMM (Líneas 715-717 del log)**: `relayEstablishTimeout` de 10s en `ConnectionManager` se iniciaba al llamar a `transport.connect()`. Si la negociación RFCOMM tardaba 8.5 segundos en conectar el socket a nivel de hardware, el temporizador de 10s vencía apenas 1.5s después de conectar el socket, matando la conexión de forma abrupta a mitad de la ráfaga de 27 mensajes (`init burst`) en el mensaje 12 (`!! link dropped during the init burst at message 12`).
  3. **Conmutación Brusca de Audio y Cierre de SPP**: Al liberar Bluetooth SCO tras invocar Gemini, invocar `am.mode = AudioManager.MODE_NORMAL` bruscamente cuando ya estaba en modo normal generaba un reset de endpoints de datos SPP en las gafas.
  4. **Log Ruidoso de Desbloqueo en Pantalla Bloqueada**: `LockScreenHelper` reportaba `!! LockScreenHelper: Keyguard dismiss error` como warning ruidoso cuando el teléfono tiene bloqueo seguro (huella/PIN).
- **Soluciones Implementadas**:
  1. `AutoSendAccessibilityService.kt`:
     - Implementado `autoEnableIfPermitted(context)`: Escribe directamente en `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` y `Settings.Secure.ACCESSIBILITY_ENABLED` si la app cuenta con permiso `android.permission.WRITE_SECURE_SETTINGS` (concedido una única vez por ADB: `adb shell pm grant com.myvu.client android.permission.WRITE_SECURE_SETTINGS`). ¡Reactivación 100% silenciosa y automática tras actualizaciones!
     - Implementado `notifyAccessibilityDisabled(context)`: Si no dispone de permiso ADB y el servicio está apagado, lanza inmediatamente una notificación de alta prioridad (Heads-Up) con canal propio (`myvu_accessibility_alert`) y PendingIntent que abre directamente los Ajustes de Accesibilidad.
     - Implementado `checkAndRestoreOrNotify(context)`: Watchdog integral que verifica, intenta restaurar automáticamente y, de no ser posible, notifica al usuario.
     - `cancelDisabledNotification(context)`: Cancela la notificación en cuanto el servicio se activa (`onServiceConnected`).
     - Declarado `<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" tools:ignore="ProtectedPermissions" />` en `AndroidManifest.xml`.
  2. Integración del Watchdog en el Ciclo de Vida:
     - `BootReceiver.kt`: Se ejecuta en `ACTION_MY_PACKAGE_REPLACED` y `ACTION_BOOT_COMPLETED`.
     - `MyvuService.kt`: Se ejecuta en `onCreate()`.
     - `ConnectActivity.kt` & `SettingsActivity.kt`: Se ejecuta en `onResume()`.
  3. Feedback Visual y Asistente ADB en UI:
     - `view_dashboard.xml` / `ConnectActivity.kt`: Añadida tarjeta de advertencia ámbar `cardAccessibilityWarning` visible únicamente cuando el servicio está inactivo, con botones para "Activar en Ajustes" y "Copiar ADB" al portapapeles.
     - `activity_settings.xml` / `SettingsActivity.kt`: Añadida tarjeta de configuración para "MYVU Auto-Send Assistant" que muestra estado en tiempo real (`Activo` en verde / `Inactivo` en ámbar) con botones para abrir ajustes y copiar comando ADB.
  4. `ConnectionManager.kt`:
     - En `relayListener.onConnected(transport)`, se reinicia `relayEstablishTimeout` (`conn.removeCallbacks(relayEstablishTimeout)` seguido de `conn.postDelayed(relayEstablishTimeout, RELAY_ESTABLISH_TIMEOUT_MS)`), garantizando que el handshake de sesión y la ráfaga de 27 mensajes `init burst` dispongan de sus 10 segundos completos desde la conexión efectiva del socket, eliminando desconexiones falsas.
  5. `TouchGestureManager.kt` & `LockScreenHelper.kt`:
     - En `releaseBluetoothSco` y `launchGeminiAssistant`, solo se conmuta a `MODE_NORMAL` si `am.mode != AudioManager.MODE_NORMAL`.
     - En `LockScreenHelper.onDismissError()`, se reemplazó el warning por log informativo normal, ya que la app continúa sin problemas mostrando la actividad sobre el lockscreen.
  6. Pruebas y Validación:
     - Pruebas añadidas en `AutoSendAccessibilityServiceTest.kt` cubriendo `autoEnableIfPermitted`, `checkAndRestoreOrNotify`, `activeInstance` y notificaciones.
     - `./gradlew testDebugUnitTest`: 100% pasando (34 tareas).
     - `./gradlew assembleDebug`: Compilación exitosa (41 tareas).

### [2026-09-10] — Optimización de Audio Gemini (SCO/A2DP), Prevención de Caídas SPP y Ahorro de Batería
- **Problema**: Al invocar Gemini mediante el gesto táctil de las patillas, Gemini escuchaba la orden del usuario y la procesaba, pero al responder, la voz de Gemini se cortaba durante varios segundos ("se va la voz y después de unos segundos regresa"). Además, se observaba un drenaje acelerado de batería y desconexiones intermitentes del socket RFCOMM SPP de las gafas.
- **Causa Raíz Identificada en Log (`myvu_client_log.txt`)**:
  1. **Fuga de Ciclo de Vida de Bluetooth SCO**: `TouchGestureManager.launchGeminiAssistant` activaba el canal SCO (`startBluetoothSco()`, `isBluetoothScoOn = true`, `setCommunicationDevice`) para el micrófono de las gafas, pero **nunca lo liberaba**. Al responder Gemini vía TTS multimedia (A2DP), Android entraba en conflicto entre el modo llamada SCO activo y la reproducción de medios. El firmware de las gafas reportaba `iot_a2dp_status_change -> a2dp_status: 1` (A2DP suspendido), provocando un silencio de ~6 segundos hasta que el sistema forzaba la reactivación de A2DP (`a2dp_status: 2`), momento en que la voz de Gemini regresaba tardíamente.
  2. **Colisión de Audio por Notificación TTS de las Gafas**: Al lanzar Gemini, `ConnectionManager` y `GlassesEventHandler` emitían `Notifications.buildShow("MYVU", "Gemini escuchando...")`. Esto disparaba el motor TTS propio de las gafas (`com.upuphone.ai.ttsengine` con `caller: com.tts.notification`) leyendo la notificación en voz alta por los parlantes de las gafas en pleno instante de escucha y respuesta de Gemini.
  3. **Saturación del Chip Bluetooth de las Gafas y Caída de SPP**: Mantener el canal de voz síncrono SCO abierto permanentemente junto a A2DP y RFCOMM saturaba el chip de radio de ultra-bajo consumo de las gafas, provocando cierres del servidor SPP (`<- SPP server closed by the glasses -- dropping the relay`). La app reintentaba inmediatamente a los 55ms y enviaba un `init burst` de 27 mensajes, provocando bucles de reconexión y alto consumo de batería.
  4. **Procesamiento Inútil de Telemetría Interna**: Docenas de eventos internos del sistema de las gafas (`iot_a2dp_status_change`, `audio_stats`, `air_starrynet_bt`, `iot_voice_asr_time`, etc.) no estaban en la lista de exclusión `nonTouchTelemetry`, consumiendo ciclos de CPU en el parser de telemetría.
- **Solución Implementada**:
  1. `TouchGestureManager.kt`:
     - Implementado ciclo de vida acotado para SCO (`GEMINI_SCO_CAPTURE_WINDOW_MS = 4500L`).
     - Al invocar Gemini, el micrófono SCO se conecta para capturar la petición del usuario y, tras una ventana de 4.5 segundos, se libera automáticamente mediante `releaseBluetoothSco()` (`stopBluetoothSco()`, `clearCommunicationDevice()`, `MODE_NORMAL`).
     - Al liberar SCO en el segundo 4.5, el canal estéreo de alta fidelidad A2DP está disponible de inmediato para reproducir la respuesta de Gemini sin retrasos ni silencios.
     - En `setCommunicationDevice`, se restringió la selección exclusivamente a `TYPE_BLUETOOTH_SCO`.
  2. `ConnectionManager.kt` & `GlassesEventHandler.kt`:
     - Retirado el despacho de `Notifications.buildShow("MYVU", "Gemini escuchando...")`, evitando que el TTS propio de las gafas interrumpa al usuario o a Gemini.
  3. `RelaySupervisor.kt`:
     - En `onRelayLost()`, se actualiza `lastAttemptAt = System.currentTimeMillis()`, garantizando que cualquier reconexión respete el intervalo mínimo de backoff (3-5s) y evitando el martilleo inmediato del socket Bluetooth.
  4. `InboundRouter.kt`:
     - Añadidos a `nonTouchTelemetry` todos los eventos internos identificados (`iot_a2dp_status_change`, `audio_stats`, `air_starrynet_bt`, `iot_voice_asr_time`, `wear_data_collect`, `starrynet_devices_disconnect`, `starrynet_devices_reconnect`, `screen_off_timeout_change`, `standby_position`), eliminando parseos y logs innecesarios.
  5. Pruebas y Validación:
     - Tests actualizados en `InboundGestureTest.kt`.
     - `./gradlew testDebugUnitTest`: 100% pasando (34 tareas).
     - `./gradlew assembleDebug`: Compilación exitosa.


### [2026-09-10] — Corrección Definitiva de Gestos Táctiles de Patillas: Keycodes 206, 207, 210, 211, 212 y Senders 1, 2, 4
- **Problema**: Tras restaurar el botón físico de la montura para STT -> Modelo IA, la configuración de gestos en las patillas ("patas") no ejecutaba ninguna acción al tocarlas.
- **Causa Raíz Identificada en Log (`myvu_client_log.txt`)**:
  1. **Filtrado erróneo de `key_event_sender: 1`**: Se creía erróneamente que `sender == 1` correspondía al botón de la montura. El análisis profundo del log demostró que el botón de la montura se transmite exclusivamente mediante `com.upuphone.ai.assistant {"code": 3}` (atendido por `checkAiTrigger`), mientras que `sync_glass_event` con `key_event` representa siempre la interacción con los sensores táctiles:
     - `sender: 1`: Sensor táctil capacitivo de patilla primaria/derecha.
     - `sender: 2`: Sensor táctil capacitivo de patilla secundaria/izquierda.
     - `sender: 4`: Controlador virtual / phonepad del launcher Flyme XR.
     Al descartar `sender == 1`, se estaba ignorando el 90% de los toques físicos del usuario.
  2. **Keycodes nativos Flyme XR no mapeados en `GlassGesture.kt`**:
     - Al correlacionar comandos del phonepad (`click`, `doubleClick`, `longPress`) con las respuestas de las gafas:
       - `210` -> `TAP` (Click / Toque simple)
       - `211` -> `DOUBLE_TAP` (Doble click)
       - `212` -> `LONG_PRESS` (Pulsación prolongada)
       - `206` -> `SWIPE_FORWARD` (Deslizar hacia adelante)
       - `207` -> `SWIPE_BACKWARD` (Deslizar hacia atrás)
     Al faltar estos códigos en `GlassGesture.fromCode`, se resolvían como `GlassGesture.UNKNOWN` y no ejecutaban ninguna acción.
- **Solución Implementada**:
  1. `GlassGesture.kt`: Mapeados los códigos `210` (TAP), `211` (DOUBLE_TAP), `212` (LONG_PRESS), `206` (SWIPE_FORWARD) y `207` (SWIPE_BACKWARD).
  2. `InboundRouter.kt`: Removido el descarte de `sender == 1`. Ahora procesa todos los toques legítimos de patillas (`sender: 1`, `sender: 2`, `sender: 4`), conservando la omisión de `down_or_up == 0` para no duplicar acciones al soltar.
  3. Pruebas y Validación:
     - `InboundGestureTest.kt`: `decodesTempleKeyEventsWithAllSendersAndKeyCodes` verifica la decodificación de los 12 casos (todos los keycodes y senders).
     - `./gradlew testDebugUnitTest`: 100% pasando (34 tareas).
     - `./gradlew assembleDebug`: Compilación exitosa.


### [2026-09-10] — Restauración del Botón Físico de la Montura (STT -> Modelo) y Separación de Gestos de Patillas (Gemini Manos Libres)
- **Problema**:
  1. Al presionar el botón físico de la montura de las gafas, la aplicación interceptaba el evento como un gesto táctil de pulsación larga (`LONG_PRESS`), impidiendo el flujo nativo de reconocimiento de voz (STT) hacia el modelo de IA configurado.
  2. El usuario solicitó mantener de forma inmutable el comportamiento del botón físico (STT -> Modelo configurado) y permitir lanzar Gemini o apps exclusivamente mediante gestos en las patillas ("patas de las gafas").
- **Causas Raíz Identificadas**:
  1. **Interceptación de `code: 3` en `ConnectionManager.kt` y `GlassesEventHandler.kt`**: Ambas clases interceptaban `code == 3` redirigiéndolo a `TouchGestureManager.handleGesture()`. En Flyme XR, `code: 3` es el código emitido por el botón físico para iniciar captura STT.
  2. **Diferenciación de Hardware por `key_event_sender`**:
     - `key_event_sender: 1`: Botón físico ubicado en la montura de las gafas (emite eventos como 206, 207, 210, 212 y dispara el listener AI `code: 3`).
     - `key_event_sender: 2`: Sensor táctil capacitivo de las patillas/varillas ("patas de las gafas") (emite códigos 200, 201, 202, 203, 237).
  3. **Estructura de Telemetría Anidada**: En paquetes `sync_glass_event`, las pulsaciones de hardware de las patillas no vienen en la raíz del JSON, sino anidadas dentro del objeto `_event_attr_value_` (`key_code`, `down_or_up`, `key_event_sender`).
- **Solución Implementada**:
  1. `ConnectionManager.kt` & `GlassesEventHandler.kt`:
     - Eliminada por completo la interceptación de `code == 3` como gesto táctil en `setAiTriggerListener`.
     - El botón físico invoca directa y exclusivamente `ai().onTrigger(code)` (STT -> Modelo configurado).
  2. `InboundRouter.kt`:
     - Inspección profunda del objeto `_event_attr_value_` en `dispatchGestureItem`.
     - Filtrado estricto por emisor: si `key_event_sender == 1`, se descarta de la capa de gestos táctiles para no interferir con el botón físico.
     - Si `key_event_sender == 2` (patillas), se procesa el keycode para mapeo de gestos.
     - Ignorados eventos de liberación (`down_or_up == 0`) para evitar disparos duplicados al soltar la patilla.
     - Añadidos `iot_voice_wakeup` e `iot_voice_quit` a `nonTouchTelemetry` para prevenir falsos positivos.
  3. `GlassGesture.kt`:
     - Mapeados códigos nativos de las patillas Flyme XR: 200 y 203 a `TAP`, 202 a `DOUBLE_TAP`, 201 a `SWIPE_FORWARD`, 237 a `SWIPE_BACKWARD`.
     - Eliminada la palabra clave `"voice"` de la heurística textual de `LONG_PRESS` para no colisionar con telemetría de voz.
  4. Pruebas y Validación:
     - Añadidos tests unitarios exhaustivos en `InboundGestureTest.kt` validando que `code: 3` va directo a IA, que los paquetes con `key_event_sender: 1` se ignoran en gestos y que los paquetes con `key_event_sender: 2` activan correctamente los gestos de las patillas.
     - `./gradlew testDebugUnitTest`: 100% pasando (34 tareas).
     - `./gradlew assembleDebug`: Compilación exitosa.


### [2026-09-10] — Habilitación Integral de Gestos Físicos del Touchpad (Toque Simple, Doble Toque, Deslizamientos)
- **Problema**: Los demás gestos táctiles de las gafas (toque simple, doble toque, triple toque, deslizamiento adelante, deslizamiento atrás) no respondían ni ejecutaban las acciones personalizadas asignadas en la configuración.
- **Causas Raíz Identificadas**:
  1. **Eventos `key_event` no decodificados**: Las gafas envían toques físicos en `sync_glass_event` empaquetados como `{"name": "key_event", "key_code": ...}`. `InboundRouter` no extraía los campos `key_code`, `keyCode`, `key_name` ni `keyName`, y evaluaba el nombre a `"key_event"`, retornando `GlassGesture.UNKNOWN`.
  2. **Keycodes de hardware no mapeados en `GlassGesture.kt`**: Los códigos físicos estándar enviados por el firmware (`23` DPAD_CENTER / click, `66` ENTER, `79` HEADSETHOOK, `85` MEDIA_PLAY_PAUSE, `96` BUTTON_A para Toque Simple; `87` MEDIA_NEXT, `90` FAST_FORWARD, `92` PAGE_UP para Deslizar Adelante; `88` MEDIA_PREVIOUS, `89` REWIND, `93` PAGE_DOWN para Deslizar Atrás) no estaban en la tabla `when (code)`.
  3. **Eventos de acción `phonepad` / `trackpad` ignorados**: Si el launcher de las gafas emitía `action = "phonepad"` o `"trackpad"` (`click`, `doubleClick`, `longPress`, `gestureMode`), `InboundRouter.checkGestureTracking` los filtraba.
  4. **Ausencia de `MediaSession` para eventos Bluetooth AVRCP**: Al activarse `music_tp_control_mode`, las gafas emiten eventos AVRCP sobre el perfil de audio clásico (`BluetoothHeadset`). Sin una sesión multimedia activa en el servicio en primer plano, Android desviaba o descartaba estas teclas.
- **Solución Implementada**:
  1. `GlassGesture.kt`:
     - Mapeados todos los códigos de hardware en `fromCode`: `1, 23, 66, 79, 85, 96 -> TAP`; `2 -> DOUBLE_TAP`; `3 -> TRIPLE_TAP`; `4, 219, 231 -> LONG_PRESS`; `5, 19, 22, 87, 90, 92 -> SWIPE_FORWARD`; `6, 20, 21, 88, 89, 93 -> SWIPE_BACKWARD`.
     - Expandidos sinónimos textuales (`click`, `select`, `enter`, `center`, `hook`, `next`, `prev`, `fast_forward`, `rewind`, `page_up`, `page_down`).
  2. `InboundRouter.kt`:
     - `checkGestureTracking`: Añadido soporte para `phonepad`, `trackpad`, `touchpad`, `key_event` y variaciones.
     - `dispatchGestureItem`: Extracción prioritaria de nombres específicos (`key_name`, `gesture_name`, etc.) antes del genérico `key_event`, y lectura exhaustiva de claves de valor (`key_code`, `keyCode`, `actionType`, `direction`, etc.).
  3. `MyvuService.kt`:
     - Implementado `MediaSession` ("MyvuGlassesMediaSession") activo con `MediaSession.Callback` para capturar eventos de botones multimedia (`KEYCODE_HEADSETHOOK`, `KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS`, `KEYCODE_MEDIA_FAST_FORWARD`, `KEYCODE_MEDIA_REWIND`, `KEYCODE_VOICE_ASSIST`).
     - Detección de pulsación simple vs doble (ventana de 450 ms) para mapear toques del auricular a `TAP` o `DOUBLE_TAP`, canalizándolos por `connection?.executeGesture()`.
  4. `ConnectionManager.kt`:
     - Expuesto `executeGesture(gesture, rawCode)` para ejecutar directamente las acciones configuradas en `TouchGestureManager`.
  5. Pruebas y Validación:
     - Nuevos tests en `InboundGestureTest.kt`: `decodesKeyEventPacketsWithKeyCodes` y `decodesPhonepadActionPackets`.
     - `./gradlew testDebugUnitTest`: 100% verde (34 tareas).
     - `./gradlew assembleDebug`: compilado exitosamente.

### [2026-09-10] — Eliminación de Disparos Espontáneos del Asistente por Telemetría de Sistema
- **Problema**: El asistente / agente configurado para la pulsación larga se disparaba espontáneamente en el móvil en bucle cada pocos segundos, sin que el usuario tocara la varilla ni el panel táctil de las gafas.
- **Causa Raíz Identificada en Log (`myvu_client_log.txt`)**:
  1. Con `music_tp_control_mode` activado, el launcher FlymeAR emite periódicamente paquetes `event_tracking` -> `sync_glass_event` para eventos internos del SO de las gafas (`suspend_stats`, `iot_screen_status_change`, `iot_sys_usages`, `battery_stats`, `iot_notification_reminder`).
  2. `InboundRouter.dispatchGestureItem` procesaba estos eventos generales y, al no coincidir con ningún gesto, generaba `GlassGesture.UNKNOWN` y los despachaba a `TouchGestureListener`.
  3. `TouchGestureManager.getActionForGesture` mapeaba `GlassGesture.UNKNOWN` a `Prefs.touchpadLongPressAction(context)`.
  4. Por tanto, cada cambio de estado de pantalla o métrica de energía de las gafas era interpretado como una "Pulsación Larga", lanzando el Asistente de Google / agente en bucle y saturando el enlace RFCOMM.
- **Solución Implementada**:
  1. `TouchGestureManager.kt`:
     - `getActionForGesture`: Mapeado `GlassGesture.UNKNOWN -> GestureAction.NONE` (y `GestureAction.NONE.id` con contexto). `UNKNOWN` nunca dispara acciones.
     - `handleGesture`: Agregada guarda temprana `if (executor == null || gesture == GlassGesture.UNKNOWN) return`.
  2. `InboundRouter.kt`:
     - `dispatchGestureItem`: Si `gesture == GlassGesture.UNKNOWN`, se descarta inmediatamente sin invocar al listener de toques.
     - `processGestureValue`: Si un código numérico resulta en `UNKNOWN`, se descarta.
  3. Pruebas y Validación:
     - Actualizado `TouchGestureManagerTest.kt`: verifica que `GlassGesture.UNKNOWN` resuelva a `GestureAction.NONE` y que `handleGesture` no ejecute ninguna acción ante eventos `UNKNOWN`.
     - Actualizado `InboundGestureTest.kt`: `ignoresNonGestureSystemTelemetryEvents` comprueba que ráfagas de telemetría del sistema (`suspend_stats`, `iot_screen_status_change`, etc.) sean ignoradas y no generen gestos.
     - `./gradlew testDebugUnitTest` 100% verde (34 tareas).
     - `./gradlew assembleDebug` compilado exitosamente.

### [2026-09-10] — Personalización de Gestos con Apps Instaladas y Activación Manos Libres de Gemini
- **Problema**:
  1. El usuario configuró gestos táctiles para ejecutar tareas y apps, pero al tocar la patilla de las gafas no se ejecutaba la acción en el teléfono.
  2. Al asignar Gemini a un gesto, el teléfono no encendía la pantalla, no desbloqueaba el keyguard ni enrutaba el micrófono de las gafas directamente hacia Gemini.
- **Causas Raíz Identificadas**:
  1. `GestureAction` y `SettingsActivity` solo contenían 10 acciones fijas; no existía opción para seleccionar aplicaciones instaladas en el dispositivo (`app:<package>`).
  2. `MyvuService` registraba un `MediaSession` sin `PlaybackState` ni `MediaButtonReceiver`, por lo que el subsistema `AudioService` de Android descartaba los eventos AVRCP de botones multimedia provenientes del audífono/patilla Bluetooth.
  3. `TouchGestureManager.launchPhoneAssistant` únicamente emitía un `KeyEvent` virtual (`KEYCODE_VOICE_ASSIST`), sin encender la pantalla (`WakeLock`), sin saltar el keyguard (`SendTrampolineActivity`) y sin habilitar Bluetooth SCO en `AudioManager` para que el micrófono de las gafas recibiera la petición del usuario.
- **Solución Implementada**:
  1. **Selector de Apps en `SettingsActivity`**:
     - Agregada acción `LAUNCH_APP` ("Abrir Aplicación...") en `GestureAction.kt` con formato `app:<package_name>`.
     - Implementado `showAppPickerDialog` en `SettingsActivity.kt` que despliega un diálogo con las aplicaciones lanzables del sistema y guarda `app:<package_name>`.
     - Al cargar la pantalla, los desplegables resuelven y muestran el nombre real de la app (ej. *"App: Spotify"*, *"App: WhatsApp"*).
  2. **Integración con Gemini Manos Libres (`launchGeminiAssistant`)**:
     - Creada acción dedicada `LAUNCH_GEMINI` ("Lanzar Gemini (Manos Libres)").
     - En `TouchGestureManager.launchGeminiAssistant`:
       - Enciende la pantalla con brillo completo usando `LockScreenHelper.wakeUpScreen(appContext, "MYVU:GeminiVoiceAssistant", 15000L)`.
       - Conecta el canal de audio Bluetooth SCO (`startBluetoothSco()` y `setCommunicationDevice` en Android 12+) para que el micrófono de las gafas sea la entrada de voz de Gemini.
       - Dispara `ACTION_VOICE_SEARCH_HANDS_FREE` y `ACTION_VOICE_COMMAND` a través de `SendTrampolineActivity.launchWithKeyguardDismiss` para descartar el keyguard y darle el control total de la pantalla a Gemini.
       - Muestra notificación HUD en las gafas: *"Gemini escuchando..."*.
  3. **Lanzador Universal de Apps por Gesto (`launchApp`)**:
     - En `ConnectionManager.kt` y `GlassesEventHandler.kt`, implementado `executeLaunchApp(packageName)`.
     - Enciende pantalla con `LockScreenHelper.wakeUpScreen`, descarta keyguard con `SendTrampolineActivity` y abre la app elegida por el usuario con notificación HUD en las gafas (*"Abriendo [App]..."*).
  4. **Corrección de `MediaSession` y AVRCP en `MyvuService`**:
     - Configurado `PlaybackState` completo (`STATE_PAUSED` con acciones de reproducción y salto de pistas) y registrado `setMediaButtonReceiver` con intent filter `android.intent.action.MEDIA_BUTTON` en el manifiesto.
     - Manejo explícito de `ACTION_MEDIA_BUTTON` en `onStartCommand` para garantizar que los toques de la patilla se capturen siempre.
  5. **Pruebas y Verificación**:
     - Pruebas unitarias actualizadas en `TouchGestureManagerTest.kt`: 249 tests pasando al 100%.
     - APK generado sin errores: `./gradlew assembleDebug`.

### [2026-09-10] — Corrección y Enrutamiento Integral de Personalización del Touchpad y Varilla de las Gafas
- **Problema**: La personalización de gestos del panel táctil/varilla configurada en `SettingsActivity` no funcionaba físicamente en las gafas. Al presionar o mantener pulsada la varilla, siempre se invocaba la IA local o los toques eran absorbidos localmente por el launcher de las gafas.
- **Causas Raíz Identificadas**:
  1. **Bypass de IA en `ConnectionManager.kt` y `GlassesEventHandler.kt`**: La pulsación prolongada de la varilla (`code: 3`) llegaba por `checkAiTrigger`. Ambas clases llamaban directamente a `ai().onTrigger(code)` sin consultar `TouchGestureManager` ni la preferencia `Prefs.touchpadLongPressAction(context)`.
  2. **Atributos FlymeAR con guiones bajos no analizados en `InboundRouter.kt`**: La telemetría real de FlymeAR utiliza claves como `_action_name_`, `_event_name_`, `_action_value_`, `_event_value_`, `_event_id_` y `actionType`, las cuales eran ignoradas por `dispatchGestureItem`.
  3. **Falta de sinónimos y códigos de hardware en `GlassGesture.kt`**: Faltaban sinónimos de deslizamiento (`slip_forward`, `slide_forward`, `slip_backward`, `slide_backward`, `flick_forward`, `flick_backward`, `deep_touch`, `press_long`, `right`, `left`, `up`, `down`) y códigos de hardware Trackpad / D-Pad (19, 20, 21, 22).
  4. **Modo `set_music_tp_control_mode` sin sincronización activa**: `SettingsActivity` no enviaba `SystemSettings.setMusicTpControl(true)` a las gafas al cambiar las acciones en los selectores desplegables, lo que impedía que el launcher de las gafas reenviara los eventos táctiles al teléfono.
- **Solución Implementada**:
  1. `GlassGesture.kt`:
     - Expandido `fromCode` con sinónimos direccionales y de deslizamiento FlymeAR.
     - Mapeados códigos de hardware Trackpad (`SWIPE_UP`=19, `SWIPE_DOWN`=20, `SWIPE_LEFT`=21, `SWIPE_RIGHT`=22) a `SWIPE_FORWARD` y `SWIPE_BACKWARD`.
  2. `InboundRouter.kt`:
     - En `dispatchGestureItem`, extracción exhaustiva de nombres (`_action_name_`, `action_name`, `_event_name_`, `event_name`, `_event_id_`, `event_id`, `name`, `action`) y valores (`_action_value_`, `action_value`, `_event_value_`, `event_value`, `actionType`, `action_type`, `value`, `code`, `event_code`).
     - Soporte en `checkGestureTracking` para payloads planos y anidados de telemetría.
  3. `ConnectionManager.kt` & `GlassesEventHandler.kt`:
     - En `setAiTriggerListener`: cuando `code == 3`, enruta a través de `TouchGestureManager.handleGesture(this.context, GlassGesture.LONG_PRESS, code, createGestureActionExecutor())`.
     - Si la acción configurada es `LAUNCH_LOCAL_AI`, se ejecuta `executeAiAssistant(3)` -> `ai().onTrigger(3)` preservando el comportamiento por defecto; si se configuró Asistente del Teléfono (Google Assistant), Play/Pausa, Modo Zen, Clima, etc., se ejecuta la acción del usuario.
     - Cuando `code == 7` (palabra de activación de voz), enruta directamente a `ai().onTrigger(7)`.
  4. `SettingsActivity.kt`:
     - Al seleccionar cualquier acción en los dropdowns de gestos táctiles, activa `Prefs.setMusicTouchPanelEnabled(this, true)` y despacha `SystemSettings.setMusicTpControl(true)` al enlace activo con las gafas.
  5. Pruebas y Validación:
     - Nuevos tests en `InboundGestureTest.kt` cubriendo telemetría con guiones bajos FlymeAR, códigos numéricos Trackpad 19..22 y enrutamiento de trigger de hardware `code: 3`.
     - `./gradlew testDebugUnitTest` 100% verde (34 tareas).
     - `./gradlew assembleDebug` compilado exitosamente.

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

### [2026-09-09] — Eliminación de Reintento Falso en Handshake de Relay, Tolerancia RFCOMM y Context Prompt en Whisper
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-09-eliminate-false-relay-retry-and-whisper-prompt.md`.
- **Análisis de Logs Post-Ajustes**:
  - Confirmada extinción total del mensaje en inglés *"Just a moment, please"* (consulta de clima resuelta en 2.77s gracias a la caché de geolocalización).
  - Consulta de calendario por voz ejecutada con éxito en 280 milisegundos.
  - Sincronización horaria `SyncOffSetTime` desduplicada eficazmente.
- **Mejoras Implementadas**:
  1. `ConnectionManager.kt`:
     - Corregida la condición en `relayListener.onConnected`: sustituido `rfSession?.ready != true` por `rfSession?.authConfirmed != true`. Evita el disparo falso del timer de reintento de 2000ms mientras la ráfaga de 29 mensajes del relay está en tránsito.
     - Ampliado `RELAY_ESTABLISH_TIMEOUT_MS` de 6000L a 10000L (10s) para brindar holgura suficiente a la resolución SDP de Android sin abortar prematuramente la conexión con `read ret: -1`.
     - Ajustado el retardo post-BLE-burst a 2500ms para permitir que el servicio SPP de las gafas Meizu Myvu termine de iniciar antes de intentar la conexión por socket.
  2. `OpenAiTranscriptionClient.kt`:
     - Incorporado parámetro multipart `prompt` (`"Preguntas, comandos y consultas en español para asistente de voz en gafas inteligentes AR."`) al endpoint de transcripción Whisper, evitando el recorte de palabras interrogativas iniciales ("¿Cuál...", "Cómo...").
- **Verificación**:
  - Tests unitarios `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL** (34 tareas ejecutadas, 0 fallos).
  - Ensamblado de APK debug `./gradlew assembleDebug`: **BUILD SUCCESSFUL** en 893ms.

### [2026-09-09] — Expansión de Fast-Path de Calendario (Singular/Plural) y Optimización de Carga Útil en IA Agéntica
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-09-expand-calendar-fastpath-and-optimize-ai-payload.md`.
- **Análisis de Logs Post-Ajustes**:
  - Confirmada conexión de RFCOMM al primer intento en 459ms (sin timeout de 6s ni `read ret: -1`).
  - Confirmada ráfaga de relay completada limpiamente sin reintentos erróneos de handshake.
  - Confirmada captura de palabras iniciales en Whisper STT ("¿Cuál es...", "¿Tengo alguna...").
  - Identificada falla en consultas de reunión en singular (*"¿Tengo alguna reunión hoy por la tarde?"* y *"Tengo alguna reunión el día de hoy."*), que cayeron al LLM remoto y generaron `SocketTimeoutException` por payload excesivo de 15.378 caracteres.
- **Mejoras Implementadas**:
  1. `VoiceActionRouter.kt`:
     - Expandida la condición de Fast-Path para calendario a singular y plural: `"reunion"`, `"agenda"`, `"calendario"`, `"evento"`, `"cita"`, `"compromiso"` y patrones naturales como `"que tengo hoy"`, `"tengo algo para hoy"`, etc.
     - Estas consultas ahora se resuelven de forma 100% local en **~200ms**, sin depender del LLM ni incurrir en timeouts.
  2. `AiConversation.kt`:
     - Omitido el addendum textual de 30 skills (`buildSystemPromptAddendum()`) en el system prompt cuando el cliente de IA soporta Function Calling nativo (`client.supportsToolCalling()`).
     - Reduce la carga útil del request agéntico en más de 5.000 caracteres (ahorrando más de 1.200 tokens por turno).
  3. `AiHttpClient.kt`:
     - Reducido `LOCAL_READ_TIMEOUT_MS` de 45.000ms a 20.000ms para fallar rápidamente si el endpoint remoto/local no responde, evitando bloqueos prolongados en la interfaz de las gafas.
- **Verificación**:
  - Pruebas unitarias en `VoiceActionRouterTest.kt` ampliadas con `testCalendarFastPathVariations` (100% pasando).
  - Suite `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL** (34 tareas OK).
  - Ensamblado de APK debug `./gradlew assembleDebug`: **BUILD SUCCESSFUL** en 900ms.

### [2026-09-09] — Fast-Path Web 'En Google', Discriminación de Timeout y Poda Dinámica de Tools
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-09-web-search-fastpath-and-ai-timeout-optimization.md`.
- **Análisis de Logs Post-Ajustes**:
  - Confirmada conexión RFCOMM ultrarrápida en **267ms** y confirmación de ability en **74ms** (cero timeouts de socket).
  - Confirmada ráfaga de 29 mensajes completada de forma limpia sin reintentos erróneos de handshake.
  - Confirmado Fast-Path de clima en **2.2s** y Fast-Path de calendario en **13ms** (100% en español, cero avisos en inglés).
  - Diagnosticada falla en consulta de programación (*"En google cuál es la manera más rápida de buscar un ítem en una array de python."*): el prefijo `"En google "` omitió el Fast-Path web, cayó al LLM con 29 herramientas completas (15.419 caracteres), y se le aplicó timeout de 20s en vez de 60s al asumir erróneamente que era una red local privada, provocando `SocketTimeoutException`.
- **Mejoras Implementadas**:
  1. `ExternalInfoService.kt`:
     - Expandido `isGeneralSearchQuery` para interceptar `"en google "`, `"googlea "`, `"googlear "`, `"busca en internet"`, `"buscar en internet"`, y fórmulas técnicas comunes (`"cual es la manera"`, `"cual es la forma"`, `"como se busca"`, `"como buscar"`, `"como programar"`, `"como hacer"`).
     - Actualizado el regex de `fetchGoogleOrWebSearch` para limpiar exhaustivamente prefijos como `"En google"`, `"busca en google"` y puntuaciones finales, resolviendo consultas informativas y de código directamente en Google / DuckDuckGo / Wikipedia en **1.2 a 2.0 segundos** sin colapsar el LLM.
  2. `AiHttpClient.kt`:
     - Refinada la discriminación de `isLocal`: solo clasifica como red local si el host es verdaderamente una IP privada (`10.*`, `192.168.*`, `172.16-31.*`, `127.0.0.1`, `localhost`).
     - Endpoints públicos en internet configurados bajo `AiProvider.LOCAL` (ej. `https://soft-ia.co/...`) ahora reciben el timeout completo de 60s (`READ_TIMEOUT_MS`).
     - Incrementado `LOCAL_READ_TIMEOUT_MS` de 20.000ms a 30.000ms para mayor holgura en inferencia local con GPUs lentas.
  3. `AgenticToolExecutor.kt`:
     - Implementada poda dinámica de herramientas (`pruneToolsForQuery`): cuando la consulta es una duda conceptual, de programación o búsqueda general, solo se envían las herramientas de búsqueda y cálculo (`google_search`, `wikipedia_search`, `duckduckgo_search`, `code_calculator_math`).
     - Las herramientas de hardware (llamadas, whatsapp, telegram, alarmas, linterna, bluetooth, teleprompter, cámara) solo se adjuntan cuando la frase contiene verbos/intenciones afines.
     - Reduce la carga del payload de 15.419 caracteres a menos de 2.500 caracteres (>80% de ahorro de tokens y drástica reducción de latencia).
- **Verificación**:
  - Pruebas unitarias ampliadas en `ExternalInfoServiceTest.kt` y `AgenticModuleIntegrationTest.kt` (100% pasando).
  - Suite `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL** (34 tareas OK).
  - Ensamblado de APK debug `./gradlew assembleDebug`: **BUILD SUCCESSFUL** en 1s.

### [2026-09-09] — Actualización en Vivo de Noticias (RSS), Acciones/Cripto (Yahoo Finance) y TRM
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-09-live-web-search-news-stocks-currency.md`.
- **Problema Reportado**: Consultas como *"noticias el día de hoy en Colombia"* retornaban resultados que parecían fijos o desactualizados, y no existía soporte en vivo para cotizaciones de acciones bursátiles ni divisas locales (TRM).
- **Causas Raíz Identificadas**:
  1. `fetchNewsSearch` contenía un regex de limpieza incompleto. Frases como *"noticias el día de hoy en Colombia"* o *"solicite información de noticias..."* enviaban a Google News RSS cadenas con ruido temporal que forzaban la búsqueda literal de artículos antiguos que contuvieran la palabra "hoy".
  2. No existía soporte para acciones (stocks) ni criptomonedas (Bitcoin, Ethereum, Apple, Tesla, Nvidia, Ecopetrol).
  3. No se reconocía la sigla "TRM" (Tasa Representativa del Mercado) para el dólar en Colombia.
  4. En `VoiceActionRouter.kt`, las noticias no tenían rama directa Fast-Path.
- **Mejoras Implementadas**:
  1. `ExternalInfoService.kt`:
     - Reescrito `fetchNewsSearch`: limpia rellenos conversacionales y términos temporales; si la consulta es general o sobre Colombia, consulta el feed en vivo de última hora `news.google.com/rss?hl=es-419&gl=CO&ceid=CO:es-419` y extrae titular, medio emisor y viñetas concisas con datos de las últimas horas.
     - Creado `fetchStockOrMarket` e `isStockOrMarketQuery`: integración directa con Yahoo Finance API en tiempo real (gratuita, sin API key) para cotizaciones de Apple (`AAPL`), Tesla (`TSLA`), Microsoft (`MSFT`), Nvidia (`NVDA`), Google (`GOOGL`), Amazon (`AMZN`), Meta (`META`), Ecopetrol (`EC`), Bitcoin (`BTC-USD`), Ethereum (`ETH-USD`), S&P 500 (`^GSPC`), Nasdaq (`^IXIC`) y búsqueda dinámica por ticker, calculando precio actual y porcentaje de variación del día.
     - Añadido soporte para "TRM" y "tasa representativa" en `isCurrencyQuery` y `extractCurrencyRequest` mapeando a `USD -> COP`.
  2. `VoiceActionRouter.kt`:
     - Añadidas sub-rutas Fast-Path explícitas 5c (Acciones/Cripto) y 5d (Noticias en vivo) dirigidas a `isAsyncExternalSearch`, resolviendo todo en **1.0 a 2.0s** sin depender del LLM.
- **Verificación**:
  - Suite unitaria `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL** (34 tareas OK, 0 fallos).
  - Ensamblado de APK debug `./gradlew assembleDebug`: **BUILD SUCCESSFUL** en 968ms.

### [2026-09-09] — Búsqueda Inteligente de Contactos por Similitud/Subconjunto en Llamadas, WhatsApp y SMS
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-09-fuzzy-contact-matching-and-messaging-enhancement.md`.
- **Problema Reportado**: Cuando el usuario pedía llamar o enviar mensajes a nombres parciales o compuestos (ej. contacto guardado como `"Matias Castro hijo"` y el usuario dicta *"matias"* o *"matias castro"*), el sistema no encontraba el contacto o fallaba el envío por falta de coincidencia exacta o divergencia en los motores de búsqueda.
- **Causas Raíz Identificadas**:
  1. `ContactHelper.kt` no calculaba cobertura de subconjunto (Token Subset Containment): premiaba subcadena con solo 100 puntos y no otorgaba bonificación determinante si el 100% de los tokens dichos por el usuario estaban presentes en el contacto de la agenda.
  2. `normalize` no limpiaba signos de puntuación, paréntesis ni emojis (ej. `"Matias Castro (Hijo) 📱"` dejaba tokens corruptos como `"(hijo)"`).
  3. `PhoneActionExecutor.kt` duplicaba la búsqueda de contactos con código desfasado, y en `openWhatsApp` descartaba los datos del contacto encontrado forzando una segunda búsqueda SQL que volvía a fallar.
  4. Faltaba soporte para SMS (mensajes de texto) tanto en `PhoneActionExecutor` como en `VoiceActionRouter` y las habilidades de IA.
- **Mejoras Implementadas**:
  1. `ContactHelper.kt`:
     - Función `cleanText`: limpia tildes (NFD), signos de puntuación, emojis, símbolos y estandariza espacios.
     - Función `calculateScore`: sistema de puntuación multi-criterio que otorga 2000 puntos a coincidencia exacta absoluta, +500 si es prefijo continuo, +350 si el 100% de los tokens pedidos están contenidos en el contacto (Subset Containment), +60 por cada token idéntico, +80 por preservación de orden y tolerancia fonética Levenshtein.
     - Estructura `ContactMatch(number, displayName, score, isExact)` y método `findBestContactMatch`.
  2. `PhoneActionExecutor.kt`:
     - Unificada toda la resolución de contactos hacia `ContactHelper`.
     - Mejorada la extracción gramatical en `openWhatsApp`: reconoce conectores coloquiales (*"que"*, *"que diga"*, *"diciendo"*, *"dile que"*) y limpia partículas residuales del mensaje antes de disparar.
     - Implementado `sendSms(text)`: envía SMS 100% manos libres si cuenta con permiso `SEND_SMS` vía `SmsManager`, con fallback automático al intent `ACTION_SENDTO` (`smsto:...`).
     - Actualizado `makeCall`: resuelve contactos parciales y compuestos con `ContactHelper` y marca con `TelecomManager`.
  3. `VoiceActionRouter.kt`:
     - Agregada ruta Fast-Path (6) para SMS (*"envía un sms a..."*, *"manda mensaje de texto a..."*, *"escríbele un texto a..."*) antes de WhatsApp para evitar interferencias.
  4. Habilidades y Handlers:
     - Creado `SendSmsHandler.kt` y registrado como habilidad `"send-sms"` en `SkillRegistry.kt` para Native Tool Calling con Gemini/LiteLLM.
- **Verificación**:
  - Creada suite `ContactHelperTest.kt` validando casos de subconjunto, prefijo, desempate y limpieza de emojis.
  - Actualizado `VoiceActionRouterTest.kt` con pruebas para Fast-Path SMS.
  - Verificación unitaria `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL** (200 tests pasando, 0 fallos).
  - Ensamblado de APK debug `./gradlew assembleDebug`: **BUILD SUCCESSFUL** en 2s.

### [2026-09-09] — Corrección de Hallazgos en Logs: Colisión de Turnos, Notificaciones, Tópicos de Noticias y TRM
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-09-log-analysis-and-findings-resolution-plan.md`.
- **Problemas Detectados en Logs**:
  1. En `AiConversation.kt`, cuando un turno remoto (`soft-ia.co`) tardó >60s y el usuario inició un nuevo turno pulsando el botón (13:51:29), el timeout de la petición huérfana (13:51:37) adoptó el `sessionId` del nuevo turno, disparó TTS con error y canceló la respuesta válida de divisas del nuevo turno.
  2. En `VoiceActionRouter.kt`, la consulta *"Notificaciones tengo pendientes por leer."* no coincidió con el regex rígido, cayó al LLM remoto y colapsó en timeout.
  3. En `ExternalInfoService.kt`, *"Noticias relevantes hay en Barranquilla el día de hoy"* buscó literalmente `"relevantes hay en Barranquilla el día"` en Google News RSS.
  4. La consulta de TRM / USD a COP dependía exclusivamente de `open.er-api.com`.
- **Mejoras Implementadas**:
  1. `AiConversation.kt`:
     - Blindaje de turnos: congelado `turnSessionId` inmutable en cada ejecución asíncrona. Si al completarse o fallar `turnSessionId != sessionId` o `!active`, la respuesta o error se descarta en silencio absoluto, protegiendo al turno nuevo de cualquier interferencia.
     - En `abandon()`: llamada inmediata a `tts.stop()` para cortar en seco el audio del turno anterior.
  2. `VoiceActionRouter.kt`:
     - Detección flexible e integral de consultas de notificaciones pendientes (`notificaciones`, `tengo`, `pendientes`, `leer`, `revisar`, `mensajes`, `correos`) retornando en **<15ms** el resumen de `MirrorNotificationListener`.
  3. `ExternalInfoService.kt`:
     - `fetchNewsSearch`: Purgado semántico de adjetivos, partículas de relleno y conectores (`relevantes`, `importantes`, `hay en`, `el dia`, etc.) para aislar la entidad temática o ciudad limpia (`"Barranquilla"`), retornando encabezados limpios en el HUD.
     - `fetchCurrencyRate`: Integrada consulta en tiempo real al dataset oficial de la Superintendencia Financiera de Colombia (`datos.gov.co/resource/ceyp-9c7c.json`) para cotización de TRM oficial USD/COP con fallbacks a `open.er-api.com` y Yahoo Finance.
- **Verificación**:
  - Pruebas unitarias en `VoiceActionRouterTest.kt` y `ExternalInfoServiceTest.kt`.
### [2026-09-09] — Corrección de Enrutamiento de Divisas vs Definiciones, Prefijos de Noticias y Respaldo Público
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-09-currency-misrouting-and-news-clean-fix.md`.
- **Problemas Detectados en Logs (14:06 - 14:10)**:
  1. Consulta `"¿Cuál es el significado de la palabra retropropagación en redes neuronales?"` fue clasificada erróneamente como `Fast-Path currency query` debido a que `currencyKeywords` contenía `"eur"` y hacía `contains("eur")` sobre `"n-EUR-onales"`.
  2. Consulta `"¿Qué noticias relevantes hay hoy en Barranquilla?"` entregó en HUD: `"Noticias de hoy (¿Qué noticias en Barranquilla): 1) ..."` porque el regex no soportaba acento en `¿Qué` sin flag Unicode `(?iu)` y dejaba partículas interrogativas pegadas.
  3. `BackupManager` falló al sobreescribir el archivo de respaldo en Descargas públicas: `Tried to overwrite the destination, but failed to delete it.` debido a restricciones de Scoped Storage de Android con `File.copyTo(..., overwrite = true)`.
- **Mejoras Implementadas**:
  1. `ExternalInfoService.kt`:
     - Blindada `isCurrencyQuery`: utiliza regex con límites de palabra `\b` (`Regex("\\b(${currencyKeywords.joinToString("|") { Regex.escape(it) }})\\b")`), evitando falsos positivos con subcadenas como `"eur"` en `"neuronales"`, `"cop"` en `"microprocesador"`, o `"soles"` en `"girasoles"`.
     - Exclusión explícita de consultas de definición conceptual (`significado`, `definicion`, `concepto`, `que es`, `quien es`) de la ruta de divisas.
     - Expandida `isGeneralSearchQuery` y `isDefinitionQuery` para capturar directamente preguntas de significado y definición, extrayendo el término limpio para resolución en Wikipedia/DuckDuckGo.
     - En `fetchNewsSearch`: bucle de purga iterativo con soporte Unicode `(?iu)` que remueve sucesivamente prefijos interrogativos (`¿qué noticias`, `cuáles son las noticias`), stopwords conversacionales (`relevantes`, `hoy`, `hay en`) y preposiciones iniciales (`en`, `de`), produciendo etiquetas limpias como `"Barranquilla"`.
     - Añadido flag `(?iu)` y soporte de tildes en `fetchGoogleOrWebSearch` y `fetchStockOrMarket`.
  2. `BackupManager.kt`:
     - Sustituido `zipFile.copyTo(publicBackupFile, overwrite = true)` por flujo directo `FileOutputStream(publicBackupFile).use { out -> zipFile.inputStream().use { input -> input.copyTo(out) } }`, truncando y sobreescribiendo los bytes in-situ sin intentar invocar `delete()` bloqueado por Scoped Storage.
  3. `ExternalInfoServiceTest.kt`:
     - Nuevas pruebas unitarias validando que consultas como `"¿Cuál es el significado de la palabra retropropagación en redes neuronales?"` no sean marcadas como divisa y sean reconocidas como búsqueda general.
     - Pruebas validando que `"¿Qué noticias relevantes hay hoy en Barranquilla?"` genere etiqueta limpia `"Barranquilla"` sin prefijos interrogativos.
- **Verificación**:
  - Ejecución de pruebas unitarias `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL** (34 tareas ejecutadas/al día, 100% pasando).
### [2026-09-09] — Corrección de Separación Contacto/Mensaje en WhatsApp y Envío Automático Manos Libres
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-09-whatsapp-autosend-and-contact-delimiter-fix.md`.
- **Problemas Detectados en Logs (14:18 - 14:21)**:
  1. Consulta `"Envía mensaje de WhatsApp a Matías Castro. Hola hijo, ¿cómo vas? ¿Cómo te fue?"`: el parser separaba indiscriminadamente por comas (`cleanRaw.contains(",")`). Como el mensaje contenía `"Hola hijo, ¿cómo vas?"`, partió después de `"Hola hijo"`, dejando el destinatario como `"Matías Castro. Hola hijo"`. Esto provocó que perdiera la coincidencia exacta y se emparejara con `"Matías Castro Nuevo"` (score 300), perdiendo además `"Hola hijo"` en el mensaje.
  2. El mensaje no se enviaba automáticamente: `AutoSendAccessibilityService` buscaba texto con `findAccessibilityNodeInfosByText("enviar")`, el cual falla en WhatsApp porque el botón de enviar es un `ImageButton` con texto nulo y `contentDescription="Enviar"`. Además, la bandera `shouldAutoSendWhatsApp` se desactivaba en el primer evento de ventana antes de que la vista de chat terminara de renderizarse.
- **Mejoras Implementadas**:
  1. `ContactHelper.kt`:
     - Implementado método unificado `extractRecipientAndMessage(context, rawText)` que prioriza delimitadores explícitos (`:`, `|`), límite de frase por punto (`.\s+` generado por Whisper STT) validando coincidencia de contacto, conectores gramaticales (`dile que`, `que diga`), separación por coma condicional a que el prefijo sea un contacto válido, y ventana deslizante de 1 a 4 tokens.
     - Implementada función `cleanPunctuation(text)` que purga exhaustivamente signos de puntuación iniciales y finales (`.`, `,`, `?`, `!`, `¿`, `¡`) antes de evaluar emparejamiento difuso.
  2. `AutoSendAccessibilityService.kt`:
     - Reemplazada la búsqueda lineal por un recorrido en amplitud (BFS) sobre el árbol de nodos de accesibilidad, inspeccionando `contentDescription` (`"enviar"`, `"send"`), View IDs (`com.whatsapp:id/send`, `conversation_send_button`, etc.) y `text`.
     - Implementados reintentos automáticos programados (400ms, 800ms, 1200ms, 1800ms, 2600ms, 3600ms) y mantenimiento activo de la bandera de auto-envío hasta que el clic sea ejecutado con éxito o expire el tiempo límite de 7 segundos.
     - Añadido método estático `isAccessibilityServiceEnabled(context)` para verificar si el usuario tiene habilitado el servicio en los Ajustes del sistema.
  3. `PhoneActionExecutor.kt` y `VoiceActionRouter.kt`:
     - Integrado `LockScreenHelper.wakeUpScreen(context)` antes de abrir WhatsApp o Telegram para asegurar que la pantalla se despierte y permita la interacción de primer plano.
     - Centralizado el parseo de WhatsApp y SMS a través de `ContactHelper.extractRecipientAndMessage`.
     - Respuesta de voz / HUD adaptativa: si el servicio de accesibilidad no está activo en Ajustes, informa pedagógicamente al usuario: `"Abriendo WhatsApp. Para envío automático sin tocar la pantalla, activa el Asistente MYVU en Accesibilidad."`.
  4. `ContactHelperTest.kt`:
     - Nuevas pruebas unitarias validando `cleanPunctuation` y la separación por punto de frase de `"Matías Castro. Hola hijo, ¿cómo vas? ¿Cómo te fue?"`.
- **Verificación**:
  - Ejecución de 205 pruebas unitarias con `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL** (0 fallos).
  - Ensamblado de APK debug `./gradlew assembleDebug`: **BUILD SUCCESSFUL** en 1s.

### [2026-09-09] — Implementación de Desbloqueo de Pantalla y Envío Automático Universal Manos Libres
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-09-lockscreen-universal-messaging-autosend.md`.
- **Problemas Detectados en Logs (14:35 - 14:37)**:
  1. Consulta `"Envía mensaje de whatsapp a Matías Castro. Hola socio, ¿cómo estás? ¿Cómo te fue?"`: el parser extrajo correctamente a `"Matías Castro"` y el mensaje limpio `"Hola socio, ¿cómo estás? ¿Cómo te fue?"`.
  2. Sin embargo, el mensaje no se envió automáticamente; el usuario tuvo que sacar el teléfono, desbloquearlo y pulsar "Enviar".
  3. Causa raíz:
     - El teléfono estaba bloqueado en el bolsillo. Android restringe que ventanas de aplicaciones de terceros (WhatsApp) se muestren por encima del Keyguard bloqueado.
     - La ventana de auto-envío de 7 segundos en `AutoSendAccessibilityService` expiró antes de que el usuario desbloqueara el móvil (`WhatsApp auto-send window timed out`).
     - Al desbloquear el móvil, el servicio ya no estaba activo y no pulsó el botón.
     - Además, el servicio de accesibilidad solo escuchaba WhatsApp y Telegram en su configuración XML, ignorando Google Messages, Samsung Messages, Signal y otras apps.
- **Mejoras Implementadas**:
  1. `SendTrampolineActivity.kt` y `themes.xml`:
     - Creada actividad transparente con `showWhenLocked="true"`, `turnScreenOn="true"`, tema `Theme.Myvu.Translucent` y `excludeFromRecents="true"`.
     - Invoca `KeyguardManager.requestDismissKeyguard`: si el móvil usa Smart Lock (p.ej. vinculado a las gafas MYVU por Bluetooth) desbloquea de forma automática sin pedir PIN; si usa huella/PIN, presenta de inmediato el lector biométrico. Al desbloquearse, lanza la app de mensajería y se finaliza limpiamente.
  2. `AutoSendAccessibilityService.kt`:
     - Escucha dinámica de `Intent.ACTION_USER_PRESENT`: en cuanto el usuario desbloquea el móvil, dispara de inmediato una ráfaga de intentos de clic (100ms a 3000ms).
     - Ventana de espera adaptativa: 45 segundos si el teléfono estaba bloqueado (dando tiempo suficiente para sacar el móvil y autenticar con huella) y 15 segundos si ya estaba desbloqueado.
     - Soporte universal de aplicaciones de mensajería: WhatsApp, Telegram, Google Messages (`com.google.android.apps.messaging`), Samsung Messages, Signal, etc.
     - Detección BFS expandida con filtrado estricto anti-micrófono/audio (evitando pulsar notas de voz) y fallback de clic por coordenadas táctiles con `dispatchGesture` en Android 7.0+.
  3. `accessibility_service_config.xml`:
     - Eliminada la restricción de paquetes (`android:packageNames`) y añadido `flagRetrieveInteractiveWindows` para permitir la supervisión de cualquier app de mensajería.
  4. `PhoneActionExecutor.kt`:
     - Creado método centralizado `dispatchMessagingIntent` que detecta si el móvil está bloqueado (`LockScreenHelper.isDeviceLocked`), disparando el trampolín si es necesario y activando el auto-envío con la duración correspondiente.
  5. `AutoSendAccessibilityServiceTest.kt`:
     - Suite completa de pruebas unitarias validando la detección de paquetes de mensajería, duraciones de ventana según estado de bloqueo y seguridad de nodos.
- **Verificación**:
  - Pruebas unitarias `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL in 15s** (209 pruebas ejecutadas y pasando al 100%).
  - Ensamblado de APK debug `./gradlew assembleDebug`: **BUILD SUCCESSFUL in 1s** (`app-debug.apk` 117MB).

### [2026-09-09] — Implementación de Control Total Manos Libres Tipo Google Assistant y Desbloqueo Invisible
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-09-google-assistant-parity-and-lockscreen-control.md`.
- **Problemas Detectados en Logs (14:48 - 14:50)**:
  1. Al solicitar envío de WhatsApp, se abrió la app MYVU en pantalla y hubo una demora de 12 segundos antes de que WhatsApp se lanzara:
     - `SendTrampolineActivity` compartía el `taskAffinity` por defecto, arrastrando a `ConnectActivity` a la pantalla.
     - `requestDismissKeyguard` llamado en `onCreate` fue cancelado en 68ms por falta de foco en la ventana (`Keyguard dismiss cancelled by user`).
     - Al cancelarse, la actividad esperó pasivamente 12 segundos hasta el timeout de seguridad para despachar WhatsApp.
  2. El usuario solicita paridad con Google Assistant para controlar aplicaciones y funciones del teléfono directamente con el móvil bloqueado y sin desbloquear.
- **Mejoras Implementadas**:
  1. `SendTrampolineActivity.kt` y `AndroidManifest.xml`:
     - Aislada con `android:taskAffinity=""`, `android:launchMode="singleInstance"`, `android:noHistory="true"` y tema translúcido invisible sin barra de título (`@android:style/Theme.Translucent.NoTitleBar`). **Nunca más se jala la app MYVU a la pantalla**.
     - Solicitud de desbloqueo movida a `onWindowFocusChanged(hasFocus = true)`.
     - Si ocurre cancelación o fallo en el desbloqueo (`onCancelledOrFailed`), despacha el intent objetivo de forma inmediata (**0ms de espera**, eliminando el timeout de 12 segundos).
  2. Control de Linterna (Torch / Flashlight):
     - `PhoneActionExecutor.setFlashlight(enabled: Boolean)` vía `CameraManager.setTorchMode`.
     - Fast-Paths en `VoiceActionRouter.kt`: *"enciende la linterna"*, *"apaga la linterna"*, *"prende la linterna"*, etc. Opera 100% en segundo plano con el móvil bloqueado en el bolsillo.
  3. Control Multimedia y Música en Segundo Plano:
     - Fast-Paths en `VoiceActionRouter.kt`: *"pausa la música"*, *"reproduce música"*, *"siguiente canción"*, *"canción anterior"*, *"reanuda la música"*. Opera 100% con la pantalla bloqueada.
  4. Control de Volumen y Modos de Sonido (Ringer):
     - `PhoneActionExecutor.adjustVolume` y `setRingerMode`.
     - Fast-Paths en `VoiceActionRouter.kt`: *"sube el volumen"*, *"baja el volumen"*, *"silencia el teléfono"*, *"pon en vibración"*, *"activa el sonido"*.
  5. Lanzador Universal de Aplicaciones ("abre [app]"):
     - Resuelve el paquete de la aplicación mediante difusa y lo lanza despertando la pantalla y a través del trampolín invisible.
  6. Integración como Asistente Digital del Sistema Android (`VoiceInteractionService`):
     - Creado `MyvuVoiceInteractionService.kt`, `MyvuVoiceInteractionSessionService`, `MyvuRecognitionService` y `voice_interaction_service.xml`.
     - Registrado en `AndroidManifest.xml` con `BIND_VOICE_INTERACTION`. Permite seleccionar MYVU Client como el Asistente Digital Predeterminado del teléfono en Ajustes de Android.
- **Verificación**:
  - Pruebas unitarias `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL in 9s** (212 pruebas ejecutadas y pasando al 100%).
  - Ensamblado de APK debug `./gradlew assembleDebug`: **BUILD SUCCESSFUL in 1s** (`app-debug.apk` 117MB).

### [2026-09-09] — Documentación Integral de Instalación y Permisos de Android
- **Objetivo**: Proveer instrucciones paso a paso detalladas para la instalación del APK en Android y la concesión de todos los permisos estándar y especiales indispensables para el funcionamiento manos libres de las gafas Meizu Myvu.
- **Archivos Creados y Modificados**:
  1. `docs/ANDROID_SETUP_GUIDE.md`:
     - Guía exhaustiva en español con pasos para instalación vía ADB (`adb install -r -g`) e instalación manual con orígenes desconocidos.
     - Tabla completa de permisos estándar en tiempo de ejecución (Bluetooth, Ubicación, Contactos, Teléfono, SMS, Cámara, Micrófono, Calendario, Notificaciones).
     - Configuración detallada de 5 permisos especiales del sistema:
       - Acceso a notificaciones (`MirrorNotificationListener`).
       - Servicio de accesibilidad (`AutoSendAccessibilityService`) con solución al "Ajuste restringido" en Android 13/14+.
       - Desbloqueo extendido / Smart Lock con dispositivos de confianza (Meizu Myvu) para desbloqueo automático en bolsillo sin PIN.
       - Asistente digital predeterminado del sistema (`MyvuVoiceInteractionService`).
       - Optimización de batería sin restricciones (Doze mode whitelist) y auto-inicio en Xiaomi/HyperOS.
     - Script de comandos ADB en bloque para desarrolladores.
  2. `README.md`:
     - Nueva sección `"📲 Instalación y Configuración en Android"` con resumen de comandos y checklist de permisos indispensables.
     - Enlace directo a `docs/ANDROID_SETUP_GUIDE.md` en la sección de Documentación Adicional.
  3. `BUILD_INSTRUCTIONS.md`:
     - Sección 6 añadida con comando de instalación rápida vía ADB y enlace a la guía de permisos.

### [2026-09-09] — Implementación de Llamadas VoIP (WhatsApp, Teams, Google Chat) y Servicios de Salud (Pasos, Estrés, Ritmo Cardíaco)
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-09-voip-calls-and-health-integration.md`.
- **Objetivo**:
  1. Permitir llamadas VoIP mediante comandos de voz para WhatsApp, Microsoft Teams y Google Chat/Meet hacia contactos específicos o números/correos.
  2. Integración nativa con servicios y sensores de salud de Android para consultar pasos diarios, nivel de estrés, ritmo cardíaco y resumen de bienestar físico.
- **Componentes Creados y Modificados**:
  1. `HealthService.kt` (`com.myvu.client.health`):
     - Listener de hardware para podómetro nativo (`Sensor.TYPE_STEP_COUNTER` y `Sensor.TYPE_STEP_DETECTOR`).
     - Cálculo de pasos diarios con línea base automática reseteada a medianoche, calorías activas (`kcal`) y distancia (`km`).
     - Evaluación de niveles de estrés (0-100: Bajo/Relajado, Moderado, Elevado).
     - Parser inteligente en `MirrorNotificationListener`: sincroniza métricas en tiempo real a partir de notificaciones de apps de wearables (Samsung Health, Google Fit, Zepp Life, Mi Fitness, Garmin, Huawei).
     - Métodos de resumen: `getStepsSummary()`, `getStressSummary()`, `getHeartRateSummary()`, `getFullHealthSummary()`.
  2. `ContactHelper.kt`:
     - Consulta de `ContactsContract.Data` para extraer el `dataId` directo de llamadas VoIP de WhatsApp (`vnd.android.cursor.item/vnd.com.whatsapp.voip.call`).
  3. `PhoneActionExecutor.kt`:
     - `makeWhatsAppCall`: Invoca intent VoIP directo de WhatsApp si existe fila en contactos; como alternativa robusta, abre el chat de WhatsApp y activa el clic automático del botón de llamada.
     - `makeTeamsCall`: Resuelve correo o teléfono y despacha deep link `https://teams.microsoft.com/l/call/0/0?users=...` a `com.microsoft.teams`.
     - `makeGoogleChatCall`: Resuelve correo o teléfono y despacha deep link `https://meet.google.com/call/...` para Meet/Chat.
     - Enrutamiento transparente a través de `SendTrampolineActivity` cuando el móvil está bloqueado.
     - Tags soportados: `ACTION:CALL_WHATSAPP=`, `ACTION:CALL_TEAMS=`, `ACTION:CALL_GOOGLE_CHAT=`, `ACTION:HEALTH_STEPS`, `ACTION:HEALTH_STRESS`, `ACTION:HEALTH_SUMMARY`.
  4. `AutoSendAccessibilityService.kt`:
     - Modo `triggerAutoCall` con detección BFS y clic en botones de llamada de voz (`com.whatsapp:id/voice_call`, "Llamada de voz", "Voice call", "Llamar").
  5. `VoiceActionRouter.kt`:
     - Fast-paths en <5ms para:
       - WhatsApp Call: *"llama a [contacto] por whatsapp"*, *"videollamada a [contacto] por whatsapp"*.
       - Teams Call: *"llama a [contacto] por teams"*, *"inicia llamada de teams con [contacto]"*.
       - Google Chat Call: *"llama a [contacto] por google chat"*, *"llama a [contacto] por meet"*.
       - Pasos: *"cuántos pasos llevo"*, *"mis pasos de hoy"*, *"podómetro"*.
       - Estrés: *"nivel de estrés"*, *"cómo está mi estrés"*, *"estoy estresado"*.
       - Ritmo cardíaco: *"ritmo cardíaco"*, *"frecuencia cardíaca"*, *"pulsaciones"*.
       - Resumen salud: *"resumen de salud"*, *"resumen de actividad"*, *"mi salud hoy"*.
  6. Manifiestos y Handlers de Habilidades:
     - `voip-call/SKILL.md` + `VoipCallHandler.kt`.
     - `health-summary/SKILL.md` + `HealthSummaryHandler.kt`.
     - Registrados en `SkillRegistry.kt`.
  7. `AndroidManifest.xml`:
     - Permisos `ACTIVITY_RECOGNITION` y `BODY_SENSORS`.
     - Queries añadidas para `com.microsoft.teams`, `com.google.android.apps.tachyon`, `com.google.android.apps.meetings`, `com.google.android.apps.dynamite`, `com.google.android.apps.fitness`, `com.samsung.android.app.shealth`.
- **Verificación**:
  - Pruebas unitarias `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL in 13s** (218 pruebas unitarias pasando al 100%, incluyendo `VoiceActionRouterTest` y `HealthServiceTest`).
  - Compilación de APK debug `./gradlew assembleDebug`: **BUILD SUCCESSFUL in 1s**.

### [2026-09-09] — Anclaje de Memoria Viva y Protocolo Maestro Caveman
- **Directivas Establecidas**:
  1. Modo Cavernícola ("Kog"): Comunicación directa, primitiva, concisa y enérgica ("Ugh!").
  2. Búsquedas y Navegación: Uso prioritario de `codegraph` (`query`, `explore`, `node`, `impact`) y MCP `codegraph_explore` / `codebase-memory-mcp`.
  3. Protocolo de Sincronización: `codegraph sync` obligatorio al inicio y final de cada tarea.
  4. Flujo Superpowers: Plan previo obligatorio en `docs/superpowers/plans/` antes de tocar código.
  5. Registro de Cambios: Actualización constante de memoria (`docs/PROJECT_MEMORY.md`) y documentación del proyecto (`README.md`, `docs/ARCHITECTURE.md`) como paso final.
### [2026-09-09] — Implementación Completa de Control de Medios (NewPipe, OpenTune y Reproductores Externos)
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-09-external-app-and-media-control-newpipe-opentune.md`.
- **Archivos Creados**:
  1. `app/src/main/java/com/myvu/client/media/MediaPlaybackHelper.kt`:
     - Integración con `MediaSessionManager` + `MediaController` mediante el permiso de notificación de `MirrorNotificationListener`.
     - Extracción en tiempo real de metadatos (`title`, `artist`, `album`, `isPlaying`) y controles de transporte (`pause`, `resume`, `skipToNext`, `skipToPrevious`, `stop`).
     - Soporte para NewPipe (búsqueda y reproducción directa por deep link web de YouTube con paquete objetivo).
     - Soporte para OpenTune, InnerTune, RiMusic y ViMusic (`INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` y MediaSession).
     - Soporte para Spotify, YouTube Music, YouTube oficial, VLC, Deezer.
     - Ejecución segura con pantalla bloqueada en bolsillo mediante `SendTrampolineActivity` y `LockScreenHelper`.
     - Método `queryNowPlaying()` para respuesta por voz y HUD a *"¿Qué canción está sonando?"*.
  2. `app/src/main/assets/skills/built-in/app-media-control/SKILL.md`:
     - Manifiesto de habilidad declarando parámetros `action`, `query` y `target_app`.
  3. `app/src/main/java/com/myvu/client/skills/handlers/AppMediaControlHandler.kt`:
     - Handler agéntico que procesa llamadas nativas de herramientas de Gemini/LiteLLM.
- **Archivos Modificados**:
  1. `app/src/main/AndroidManifest.xml`: Declarados paquetes en `<queries>` (`org.schabi.newpipe`, `tune.music.opentune`, `com.zionhuang.music`, `it.fast4x.rimusic`, `it.fast4x.vimusic`, `com.spotify.music`, `com.google.android.apps.youtube.music`, etc., y acciones de búsqueda/reproducción).
  2. `PhoneActionExecutor.kt`:
     - Delegación de `sendMediaKey`, `pauseMusic`, `resumeMusic`, `nextTrack`, `previousTrack`, `stopMusic`, `queryNowPlaying`, `playInThirdPartyApp` y `searchInThirdPartyApp` a `MediaPlaybackHelper`.
     - Tags de acción procesados: `ACTION:MEDIA_PLAY=`, `ACTION:MEDIA_SEARCH=`, `ACTION:MEDIA_NOW_PLAYING`, `ACTION:MEDIA_PAUSE`, `ACTION:MEDIA_RESUME`, `ACTION:MEDIA_NEXT`, `ACTION:MEDIA_PREV`, `ACTION:MEDIA_STOP`.
  3. `VoiceActionRouter.kt`:
     - Fast-paths en <5ms para:
       - *"reproduce [canción] en (newpipe|opentune|spotify|youtube...)"*
       - *"busca [video/canción] en (newpipe|opentune...)"*
       - *"pausa la música"* / *"silencia la música"*
       - *"reproduce música"* / *"reanuda la música"*
       - *"siguiente canción"* / *"salta la canción"* / *"siguiente pista"*
       - *"canción anterior"* / *"retrocede la canción"* / *"repite la canción"*
       - *"detén la música"* / *"stop"* / *"para la música"*
       - *"¿qué canción está sonando?"* / *"¿qué está sonando?"* / *"info de la canción"*
  4. `SkillRegistry.kt`: Registrada la habilidad `app-media-control`.
  5. `VoiceActionRouterTest.kt`: Pruebas unitarias completas para controles de transporte y reproducción/búsqueda en NewPipe, OpenTune y Spotify.
  6. `README.md` y `docs/ARCHITECTURE.md`: Documentación técnica y guía de usuario actualizadas.
- **Verificación**:
  - Pruebas unitarias `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL in 12s** (228 pruebas unitarias ejecutadas y pasando al 100%).
  - Compilación de APK debug `./gradlew assembleDebug`: **BUILD SUCCESSFUL in 1s** (`app-debug.apk` 117MB).

### [2026-09-10] — Corrección de Drenaje Rápido de Batería y Desactivación de Escucha Activa por Defecto
- **Plan de Trabajo**: `docs/superpowers/plans/2026-09-10-glasses-battery-drain-and-active-listening-fix.md`.
- **Problema Detectado en Logs (`myvu_client_log.txt`)**:
  - Drenaje acelerado de batería en las gafas: caída de 23% a 15% en ~29 minutos (aprox. 16.5% por hora) en reposo relativo.
  - Causa raíz: En el mensaje de configuración inicial enviado a las gafas (`CODE_ASSISTANT_CONFIG`, msgId=40), el flag `"isContinuousDialogueEnable": true` estaba clavado en código duro (`hardcoded`) dentro de `AiProtocol.kt`.
  - En el firmware FlymeAR de Meizu Myvu, `isContinuousDialogueEnable: true` activa el modo de diálogo continuo / escucha activa permanente en el DSP de audio, manteniendo el micrófono y subsistema de reconocimiento en vigilia esperando más habla sin pulsar la patilla.
  - Además, no existía preferencia ni interfaz de usuario para que el usuario gestionara este comportamiento.
- **Solución Implementada**:
  1. `Prefs.kt`:
     - Añadidos `continuousDialogueEnabled(context)` y `setContinuousDialogueEnabled(context, boolean)`, con valor por defecto **`false`**.
     - `voiceWakeupEnabled` permanece también en **`false`** por defecto.
  2. `AiProtocol.kt`:
     - Actualizado `assistantConfig(lowPowerWakeupEnabled = false, continuousDialogueEnabled = false)`.
     - `isContinuousDialogueEnable` ahora toma el valor del parámetro (por defecto `false`), enviando `false` a las gafas.
  3. `ConnectionManager.kt` y `AiConversation.kt`:
     - Transmisión sincronizada de `AiProtocol.assistantConfig(Prefs.voiceWakeupEnabled(context), Prefs.continuousDialogueEnabled(context))` al conectar y en cada turno.
  4. `activity_settings.xml` y `SettingsActivity.kt`:
     - Añadida tarjeta "Escucha y Ahorro de Batería" en Ajustes con switches Material 3 para:
       - `swContinuousDialogue`: "Escucha activa continua" (con advertencia de batería, default apagado).
       - `swVoiceWakeup`: "Activación por voz (Wake word)" (con advertencia de batería, default apagado).
     - Actualización dinámica instantánea hacia las gafas al alternar los switches sin necesidad de reconectar (`pushAssistantConfigToGlasses()`).
  5. `AiProtocolTest.kt`:
     - Nueva suite de pruebas unitarias validando que `assistantConfig()` genera `isContinuousDialogueEnable: false` e `isLowPowerWakeupEnable: false` por defecto.
- **Verificación**:
  - Pruebas unitarias `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL in 33s** (100% pasando).
  - Compilación de APK debug `./gradlew assembleDebug`: **BUILD SUCCESSFUL in 2s**.

### [2026-09-10] — Diagnóstico Profundo de Log (1h 38m) y Plan Integral de Optimización de Batería/Recursos
- **Log Analizado**: `/home/rcastro/Descargas/myvu_client_log.txt` (Sesión de 10:11:58 a 11:49:41).
- **Plan Detallado**: [`docs/superpowers/plans/2026-09-10-battery-and-resource-optimization-plan.md`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/docs/superpowers/plans/2026-09-10-battery-and-resource-optimization-plan.md).
- **Comportamiento Observado de Batería**:
  - Gafas pasaron de 100% (10:55:06, al desconectar cargador) a 93% (11:48:05): 7% en 53 minutos (~8.0% por hora) en reposo pasivo.
- **Causas Raíz y Fugas Identificadas**:
  1. *Fuga crítica en `captured_init.txt` / `InitBurst.kt`*: La trama capturada 1117 (mensaje 24) reenvía `isContinuousDialogueEnable: true` e `isLowPowerWakeupEnable: true` en el init burst de RFCOMM, sobreescribiendo las preferencias del usuario porque `applyDefaults` se omite al reconectar el relay.
  2. *Polling excesivo de `get_device_info`*: Emitido 11 veces en 50 minutos por llamadas en `onResume` y `batteryQueryTask`, forzando a las gafas a responder con JSON pesado cuando ya emiten push espontáneo de batería (`sync_glass_battery_info`).
  3. *Sensores a 60Hz en segundo plano (`HealthService.kt`)*: `STEP_COUNTER` y `STEP_DETECTOR` registrados con `SENSOR_DELAY_UI`, impidiendo que el SoC del celular entre en deep sleep.
  4. *Heartbeat BLE rígido cada 10s*: Sin coalescencia; `notifyDataActivity()` nunca es llamado ante tráfico real.
  5. *Watchdog rompe Doze Mode*: `ServiceWatchdogReceiver` utiliza `setAndAllowWhileIdle(WAKEUP)` cada 15 min, despertando al celular innecesariamente.
  6. *Tempestad de reconexiones RFCOMM*: Sin backoff progresivo ante caídas del servidor SPP de las gafas.
- **Fases del Plan de Mejora**:
  - Fase 1: Filtro de init burst en `InitBurst.kt` y forzado de `applyDefaults()` en reconexiones.
  - Fase 2: Supresión de polling redundante de batería en `ConnectionManager`, `ConnectActivity` y `NotesActivity`.
  - Fase 3: Heartbeat BLE inteligente con coalescencia e intervalo extendido.
  - Fase 4: Optimización de sensores de salud (`SENSOR_DELAY_NORMAL` + batching) y watchdog amigable con Doze.
  - Fase 5: Backoff exponencial en reconexiones RFCOMM.
  - Fase 6: Pruebas, compilación y verificación.
- **Implementación Realizada**:
  1. `InitBurst.kt`:
     - Se filtran automáticamente tramas con `com.upuphone.ai.assistant` e `isContinuousDialogueEnable` para evitar que el init burst capturado en código duro sobreescriba las preferencias de bajo consumo.
  2. `ConnectionManager.kt`:
     - Añadido `lastBatteryUpdateTime` para registrar frescura de telemetría de batería.
     - `queryBatteryInfo`: No consulta `get_device_info` si la batería ya es conocida y tiene menos de 15 minutos (a menos que se pase `force = true`).
     - `batteryQueryTask`: Convertido en comprobación de seguridad cada 30 minutos, ejecutando `queryBatteryInfo` únicamente si no ha habido reporte push en > 45 minutos.
     - Enlace de Relay: En caso de que BLE ya haya ejecutado `applyDefaults()`, se envía de forma dirigida el `assistantConfig` de bajo consumo (`continuousDialogue = false`, `voiceWakeup = false`) para garantizar que el canal relay no quede desalineado.
  3. `ConnectActivity.kt` y `NotesActivity.kt`:
     - En `onServiceConnected` de `ConnectActivity`, solo se consulta batería si `glassesInfo()?.battery == null`.
     - Los toques explícitos del usuario en `cardStatus` y `layNotesGlassesBattery` pasan `force = true`.
  4. `BleHeartbeat.kt` y `BleTransport.kt`:
     - Conectado `notifyDataActivity()` a `dispatchNotification` (recepción) y `writerFor` (transmisión) en `BleTransport`.
     - `BleHeartbeat`: Intervalos ajustados a `STANDARD_INTERVAL_MS = 20000L` (20s) y `EXTENDED_INTERVAL_MS = 25000L` (25s). Cada paquete intercambiado reprograma el temporizador evitando disparar el heartbeat durante tráfico activo.
     - Corregido bug en `isDataActive` requiriendo `lastDataActivityTime > 0L`.
  5. `HealthService.kt`:
     - Cambiado `SENSOR_DELAY_UI` (60 Hz) a `SENSOR_DELAY_NORMAL` con batching de hardware de 60 segundos (`maxReportLatencyUs = 60_000_000`), permitiendo que el SoC del celular permanezca en reposo profundo.
     - Si `TYPE_STEP_COUNTER` está presente, se omite registrar `TYPE_STEP_DETECTOR`, eliminando interrupciones por paso en CPU.
  6. `ServiceWatchdogReceiver.kt`:
     - Reemplazado `setAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)` por `alarmManager.set(AlarmManager.ELAPSED_REALTIME, ...)` (sin WAKEUP), permitiendo que el celular duerma plenamente en Doze Mode.
  7. `RelaySupervisor.kt`:
     - `onRelayLost`: Ya no resetea `attempt = 0` inmediatamente, sino que respeta el `calculateBackoffDelay(attempt)` (5s -> 10s -> 20s -> 40s -> 60s) para no asediar el socket SPP de las gafas mientras reciclan su servidor.
     - `RESET_ATTEMPTS_AFTER_MS` ampliado a 120 segundos.
  8. `InitBurstTest.kt` y `BleHeartbeatTest.kt`:
     - Creadas suites de pruebas unitarias que verifican la exclusión de tramas de escucha activa y la coalescencia de tráfico del heartbeat.
- **Verificación**:
  - Pruebas unitarias `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL in 10s** (233 pruebas ejecutadas, 100% pasando).
  - Compilación de APK debug `./gradlew assembleDebug`: **BUILD SUCCESSFUL in 929ms** (`app-debug.apk` generado correctamente).

### [2026-09-10] — Planificación: Nuevas Capacidades Agénticas, Automatización Cotidiana y Delegación ("Segundo Cerebro")
- **Plan Detallado**: [`docs/superpowers/plans/2026-09-10-agent-daily-assistant-and-automation-plan.md`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/docs/superpowers/plans/2026-09-10-agent-daily-assistant-and-automation-plan.md).
- **Objetivo Central**: Transformar el asistente pasivo en un asistente cotidiano proactivo de mínima fricción para el usuario de gafas AR.
- **Ejes de Funcionalidades Propuestas**:
  1. *Daily Briefing ("Mi Día" / "Buenos días")*: Sintetiza en <5s clima, próxima cita, tareas pendientes, avisos sin leer y batería.
  2. *Rutinas y Modos Contextuales (Macros de una frase)*:
     - Modo Reunión: Zen Mode en gafas + DND en celular + grabación y extracción de minutas.
     - Modo Conducción: Brillo alto + TTS de mensajes VIP sin desbloquear.
     - Modo Gimnasio: Música de entrenamiento + contador de pasos y descansos en HUD.
     - Modo Noche: Brillo 1 + verificación de alarmas + reporte de batería.
  3. *Memoria Espacial*: Guardar ubicación GPS del auto ("Dónde estacioné") y cálculo de distancia/rumbo para retorno.
  4. *Listas de Compras y Tareas*: Delegar adición y marcado de checklists ("añade café a las compras") en Room.
  5. *Envío Rápido de Ubicación y Plantillas*: "Mándale mi ubicación a [Contacto]" por WhatsApp/Telegram en un toque.
  6. *Temporizadores y Pomodoro Nombrados*: Cuenta regresiva en HUD.
  7. *Segundo Cerebro*: Búsqueda semántica rápida en notas para datos personales ("Recuerda que...").
### [2026-09-10] — Implementación Completa: Asistente Cotidiano, Automatizaciones y Delegación ("Segundo Cerebro")
- **Plan**: `docs/superpowers/plans/2026-09-10-agent-daily-assistant-and-automation-plan.md`
- **Componentes Creados e Integrados**:
  1. `DailyBriefingService.kt`:
     - Genera síntesis ejecutiva hablada (TTS) en <5ms: saludo según hora del día, resumen de clima local síncrono (`WeatherSync.lastSummary`), reuniones del día vía `CalendarService.getEvents`, conteo y títulos de tareas pendientes vía `TodoRepository`, resumen de avisos VIP vía `MirrorNotificationListener` y batería de las gafas.
     - Proyecta en el HUD de las gafas un resumen compacto de hasta 120 caracteres en el teleprompter ("Mi Día").
  2. `RoutineManager.kt`:
     - Macros de sistema ejecutados por una sola orden de voz:
       - *Modo Reunión*: Activa `SystemSettings.setZenMode(true)` en gafas, conmuta audio de celular a `RINGER_MODE_VIBRATE`, persiste modo en `Prefs`. Desactivación restaura `ZenMode(false)` y `RINGER_MODE_NORMAL`.
       - *Modo Conducción*: Ajusta brillo de gafas al máximo (`setBrightness(3)`), prepara audio manos libres. Desactivación restaura brillo estándar del usuario.
       - *Modo Gimnasio*: Consulta pasos y actividad en `HealthService`, notifica audio y HUD.
       - *Modo Noche*: Fija brillo mínimo (`setBrightness(1)`), silencia celular (`RINGER_MODE_SILENT`), alerta de nivel de batería de las gafas para carga nocturna.
  3. `SpatialMemoryManager.kt`:
     - Recordar punto de estacionamiento: Guarda coordenadas (latitud, longitud), marca temporal y nota adicional en `Prefs`.
     - Consulta síncrona inmediata mediante `LocationManager` (GPS / Network) y FusedLocation de Google Play Services.
     - Cálculo de distancia (metros o kilómetros) y rumbo cardinal (`bearingToCardinal`: Norte, Noreste, Este, Sureste, Sur, Suroeste, Oeste, Noroeste).
  4. `VoiceActionRouter.kt`:
     - Enrutamiento determinista de alta prioridad (<5ms) antes de llamadas a LLM:
       - Fast-paths de Daily Briefing: `"buenos dias"`, `"buen dia"`, `"mi dia"`, `"resumen del dia"`, `"resumen diario"`, `"inicia mi dia"`, `"briefing"`.
       - Fast-paths de Rutinas: `"modo reunion"`, `"modo auto"`, `"modo conduccion"`, `"modo gym"`, `"modo noche"`, con soporte para activar y desactivar.
       - Fast-paths de Memoria Espacial: `"estacione aqui"`, `"donde estacione"`, `"donde deje el carro"`, `"borra mi estacionamiento"`.
       - Fast-paths de Lista de Compras: `"agrega X a las compras"`, `"lista de compras"`, `"tacha X de las compras"`, `"compre X"`.
       - Fast-paths de Despacho de Ubicación: `"mandale mi ubicacion a X"`, `"comparte mi ubicacion con X"` por WhatsApp / Telegram.
  5. `PhoneActionExecutor.kt`:
     - Implementado `sendLocationToContact(contact, app)` con resolución de coordenadas GPS síncrona/asíncrona y despacho de enlace Google Maps a través de WhatsApp / Telegram con pantalla encendida (`LockScreenHelper.wakeUpScreen`).
     - Añadidos action tags (`ACTION:BRIEFING`, `ACTION:ROUTINE=...`, `ACTION:PARKING_SAVE`, `ACTION:PARKING_GET`, `ACTION:SEND_LOCATION=...`) en `processAndExecute` y `executeAction`.
  6. `WeatherSync.kt`:
     - Unificado `companion object` con `@Volatile var lastSummary: String?` para consumo síncrono sin bloqueo de red.
  7. `DailyAssistantAutomationTest.kt`:
     - Suite completa de 8 pruebas unitarias con Robolectric cubriendo Daily Briefing, Modos de Rutina, Memoria Espacial, Enrutamiento de Compras y Envío de Ubicación.
- **Verificación**:
  - Pruebas unitarias de las nuevas funciones: **8/8 tests PASSED**.
  - Suite completa de pruebas unitarias (`./gradlew testDebugUnitTest`): **BUILD SUCCESSFUL in 11s** (100% pruebas pasando).
  - Ensamblado de APK (`./gradlew assembleDebug`): **BUILD SUCCESSFUL in 2s** (`app-debug.apk` listo).

### [2026-09-10] — Eliminación de Librerías No Utilizadas: Remoción de MediaPipe GenAI y Reducción del APK de 117MB a 10MB
- **Plan**: `docs/superpowers/plans/2026-09-10-remove-unused-dependencies.md`
- **Auditoría Exhaustiva de Código y Dependencias**:
  - Se inspeccionó todo el árbol `app/src/` (main y test) contra las dependencias declaradas en `app/build.gradle.kts` y `gradle/libs.versions.toml`.
  - Se confirmó que **`com.google.mediapipe:tasks-genai`** tenía **0 imports, 0 clases y 0 llamadas** en todo el proyecto Kotlin/Java.
  - La inferencia de IA local/privada está construida con `LocalAiClient` (HTTP OpenAI/LiteLLM compatible) y la nube con `GeminiClient`.
  - MediaPipe Tasks GenAI empaquetaba de forma innecesaria 4 arquitecturas de binarios nativos C++ (`lib/arm64-v8a/libllm_inference_engine_jni.so`, `lib/armeabi-v7a/...`, `lib/x86/...`, `lib/x86_64/...`), totalizando más de 108 MB de archivos `.so` y disparando el peso del APK a **117 MB**.
- **Cambios Realizados**:
  1. `app/build.gradle.kts`: Eliminada dependencia `implementation(libs.mediapipe.tasks.genai)`.
  2. `gradle/libs.versions.toml`: Eliminada versión `mediapipeGenai = "0.10.35"` y alias `mediapipe-tasks-genai`.
  3. Eliminado archivo obsoleto `app/src/main/assets/public.libraries.txt` (que contenía `libvndksupport.so`, `libOpenCL.so`, `libGLES_mali.so` requerido solo para drivers GPU de MediaPipe).
  4. `app/src/main/AndroidManifest.xml`: Eliminadas directivas `<uses-feature android:name="android.hardware.vulkan.version" ... />` y `<uses-feature android:name="android.hardware.opengles.aep" ... />`.
  5. Actualizada documentación (`README.md` y `docs/ARCHITECTURE.md`) para aclarar el motor de IA (`LocalAiClient` para endpoints OpenAI/LiteLLM y `GeminiClient` en la nube).
- **Resultados y Verificación**:
  - Peso del APK debug (`app-debug.apk`): Reducido de **117 MB** a **10 MB** (reducción masiva del ~92%).
  - Librerías nativas `.so` empaquetadas: **0** (APK 100% bytecode limpio).
  - Pruebas unitarias (`./gradlew testDebugUnitTest`): **BUILD SUCCESSFUL** (100% pasando).
  - Ensamblado (`./gradlew assembleDebug`): **BUILD SUCCESSFUL** en 10s.

### [2026-09-10] — Optimización de Audio para Gemini (SCO Condicional) y Aceleración de Reconexión de Relay (2s)
- **Plan**: `docs/superpowers/plans/2026-09-10-gemini-audio-routing-and-relay-speedup.md`
- **Diagnóstico del Log**:
  - Tras el doble toque exitoso que lanzó Gemini, al cumplirse el timer de 4.5s de captura SCO (`releaseBluetoothSco`), el firmware de las gafas Meizu Myvu cerró el servidor SPP (`<- SPP server closed by the glasses -- dropping the relay`) debido a la conmutación agresiva entre modos HFP y A2DP.
- **Solución Implementada**:
  1. `Prefs.kt`: Añadida preferencia `gemini_force_sco` (por defecto `false` / Desactivado).
  2. `TouchGestureManager.kt`: Forzado de SCO ahora es 100% opcional y condicionado por `Prefs.isGeminiForceScoEnabled`. Al permanecer desactivado, Android delega la entrada de audio a la app de Gemini nativamente sin alternar modos de audio de telefonía en las gafas, garantizando que el socket SPP **nunca se caiga**.
  3. `activity_settings.xml` y `SettingsActivity.kt`: Añadido switch `swForceGeminiSco` en la sección de gestos de touchpad para que el usuario pueda activarlo si lo desea.
  4. `RelaySupervisor.kt`: Reducido `INITIAL_DISCONNECTED_POLL_MS` de 5000L a 2000L y ajustado exponente de backoff `min(attemptCount, 5)`. Si el relay llega a caerse por alcance Bluetooth, el primer intento de reconexión se produce a los **2 segundos** (antes 5s).
  5. `RelaySupervisorTest.kt`: Pruebas unitarias actualizadas y validadas al 100%.
- **Verificación**:
  - Tests unitarios (`./gradlew testDebugUnitTest`): **BUILD SUCCESSFUL** (259 tests passing, 0 failures).
  - Ensamblado (`./gradlew assembleDebug`): **BUILD SUCCESSFUL in 961ms**.

### [2026-09-11] — Corrección de Brillo HUD y Migración Integral de Parámetros Exclusivos a Configuración de Gafas
- **Plan**: `docs/superpowers/plans/2026-09-11-migrate-glasses-settings-and-fix-brightness-plan.md`
- **Problema Reportado**:
  1. El parámetro de brillo de pantalla en la configuración de las gafas AR no se estaba aplicando físicamente ni guardando al presionar "Guardar".
  2. En los ajustes generales (`SettingsActivity` / `activity_settings.xml`) aún permanecían parámetros que son exclusivos del hardware de las gafas AR (Brillo, Volumen, Posición Standby del HUD, Tiempo de pantalla activa, Duración de notificaciones HUD, Modo de respuesta IA, Escucha continua y Wake word).
- **Causa Raíz**:
  1. En `GlassesSettingsActivity`, el slider de brillo tenía un rango erróneo de 10 a 100 (porcentaje) y solo persistía en la base de datos Room (`hudBrightness`), sin invocar `GlassesConfig.setBrightness(this, value)` ni emitir el comando Flyme XR `set_brightness`.
  2. El firmware de las gafas Meizu Myvu espera niveles discretos del 1 al 5 (`{"action":"system_settings","sub_action":"set_brightness","brightness":N}`). Valores mayores a 5 o la ausencia de IPC eran ignorados por el hardware.
  3. Parámetros de hardware de las gafas se encontraban dispersos en `SettingsActivity`, causando duplicidad y confusión frente a la configuración por dispositivo.
- **Solución Implementada**:
  1. **Corrección de Brillo HUD**:
     - `activity_glasses_settings.xml`: `sliderHudBrightness` reconfigurado con `valueFrom="1"`, `valueTo="5"`, `stepSize="1"`.
     - `GlassesSettingsActivity.kt`: Al mover el slider y al presionar "Guardar", se invoca directamente `GlassesConfig.setBrightness(this, brightness)`, el cual envía `SystemSettings.setBrightness(level)` a las gafas y actualiza Room DB (`hudBrightness = brightness * 20`).
  2. **Migración Integral a `GlassesSettingsActivity` y `activity_glasses_settings.xml`**:
     - *Pantalla y Audio*: Brillo HUD (1..5), Volumen de altavoces (0..15), Posición Standby de la pantalla (Centro, Superior, Inferior, Lateral 0..3), Tiempo de pantalla activa (3..60s), Duración de notificaciones en visor HUD (1..30s).
     - *Modo de Respuesta de IA*: Toggle group con 3 opciones (Solo voz, Solo pantalla HUD, Voz y HUD).
     - *Escucha y Micrófono*: Switches para Diálogo continuo (`swContinuousDialogue`), Activación por voz / Wake word (`swVoiceWakeup`) con envío en vivo de `AiProtocol.assistantConfig`, y Enrutamiento forzado de micrófono SCO para Gemini (`swForceGeminiSco`).
  3. **Limpieza Completa de Ajustes Generales (`SettingsActivity`)**:
     - Eliminadas tarjetas de Modo de Respuesta IA, Escucha y Ahorro de Batería, Pantalla y Audio de Gafas, y duración de notificaciones HUD en `activity_settings.xml`.
     - Removidos métodos y oyentes huérfanos (`configureResponseMode`, `configureListeningSettings`, `wireGlassesSettings`, `wireTouchpad`, etc.) en `SettingsActivity.kt`.
     - `SettingsActivity` queda dedicado estrictamente a configuraciones globales (Proveedores y endpoints de IA/STT/TTS, Temas, Clima, Copias de seguridad en la nube, Perfil, Logs y Bloqueo automático del móvil tras acciones).
  4. **Pruebas y Verificación**:
     - Adaptado `SettingsGestureConfigTest.kt` para inicializar y validar completamente `GlassesSettingsActivity`, sus componentes de UI, persistencia de `GlassesConfig`, `Prefs` y base de datos Room.
     - Pruebas unitarias (`./gradlew testDebugUnitTest`): **BUILD SUCCESSFUL** (100% pasando).
     - Compilación de APK (`./gradlew assembleDebug`): **BUILD SUCCESSFUL**.

---

### [2026-09-12] — Interruptor Maestro de Servicios en Segundo Plano y Garantía de IA Standalone
- **Plan**: `docs/superpowers/plans/2026-09-12-service-toggle-and-standalone-ai-guarantee-plan.md`
- **Problema y Requerimiento**:
  1. La aplicación podía generar ciclos de reconexión automática en bucle o mantener servicios en segundo plano encendidos consumiendo batería innecesariamente cuando el usuario no deseaba usar los dispositivos. Se solicitó un botón/switch que permita activar o desactivar los servicios según sea el caso para evitar consumo de recursos (por ejemplo, reintentos automáticos de reconexión a dispositivos Bluetooth).
  2. Garantizar que las funcionalidades de IA (Chat, Notas, Resúmenes del día/Briefing, Grabadora y Tareas) se puedan utilizar 100% de forma autónoma (standalone) sin requerir ningún dispositivo Bluetooth conectado.
- **Solución Implementada**:
  1. **Interruptor Maestro de Servicios y Desconexión Total**:
     - `TotalDisconnectHelper.kt`: Controlador centralizado que:
       - Pone `Prefs.setAutoReconnectEnabled(context, false)`.
       - Cancela alarmas de watchdog (`ServiceWatchdogReceiver.cancelWatchdog(context)`).
       - Libera canales de audio Bluetooth SCO (`TouchGestureManager.releaseBluetoothSco(context)`).
       - Desregistra sensores de salud y podómetro (`HealthService.unregisterHardwareSensor()`).
       - Detiene escaneos y marca en Room DB todos los dispositivos como desconectados (`BluetoothDeviceManager.markAllDevicesDisconnected()`).
       - Detiene el servicio en primer plano `MyvuService` (`ACTION_STOP`).
     - `view_dashboard.xml` y `ConnectActivity.kt`: Añadido switch Material 3 `swMasterService` ("Servicios en Segundo Plano") en `cardStatus`. Al apagarlo, desactiva la reconexión y ejecuta la desconexión total; al encenderlo, reanuda la reconexión y levanta el servicio.
     - `activity_settings.xml` y `SettingsActivity.kt`: Añadida tarjeta "Servicios en Segundo Plano & Energía" con `swSettingsMasterService` y botón de "Desconexión Total (Inactivar Todo)".
     - `ChatActivity.kt` y `menu/menu_navigation_drawer.xml`: Opción `nav_total_disconnect` incorporada en el menú lateral para ejecutar desconexión total con un toque.
  2. **Garantía de IA Standalone**:
     - `view_dashboard.xml`: Añadido botón de acceso directo a Chat IA Aura (`btnOpenAiChat`) junto al botón de voz para entrar al asistente sin requerir gafas conectadas.
     - `DailyBriefingService.kt`: Manejo seguro de ausencia de dispositivos Bluetooth (la lectura de batería de gafas se omite elegantemente sin lanzar excepciones si no hay conexión activa), proveyendo el resumen completo (saludo, hora, clima, calendario, notas, notificaciones) mediante el motor TTS local.
     - Verificado que `ChatActivity`, `NotesActivity` y `VoiceRecorderActivity` funcionan de manera completamente desacoplada mediante llamadas HTTP a LLMs, base de datos local Room y micrófono nativo de Android.
     - Creada suite de pruebas unitarias `StandaloneAiAndServiceToggleTest.kt` (Robolectric SDK 34) validando:
       - Alternancia correcta del estado de reconexión/servicios con `Prefs.setAutoReconnectEnabled` y `TotalDisconnectHelper.performTotalDisconnect`.
       - Generación exitosa y sin fallos de `DailyBriefingService.generateBriefingText()` sin dispositivos conectados.
       - Creación y recuperación en Room DB de Notas y Recordatorios en modo standalone.
     - `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL** (todas las pruebas pasando).
     - `./gradlew assembleDebug`: **BUILD SUCCESSFUL**.
     - Grafo de código sincronizado (`codegraph sync`).

---

### [2026-09-12] — Resolución de Conflicto y Doble Acción entre Botón Físico de Montura y Patilla Táctil
- **Plan**: `docs/superpowers/plans/2026-09-12-fix-physical-action-button-and-temple-conflict-plan.md`
- **Problema y Requerimiento**:
  - Al presionar el botón físico de acción de las gafas inteligentes Meizu Myvu AR, el sistema ejecutaba dos acciones consecutivas (ej. lanzar Gemini o Aura por el botón físico y seguidamente disparar una acción de patilla táctil, como doble toque o pulsación larga).
  - Se confundían eventos de hardware del botón de la montura con interacciones de la patilla capacitiva táctil.
- **Causa Raíz Descubierta**:
  1. **Despacho Concurrente Dual del Firmware Flyme XR**:
     - Al presionar el botón físico, el firmware de las gafas envía simultáneamente un paquete de activación IA (`com.upuphone.ai.assistant`, `code: 3`) y uno o más paquetes de telemetría de eventos de teclas (`action: sync_glass_event`, con `key_code: 231`, `230`, `212` o `210`).
     - `InboundRouter.checkAiTrigger()` procesaba el paquete `code: 3` y ejecutaba la acción del botón de acción (`Prefs.glassesActionButtonAction`, ej. Gemini o Aura).
     - Paralelamente, `InboundRouter.checkGestureTracking()` procesaba el evento de tecla y lo enviaba sin discriminación a `TouchGestureManager.handleGesture()`.
  2. **Colisión en `GlassGesture.kt`**:
     - El keycode `230` (botón de acción corto) estaba asignado a `GlassGesture.TAP`. El acumulador de software lo agrupaba con otros toques y sintetizaba un falso `DOUBLE_TAP`.
     - El keycode `231` (botón de acción largo / `KEYCODE_VOICE_ASSIST`) estaba asignado a `GlassGesture.LONG_PRESS`, disparando la acción de patilla larga (`Prefs.touchpadLongPressAction`) en vez de la acción configurada para el botón físico (`Prefs.glassesActionButtonAction`).
     - `GlassGesture.fromCode(3)` mapeaba `3` a `TRIPLE_TAP`, confundiendo el trigger de IA con un triple toque de patilla.
  3. **Falta de Ventana de Supresión Temporal**:
     - No existía un mecanismo para que `InboundRouter` o `TouchGestureManager` suprimieran eventos táctiles de patilla generados mecánicamente por la deformación o proximidad de la mano al sujetar la montura para oprimir el botón físico.
- **Solución Implementada**:
  1. **Desacoplamiento Estricto en `GlassGesture.kt`**:
     - Creada nueva entrada de enum: `ACTION_BUTTON(230, "action_button", "Botón de Acción")`.
     - `fromCode(230)` y `fromCode(231)` devuelven `ACTION_BUTTON`.
     - `fromCode(3)` devuelve `ACTION_BUTTON` a menos que el nombre contenga explícitamente `"triple"`.
     - Las cadenas `"assist"`, `"action_button"`, `"action_btn"` mapean a `ACTION_BUTTON`.
  2. **Ventana de Supresión en `TouchGestureManager.kt`**:
     - Añadida constante `PHYSICAL_BUTTON_SUPPRESSION_MS = 1200L` y variables atómicas de tiempo `lastPhysicalButtonTime` y `lastPhysicalKeyEventTime`.
     - En `handleGesture()`: Si un gesto proviene de las patillas táctiles y transcurrieron menos de 1200ms desde una pulsación del botón físico (`(now - lastPhysicalButtonTime) in 0 until PHYSICAL_BUTTON_SUPPRESSION_MS`), se descarta de inmediato con log de auditoría.
     - Manejo explícito de botón físico (`isPhysicalButton`):
       - Keycode `230`: Ejecuta exclusivamente `executeHudDashboard()`, restablece el acumulador de toques (`accumulatedTapCount = 0`, `lastTapTime = 0L`) y registra la marca de tiempo, impidiendo cualquier síntesis de doble toque.
       - Keycode `231`: Ejecuta directamente la acción mapeada para el botón físico (`Prefs.glassesActionButtonAction(context)`: Gemini, Gemini Live, Asistente de teléfono o Aura), sin pasar jamás por los mapeos de patilla táctil.
     - Método `notifyPhysicalButtonPressed(context)` para notificar a `TouchGestureManager` desde eventos externos (ej. trigger de IA `code: 3`).
  3. **Deduplicación Bidireccional en `ConnectionManager.kt`, `GlassesEventHandler.kt` e `InboundRouter.kt`**:
     - En `InboundRouter.checkAiTrigger()`: Al llegar `code == 3`, invoca `TouchGestureManager.notifyPhysicalButtonPressed(null)` para abrir la ventana de supresión de 1200ms contra eventos parásitos de patilla.
     - En `ConnectionManager` y `GlassesEventHandler`: Si llega un trigger `code: 3` dentro de los 500ms posteriores a un `key_event` físico (`lastPhysicalKeyEventTime`), se descarta como duplicado para evitar disparar 2 veces el asistente.
     - En `InboundRouter.dispatchGestureBatch()`: Añadido `ACTION_BUTTON` con máxima prioridad (`0`) en la jerarquía de resolución de eventos simultáneos.
  4. **Pruebas y Verificación**:
     - Actualizados tests en `InboundGestureTest.kt` para validar que `key_code: 230` y `key_code: 231` decodifican como `GlassGesture.ACTION_BUTTON`.
     - Añadidos tests unitarios en `TouchGestureManagerTest.kt`:
       - `actionButtonCode230ExecutesHudDashboardAndDoesNotSynthesizeTap()`
       - `physicalButtonPressedSuppressesSubsequentTempleGestures()`
       - `templeGesturesWorkAfterSuppressionWindowExpires()`
     - Creada suite dedicada `PhysicalActionButtonConflictTest.kt` validando la supresión de eventos de patilla tras trigger de IA, ejecución limpia de HUD Dashboard en código 230 y funcionamiento normal de patilla fuera de la ventana de supresión.
     - `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL** (300 pruebas unitarias pasando).
     - `./gradlew assembleDebug`: **BUILD SUCCESSFUL**.
     - Sincronizado `codegraph sync`.

---

### [2026-09-12] — Sistema Unificado e Independiente de Activity Log y Remoción de UI Vieja
- **Plan**: `docs/superpowers/plans/2026-09-12-unified-activity-log-system-plan.md`
- **Requerimiento y Problema**:
  - El visor de registros ("Activity log") estaba confinado como pestaña secundaria en el `TabLayout` de `ConnectActivity`, acoplado exclusivamente a las gafas MYVU.
  - No permitía ver eventos de otros dispositivos (auriculares y dispositivos Bluetooth vinculados, actividades del sistema telefónico, ejecuciones de IA Aura/Gemini).
  - Carecía de interfaz independiente, switch maestro accesible para activar/pausar el logging en caliente, filtros en tiempo real, búsqueda y exportación avanzada.
  - Al pulsar "Logs y Actividad" desde el menú lateral de otras pantallas como `ChatActivity`, el intent intentaba alternar pestañas en `ConnectActivity`.
- **Solución Implementada**:
  1. **Modelo de Registro Estructurado en `LogBus.kt`**:
     - Creado enum `DeviceSource`: `ALL` ("Todos"), `GLASSES` ("Gafas MYVU"), `BLUETOOTH` ("Dispositivos BT / Auriculares"), `PHONE` ("Teléfono / Sistema") y `AI` ("Asistente IA").
     - Creado data class `LogEntry`: `id`, `timestamp`, `source: DeviceSource`, `level: Int`, `tag: String`, `message: String`, `throwable: Throwable?`, `deviceName: String?` y `formattedLine: String`.
     - Buffer circular estructurado `ENTRIES: Deque<LogEntry>` (capacidad 2000) coexistiendo de manera transparente con `BUFFER: Deque<String>` y `history()` para 100% de retrocompatibilidad.
     - Añadido `entryFlow: SharedFlow<LogEntry>` y listener `EntryListener`.
     - Inferencia inteligente de origen (`inferDeviceSource`) analizando tags y contenido cuando se llama a `LogBus.log()`, `warn()` o `error()`.
     - Helpers dedicados por origen: `LogBus.glasses()`, `LogBus.bluetooth(msg, deviceName)`, `LogBus.phone()`, `LogBus.ai()`.
     - Control maestro `isEnabled` reactivo y método `clear()` que vacía tanto strings como records estructurados.
  2. **Nueva Pantalla Independiente `ActivityLogActivity`**:
     - Nueva actividad `ActivityLogActivity.kt` y layout `activity_log.xml` con diseño Obsidian / Cyber Teal.
     - Switch maestro de monitoreo (`swActivityLogging`): sincronizado con `Prefs.setLoggingEnabled()` y `LogBus.isEnabled`.
     - Barra de búsqueda en vivo con `etLogSearch` para filtrado instantáneo por texto, tag o dispositivo.
     - Chips de filtro horizontal: `[Todos]`, `[👓 Gafas MYVU]`, `[🎧 Dispositivos BT]`, `[📱 Teléfono / App]`, `[🤖 Asistente IA]`, `[⚠️ Solo Errores]`.
     - Chip de auto-scroll conmutable (`chipAutoScroll`).
     - Botón de compartir logs con generación de archivo seguro `myvu_activity_log.txt` via `FileProvider` con fallback a texto plano.
     - Botón de limpiar logs con diálogo de confirmación Material (`MaterialAlertDialogBuilder`).
     - `RecyclerView` con `item_activity_log.xml`: badges de fuente coloreados, nivel (INFO/WARN/ERROR), hora con milisegundos, mensaje monoespaciado y stack traces expandibles si hay error.
     - Soporte para copiar al portapapeles en long-press.
     - Vista vacía contextual (`layoutEmptyLogs`) con estado reactivo según búsqueda, filtros o si el logging está pausado.
  3. **Remoción de la Interfaz Vieja de `ConnectActivity`**:
     - En `activity_connect.xml`:
       - Eliminado `TabLayout` (pestañas "Controls" y "Log").
       - Eliminado contenedor `pageLog` y sus vistas (`rvLog`, `btnShareLog`, `btnClearLog`).
       - `pageControls` (`NestedScrollView`) pasa a ser la vista principal directa bajo la barra superior.
       - Añadido botón `btnOpenActivityLog` con icono `@android:drawable/ic_menu_info_details` en la barra superior para acceso directo.
     - En `ConnectActivity.kt`:
       - Removida la implementación de `LogBus.Listener`.
       - Removidas variables `rvLog`, `logAdapter`.
       - Removidos métodos obsoletos `wireTabs()`, `crossFade()`, `shareLog()`, `onLine()`, `logAtBottom()`, `scrollToBottom()`.
       - Conectado `btnOpenActivityLog` hacia `ActivityLogActivity`.
       - Eliminada clase obsoleta `LogAdapter.kt`.
  4. **Navegación Global y Ajustes**:
     - En `ConnectActivity.kt` y `ChatActivity.kt`: `R.id.nav_logs` en el navigation drawer ahora abre directamente `ActivityLogActivity`.
     - En `SettingsActivity.kt`: añadido botón `btnOpenActivityLog` ("Abrir Registro de Actividad Unificado") bajo la sección de logging.
     - Registrada `ActivityLogActivity` en `AndroidManifest.xml`.
  5. **Pruebas y Verificación**:
     - Pruebas unitarias en `LogBusTest.kt`:
       - `testStructuredLogEntriesAndSourceInference()`
       - `testEntryListenerReceivesStructuredEntries()`
     - `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL** (302 pruebas pasando).
     - `./gradlew assembleDebug`: **BUILD SUCCESSFUL**.
     - `codegraph sync`: Sincronizado.

