---
id: create-reminder
name: Create Reminder
description: Crea un recordatorio con fecha/hora especificada y programa una alarma o notificación en el teléfono/lentes.
parameters:
  title: { type: string, description: "Título o descripción del recordatorio", required: true }
  minutes_from_now: { type: string, description: "Minutos a partir de ahora para activar el recordatorio (por defecto 30)", required: false }
  body: { type: string, description: "Detalles adicionales del recordatorio", required: false }
---

# Create Reminder Skill

Utiliza esta habilidad cuando el usuario pida que le recuerdes algo (ej. "recuérdame llamar a María en 15 minutos", "pon un recordatorio para tomar agua").

### Formato de Ejecución
```json
[SKILL: create-reminder {"title": "Llamar a María", "minutes_from_now": "15"}]
```
