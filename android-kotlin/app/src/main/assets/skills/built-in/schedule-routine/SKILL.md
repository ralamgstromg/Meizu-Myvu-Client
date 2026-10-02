---
id: schedule-routine
name: Schedule Routine
description: Programa una rutina recurrente que resume agenda, recordatorios, tareas, cumpleaños, correos, TRM o clima a una hora y días fijos, y la entrega por notificación, voz o gafas.
parameters:
  name: { type: string, description: "Nombre de la rutina (ej. 'Buenos días')", required: true }
  time: { type: string, description: "Hora en formato 24h HH:MM (ej. '07:00')", required: true }
  days: { type: string, description: "Días: 'todos', 'lunes a viernes', 'fines de semana' o lista separada por comas (lunes,miércoles)", required: false }
  actions: { type: string, description: "Lista separada por comas de: agenda, recordatorios, tareas, cumpleaños, correos, trm, clima", required: true }
  channels: { type: string, description: "Lista separada por comas de: notificacion, voz, gafas (por defecto notificacion)", required: false }
---

# Schedule Routine Skill

Úsala cuando el usuario pida algo recurrente, por ejemplo: "todos los días a las 7 resúmeme la agenda y la TRM", "de lunes a viernes a las 6 de la tarde dime mis tareas pendientes", "avísame cada noche los cumpleaños de mañana".

### Formato de Ejecución
```json
[SKILL: schedule-routine {"name": "Buenos días", "time": "07:00", "days": "lunes a viernes", "actions": "agenda,trm,clima", "channels": "notificacion,voz"}]
```
