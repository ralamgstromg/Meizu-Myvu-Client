---
id: calendar-events
name: Consulta del Calendario
description: Consulta eventos y reuniones próximas en el calendario filtrando por fecha, día objetivo o término del evento.
parameters:
  date:
    type: string
    description: Fecha o día objetivo a consultar (ej. 2026-08-22, hoy, mañana, lunes).
    required: false
  query:
    type: string
    description: Nombre, palabra clave o asistente del evento a buscar.
    required: false
  limit:
    type: integer
    description: Límite de eventos a retornar.
    required: false
---
