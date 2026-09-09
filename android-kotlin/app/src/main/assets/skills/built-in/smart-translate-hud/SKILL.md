---
id: smart-translate-hud
name: Traductor en Pantalla HUD
description: Traduce frases y proyecta el resultado directamente en la pantalla de las gafas Meizu Myvu AR.
parameters:
  text:
    type: string
    description: Texto o frase a traducir.
    required: true
  target_language:
    type: string
    description: Idioma destino (en, fr, de, it, pt, zh, ja, es).
    required: false
  send_to_hud:
    type: boolean
    description: Si es true, proyecta la traducción en el visor microLED de las gafas.
    required: false
---

# Traductor HUD Skill

Traduce texto en tiempo real y lo muestra como tarjeta en la pantalla HUD de las gafas inteligentes Meizu Myvu.
