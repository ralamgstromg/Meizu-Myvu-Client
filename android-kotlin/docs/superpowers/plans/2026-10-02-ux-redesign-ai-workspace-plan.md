# Plan: Rediseño UX, Espacio de Trabajo IA, Tareas Programadas, Locale es-CO y Permisos

**Fecha:** 2026-10-02
**Estado:** Fase 1 implementada (TTS es-CO y notificaciones resumidas). Fases 2–5 propuestas, pendientes de aprobación.

---

## 0. Punto de partida (lo que ya existe)

| Capacidad | Dónde | Limitación actual |
|---|---|---|
| Notas, grabaciones, reuniones con IA | `NotesActivity`, `VoiceRecorderActivity`, `MeetingAiProcessor`, `NoteAiProcessor` | Pantallas separadas; sin un punto de entrada común |
| Mapas mentales | `MindMapVisualizerHelper` | Solo dentro del detalle de una nota |
| Búsqueda | `RagHistorySearchHandler` (notas, grabaciones, recordatorios, tareas) | Búsqueda por subcadena, sin ranking ni UI propia: solo funciona como skill del LLM |
| Agenda | `CalendarService` (`READ_CALENDAR`) | Solo lectura bajo demanda |
| Correos | `UnreadEmailsSummaryHandler` lee **notificaciones** (`MirrorNotificationListener`) | No hay acceso al buzón: solo a lo que aparece en notificaciones |
| Resumen diario | `DailyBriefingService` | Solo bajo demanda (gesto o voz); no se puede programar |
| Recordatorios | `ReminderScheduler` (AlarmManager exacto) | Reutilizable como base para tareas programadas |
| Logs | `LogBus` + `CrashReporter` (`logs/crash_log.txt`) | 51 `catch (ignored)`, mensajes de error al usuario inconsistentes |

---

## 1. Fase 1 — Implementada

### 1.1 TTS natural en es-CO (`core/locale/SpeechNormalizer`)
- Números en formato US ("4,150.32", que salen de `ExternalInfoService`) se convierten a formato es-CO ("4.150,32").
- Monedas: `COP`/`$` → pesos, `USD`/`US$` → dólares, `EUR`/`€` → euros, con centavos hablados ("4.150 pesos con 32 centavos").
- Horas: "15:30" / "8:15 pm" → "3:30 de la tarde" / "8:15 de la noche".
- Unidades: `%` → por ciento, `°C` → grados, `km/h`, `km`. Las URLs se leen como "enlace".
- Se aplica en `TtsPlayer` y `TextToSpeechHelper`.

**Fluidez:**
- `TtsPlayer` encola la respuesta en fragmentos de ≤220 caracteres por oración (`QUEUE_ADD`). La voz empieza con la primera oración sin esperar a que se sintetice todo.
- Velocidad de voz configurable (`Prefs.ttsSpeechRate`, slider en Ajustes, compartida por ambos motores).

### 1.2 Notificaciones legibles (`service/NotificationDigest`)
- Formato hablado: "Mensaje de Ana en WhatsApp: …". Se quitan los emojis.
- El texto se limita a **N palabras** (`Prefs.notificationMaxWords`, 5–100, por defecto 20; slider en Ajustes). Aplica al HUD y a la voz.
- Los horarios y montos del texto pasan por `SpeechNormalizer` al hablarse.

---

## 2. Fase 2 — Rediseño de la interfaz (propuesta)

**Principio:** el agente es el centro. Navegación inferior con 4 pestañas:

```
┌─────────────────────────────────────┐
│  Hoy                                │  ← tarjeta "Tu día": eventos, tareas, recordatorios,
│  ┌───────────────────────────────┐  │    cumpleaños, TRM, clima (datos de la fase 4)
│  │ 9:00 Reunión equipo  ·  2 tareas│  │
│  │ TRM 4.150,32 · 18 °C Bogotá    │  │
│  └───────────────────────────────┘  │
│  [🎙 Preguntar al agente…        ]   │  ← barra de chat/voz persistente
├─────────────────────────────────────┤
│ Hoy │ Biblioteca │ Agente │ Ajustes │
└─────────────────────────────────────┘
```

| Pestaña | Contenido |
|---|---|
| **Hoy** | Panel del día + accesos rápidos (grabar reunión, nota de voz, resumen del día). Reemplaza la entrada actual a `ConnectActivity`; el estado de los dispositivos pasa a una cabecera compacta. |
| **Biblioteca** | Notas, reuniones, grabaciones y mapas mentales en **una sola lista** con filtros (tipo, fecha, etiqueta) y **búsqueda** (fase 3). Desde cada elemento: resumir, puntos de acción, mapa mental, preguntar. |
| **Agente** | El `ChatActivity` actual + rutinas programadas (fase 4) + historial de acciones confirmadas. |
| **Ajustes** | Agrupados por dispositivo, IA, voz y notificaciones, privacidad y permisos (fase 5), backup. |

