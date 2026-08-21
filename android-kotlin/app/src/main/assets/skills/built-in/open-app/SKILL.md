---
id: open-app
name: Abrir Aplicación
description: Abre o lanza una aplicación instalada en el dispositivo móvil por su nombre o nombre de paquete.
parameters:
  app_name:
    type: string
    description: Nombre popular de la aplicación a abrir (ej. WhatsApp, YouTube, Spotify, Cámara, Galería, Chrome, Gmail, Calendario).
    required: true
  package_name:
    type: string
    description: Nombre del paquete Android si se conoce exactamente (ej. com.whatsapp).
    required: false
---
