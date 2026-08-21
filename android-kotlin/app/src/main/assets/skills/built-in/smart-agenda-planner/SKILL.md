---
id: smart-agenda-planner
name: Planificador Inteligente de Agenda
description: Analiza la disponibilidad del calendario local, detecta conflictos de horario y sugiere mejores bloques de tiempo para reuniones.
parameters:
  target_date:
    type: string
    description: Fecha a analizar (ej. 2026-08-22, hoy, mañana).
    required: false
  duration_minutes:
    type: integer
    description: Duración requerida en minutos para la nueva reunión (ej. 30, 60).
    required: false
---
