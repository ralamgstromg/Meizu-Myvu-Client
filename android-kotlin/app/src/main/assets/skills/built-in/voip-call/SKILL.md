---
id: voip-call
name: VoIP Call (WhatsApp, Teams, Google Chat)
description: Realiza llamadas por internet mediante WhatsApp, Microsoft Teams o Google Chat/Meet a un contacto específico directo desde las gafas.
parameters:
  platform: { type: string, description: "Plataforma de llamada: whatsapp, teams o google_chat", required: true }
  contact: { type: string, description: "Nombre del contacto registrado o número/correo a llamar", required: true }
---

# VoIP Call Skill

Utiliza esta habilidad cuando el usuario exprese la intención de realizar una llamada de voz o videollamada por internet usando aplicaciones como WhatsApp, Microsoft Teams o Google Chat / Meet (ej. "llama a Juan por WhatsApp", "haz una llamada por Teams a María", "llamar por Google Chat a Pedro").

### Formato de Ejecución
```json
[SKILL: voip-call {"platform": "whatsapp", "contact": "Juan"}]
```
