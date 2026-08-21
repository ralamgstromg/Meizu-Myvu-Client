# Documentación del Motor de Skills y Asistente Virtual (Aura)

## Arquitectura del Skill Engine en Android

La aplicación móvil Kotlin para los lentes inteligentes **Meizu MYVU** integra un motor de habilidades (**Skill Engine**) extensible, interpretable tanto por el agente **Aura** como por la interfaz de chat en el dispositivo móvil y la pantalla de visualización frontal HUD / TTS de las gafas AR.

---

## Formato Exclusivo Markdown en Notas, Recordatorios y Grabadora de Voz IA

Se ajustó la lógica de procesamiento de Inteligencia Artificial para los módulos de **Notas y Recordatorios** ([`NoteAiProcessor.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/NoteAiProcessor.kt)) y **Grabadora de Voz IA** ([`MeetingAiProcessor.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/MeetingAiProcessor.kt)):

1. **Garantía de Markdown Puro sin JSON Crudo**:
   - Se implementó la clase [`MarkdownUtils.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/core/MarkdownUtils.kt), encargada de filtrar y desempaquetar cualquier respuesta estructurada en JSON o delimitada con bloques ` ```json ` para extraer exclusivamente Markdown estructurado (encabezados `###`, viñetas, negritas).
2. **Prompts de IA Rigurosos**:
   - Los prompts de sistema para resumir notas, recordatorios y reuniones especifican explícitamente que la propiedad `summary` debe retornar **única y exclusivamente texto en formato Markdown**.
3. **Renderizado en Pantallas de Detalle**:
   - [`NoteDetailActivity.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/NoteDetailActivity.kt) y [`RecordingDetailActivity.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/RecordingDetailActivity.kt) sanean dinámicamente cualquier resumen almacenado con `MarkdownUtils.sanitizeToMarkdown(...)` antes de renderizar con `TextView.setMarkdown(...)`.

---

## Catálogo Actualizado de 21 Habilidades Nativas Activas

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
| `x-twitter-search` | Búsqueda en X / Twitter | Consulta tendencias e información en tiempo real en X (Twitter). | `query` (String), `topic` (String), `author` (String) |
| `hud-navigation` | Navegación AR en Lentes | Inicia navegación GPS proyectada en la interfaz AR / HUD indicando dirección, barrio o ciudad. | `destination` (String), `city` (String), `neighborhood` (String), `mode` (String) |
