---
id: send-telegram
name: Send Telegram
description: Envía mensajes de Telegram manos libres a un usuario (@alias), contacto o número telefónico.
parameters:
  username_or_phone: { type: string, description: "Alias @usuario, nombre de contacto o número telefónico", required: true }
  message: { type: string, description: "Contenido del mensaje a enviar", required: true }
---

# Send Telegram Skill

Utiliza esta habilidad cuando el usuario desee comunicarse vía Telegram (ej. "envía un telegram a @juan diciendo hola").

### Formato de Ejecución
```json
[SKILL: send-telegram {"username_or_phone": "@juan", "message": "Hola, ya estoy listo"}]
```
