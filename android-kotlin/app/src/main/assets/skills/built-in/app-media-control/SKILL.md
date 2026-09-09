---
id: app-media-control
name: Control de Medios y Música
description: Controla la reproducción de música, búsqueda de canciones o videos en aplicaciones como NewPipe, OpenTune, Spotify, YouTube Music, o reproductores locales, y gestiona pausar, reanudar, saltar pistas o consultar qué canción está sonando.
parameters:
  action:
    type: string
    description: Acción a ejecutar ("play_search", "search", "pause", "resume", "next", "previous", "stop", "now_playing").
    required: true
  query:
    type: string
    description: Título de la canción, artista o término de búsqueda (ej. "Bohemian Rhapsody", "Luis Miguel", "rock en español").
    required: false
  target_app:
    type: string
    description: Nombre de la aplicación objetivo (ej. "newpipe", "opentune", "spotify", "youtube", "youtube music", "vlc").
    required: false
---
