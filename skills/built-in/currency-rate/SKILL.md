---
id: currency-rate
name: Currency Rate Query
description: Consulta la tasa de cambio actual entre dos divisas (ej. USD a EUR, dólar a peso mexicano).
parameters:
  from: { type: string, description: "Código o nombre de la divisa de origen (ej. USD, EUR, Dólar)", required: true }
  to: { type: string, description: "Código o nombre de la divisa de destino (ej. MXN, COP, Euro)", required: true }
---

# Currency Rate Query Skill

Utiliza esta habilidad cuando el usuario pregunte el valor o tipo de cambio entre dos monedas.

### Formato de Ejecución
```json
[SKILL: currency-rate {"from": "USD", "to": "MXN"}]
```
