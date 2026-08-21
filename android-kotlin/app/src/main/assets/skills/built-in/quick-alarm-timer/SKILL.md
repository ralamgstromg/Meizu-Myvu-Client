---
id: quick-alarm-timer
name: Alarmas y Temporizadores Rápidos
description: Configura alarmas o temporizadores de cuenta regresiva directamente en el dispositivo.
parameters:
  action:
    type: string
    description: Acción a ejecutar (set_alarm, set_timer).
    required: true
  time_or_duration:
    type: string
    description: Hora para la alarma (ej. "07:30") o duración para el temporizador (ej. "15m", "45s").
    required: true
  label:
    type: string
    description: Etiqueta o título del recordatorio de la alarma/temporizador.
    required: false
---
