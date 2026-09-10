# Plan Detallado: Corrección de Gestos con Apps y Activación de Gemini Manos Libres desde Gafas MYVU

## 1. Diagnóstico del Problema y Análisis de Logs

### 1.1 Análisis del Log (`/home/rcastro/Descargas/myvu_client_log.txt`)
- **Sesión de pruebas (13:45:49 a 13:47:57)**:
  - El usuario ingresó a [`SettingsActivity`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/SettingsActivity.kt) y configuró acciones en los desplegables de gestos.
  - Al tocar la patilla de las gafas (13:46:08, 13:46:17, 13:46:20, 13:46:22, 13:46:39, etc.), las gafas emitieron paquetes de telemetría y eventos internos del launcher (`sync_glass_event`).
  - Sin embargo, **ninguna acción configurada se ejecutó** en el teléfono.
- **Causas Raíz Identificadas**:
  1. **Falta de soporte para lanzar aplicaciones en los gestos**:
     - [`GestureAction`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/feature/GestureAction.kt) solo contenía 10 acciones fijas de sistema (`NONE`, `LAUNCH_PHONE_ASSISTANT`, `LAUNCH_LOCAL_AI`, `MEDIA_...`, `WEATHER_SYNC`, etc.).
     - No existía la posibilidad de asignar una aplicación instalada específica (ej. WhatsApp, Spotify, Cámara, YouTube, Gemini) a un gesto táctil.
     - [`TouchGestureManager.ActionExecutor`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt) no tenía un método `executeLaunchApp(packageName)`.
  2. **Inactividad del `MediaSession` para eventos AVRCP de la patilla**:
     - En [`MyvuService.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/MyvuService.kt), el `MediaSession` se instanciaba con `isActive = true`, pero **sin un `PlaybackState` registrado** ni `MediaButtonReceiver`.
     - En Android, `AudioService` descarta o desvía los eventos de teclas multimedia de audífonos Bluetooth (como la patilla táctil de las gafas) si la sesión multimedia no tiene un `PlaybackState` activo (`STATE_PAUSED` / `STATE_PLAYING`) con acciones de transporte válidas.
  3. **Eventos `sync_glass_event` silenciados**:
     - Cuando un item de telemetría o evento de patilla no hacía match con los keycodes numéricos habituales, se retornaba `GlassGesture.UNKNOWN` y se descartaba silenciosamente sin loguear los campos completos del JSON, lo cual impedía diagnosticar variaciones en la firma del paquete del launcher.
  4. **Activación de Gemini deficiente**:
     - [`TouchGestureManager.launchPhoneAssistant()`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt) únicamente despachaba `KEYCODE_VOICE_ASSIST` al `AudioManager`.
     - Si la pantalla del teléfono estaba apagada o bloqueada, Android no encendía la pantalla, no descartaba el keyguard ni iniciaba la captura de audio por Bluetooth SCO desde el micrófono de las gafas, dejando al usuario sin respuesta visual ni auditiva en Gemini.

---

## 2. Objetivos y Alcance

1. **Gestos asociados a Aplicaciones Instaladas**:
   - Permitir asignar a cualquier gesto táctil (Toque, Doble toque, Triple toque, Deslizar adelante, Deslizar atrás, Pulsación larga) la acción de abrir cualquier app instalada en el dispositivo móvil.
   - En [`SettingsActivity`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/SettingsActivity.kt), agregar la opción de seleccionar una app mediante un selector de aplicaciones con iconos y nombres.
   - Al recibir el gesto, encender la pantalla, desbloquear/descartar keyguard mediante [`SendTrampolineActivity`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/SendTrampolineActivity.kt) y lanzar la aplicación solicitada.

2. **Integración Completa con Gemini (Encendido de Pantalla + Escucha desde Gafas + Control Total)**:
   - Al disparar la acción de Gemini (o Asistente de Teléfono) desde las gafas:
     1. Adquirir WakeLock y encender la pantalla del teléfono con brillo completo usando [`LockScreenHelper.wakeUpScreen()`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/core/LockScreenHelper.kt).
     2. Descartar keyguard para que Gemini se muestre al frente con control total de pantalla.
     3. Activar el canal de audio Bluetooth SCO (`AudioManager.startBluetoothSco()` y `setCommunicationDevice` en Android 12+) para que el micrófono de las gafas MYVU sea la entrada directa de voz hacia Gemini.
     4. Lanzar la actividad de comando de voz manos libres (`ACTION_VOICE_SEARCH_HANDS_FREE` / `ACTION_VOICE_COMMAND` / paquete `com.google.android.apps.bard`) para que Gemini abra su interfaz en modo de escucha activa.
     5. Mostrar confirmación en el HUD de las gafas: *"Gemini escuchando..."*.

3. **Recepción Robusta de Gestos AVRCP y StarryNet**:
   - En [`MyvuService`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/MyvuService.kt), configurar `PlaybackState` completo en el `MediaSession` y enlazar `MediaButtonReceiver` para capturar toques físicos de audífono Bluetooth sin que Android los descarte.
   - En [`InboundRouter`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/InboundRouter.kt), registrar diagnósticos de eventos no mapeados para depuración en vivo y ampliar nombres de gestos en español e inglés.

---

## 3. Plan de Implementación Paso a Paso

### Paso 1: Extender [`GestureAction`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/feature/GestureAction.kt) y [`Prefs`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/core/Prefs.kt) para Lanzamiento de Apps
- Modificar [`GestureAction`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/feature/GestureAction.kt):
  - Añadir soporte para identificar acciones de tipo app: formato `app:<package_name>`.
  - Crear clase de utilidad o métodos estáticos `isAppAction(actionId: String): Boolean` y `getPackageName(actionId: String): String?`.
  - Añadir acción especial `LAUNCH_APP("launch_app", "Abrir Aplicación...")`.
  - Añadir acción directa dedicada `LAUNCH_GEMINI("launch_gemini", "Lanzar Gemini (Manos Libres)")`.
- En [`Prefs.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/core/Prefs.kt):
  - Permitir guardar y recuperar `String` con el ID de acción o el paquete de la app (`app:com.spotify.music`, etc.).

