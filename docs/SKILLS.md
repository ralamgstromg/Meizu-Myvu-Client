# Documentación del Motor de Skills y Asistente Virtual (Aura)

## Arquitectura del Skill Engine en Android

La aplicación móvil Kotlin para los lentes inteligentes **Meizu MYVU** integra un motor de habilidades (**Skill Engine**) extensible, interpretable tanto por el agente **Aura** como por la interfaz de chat en el dispositivo móvil y la pantalla de visualización frontal HUD / TTS de las gafas AR.

---

## Solución al Fallo del Servicio STT (Transcripción de Voz de las Gafas)

Se identificó y corrigió el error crítico en [`OpusDecoderStream.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/OpusDecoderStream.kt):

- **Diagnóstico en Logs**: La línea `captured 0 samples (0ms @ 48000Hz) from X Opus packets` indicaba que el búfer de muestras decodificadas llegaba vacío al servicio STT.
- **Causa Raíz**: En `OpusDecoderStream.kt`, el método `finish()` invocaba en su cláusula `finally { stop() }`. A su vez, `stop()` ejecutaba `all.reset()`, limpiando inmediatamente el flujo de audio `ByteArrayOutputStream` antes de que `decoder.allPcm()` pudiese leer las muestras capturadas.
- **Solución**: Se removió `all.reset()` de `stop()`. El búfer `all.reset()` únicamente se limpia de forma explícita al reiniciar la sesión de grabación en `start()` o `reset()`, garantizando la entrega del 100% de la ráfaga PCM al motor STT.

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
