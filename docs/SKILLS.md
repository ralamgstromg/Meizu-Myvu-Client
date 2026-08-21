# Documentación del Sistema de Skills (Habilidades) - Meizu Myvu Client

El **Sistema de Skills** de `Meizu-Myvu-Client` permite al Agente LLM ejecutar acciones nativas en Android (Llamadas, Correo, WhatsApp, Telegram) mediante especificaciones dinámicas basadas en archivos `SKILL.md`.

---

## Estructura de Directorio de Skills

Las habilidades se organizan dentro de carpetas independientes que contienen su manifiesto `SKILL.md`:

```
skills/
└── built-in/
    ├── call-contact/
    │   └── SKILL.md
    ├── send-email/
    │   └── SKILL.md
    ├── send-whatsapp/
    │   └── SKILL.md
    └── send-telegram/
        └── SKILL.md
```

Y en Android Assets: `android-kotlin/app/src/main/assets/skills/built-in/...`

---

## Formato del Manifiesto `SKILL.md`

Cada habilidad se define utilizando metadatos **YAML Frontmatter** combinados con instrucciones en Markdown:

```markdown
---
id: send-whatsapp
name: Send WhatsApp
description: Permite enviar un mensaje por WhatsApp a un contacto o número telefónico.
parameters:
  contact_or_phone: { type: string, description: "Nombre de contacto o número telefónico", required: true }
  message: { type: string, description: "Contenido del mensaje a enviar", required: true }
---

# Send WhatsApp Skill
Instrucciones detalladas de uso...
```

---

## Motor de Ejecución en Android Kotlin

1. **`SkillParser`**: Parsea el frontmatter YAML y extrae parámetros e instrucciones del archivo `SKILL.md`.
2. **`SkillLoader`**: Carga recursivamente todos los archivos `SKILL.md` desde `assets/skills/built-in` en runtime.
3. **`SkillRegistry`**: Almacena las definiciones registradas y genera el bloque `buildSystemPromptAddendum()` que inyecta el catálogo en el System Prompt del LLM.
4. **`SkillExecutor`**: Detecta etiquetas `[SKILL: id_habilidad {...}]` en la respuesta del LLM y ejecuta el `SkillHandler` Kotlin correspondiente.

---

## Habilidades Nativas Disponibles

| Skill ID | Nombre | Descripción | Handler Kotlin |
| :--- | :--- | :--- | :--- |
| `call-contact` | Call Contact | Inicia o realiza llamadas telefónicas con resolución opcional en la agenda nativa. | `CallContactHandler` |
| `send-email` | Send Email | Redacta y abre cliente de correo mediante Intent `mailto:`. | `SendEmailHandler` |
| `send-whatsapp` | Send WhatsApp | Envia mensaje por WhatsApp a través de `com.whatsapp` o URI `api.whatsapp.com`. | `SendWhatsappHandler` |
| `send-telegram` | Send Telegram | Envia mensaje por Telegram usando `org.telegram.messenger` o `t.me`. | `SendTelegramHandler` |
