---
id: calendar-events
name: Calendar Events
description: Consulta eventos y reuniones del calendario o agenda nuevos compromisos en el dispositivo.
parameters:
  action: { type: string, description: "Acción a realizar: 'query' para consultar o 'create' para agendar", required: false }
  date: { type: string, description: "Fecha a consultar (ej. 'hoy', 'mañana', 'próxima semana')", required: false }
  query: { type: string, description: "Filtro de búsqueda por título del evento", required: false }
  title: { type: string, description: "Título del nuevo evento a crear si action es 'create'", required: false }
---

# Calendar Events Skill

Permite inspeccionar eventos y agendar reuniones directamente en Google Calendar / calendario local de Android.
