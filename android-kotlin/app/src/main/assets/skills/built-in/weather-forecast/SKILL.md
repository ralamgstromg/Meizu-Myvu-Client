---
id: weather-forecast
name: Weather Forecast
description: Consulta el pronóstico del clima para una ciudad o ubicación geográfica específica mediante Open-Meteo API.
parameters:
  city: { type: string, description: "Nombre de la ciudad o ubicación", required: true }
---

# Weather Forecast Skill

Utiliza esta habilidad cuando el usuario pregunte el clima, la temperatura o el pronóstico meteorológico de una ciudad.

### Formato de Ejecución
```json
[SKILL: weather-forecast {"city": "Madrid"}]
```
