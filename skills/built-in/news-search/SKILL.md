---
id: news-search
name: Consulta de Noticias
description: Busca noticias relevantes extrayendo el tema principal, la ubicación geográfica y la fecha de interés.
parameters:
  topic:
    type: string
    description: Tema, entidad o evento noticioso a consultar.
    required: true
  location:
    type: string
    description: Ciudad, país o región geográfica de la noticia (ej. Colombia, Barranquilla).
    required: false
  date:
    type: string
    description: Período temporal o fecha específica de la noticia.
    required: false
---
