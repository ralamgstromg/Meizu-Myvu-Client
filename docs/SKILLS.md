# Documentación del Sistema de Skills y Agente Aura - Meizu Myvu Client

El **Sistema de Skills** de `Meizu-Myvu-Client` dota al Agente IA (llamado **Aura**) de la capacidad de ejecutar acciones nativas en Android, gestionar notas, recordatorios, grabadora de voz IA y consultar información web/divisas/clima en tiempo real.

---

## Identidad del Agente Aura

- **Nombre:** Aura.
- **Idioma y Región:** Español con configuración regional de **Colombia (`es-CO`)**, utilizando pesos colombianos (`COP $`) y contexto geográfico de Colombia.
- **Canales de Interacción:**
  1. **Pantalla HUD / Gafas AR:** Respuestas breves conversacionales en texto plano (1-2 oraciones).
  2. **Aplicación Móvil / Modo Chat (`ChatActivity` / `ChatSidebar`):** Conversación fluida en formato chat con soporte para respuestas extendidas, imágenes y ejecución de habilidades nativas.

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

## Motor de Ejecución en Android Kotlin

1. **`SkillParser`**: Parsea el frontmatter YAML y extrae parámetros e instrucciones del archivo `SKILL.md`.
2. **`SkillLoader`**: Carga recursivamente todos los archivos `SKILL.md` desde `assets/skills/built-in` en runtime.
3. **`SkillRegistry`**: Almacena las definiciones registradas y genera el bloque `buildSystemPromptAddendum()` que inyecta la identidad de **Aura** y el catálogo de habilidades en el System Prompt del LLM.
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
