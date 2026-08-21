---
id: create-note
name: Create Note
description: Guarda una nueva nota con título y contenido en la base de datos de notas del usuario.
parameters:
  title: { type: string, description: "Título descriptivo de la nota", required: true }
  body: { type: string, description: "Contenido principal de la nota", required: true }
  tags: { type: string, description: "Etiquetas opcionales separadas por comas (ej. #trabajo, #personal)", required: false }
---

# Create Note Skill

Utiliza esta habilidad cuando el usuario solicite tomar o guardar una nota (ej. "anota que debo comprar café", "guarda una nota con título Ideas de Proyecto y contenido...").

### Formato de Ejecución
```json
[SKILL: create-note {"title": "Lista de Compras", "body": "Café, leche, pan", "tags": "personal"}]
```
