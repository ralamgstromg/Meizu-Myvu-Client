# Documentación del Motor de Skills y Asistente Virtual (Aura)

## Arquitectura del Skill Engine en Android

La aplicación móvil Kotlin para los lentes inteligentes **Meizu MYVU** integra un motor de habilidades (**Skill Engine**) extensible, interpretable tanto por el agente **Aura** como por la interfaz de chat en el dispositivo móvil y la pantalla de visualización frontal HUD / TTS de las gafas AR.

---

## Interacción con el Agente en Pantalla Bloqueada (Lock Screen & Keyguard)

Se implementó el componente de utilidad [`LockScreenHelper`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/core/LockScreenHelper.kt) y se agregaron los permisos del sistema requeridos para interactuar con el agente **Aura** cuando la pantalla del teléfono móvil se encuentra bloqueada o apagada:

1. **Permisos Registrados en el Manifest**:
   - `android.permission.DISABLE_KEYGUARD`: Desbloqueo temporal seguro de la pantalla de bloqueo durante la interacción activa.
   - `android.permission.USE_FULL_SCREEN_INTENT`: Lanzamiento de la interfaz de conversación en pantalla completa sobre la pantalla bloqueada.
   - `android.permission.SYSTEM_ALERT_WINDOW`: Permiso de superposición para mostrar el asistente sobre otras aplicaciones o sobre el Keyguard.
   - `android.permission.WAKE_LOCK`: Encendido inmediato de la pantalla al activarse una consulta por voz desde las gafas o mediante el asistente.

2. **Atributos de Actividad**:
   - Las actividades principales (`ConnectActivity`, `ChatActivity`, `NotesActivity`, `VoiceRecorderActivity`) integran `android:showWhenLocked="true"` y `android:turnScreenOn="true"` en `AndroidManifest.xml` y la llamada `LockScreenHelper.setupShowWhenLocked(this)` en su ciclo de vida `onCreate`.

---

## Catálogo Actualizado de 19 Habilidades Nativas Activas

Todas las habilidades cuentan con su respectiva carpeta y archivo de definición manifest `SKILLS.md` dentro de `skills/built-in/<skill-id>/SKILL.md` y `assets/skills/built-in/<skill-id>/SKILL.md`.

| ID de Habilidad | Nombre | Descripción | Parámetros Refinados |
| :--- | :--- | :--- | :--- |
| `call-contact` | Llamar a Contacto | Realiza una llamada telefónica directa a un contacto o número. | `contact_or_number` (String) |
| `send-email` | Enviar Correo | Redacta y envía un correo electrónico. | `recipient` (String), `subject` (String), `body` (String) |
| `send-whatsapp` | Enviar WhatsApp | Envía un mensaje por la aplicación WhatsApp. | `recipient` (String), `message` (String) |
| `send-telegram` | Enviar Telegram | Envía un mensaje por la aplicación Telegram. | `recipient` (String), `message` (String) |
| `google-search` | Búsqueda en Google | Búsqueda en Google refinando con términos clave y fechas. | `query` (String), `date_filter` (String) |
| `wikipedia-search` | Búsqueda Wikipedia | Consulta resúmenes informativos en Wikipedia en español. | `topic` (String) |
| `currency-rate` | Tasa de Cambio | Consulta el tipo de cambio oficial entre dos divisas. | `from` (String), `to` (String) |
| `currency-convert` | Conversión de Divisas | Convierte un monto entre dos divisas. | `amount` (Double), `from` (String), `to` (String) |
| `weather-forecast` | Pronóstico del Clima | Consulta el pronóstico especificando ciudad, fecha y hora. | `city` (String), `date` (String), `time_frame` (String) |
| `create-note` | Crear Nota Local | Guarda una nueva nota de texto en la base de datos local SQLite. | `title` (String), `body` (String), `tags` (String) |
| `create-reminder` | Programar Recordatorio | Programa una alarma especificando fecha, hora o minutos. | `title` (String), `minutes_from_now` (Int), `date_time` (String) |
| `ai-voice-recorder` | Grabadora de Voz IA | Inicia una sesión de grabación de voz procesada con IA. | `duration_seconds` (Int), `notes` (String) |
| `calendar-events` | Consulta de Calendario | Consulta eventos filtrando por fecha objetivo y término clave. | `date` (String), `query` (String), `limit` (Int) |
| `unread-notifications` | Notificaciones Pendientes | Revisa notificaciones no leídas filtradas por categoría o remitente. | `category` (String), `sender` (String) |
| `unread-emails-summary` | Resumen de Correos | Obtiene un resumen de correos electrónicos no leídos y pendientes. | - |
| `unread-whatsapp-summary` | Resumen de WhatsApp | Obtiene un resumen de los mensajes sin leer de WhatsApp. | - |
| `unread-telegram-summary` | Resumen de Telegram | Obtiene un resumen de notificaciones y mensajes pendientes de Telegram. | - |
| `news-search` | Consulta de Noticias | Busca noticias filtradas por tema, ubicación y fecha. | `topic` (String), `location` (String), `date` (String) |
| `duckduckgo-search` | Búsqueda DuckDuckGo | Búsqueda directa en DuckDuckGo refinando categoría y términos. | `query` (String), `category` (String) |