### Paso 2: Implementar Lanzador Robusto de Gemini y Ruteo de Audio Bluetooth en [`TouchGestureManager`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt)
- Crear método `launchGeminiAssistant(context: Context)`:
  - Paso A: Encender pantalla con [`LockScreenHelper.wakeUpScreen(context, "MYVU:GeminiVoice", 15000L)`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/core/LockScreenHelper.kt).
  - Paso B: Iniciar Bluetooth SCO en `AudioManager` para que el micrófono de las gafas capture la voz:
    ```kotlin
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    am?.startBluetoothSco()
    am?.isBluetoothScoOn = true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val btDevice = am?.availableCommunicationDevices?.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
        if (btDevice != null) am.setCommunicationDevice(btDevice)
    }
    ```
  - Paso C: Construir intent de voz manos libres con `RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE` y flags `FLAG_ACTIVITY_NEW_TASK`.
  - Paso D: Si la app dedicada de Gemini (`com.google.android.apps.bard`) está instalada, intentar invocarla con su intent directo o a través de `ACTION_VOICE_COMMAND` apuntando a `com.google.android.googlequicksearchbox`.
  - Paso E: Si el dispositivo está bloqueado o con pantalla apagada, lanzar a través de [`SendTrampolineActivity.launchWithKeyguardDismiss(context, voiceIntent)`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/SendTrampolineActivity.kt) para desbloquear y presentar a Gemini al frente de la pantalla.
  - Paso F: Despachar `KEYCODE_VOICE_ASSIST` como trigger adicional al sistema.

