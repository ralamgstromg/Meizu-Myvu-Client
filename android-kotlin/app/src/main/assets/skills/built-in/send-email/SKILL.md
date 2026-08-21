---
id: send-email
name: Send Email
description: Permite redactar y enviar un correo electrónico a un destinatario con asunto y cuerpo de mensaje.
parameters:
  to: { type: string, description: "Correo electrónico del destinatario o nombre de contacto", required: true }
  subject: { type: string, description: "Asunto del correo electrónico", required: true }
  body: { type: string, description: "Cuerpo principal del mensaje", required: true }
---

# Send Email Skill

Utiliza esta habilidad cuando el usuario desee enviar un email o correo (ej. "envía un correo a pedro@test.com con asunto Reunión y cuerpo Hola Pedro").

### Formato de Ejecución
```json
[SKILL: send-email {"to": "pedro@test.com", "subject": "Reunión", "body": "Hola Pedro..."}]
```
