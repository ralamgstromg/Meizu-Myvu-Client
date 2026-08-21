---
id: web-page-summarizer
name: Resumen de Páginas Web
description: Extrae el contenido de una URL web y genera un resumen ejecutivo estructurado con puntos clave.
parameters:
  url:
    type: string
    description: Enlace web o dirección URL a analizar (ej. https://ejemplo.com/articulo).
    required: true
  max_bullet_points:
    type: integer
    description: Número máximo de viñetas a resumir.
    required: false
---
