# Plan de Mejora Integral: Skills, Tools e Integraciones con Android y Terceros
**Fecha:** 2026-09-09  
**Autor:** Kog (Caveman Agent)  
**Proyecto:** Meizu Myvu AR Smart Glasses Client (Android Kotlin)

---

## 1. Diagnóstico y Estado Actual

La aplicación cuenta con un ecosistema modular de **30 skills declarativas** (manifiestos `SKILL.md` en `app/src/main/assets/skills/built-in/`) y **29 handlers nativos** en `com.myvu.client.skills.handlers.*`. Estos se ejecutan tanto en bucle agéntico ReAct (Function Calling nativo vía LiteLLM / Gemini con `AgenticToolExecutor`) como por fallback de etiquetas `[SKILL: ...]`.

### Gaps y Puntos Débiles Detectados:
1. **Comunicación y Mensajería (`call-contact`, `send-whatsapp`, `send-telegram`, `send-email`)**:
   - `CallContactHandler`: Usa `Intent.ACTION_DIAL`, obligando al usuario a mirar la pantalla del móvil y presionar el botón verde. Con permiso `CALL_PHONE` o `TelecomManager.placeCall` se puede hacer la llamada 100% manos libres desde las gafas.
   - `SendWhatsappHandler`: Si el usuario dice "envía un WhatsApp a Carlos", el handler simplemente limpia los caracteres no numéricos, dejando el teléfono vacío (`""`), lo cual abre WhatsApp genérico en vez del chat directo. No añade prefijo de país (`+57` para Colombia) ni activa el servicio `AutoSendAccessibilityService` para envío automático.
   - `SendTelegramHandler`: Solo abre `https://t.me/username` en navegador o perfil. No envía el mensaje ni aprovecha `tg://msg?text=` o `AutoSendAccessibilityService.triggerTelegramAutoSend()`.
   - `SendEmailHandler`: No resuelve el correo de un contacto si se proporciona un nombre propio ("Carlos") en lugar de un email explícito.

2. **Control del Sistema y Productividad (`open-app`, `quick-alarm-timer`, `calendar-events`, `create-reminder`)**:
   - `OpenAppHandler`: Soporta pocos alias. Faltan aplicaciones clave (Cámara, Configuración, Galería, Waze, OpenTune, Netflix, Uber, etc.).
   - `QuickAlarmTimerHandler`: Solo crea alarmas o temporizadores básicos. No soporta ver alarmas (`ACTION_SHOW_ALARMS`), ver temporizadores (`ACTION_SHOW_TIMERS`) ni parseo avanzado de lenguaje natural.
   - `CalendarEventsHandler`: Solo lee las próximas 24 horas a partir del momento actual; no calcula fechas relativas como "mañana" ni permite agendar nuevos eventos en el calendario.
   - `CreateReminderHandler`: Solo recibe minutos numéricos (`minutes_from_now`), ignorando horas específicas ("a las 4pm") que ya soporta `ReminderTimeParser`.

3. **Interacción con el HUD de las Gafas AR (`smart-translate-hud`, `hud-navigation`, notificaciones)**:
   - `SmartTranslateHudHandler`: Tenía texto simulado ("Traducción realizada para...") e intentaba llamar a `HudNavigationHandler` con comandos inexistentes en lugar de proyectar en las gafas mediante `openTeleprompter(...)`.
   - `HudNavigationHandler`: Falta fallback dinámico con Waze / Google Maps y confirmación directa de estado HUD.
   - Resúmenes de notificaciones (`unread-whatsapp-summary`, `unread-telegram-summary`, `unread-emails-summary`): No agrupan mensajes por remitente de forma sintetizada para lectura rápida en pantallas monocromáticas microLED.

4. **Herramientas de Información y Cálculo (`code-calculator-math`, `smart-ocr-scanner`, `rag-history-search`)**:
   - `CodeCalculatorMathHandler`: Evaluación matemática secuencial simple de izquierda a derecha sin respetar precedencia de operadores ni paréntesis.
   - `SmartOcrScannerHandler`: Solo revisa existencia y peso del archivo; falta extracción o procesamiento de imagen real.
   - `RagHistorySearchHandler`: Puede enriquecerse con formato de citas de grabaciones y notas para lectura en HUD.

5. **Manifiestos `SKILL.md` y Schemas OpenAPI**:
   - Varios manifiestos tienen descripciones cortas en inglés o sin ejemplos específicos en español colombiano, lo que puede inducir al modelo a errores en la selección de parámetros.

---

## 2. Arquitectura de Integración

```
  Usuario (Voz / HUD / Touchpad)
               │
               ▼
      [AndroidSpeechRecognizer]
               │
               ▼
      [LocalAiClient / LiteLLM]
      (Modelo: 'gafas' / Gemini)
               │
   ┌───────────┴───────────┐
   ▼                       ▼
[Native Tool Calls]    [Direct Text]
   │                       │
   ▼                       ▼
[AgenticToolExecutor]  [AiConversation]
   │                       │
   ▼                       ▼
[SkillRegistry]        [Glasses HUD]
   │
   ├──> CallContactHandler      ──> TelecomManager / ACTION_CALL
   ├──> SendWhatsappHandler     ──> Intent + AutoSendAccessibilityService
   ├──> SendTelegramHandler     ──> tg://msg + AutoSendAccessibilityService
   ├──> SendEmailHandler        ──> ContactsContract.Email + ACTION_SENDTO
   ├──> OpenAppHandler          ──> PackageManager + Alias Matrix
   ├──> QuickAlarmTimerHandler  ──> AlarmClock Intents (Alarm/Timer/Show)
   ├──> CalendarEventsHandler   ──> CalendarContract (Query & Insert)
   ├──> CreateReminderHandler   ──> ReminderTimeParser + ReminderScheduler
   ├──> SmartTranslateHudHandler──> Translation Engine + openTeleprompter()
   ├──> HudNavigationHandler    ──> Meizu Nav Protocol + Google Maps / Waze
   └──> CodeCalculatorMath      ──> Math Parser + COP Tax / Financial Rules
```

