---
id: send-email
name: Send Email
description: Prepara y envía correos electrónicos resolviendo el destinatario desde los contactos de Android.
parameters:
  to: { type: string, description: "Correo electrónico del destinatario o nombre de contacto guardado", required: true }
  subject: { type: string, description: "Asunto del correo electrónico", required: true }
  body: { type: string, description: "Cuerpo principal del mensaje", required: true }
---

# Send Email Skill

Utiliza esta habilidad cuando el usuario solicite redactar o enviar un correo electrónico (ej. "envía un correo a soporte@empresa.com con asunto Reporte").

### Formato de Ejecución
```json
[SKILL: send-email {"to": "Carlos", "subject": "Informe", "body": "Adjunto los datos solicitados."}]
```
