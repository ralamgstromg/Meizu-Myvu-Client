# Documentación del Sistema de Skills (Habilidades) - Meizu Myvu Client

El **Sistema de Skills** de `Meizu-Myvu-Client` permite al Agente LLM ejecutar acciones nativas en Android, gestionar notas, recordatorios y grabadora de voz IA, así como consultar información externa en tiempo real mediante manifiestos estructurados en archivos `SKILL.md`.

---

## Estructura de Directorio de Skills

Las habilidades se organizan dentro de carpetas independientes con su manifiesto `SKILL.md`:

```
skills/
└── built-in/
    ├── call-contact/
    │   └── SKILL.md
    ├── send-email/
    │   └── SKILL.md
    ├── send-whatsapp/
    │   └── SKILL.md
    ├── send-telegram/
    │   └── SKILL.md
    ├── google-search/
    │   └── SKILL.md
    ├── wikipedia-search/
    │   └── SKILL.md
    ├── currency-rate/
    │   └── SKILL.md
    ├── currency-convert/
    │   └── SKILL.md
    ├── weather-forecast/
    │   └── SKILL.md
    ├── create-note/
    │   └── SKILL.md
    ├── create-reminder/
    │   └── SKILL.md
    └── ai-voice-recorder/
        └── SKILL.md
```

Y en Android Assets: `android-kotlin/app/src/main/assets/skills/built-in/...`

---

## Formato del Manifiesto `SKILL.md`

Cada habilidad se define utilizando metadatos **YAML Frontmatter** combinados con instrucciones en Markdown:

```markdown
---
id: create-note
name: Create Note
description: Guarda una nueva nota con título y contenido en la base de datos de notas del usuario.
parameters:
  title: { type: string, description: "Título descriptivo de la nota", required: true }
  body: { type: string, description: "Contenido principal de la nota", required: true }
---

# Create Note Skill
Instrucciones detalladas de uso...
```

---

## Motor de Ejecución en Android Kotlin

1. **`SkillParser`**: Parsea el frontmatter YAML y extrae parámetros e instrucciones del archivo `SKILL.md`.
2. **`SkillLoader`**: Carga recursivamente todos los archivos `SKILL.md` desde `assets/skills/built-in` en runtime.
3. **`SkillRegistry`**: Almacena las definiciones registradas y genera el bloque `buildSystemPromptAddendum()` que inyecta el catálogo en el System Prompt del LLM.
4. **`SkillExecutor`**: Detecta etiquetas `[SKILL: id_habilidad {...}]` en la respuesta del LLM y ejecuta el `SkillHandler` Kotlin correspondiente.

---

## Habilidades Nativas Disponibles (12 Built-in Skills)

| Skill ID | Nombre | Descripción | Handler Kotlin |
| :--- | :--- | :--- | :--- |
| `call-contact` | Call Contact | Inicia o realiza llamadas telefónicas con resolución opcional en la agenda nativa. | `CallContactHandler` |
| `send-email` | Send Email | Redacta y abre cliente de correo mediante Intent `mailto:`. | `SendEmailHandler` |
| `send-whatsapp` | Send WhatsApp | Envia mensaje por WhatsApp a través de `com.whatsapp` o URI `api.whatsapp.com`. | `SendWhatsappHandler` |
| `send-telegram` | Send Telegram | Envia mensaje por Telegram usando `org.telegram.messenger` o `t.me`. | `SendTelegramHandler` |
| `google-search` | Google Search | Búsqueda web en vivo mediante raspado y fallback a DuckDuckGo/Google News. | `GoogleSearchHandler` |
| `wikipedia-search` | Wikipedia Search | Consulta resúmenes enciclopédicos directamente de Wikipedia API. | `WikipediaSearchHandler` |
| `currency-rate` | Currency Rate Query | Consulta tasa de cambio en vivo entre divisas. | `CurrencyRateHandler` |
| `currency-convert` | Currency Convert | Calcula conversión de un monto específico entre divisas. | `CurrencyConvertHandler` |
| `weather-forecast` | Weather Forecast | Consulta el pronóstico del tiempo vía Open-Meteo API. | `WeatherForecastHandler` |
| `create-note` | Create Note | Inserta y guarda una nueva nota en la base de datos local `notes`. | `CreateNoteHandler` |
| `create-reminder` | Create Reminder | Crea y programa un recordatorio y su alarma en `ReminderScheduler`. | `CreateReminderHandler` |
| `ai-voice-recorder` | AI Voice Recorder | Abre e inicia la pantalla de la grabadora de voz IA con transcripción Whisper. | `AiVoiceRecorderHandler` |
