# Plan: Optimización Integral de Gestos de Patilla Táctil, Modo Escucha en Gemini y Gemini Live Continuo

## 1. Contexto y Objetivos del Usuario
1. **Gemini (Asistente / App)**:
   - Debe lanzar Gemini directamente en **modo escucha** (esperando dictado por voz).
   - Debe activar automáticamente el **micrófono de las gafas Meizu MYVU** (enrutamiento de audio Bluetooth SCO / Communication Device).
   - Debe **activar y desbloquear la pantalla del móvil** para responder a la solicitud.
2. **Gemini Live (Conversación Continua)**:
   - Debe lanzar Gemini Live en **modo escucha continua** (activando el botón de Live / onda de voz).
   - Debe activar el **micrófono de las gafas** y mantenerlo activo durante **toda la conversación** (sin cortarse a los 4.5 segundos).
   - Debe mantener la pantalla encendida y desbloqueada para sostener la interacción bidireccional continua.
3. **Mejora en la Detección de Gestos del Touchpad**:
   - Resolver la falta de reconocimiento en toques y deslizamientos que obliga a repetir los gestos múltiples veces.
   - Eliminar el filtrado erróneo de deslizamientos (micro-swipes) que descartaba swipes legítimos.
   - Eliminar el descarte de eventos con `down_or_up == 0`.
   - Ajustar ventanas temporales (debounce a 200ms, ventana de doble toque 30ms..800ms, y síntesis de triple toque).

---

## 2. Diagnóstico Técnico y Causas Raíz

### 2.1 Enrutamiento del Micrófono de las Gafas (Bluetooth SCO)
- **Problema**: En `Prefs.kt:659`, `isGeminiForceScoEnabled` estaba en `false` por defecto. En consecuencia, `TouchGestureManager.kt:264` omitía por completo `setCommunicationDevice(btScoDevice)`. El teléfono abría Gemini usando el micrófono interno del móvil (en el bolsillo o mesa), ignorando el micrófono de las gafas.
- **Problema en Gemini Live**: El temporizador de liberación de SCO (`GEMINI_SCO_CAPTURE_WINDOW_MS = 4500L`) cortaba el micrófono a los 4.5 segundos. En una conversación continua de Gemini Live, esto apagaba el micrófono en medio del diálogo.

### 2.2 Entrada en Modo Escucha (Voice Listening) de Gemini
- **Problema**: `getLaunchIntentForPackage("com.google.android.apps.bard")` abría la app en modo de texto estático (esperando teclado), no en modo de captura de voz.
- **Solución**:
  1. Si se invoca Gemini asistente: lanzar `Intent(Intent.ACTION_VOICE_COMMAND)` con `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP`, lo cual despierta el overlay oficial de Gemini en escucha de voz. Si se abre la app dedicada `com.google.android.apps.bard`, el servicio de accesibilidad detecta y pulsa el botón de micrófono/búsqueda por voz.
  2. Si se invoca Gemini Live: abrir `com.google.android.apps.bard`, desbloquear pantalla y `AutoSendAccessibilityService` pulsa el botón Live (waveform) para entrar de inmediato a conversación en directo.

### 2.3 Fallas de Detección en la Patilla Táctil
1. **Filtro de Micro-Swipes Invertido (`InboundRouter.kt:493-516`)**:
   - Cuando el usuario deslizaba el dedo por la patilla, la patilla registraba primero el contacto inicial (`TAP` 200 o 210) seguido del movimiento (`SWIPE` 201 o 206) con <50ms de diferencia.
   - El código descartaba el `SWIPE` llamándolo "parasitario" y dejaba el `TAP`. Como `TAP` tenía acción `NONE`, el gesto era completamente ignorado.
   - **Corrección**: Si coinciden en tiempo un `TAP` y un `SWIPE`, se descarta el `TAP` (contacto previo) y se preserva el `SWIPE` intencional.
2. **Descarte de `down_or_up == 0` (`InboundRouter.kt:353`)**:
   - Cualquier evento de toque o pulsación con `down_or_up == 0` era descartado preventivamente.
   - **Corrección**: Procesar los eventos de hardware reconocidos por `GlassGesture.fromCode`, aprovechando la deduplicación por timestamp en lotes.
3. **Falta de Síntesis en Lotes sin `eventTime` (`InboundRouter.kt:526`)**:
   - Si los eventos no incluían timestamp explícito en el JSON, `dt` era `-1L` y fallaba la condición `dt in 50L..700L`.
   - **Corrección**: Si dos toques vienen en el mismo lote, asumir cercanía temporal y sintetizar doble toque.
4. **Debounce excesivo (350ms)**:
   - Reducir `DEBOUNCE_MS` a 200ms para permitir gestos rápidos sucesivos (doble track-skip, ajustes de volumen o toques ágiles).
