---
id: wikipedia-search
name: Wikipedia Search
description: Consulta resúmenes enciclopédicos en Wikipedia sobre personas, conceptos, eventos o lugares.
parameters:
  topic: { type: string, description: "Tema, personaje o concepto a consultar en Wikipedia", required: true }
---

# Wikipedia Search Skill

Utiliza esta habilidad cuando el usuario pida saber "quién es", "qué es" o busque una definición enciclopédica detallada.

### Formato de Ejecución
```json
[SKILL: wikipedia-search {"topic": "Teoría de la relatividad"}]
```
