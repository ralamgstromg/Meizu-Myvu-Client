# Plan: Control Integral de Aplicaciones de Medios (NewPipe, OpenTune y Reproductores Multimedia)

## 1. Contexto y Objetivos
Permitir a los usuarios de las gafas inteligentes Meizu Myvu controlar aplicaciones multimedia de terceros (con soporte de primera clase para aplicaciones libres como **NewPipe** y **OpenTune / ViMusic / RiMusic**, además de Spotify y YouTube Music) mediante comandos de voz naturales y atajos táctiles, con el teléfono desbloqueado o bloqueado en el bolsillo.

### Capacidades Requeridas:
1. **Reproducción Dirigida por Búsqueda ("Play from Search")**:
   - "reproduce [canción/artista] en NewPipe"
   - "reproduce [canción/artista] en OpenTune"
   - "pon [canción/artista] en Spotify"
   - "reproduce [canción/artista] en YouTube"
2. **Búsquedas Explícitas en Apps de Terceros**:
   - "busca [término] en NewPipe"
   - "busca [término] en OpenTune"
   - "busca canciones de [artista] en [app]"
3. **Control Universal de Reproducción (Transport Controls)**:
   - Pausar / Detener: "pausa la música", "detén la música", "stop"
   - Reanudar: "reproduce música", "reanuda la música", "play"
   - Saltar / Siguiente: "siguiente canción", "salta la canción", "cambia de canción", "pasa de pista"
   - Anterior / Repetir: "canción anterior", "retrocede la canción", "repite la canción"
4. **Consulta de Canción Actual ("Now Playing")**:
   - "¿qué canción está sonando?", "¿qué canción es esta?", "¿qué está sonando?"
   - Muestra en el HUD de las gafas y responde por audio TTS: "[Título] de [Artista] en [App]".
5. **Compatibilidad con Pantalla Bloqueada (Lockscreen & Trampoline)**:
   - Activación directa sin que el móvil se quede bloqueado o descarte la orden usando `SendTrampolineActivity` y `LockScreenHelper`.
6. **Agente IA con Tool Calling Nativo (Gemini / LiteLLM)**:
   - Nueva habilidad `app-media-control` con OpenAPI Schema para que el asistente resuelva peticiones complejas en lenguaje natural.

---

## 2. Arquitectura de Control de Medios en Android

### A. Doble Capa de Transporte de Control:
1. **Capa Primaria — `MediaSessionManager` + `MediaController`**:
   - `MirrorNotificationListener` ya cuenta con el permiso especial de escucha de notificaciones (`NotificationListenerService`).
   - Gracias a esto, `MediaSessionManager.getActiveSessions(ComponentName)` devuelve los `MediaController` de las aplicaciones de audio activas (NewPipe en reproducción en segundo plano, OpenTune, Spotify, VLC, RiMusic, etc.).
   - Permite:
     - `controller.transportControls.pause()` / `play()` / `skipToNext()` / `skipToPrevious()` / `stop()`
     - `controller.transportControls.playFromSearch(query, extras)`
     - `controller.metadata`: extracción precisa de `METADATA_KEY_TITLE`, `METADATA_KEY_ARTIST`, `METADATA_KEY_ALBUM` y `playbackState`.
2. **Capa Secundaria (Fallback) — `AudioManager.dispatchMediaKeyEvent`**:
   - En caso de que no haya sesión activa registrada o la app responda solo a eventos globales de hardware (`KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_MEDIA_NEXT`, etc.).

### B. Mapeo de Aplicaciones y Paquetes de Terceros:
- **NewPipe y Variantes**:
  - Paquetes soportados: `org.schabi.newpipe`, `org.schabi.newpipe.debug`, `org.polymorphicshade.tubular`, `org.blayfeed.bravenewpipe`.
  - Mecanismo de Búsqueda / Reproducción:
    - Búsqueda directa con `Intent(Intent.ACTION_SEARCH)` + `SearchManager.QUERY`.
    - Deep link web interceptable: `https://www.youtube.com/results?search_query=[QUERY]`.
- **OpenTune y Clientes Libres de YouTube Music**:
  - OpenTune: `tune.music.opentune`, `com.opentune.app`, `org.opentune.android`, `com.opentune.music`.
  - InnerTune: `com.zionhuang.music`, `com.zionhuang.innertune`.
  - RiMusic: `it.fast4x.rimusic`.
  - ViMusic: `it.fast4x.vimusic`.
  - Mecanismo: `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` con `SearchManager.QUERY` y `setPackage(...)`, más deep links directos.
- **Servicios Comerciales Populares**:
  - Spotify: `com.spotify.music`, `com.spotify.lite` (`spotify:search:[QUERY]`).
  - YouTube Music: `com.google.android.apps.youtube.music` (`https://music.youtube.com/search?q=[QUERY]`).
  - YouTube: `com.google.android.youtube`.
  - Deezer: `deezer.android.app`.
  - VLC: `org.videolan.vlc`.

