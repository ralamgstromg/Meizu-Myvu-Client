---
id: google-search
name: Búsqueda en Google
description: Realiza una búsqueda refinada en Google en español extrayendo términos clave, fechas y entidades específicas.
parameters:
  query:
    type: string
    description: Términos o preguntas refinadas a buscar.
    required: true
  date_filter:
    type: string
    description: Filtro temporal o fecha refinada de consulta (ej. 2026, hoy, esta semana).
    required: false
---