5. **Ventana de Doble Toque**:
   - Ajustar rango de `DOUBLE_TAP_MIN_INTERVAL_MS = 30L` a `DOUBLE_TAP_MAX_INTERVAL_MS = 800L`.
   - Agregar soporte para síntesis de `TRIPLE_TAP` cuando se detectan 3 toques consecutivos.

---

## 3. Plan de Implementación Paso a Paso

### Fase 1: Optimización del Reconocimiento de Gestos (`InboundRouter.kt` y `TouchGestureManager.kt`)
- **Archivo**: `app/src/main/java/com/myvu/client/app/InboundRouter.kt`
  - Eliminar el descarte ciego de `downOrUp == 0`.
  - Corregir el filtro de concurrencia: si hay `SWIPE` y `TAP` simultáneos, conservar el `SWIPE` y descartar el `TAP`.
  - En la síntesis de `DOUBLE_TAP` en lote: si `dt == -1L` pero vienen juntos en el mismo lote, sintetizar `DOUBLE_TAP`.
  - Permitir síntesis de `DOUBLE_TAP` sin requerir igualdad estricta de `sender` si `sender` es 1 o 2.
  - Añadir síntesis de `TRIPLE_TAP` cuando se detectan 3 `TAP` en el lote.
- **Archivo**: `app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt`
  - Ajustar constantes:
    - `DEBOUNCE_MS = 200L`
    - `DOUBLE_TAP_MIN_INTERVAL_MS = 30L`
    - `DOUBLE_TAP_MAX_INTERVAL_MS = 800L`
  - Implementar acumulador de toques triples: si se recibe un 3er `TAP` dentro de los 800ms, despachar `TRIPLE_TAP`.

### Fase 2: Enrutamiento Confiable de Micrófono de Gafas (Bluetooth SCO / HFP)
- **Archivo**: `app/src/main/java/com/myvu/client/core/Prefs.kt`
  - Cambiar valor por defecto de `isGeminiForceScoEnabled` a `true` para que el micrófono de las gafas se active automáticamente al invocar Gemini / Gemini Live.
- **Archivo**: `app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt`
  - En `launchGeminiAssistant`:
    - Activar siempre `setCommunicationDevice(btScoDevice)` o `startBluetoothSco()`.
    - **Para Gemini normal (Asistente)**: Mantener ventana de captura de 8 segundos (o auto-liberación tras dictado) para restaurar canal estéreo A2DP al responder.
    - **Para Gemini Live (Conversación continua)**: **NO** aplicar auto-liberación corta de 4.5s. Mantener el canal de comunicación abierto de forma continua para streaming bidireccional de voz durante toda la sesión.

### Fase 3: Activación Directa en "Modo Escucha" y Desbloqueo de Pantalla
- **Archivo**: `app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt`
  - Despertar pantalla con `LockScreenHelper.wakeUpScreen(appContext, "MYVU:GeminiVoiceAssistant", 30000L)`.
  - Descartar pantalla de bloqueo/keyguard mediante `SendTrampolineActivity.launchWithKeyguardDismiss`.
  - **Lanzamiento de Gemini Asistente en modo escucha**:
    - Priorizar intent `Intent(Intent.ACTION_VOICE_COMMAND)` con flags `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP`.
    - Disparar `KEYCODE_VOICE_ASSIST`.
    - Activar disparador de accesibilidad `triggerGeminiVoiceMicAutoClick()` para pulsar el micrófono si la app completa de Gemini se abre al frente.
  - **Lanzamiento de Gemini Live**:
    - Abrir `com.google.android.apps.bard` con keyguard dismiss.
    - Activar `triggerGeminiLiveAutoStart()` en `AutoSendAccessibilityService` para pulsar el botón de onda de voz (Live) de inmediato.

### Fase 4: Automatización de Interfaz en `AutoSendAccessibilityService`
- **Archivo**: `app/src/main/java/com/myvu/client/service/AutoSendAccessibilityService.kt`
  - Añadir soporte para clic automático en el botón de micrófono de dictado de Gemini (`triggerGeminiVoiceMicAutoClick`).
  - Robustecer `findAndClickGeminiLiveButton` con selectores ampliados (waveform, sparkle, live, botón de micrófono, etc.).

### Fase 5: Pruebas Unitarias y Verificación
- **Archivo**: `app/src/test/java/com/myvu/client/app/InboundGestureTest.kt` y `TouchGestureManagerTest.kt`:
  - Verificar que swipes concurrentes con taps no sean eliminados.
  - Probar síntesis de doble y triple toque con nuevas ventanas temporales.
  - Probar enrutamiento de audio para Gemini y Gemini Live.
- Ejecutar `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.

### Fase 6: Documentación y CodeGraph
- Actualizar `docs/PROJECT_MEMORY.md`, `README.md` y `docs/ARCHITECTURE.md`.
- Ejecutar `codegraph sync`.