### Paso 3: Soporte para Abrir Cualquier Aplicación en [`TouchGestureManager`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt) y [`ConnectionManager`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt)
- En [`TouchGestureManager.ActionExecutor`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt):
  - Añadir `fun executeLaunchApp(packageName: String)`.
  - Añadir `fun executeGeminiAssistant()`.
- En `TouchGestureManager.handleGesture()`:
  - Si la acción guardada empieza con `app:`, extraer el `packageName` y llamar `executor.executeLaunchApp(pkg)`.
  - Si la acción es `LAUNCH_GEMINI` o `LAUNCH_PHONE_ASSISTANT`, llamar `executor.executeGeminiAssistant()`.
- En [`ConnectionManager.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt):
  - Implementar `executeLaunchApp(packageName)`:
    - Encender pantalla: `LockScreenHelper.wakeUpScreen(context)`.
    - Obtener `launchIntent` de `PackageManager`.
    - Ejecutar vía `SendTrampolineActivity.launchWithKeyguardDismiss(context, launchIntent)`.
    - Enviar HUD a las gafas: `sendAction(Notifications.buildShow("MYVU", "Abriendo $appName..."))`.
  - Implementar `executeGeminiAssistant()`:
    - Llamar a `TouchGestureManager.launchGeminiAssistant(context)`.
    - Enviar HUD a las gafas: `sendAction(Notifications.buildShow("MYVU", "Gemini activo"))`.

### Paso 4: Selector de Apps en [`SettingsActivity.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/SettingsActivity.kt)
- En `wireTouchpadActions()`:
  - Cuando el usuario seleccione "Abrir Aplicación..." en el desplegable de un gesto:
    - Abrir un diálogo con la lista de aplicaciones instaladas en el teléfono (usando `packageManager.queryIntentActivities`), mostrando icono, nombre y paquete.
    - Al tocar una aplicación, guardar `app:<packageName>` en las preferencias del gesto correspondiente.
    - Actualizar el texto del desplegable con el nombre amigable de la app seleccionada (ej. *"App: Spotify"* o *"App: WhatsApp"*).
  - Al iniciar la actividad, si el valor guardado es `app:<packageName>`, resolver el nombre real de la app y mostrarlo en el desplegable.

### Paso 5: Corrección de `MediaSession` y `PlaybackState` en [`MyvuService.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/MyvuService.kt)
- Configurar un `PlaybackState` activo en el `MediaSession`:
  ```kotlin
  val state = PlaybackState.Builder()
      .setActions(
          PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
          PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
          PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_FAST_FORWARD or
          PlaybackState.ACTION_REWIND
      )
      .setState(PlaybackState.STATE_PAUSED, 0L, 1.0f)
      .build()
  session.setPlaybackState(state)
  ```
- Enlazar PendingIntent con `MediaButtonReceiver` para garantizar que Android registre la sesión como destino prioritario de eventos de botones multimedia de audífonos Bluetooth.

### Paso 6: Tests Unitarios y Verificación
- En [`TouchGestureManagerTest.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/test/java/com/myvu/client/app/TouchGestureManagerTest.kt):
  - Agregar test para resolución de acciones `app:<packageName>` y despacho a `executeLaunchApp`.
  - Agregar test para resolución de `LAUNCH_GEMINI` y despacho a `executeGeminiAssistant`.
- En [`InboundGestureTest.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/test/java/com/myvu/client/app/InboundGestureTest.kt):
  - Validar ruteo y decodificación de eventos StarryNet.
- Ejecutar suite de pruebas: `./gradlew testDebugUnitTest`.
- Compilar APK: `./gradlew assembleDebug`.
- Sincronizar codegraph: `codegraph sync`.
- Actualizar documentación: [`README.md`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/README.md), [`docs/ARCHITECTURE.md`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/docs/ARCHITECTURE.md), [`docs/PROJECT_MEMORY.md`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/docs/PROJECT_MEMORY.md).