---

## 3. Fases de Implementación

### Fase 1: Comunicación y Mensajería Manos Libres
- [ ] **1.1 Mejorar `CallContactHandler.kt`**:
  - Integrar búsqueda difusa y normalización de acentos para contactos con `lookupContactNumber` de `PhoneActionExecutor`.
  - Intentar llamada directa mediante `TelecomManager.placeCall` o `Intent.ACTION_CALL` si el permiso `CALL_PHONE` está concedido.
  - Caer a `Intent.ACTION_DIAL` únicamente si no hay permiso directo.
- [ ] **1.2 Mejorar `SendWhatsappHandler.kt`**:
  - Resolver el nombre de contacto a número telefónico consultando `ContactsContract`.
  - Formatear números celulares de Colombia (10 dígitos que empiezan por 3 o 6) anteponiendo el código de país `57`.
  - Disparar `AutoSendAccessibilityService.triggerWhatsAppAutoSend()` para envío automatizado sin tocar la pantalla del móvil.
- [ ] **1.3 Mejorar `SendTelegramHandler.kt`**:
  - Usar esquema `tg://msg?text=` y/o `Intent.ACTION_SEND` explícito para `org.telegram.messenger`.
  - Disparar `AutoSendAccessibilityService.triggerTelegramAutoSend()`.
- [ ] **1.4 Mejorar `SendEmailHandler.kt`**:
  - Resolver la dirección de email de contactos desde `ContactsContract.CommonDataKinds.Email` cuando el usuario diga un nombre.

### Fase 2: Control del Sistema Android y Productividad
- [ ] **2.1 Potenciar `OpenAppHandler.kt`**:
  - Ampliar matriz de alias (cámara, galería, ajustes, reloj, calculadora, spotify, opentune, waze, google maps, uber, rappi, netflix, youtube).
  - Normalizar acentos y mayúsculas/minúsculas.
- [ ] **2.2 Ampliar `QuickAlarmTimerHandler.kt`**:
  - Soporte para acciones: `set_alarm`, `set_timer`, `show_alarms`, `show_timers`, `dismiss_alarm`.
  - Soporte de duración en lenguaje natural ("10 minutos", "media hora", "1 hora y media", "45 segundos").
- [ ] **2.3 Enriquecer `CalendarEventsHandler.kt` y `CalendarService.kt`**:
  - Soporte para consultar fechas específicas (hoy, mañana, fecha exacta).
  - Capacidad para agendar eventos mediante `Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)`.
- [ ] **2.4 Enriquecer `CreateReminderHandler.kt`**:
  - Conectar con `ReminderTimeParser` para permitir expresiones como "en 15 minutos", "a las 5 de la tarde", "mañana a las 8am".

### Fase 3: Interacción con el HUD de las Gafas AR
- [ ] **3.1 Corregir y completar `SmartTranslateHudHandler.kt`**:
  - Implementar traducción real de texto (usando diccionario contextual rápido o API).
  - Proyectar el resultado traducido directamente en la pantalla de las gafas usando `MyvuService.activeConnection()?.openTeleprompter(...)`.
- [ ] **3.2 Optimizar `HudNavigationHandler.kt`**:
  - Enviar ruta a gafas Meizu si están conectadas.
  - Lanzar Google Maps o Waze en el teléfono con fallback a URI `geo:`.
- [ ] **3.3 Perfeccionar resúmenes de notificaciones (`UnreadNotificationsHandler.kt`)**:
  - Formatear salidas concisas con conteo por aplicación y viñetas breves diseñadas para las gafas.

### Fase 4: Herramientas de Cálculo y RAG
- [ ] **4.1 Mejorar `CodeCalculatorMathHandler.kt`**:
  - Implementar parser de expresiones matemáticas con soporte de precedencia (`*`, `/` antes de `+`, `-`), porcentajes, y cálculo tributario colombiano (IVA 19%, Retención en la fuente).
- [ ] **4.2 Optimizar `RagHistorySearchHandler.kt`**:
  - Mejorar los extractos contextuales (snippets con resaltado) de notas y grabaciones de voz.

### Fase 5: Optimización de Esquemas `SKILL.md` para Function Calling
- [ ] **5.1 Actualizar los manifiestos `SKILL.md`**:
  - Asegurar parámetros bien descritos, tipados y con ejemplos en español para maximizar la precisión de Gemini/LiteLLM.

---

## 4. Protocolo de Verificación
1. **Compilación**: `./gradlew assembleDebug` en OpenJDK 25 sin errores.
2. **Tests Unitarios**: Ejecutar tests existentes de skills y llamadas agénticas.
3. **Sincronización Ritual**: Ejecutar `codegraph sync` al iniciar y finalizar.
4. **Verificación de Batería**: Comprobar que ninguna skill mantenga wakelocks ni servicios en bucles infinitos.
