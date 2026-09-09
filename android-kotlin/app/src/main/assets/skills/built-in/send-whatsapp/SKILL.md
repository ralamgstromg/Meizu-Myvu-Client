---
id: send-whatsapp
name: Send WhatsApp
description: Envía mensajes de WhatsApp manos libres resolviendo contactos y enviando automáticamente sin tocar el teléfono.
parameters:
  contact_or_phone: { type: string, description: "Nombre del contacto o número telefónico", required: true }
  message: { type: string, description: "Contenido del mensaje a enviar", required: true }
---

# Send WhatsApp Skill

Utiliza esta habilidad cuando el usuario desee enviar un mensaje de WhatsApp (ej. "manda un whatsapp a Carlos diciendo que llego en 10 minutos", "escribe por whatsapp a María").

### Formato de Ejecución
```json
[SKILL: send-whatsapp {"contact_or_phone": "Carlos", "message": "Llego en 10 minutos"}]
```
