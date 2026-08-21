---
id: send-telegram
name: Send Telegram
description: Permite enviar un mensaje por Telegram a un contacto, alias (@usuario) o número telefónico.
parameters:
  username_or_phone: { type: string, description: "Alias @usuario, nombre de contacto o número de teléfono", required: true }
  message: { type: string, description: "Contenido del mensaje a enviar", required: true }
---

# Send Telegram Skill

Utiliza esta habilidad cuando el usuario desee enviar un mensaje de Telegram (ej. "envía un Telegram a @maria diciendo Hola").

### Formato de Ejecución
```json
[SKILL: send-telegram {"username_or_phone": "@maria", "message": "Hola"}]
```
