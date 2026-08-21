---
id: hud-navigation
name: Navegación AR en Lentes HUD
description: Inicia la navegación GPS guiada paso a paso proyectada en la interfaz AR / HUD de los lentes inteligentes indicando la dirección, barrio o ciudad de destino.
parameters:
  destination:
    type: string
    description: Dirección completa, lugar de interés o sitio de destino (ej. Calle 72 #53-45, Centro Comercial Buenavista).
    required: true
  city:
    type: string
    description: Ciudad de destino (ej. Barranquilla, Bogotá, Medellín).
    required: false
  neighborhood:
    type: string
    description: Barrio, sector o zona objetivo (ej. El Prado, Chapinero).
    required: false
  mode:
    type: string
    description: Modo de transporte (ej. driving, walking).
    required: false
---
