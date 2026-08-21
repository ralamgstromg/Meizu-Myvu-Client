---
id: currency-convert
name: Currency Convert
description: Convierte un monto específico de dinero entre dos divisas (ej. 100 dólares a euros).
parameters:
  amount: { type: string, description: "Cantidad numérica a convertir", required: true }
  from: { type: string, description: "Divisa de origen (ej. USD, EUR)", required: true }
  to: { type: string, description: "Divisa de destino (ej. COP, MXN)", required: true }
---

# Currency Convert Skill

Utiliza esta habilidad cuando el usuario solicite calcular la conversión de una suma de dinero de una moneda a otra.

### Formato de Ejecución
```json
[SKILL: currency-convert {"amount": "100", "from": "USD", "to": "EUR"}]
```