---

## 3. Fases de Implementación

### Fase 1: Creación del Módulo Central de Control Multimedia (`MediaPlaybackHelper.kt`)
- Ubicación: `app/src/main/java/com/myvu/client/media/MediaPlaybackHelper.kt`
- Métodos clave:
  - `getActiveController(): MediaController?`: Obtiene la sesión activa prioritaria desde `MediaSessionManager`.
  - `getNowPlayingInfo(): NowPlayingInfo?`: Extrae título, artista, app y estado de reproducción.
  - `executePlaybackAction(action: PlaybackAction): Boolean`: Maneja `PLAY`, `PAUSE`, `NEXT`, `PREVIOUS`, `STOP`.
  - `playFromSearch(appName: String?, query: String): PlayResult`: Resuelve la app objetivo, despacha el intent de búsqueda/reproducción mediante `SendTrampolineActivity` (asegurando paso por pantalla bloqueada), y sincroniza el inicio de reproducción.
  - `searchInApp(appName: String?, query: String): PlayResult`: Abre la búsqueda directamente dentro de NewPipe, OpenTune u otra app.

### Fase 2: Integración en `PhoneActionExecutor.kt`
- Actualizar y refactorizar `playInThirdPartyApp`, `sendMediaKey`, `pauseMusic`, `resumeMusic`, `nextTrack`, `previousTrack`.
- Añadir métodos:
  - `queryNowPlaying(): String` -> Formatea texto amigable para TTS y HUD.
  - `searchInThirdPartyApp(appAndQuery: String): String` -> Ejecuta búsqueda directa en la app indicada.
- Soporte para etiquetas de acción:
  - `ACTION:MEDIA_PLAY=...`
  - `ACTION:MEDIA_SEARCH=...`
  - `ACTION:MEDIA_NOW_PLAYING`
  - `ACTION:MEDIA_NEXT`
  - `ACTION:MEDIA_PREV`
  - `ACTION:MEDIA_PAUSE`
  - `ACTION:MEDIA_RESUME`

### Fase 3: Reglas de Enrutamiento Rápido en `VoiceActionRouter.kt` (< 5ms)
- Ampliar `VoiceActionRouter.tryRoute`:
  - Enriquecer comandos de pausa, play, salto y retroceso con soporte de más variantes coloquiales ("salta", "pásala", "pon la siguiente", "para la música").
  - Fast-paths de "¿qué canción está sonando?":
    - *"qué canción está sonando"*, *"qué está sonando"*, *"qué canción suena"*, *"qué música es esta"*, *"dime la canción"*.
  - Fast-paths de reproducción por app:
    - `"reproduce [canción] en (newpipe|opentune|spotify|youtube|deezer|vlc)"`
    - `"pon [canción] en (newpipe|opentune|spotify|youtube)"`
    - `"toca [canción] en (newpipe|opentune|spotify|youtube)"`
  - Fast-paths de búsqueda en app:
    - `"busca [query] en (newpipe|opentune|spotify|youtube)"`
    - `"buscar [query] en (newpipe|opentune|spotify|youtube)"`

### Fase 4: Habilidad IA Modular con Tool Calling (`app-media-control`)
- Manifiesto: `app/src/main/assets/skills/built-in/app-media-control/SKILL.md`
- Handler: `app/src/main/java/com/myvu/client/skills/handlers/AppMediaControlHandler.kt`
- Registro en `SkillRegistry.kt`.
- Tools expuestas a Gemini / LiteLLM:
  - `media_play_search(query, target_app)`
  - `media_search_app(query, target_app)`
  - `media_playback_control(action: "play" | "pause" | "next" | "previous" | "stop")`
  - `media_get_now_playing()`

### Fase 5: Declaración de Paquetes en `AndroidManifest.xml`
- Añadir elementos `<package android:name="org.schabi.newpipe" />`, `<package android:name="tune.music.opentune" />`, etc., en la sección `<queries>` para que Android 11+ (API 30+) permita la visibilidad de paquetes sin restricciones.

### Fase 6: Pruebas y Verificación
- Pruebas unitarias en `VoiceActionRouterTest.kt`:
  - 100% de cobertura en parsing de "reproduce en newpipe", "busca en opentune", "qué está sonando", controles de transporte.
- Pruebas unitarias para `MediaPlaybackHelperTest.kt`.
- Ejecución completa de `./gradlew testDebugUnitTest`.
- Ensamblado de APK `./gradlew assembleDebug`.
- Actualización de documentación y memoria (`README.md`, `docs/ARCHITECTURE.md`, `docs/PROJECT_MEMORY.md`).
- `codegraph sync` al finalizar.
