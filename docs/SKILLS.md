# Documentación del Motor de Skills y Asistente Virtual (Aura)

## Arquitectura del Skill Engine en Android

La aplicación móvil Kotlin para los lentes inteligentes **Meizu MYVU** integra un motor de habilidades (**Skill Engine**) extensible, interpretable tanto por el agente **Aura** como por la interfaz de chat en el dispositivo móvil y la pantalla de visualización frontal HUD / TTS de las gafas AR.

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
- **Botón `⚡ Skills`**: Despliega un cuadro de diálogo con el catálogo completo de las 12 habilidades registradas.

### 2. Asistente por Voz desde Micrófono de las Gafas (`AiConversation`)
- **Prompt de Sistema Unificado**: Inyecta `SkillRegistry.buildSystemPromptAddendum()` a todas las consultas generadas por voz junto con el nombre del usuario.
- **Ejecución Automática de Handlers**: Invoca `SkillExecutor.processAndExecute` en segundo plano cuando el LLM emite etiquetas `[SKILL: id_habilidad {...}]`.
- **Sanitización de Respuestas**: Limpia etiquetas JSON o bloques de formato crudo antes de proyectar en el HUD y sintetizar la voz mediante el reproductor TTS.

---

## Catálogo de 12 Habilidades Nativas Activas

| ID de Habilidad | Nombre | Descripción | Parámetros Principales |
| :--- | :--- | :--- | :--- |
| `call-contact` | Llamar a Contacto | Realiza una llamada telefónica directa a un contacto o número. | `contact_or_number` (String) |
| `send-email` | Enviar Correo | Redacta y envía un correo electrónico. | `recipient` (String), `subject` (String), `body` (String) |
| `send-whatsapp` | Enviar WhatsApp | Envía un mensaje por la aplicación WhatsApp. | `recipient` (String), `message` (String) |
| `send-telegram` | Enviar Telegram | Envía un mensaje por la aplicación Telegram. | `recipient` (String), `message` (String) |
| `google-search` | Búsqueda en Google | Realiza una búsqueda web en Google. | `query` (String) |
| `wikipedia-search` | Búsqueda Wikipedia | Consulta resúmenes informativos en Wikipedia en español. | `topic` (String) |
| `currency-rate` | Tasa de Cambio | Consulta el tipo de cambio oficial entre dos divisas. | `from` (String), `to` (String) |
| `currency-convert` | Conversión de Divisas | Convierte un monto entre dos divisas. | `amount` (Double), `from` (String), `to` (String) |
| `weather-forecast` | Pronóstico del Clima | Consulta el estado del tiempo y pronóstico en una ciudad. | `city` (String) |
| `create-note` | Crear Nota Local | Guarda una nueva nota de texto en la base de datos local SQLite. | `title` (String), `body` (String), `tags` (String) |
| `create-reminder` | Programar Recordatorio | Programa una alarma/recordatorio con notificación. | `title` (String), `minutes_from_now` (Int), `body` (String) |
| `ai-voice-recorder` | Grabadora de Voz IA | Inicia una sesión de grabación de voz procesada con IA. | `duration_seconds` (Int), `notes` (String) |
