---
id: health-summary
name: Health & Wellness Summary
description: Consulta y resume métricas de salud y actividad física como pasos diarios, nivel de estrés, ritmo cardíaco y calorías quemadas directamente en el HUD.
parameters:
  metric: { type: string, description: "Tipo de métrica a consultar: all (resumen completo), steps (pasos), stress (nivel de estrés) o heart_rate (ritmo cardíaco)", required: false }
---

# Health & Wellness Summary Skill

Utiliza esta habilidad cuando el usuario pregunte por su estado de salud, actividad física o bienestar (ej. "¿cuántos pasos llevo hoy?", "¿cuál es mi nivel de estrés?", "resumen de salud", "ritmo cardíaco").

### Formato de Ejecución
```json
[SKILL: health-summary {"metric": "all"}]
```
