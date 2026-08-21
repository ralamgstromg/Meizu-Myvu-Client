---
id: rag-history-search
name: Búsqueda Semántica en Historial Local
description: Busca información contextual en el historial local de grabaciones de voz, notas almacenadas y tareas registradas.
parameters:
  query:
    type: string
    description: Consulta o término de búsqueda a localizar en la base de datos local.
    required: true
  search_scope:
    type: string
    description: Ámbito de búsqueda (all, recordings, notes, tasks).
    required: false
---