**Entregable previo:** mockups navegables para aprobación antes de tocar las Activities. Se migra pantalla por pantalla, empezando por "Hoy" y "Biblioteca", reutilizando los controladores existentes (`AttachmentUiController`, `TaskChecklistController`, `MindMapVisualizerHelper`).

---

## 3. Fase 3 — Búsqueda sobre el contenido indexado (propuesta)

- **Índice FTS4** (SQLite, sin dependencias nuevas) en `myvu_client.db`. Tabla virtual `search_index(kind, ref_id, title, body, date)` alimentada por triggers en notas, transcripciones, resúmenes de reunión, recordatorios y tareas, más el chat (Room).
- **Ranking** con `bm25`/`matchinfo`, snippets resaltados y búsqueda tolerante a acentos (columna normalizada sin diacríticos).
- **UI:** barra de búsqueda en la Biblioteca. **Voz:** `RagHistorySearchHandler` pasa a usar el índice y las preguntas sobre el propio contenido mejoran sin enviar todo al LLM.
- **Opcional (fase 3b):** embeddings para búsqueda semántica. Cuesta llamadas a la API o un modelo local; decidir después de medir la calidad de FTS.

---

## 4. Fase 4 — Integración de datos y tareas programadas (propuesta)

### 4.1 Fuentes
| Fuente | Cómo | Permiso |
|---|---|---|
| Agenda y reuniones | `CalendarService` (ya existe) | `READ_CALENDAR` (ya declarado) |
| Cumpleaños de contactos | `ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY` | `READ_CONTACTS` (ya declarado) |
| Correos | **Hoy:** notificaciones. **Propuesto:** Gmail API con el OAuth de Google ya existente (`GoogleDriveSyncHelper`), agregando el scope `gmail.readonly` | Consentimiento OAuth; no requiere permiso de Android |
| Recordatorios y tareas | Repositorios existentes | — |
| TRM, clima, noticias | `ExternalInfoService` / skills existentes | — |

### 4.2 Rutinas programadas
- Modelo `ScheduledRoutine(id, nombre, horario (hora + días), acciones[], canal: HUD | voz | notificación)`. Acciones componibles: resumen de agenda, tareas pendientes, recordatorios del día, cumpleaños de hoy y mañana, TRM, clima, correos sin leer.
- Ejecución con `ReminderScheduler` (AlarmManager exacto ya permitido) y re-programación en `BootReceiver`. El resultado se compone con `DailyBriefingService` y se entrega por el canal elegido (HUD resumido, voz o notificación).
- **UI:** en la pestaña Agente, "Rutinas" → crear con plantillas ("Buenos días 7:00 L–V", "Cierre del día 18:00") + editor de acciones con checkboxes.
- **Voz:** "todos los días a las 7 resúmeme la agenda y la TRM" crea la rutina mediante una nueva skill `schedule-routine`.

---

## 5. Fase 5 — Errores, logs y permisos (propuesta)

### 5.1 Errores y logs unificados
- `AppError` (sellado: `Network`, `Permission(permission)`, `Config(campo)`, `Device`, `Unknown`) con un mensaje es-CO para el usuario y uno técnico para el log, en un solo lugar.
- `CrashReporter` escribe también en `LogBus` (`DeviceSource`) para que los crashes aparezcan en Activity Log; rotación de `logs/` (5 archivos × 1 MB) y exportación desde Activity Log.
- Revisión de los 51 `catch (ignored)`: registrar con `LogBus.warn` donde oculten fallos reales (envío al HUD, TTS, red).

### 5.2 Permisos y Auto-Send
**Restricción de Android:** pulsar "Enviar" en WhatsApp/Telegram desde otra app **solo** es posible con un servicio de accesibilidad activado por el usuario. No existe una alternativa sin ese permiso, y saltarlo sería eludir una protección del sistema. Propuesta:
- **Centro de permisos:** una pantalla que muestra cada permiso, para qué se usa y su estado, con un botón para concederlo. Los permisos se piden **en el momento de uso** y no en bloque al inicio.
- **Degradación elegante:** sin accesibilidad, el mensaje queda escrito y la app avisa por HUD/voz "Toca Enviar para terminar". Con accesibilidad, se envía solo (y siempre pasa por `SensitiveActionGate`).
- **Vías sin accesibilidad cuando existan:** SMS con `SmsManager` (permiso `SEND_SMS` ya declarado), correo con la Gmail API (fase 4).
- Revisar si `SYSTEM_ALERT_WINDOW`, `DISABLE_KEYGUARD` y `BODY_SENSORS` siguen siendo necesarios; quitar los que no.

---

## 6. Orden sugerido
1. ~~Fase 1~~ ✅
2. Fase 5.1 (errores y logs): base para todo lo demás.
3. Fase 4 (rutinas + cumpleaños): valor inmediato sin rediseño.
4. Fase 3 (búsqueda FTS).
5. Fase 2 (rediseño), con mockups aprobados.
6. Fase 5.2 (centro de permisos), integrado en los nuevos Ajustes.
