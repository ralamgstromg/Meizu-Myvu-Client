# Plan de Mejora: Control Total Manos Libres Tipo Google Assistant y Desbloqueo Invisible

## 1. Diagnóstico del Log (14:48 - 14:50)

Del análisis exhaustivo de `/home/rcastro/Descargas/myvu_client_log.txt`:

1. **Separación de Contacto y Auto-Send Funcionaron**:
   - En línea 365: Audio `"enviar mensaje de whatsapp a matias castro hola socio como vas?"`.
   - En línea 383: [`ContactHelper`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/core/ContactHelper.kt) resolvió `"matias castro"` -> `"Matías Castro Nuevo"` y mensaje limpio `"hola socio como vas?"`.
   - En línea 422: [`AutoSendAccessibilityService`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/AutoSendAccessibilityService.kt) hizo clic exitoso: `Clicked send button (viewId='com.whatsapp:id/send', desc='enviar')`.

2. **Causa Raíz de por qué se abre la app MYVU y tarda en enviar**:
   - En línea 398 (14:49:52.490): `SendTrampolineActivity: Requesting keyguard dismissal for messaging intent`.
   - En línea 400 (14:49:52.558): `LockScreenHelper: Keyguard dismiss cancelled by user`.
   - En línea 419 (14:50:04.512): `SendTrampolineActivity: Timeout reached, attempting launch target intent directly`.
   - **Causas Técnicas Identificadas**:
     1. **Arrastre de la App MYVU al frente**: [`SendTrampolineActivity`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/SendTrampolineActivity.kt) compartía el `taskAffinity` por defecto de la aplicación. Al ser lanzada, Android trajo toda la pila de tareas de MYVU (`ConnectActivity`) al frente, haciendo que el usuario viera la app MYVU en pantalla.
     2. **Cancelación inmediata en `onCreate`**: Solicitar `requestDismissKeyguard` en `onCreate()` antes de que la ventana obtenga el foco (`onWindowFocusChanged`) provoca que Android cancele la solicitud de inmediato (`onDismissCancelled` a los 68 ms).
     3. **Espera innecesaria de 12 segundos**: Al cancelarse, la actividad no despachó el intent inmediatamente, sino que esperó 12 segundos hasta el timeout para lanzar WhatsApp.
     4. **Comportamiento sin Smart Lock**: Si el teléfono tiene PIN/huella y no está configurado con "Desbloqueo extendido / Smart Lock" para las gafas MYVU, Android exige autenticación para abrir apps de terceros (WhatsApp).

---

## 2. Arquitectura y Fases de Solución

### Fase 1: Trampolín 100% Invisible y Despacho Inmediato
- En [`AndroidManifest.xml`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/AndroidManifest.xml):
  - Asignar a [`SendTrampolineActivity`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/SendTrampolineActivity.kt):
    - `android:taskAffinity=""` (aísla por completo la actividad para que NUNCA traiga `ConnectActivity` ni la app MYVU a la pantalla).
    - `android:launchMode="singleInstance"`.
    - `android:theme="@android:style/Theme.Translucent.NoTitleBar"`.
- En [`SendTrampolineActivity.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/SendTrampolineActivity.kt):
  - Mover la solicitud de `requestDismissKeyguard` a `onWindowFocusChanged(hasFocus = true)`.
  - Si ocurre `onDismissCancelled` o `onDismissError`, despachar el intent objetivo **de inmediato** (0 ms de espera) en vez de esperar 12 segundos.
  - Al despacharse, llamar `finish()` de inmediato para desaparecer.

### Fase 2: Control Multimedia Manos Libres (Tipo Google Assistant)
- En [`VoiceActionRouter.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/VoiceActionRouter.kt) y [`PhoneActionExecutor.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/PhoneActionExecutor.kt):
  - Añadir Fast-Path por voz para control de música:
    - `"pausa la música"`, `"pausar"`, `"para la música"`, `"detén la música"` -> `sendMediaKey(KEYCODE_MEDIA_PAUSE)`.
    - `"reproduce música"`, `"reanudar"`, `"play"`, `"sigue reproduciendo"` -> `sendMediaKey(KEYCODE_MEDIA_PLAY)`.
    - `"siguiente canción"`, `"pasa la canción"`, `"siguiente"` -> `sendMediaKey(KEYCODE_MEDIA_NEXT)`.
    - `"canción anterior"`, `"anterior canción"`, `"atrás"` -> `sendMediaKey(KEYCODE_MEDIA_PREVIOUS)`.
  - Funciona 100% con el móvil bloqueado en el bolsillo sin encender pantalla ni requerir interacción.

### Fase 3: Control de Linterna (Flashlight / Torch)
- En [`PhoneActionExecutor.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/PhoneActionExecutor.kt):
  - Implementar `setFlashlight(enabled: Boolean)` usando `CameraManager.setTorchMode`.
- En [`VoiceActionRouter.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/VoiceActionRouter.kt):
  - Fast-Path para `"enciende la linterna"`, `"prende la linterna"`, `"apaga la linterna"`, `"linterna on/off"`.
  - Funciona 100% con el móvil bloqueado en el bolsillo.

### Fase 4: Control de Volumen y Modos de Sonido
- En [`VoiceActionRouter.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/VoiceActionRouter.kt) y [`PhoneActionExecutor.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/PhoneActionExecutor.kt):
  - `"sube el volumen"` / `"baja el volumen"` / `"volumen al máximo"` / `"volumen al 50%"`.
  - `"silencia el teléfono"` / `"modo silencio"` -> `RINGER_MODE_SILENT`.
  - `"pon en vibración"` -> `RINGER_MODE_VIBRATE`.
  - `"activa el sonido"` / `"modo normal"` -> `RINGER_MODE_NORMAL`.
  - Funciona 100% con el móvil bloqueado.

### Fase 5: Apertura de Cualquier Aplicación con Desbloqueo
- En [`VoiceActionRouter.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/VoiceActionRouter.kt):
  - Reconocer patrones `"abre [nombre app]"`, `"abrir [nombre app]"`, `"inicia [nombre app]"`.
  - Localizar el paquete mediante `packageManager.getLaunchIntentForPackage` y resolver nombres comunes (Spotify, YouTube, Maps, Calculadora, Cámara, Chrome, etc.).
  - Lanzar a través de `SendTrampolineActivity` despertando la pantalla y solicitando desbloqueo.

### Fase 6: Servicio de Asistente de Voz del Sistema (VoiceInteractionService)
- Crear [`MyvuVoiceInteractionService.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/MyvuVoiceInteractionService.kt):
  - Registrar en `AndroidManifest.xml` con `android.permission.BIND_VOICE_INTERACTION`.
  - Permite al usuario configurar MYVU Client como la **Aplicación de Asistente Digital Predeterminada** en los Ajustes de Android, otorgando privilegios nativos de asistente del sistema sobre la pantalla bloqueada.

---

## 3. Criterios de Éxito
1. Al pedir enviar WhatsApp con el móvil bloqueado, la app MYVU **nunca** aparece en pantalla (`taskAffinity=""`), el intent se despacha de inmediato y el envío se completa.
2. Comandos multimedia ("pausa la música", "siguiente canción") se ejecutan instantáneamente en segundo plano con el móvil bloqueado.
3. Comandos de linterna ("enciende la linterna", "apaga la linterna") operan de inmediato sin tocar el teléfono.
4. Comandos de volumen y modo silencio/vibración operan de inmediato con el móvil en el bolsillo.
5. Comandos de apertura de apps ("abre Spotify", "abre Maps") despiertan la pantalla y abren la aplicación requerida.