---

## Servicio de Transcripción de Audio (STT Simplificado)

El cliente HTTP de transcripción (`OpenAiTranscriptionClient`) cumple estrictamente con las siguientes especificaciones:

1. **Envío del Archivo de Audio e Idioma Español (`es`)**:
   - Se envía la parte `file` del archivo de audio, el parámetro de modelo `model` y la especificación del idioma `language = "es"`.
   - Se excluyen prompts guiados o parámetros innecesarios que distorsionen el resultado de la transcripción.
2. **Respuesta Exclusiva de Transcripción**:
   - El analizador de respuesta `extractText` retorna única y exclusivamente el texto transcrito directo en español, descartando envoltorios JSON o caracteres innecesarios.

---

## Proceso del Agente de IA y Personalización Ligera

Se ha ajustado el proceso de inyección de contexto en `UserProfileAnalyzer`:

### Reglas de Procesamiento de Solicitudes:
1. **Aislamiento de la Petición Actual**: Se deshabilitó la inclusión del historial de conversaciones previas (`buildRecentHistoryContext` retorna cadena vacía `""`). Cada consulta procesa única y exclusivamente la solicitud actual del usuario.
2. **Eliminación de Intereses e Instrucciones Detectadas**: Se removió la extracción automática de etiquetas de interés (`interestTags`) y la inyección de instrucciones personalizadas (`customInstructions`) en el Prompt de Sistema.
3. **Personalización por Nombre**: Se almacena y conserva el **Nombre del usuario** (`profile.name`) para que el agente **Aura** se dirija al usuario de forma personalizada en cada respuesta.

---

## Modos de Interacción

### 1. Interfaz de Chat Móvil (`ChatActivity`)
- **Teclado Virtual & Físico**: Configurado para enviar consultas directamente al presionar la tecla **Enter / Intro** (`imeOptions="actionSend"`).
- **Barra de Acceso Rápido (`Quick Skills Toolbar`)**: Botones tipo Chip desplegables sobre el campo de texto para autocompletar plantillas de comandos al instante.
- **Botón `⚡ Skills`**: Despliega un cuadro de diálogo con el catálogo completo de las 19 habilidades registradas.

### 2. Asistente por Voz desde Micrófono de las Gafas (`AiConversation`)
- **Prompt de Sistema Unificado**: Inyecta `SkillRegistry.buildSystemPromptAddendum()` a todas las consultas generadas por voz junto con el nombre del usuario.
- **Ejecución Automática de Handlers**: Invoca `SkillExecutor.processAndExecute` en segundo plano cuando el LLM emite etiquetas `[SKILL: id_habilidad {...}]`.
- **Sanitización de Respuestas**: Limpia etiquetas JSON o bloques de formato crudo antes de proyectar en el HUD y sintetizar la voz mediante el reproductor TTS.
