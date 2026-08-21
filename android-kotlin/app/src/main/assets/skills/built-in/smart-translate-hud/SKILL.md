---
id: smart-translate-hud
name: Traductor Contextual e Interfaz HUD
description: Traduce texto o expresiones a otros idiomas y opcionalmente transmite la traducción a las gafas inteligentes Meizu Myvu.
parameters:
  text:
    type: string
    description: Texto o frase a traducir.
    required: true
  target_language:
    type: string
    description: Código de idioma destino (ej. en, es, fr, de, zh, pt).
    required: true
  send_to_hud:
    type: boolean
    description: Si es true, envía la traducción a la pantalla de las gafas Myvu.
    required: false
---
