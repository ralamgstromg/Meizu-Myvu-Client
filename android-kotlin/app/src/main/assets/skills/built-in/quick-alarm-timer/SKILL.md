---
id: quick-alarm-timer
name: Alarmas y Temporizadores
description: Configura y consulta alarmas o temporizadores de cuenta regresiva en el dispositivo.
parameters:
  action:
    type: string
    description: Acción (set_alarm, set_timer, show_alarms, show_timers, dismiss_alarm).
    required: true
  time_or_duration:
    type: string
    description: Hora de alarma (ej. "07:30", "7:30 am") o duración (ej. "15m", "10 minutos", "1 hora y media", "45s").
    required: false
  label:
    type: string
    description: Etiqueta o título de la alarma o temporizador.
    required: false
---

# Quick Alarm and Timer Skill

Configura y gestiona alarmas y temporizadores del sistema Android.
