---
id: weather-forecast
name: Pronóstico del Clima
description: Consulta el pronóstico del tiempo especificando la ciudad, fecha (ej. 2026-08-22, mañana) y período del día.
parameters:
  city:
    type: string
    description: Nombre de la ciudad o municipio (ej. Barranquilla, Bogotá, Madrid).
    required: true
  date:
    type: string
    description: Fecha o indicación temporal objetivo (ej. 2026-08-22, mañana, hoy).
    required: false
  time_frame:
    type: string
    description: Período del día a consultar (ej. mañana, tarde, noche).
    required: false
---
