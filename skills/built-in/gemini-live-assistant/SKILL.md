---
id: gemini-live-assistant
name: Asistente Gemini de Voz e Interacción en Gafas
description: Activa la interacción conversacional continua dúplex con Gemini utilizando el micrófono Bluetooth de las gafas inteligentes Meizu MYVU, salida de audio sintetizado A2DP/SCO y transmisión de texto plano limpio a la pantalla AR (HUD).
parameters:
  trigger_mode:
    type: string
    description: Modo de activación (voice_wakeword para detección "Oye Gemini" o touch_gesture para toque prolongado en la patilla de las gafas).
    required: false
  enable_hud:
    type: string
    description: Si es true (por defecto), transmite resúmenes de texto limpio sin formato Markdown a la pantalla AR HUD.
    required: false
  context_skills:
    type: string
    description: Lista de habilidades nativas habilitadas para Function Calling durante la sesión con Gemini.
    required: false
---
