---
id: send-whatsapp
name: Send WhatsApp
description: Permite enviar un mensaje por WhatsApp a un contacto o número telefónico.
parameters:
  contact_or_phone: { type: string, description: "Nombre de contacto o número telefónico con clave de país", required: true }
  message: { type: string, description: "Contenido del mensaje a enviar", required: true }
---

# Send WhatsApp Skill

Utiliza esta habilidad cuando el usuario desee enviar un WhatsApp (ej. "manda un whatsapp a Carlos diciendo que llego en 10 minutos").

### Formato de Ejecución
```json
[SKILL: send-whatsapp {"contact_or_phone": "Carlos", "message": "Llego en 10 minutos"}]
```
