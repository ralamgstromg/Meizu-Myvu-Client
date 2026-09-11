# Plan: Corrección de Doble Toque para Lanzar App de Gemini y Soporte Gemini Live

## 1. Diagnóstico del Problema y Logs
Del análisis de `/home/rcastro/Descargas/myvu_client_log.txt`:
- Los eventos de toque físico (`TAP`, códigos 200 y 210) llegaban a la app pero se mapeaban a `Action: none (NONE)` o `media_play_pause`.
- `DOUBLE_TAP_MAX_INTERVAL_MS` estaba fijado en solo `450L`. En toques físicos en la patilla de las gafas Meizu MYVU con latencia BLE, el intervalo entre los dos toques suele oscilar entre 400ms y 700ms, por lo que el segundo toque era tratado como otro toque simple y se descartaba.
- `launchGeminiAssistant` intentaba lanzar `ACTION_VOICE_SEARCH_HANDS_FREE` en lugar de la aplicación oficial de Gemini (`com.google.android.apps.bard`). En Android 12+, dicho intent no abre la app de Gemini.
- No existía opción ni soporte para activar directamente "Gemini Live" (modo de conversación continua).

## 2. Objetivos
1. **Doble Toque Robusto**:
   - Ampliar la ventana de síntesis de doble toque a `700ms` (tanto en `TouchGestureManager` como en `InboundRouter`).
   - Configurar por defecto el doble toque (`touchpad_double_tap_action`) para invocar `launch_gemini`.
2. **Lanzamiento y Desbloqueo de la App de Gemini**:
   - Despertar la pantalla mediante `LockScreenHelper.wakeUpScreen`.
   - Utilizar `SendTrampolineActivity.launchWithKeyguardDismiss` para descartar el keyguard y traer la aplicación `com.google.android.apps.bard` inmediatamente al frente.
   - En caso de no tener instalada la app dedicada de Bard/Gemini, mantener cascada de fallbacks a `ACTION_ASSIST` y `ACTION_VOICE_COMMAND`.
3. **Soporte para Gemini Live**:
   - Agregar acción `LAUNCH_GEMINI_LIVE` (`gemini_live`) en `GestureAction` para que el usuario pueda seleccionarla en Ajustes para el gesto que desee (ej. Doble Toque o Pulsación Larga).
   - En `AutoSendAccessibilityService`, incorporar `triggerGeminiLiveAutoStart` que detecta la apertura de `com.google.android.apps.bard` y hace clic automático en el botón de Live (waveform / "Live" / "Gemini Live").

## 3. Fases de Implementación

### Fase 1: Mapeo de Acciones y Preferencias
- Archivo: `app/src/main/java/com/myvu/client/app/feature/GestureAction.kt`
  - Añadir `LAUNCH_GEMINI_LIVE("gemini_live", "Gemini Live (Conversación)")`.
  - Renombrar display de `LAUNCH_GEMINI` a `"Gemini (Asistente / App)"`.
  - Actualizar `fromId` para distinguir `gemini_live` de `launch_gemini`.
- Archivo: `app/src/main/java/com/myvu/client/core/Prefs.kt`
  - Cambiar valor por defecto de `touchpadDoubleTapAction` de `"media_play_pause"` a `"launch_gemini"`.

### Fase 2: Gestor de Gestos y Lanzamiento de Gemini
- Archivo: `app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt`
  - Ampliar `DOUBLE_TAP_MAX_INTERVAL_MS` a `700L`.
  - Default de `GlassGesture.DOUBLE_TAP` en `getRawActionIdForGesture` a `LAUNCH_GEMINI.id`.
  - Agregar `executeGeminiLive()` en `ActionExecutor`.
  - En `dispatchAction`: despachar `LAUNCH_GEMINI_LIVE` a `executeGeminiLive()`.
  - Actualizar `launchGeminiAssistant(context, isLive: Boolean = false)`:
    - Despertar pantalla (`LockScreenHelper.wakeUpScreen`).
    - Si `isLive`: activar `AutoSendAccessibilityService.triggerGeminiLiveAutoStart()`.
    - Obtener Launch Intent de `com.google.android.apps.bard` con flags `NEW_TASK` y `CLEAR_TOP`.
    - Lanzar mediante `SendTrampolineActivity.launchWithKeyguardDismiss`.
    - Fallbacks en cascada si la app no está instalada.
- Archivo: `app/src/main/java/com/myvu/client/app/InboundRouter.kt`
  - Ajustar ventana en batch synthesis a `50L..700L`.

### Fase 3: Soporte de Automatización en Accesibilidad para Gemini Live
- Archivo: `app/src/main/java/com/myvu/client/service/AutoSendAccessibilityService.kt`
  - Añadir `triggerGeminiLiveAutoStart(isDeviceLocked, timeoutMs)`.
  - En `onAccessibilityEvent`, si `isGeminiLiveActive` y el paquete es `com.google.android.apps.bard`, buscar y accionar el botón de Gemini Live mediante `AccessibilityNodeInfo`.

### Fase 4: Cableado en Manejadores de Conexión y UI
- Archivo: `app/src/main/java/com/myvu/client/service/ConnectionManager.kt`
  - Implementar `executeGeminiLive()` en `createGestureActionExecutor()`.
- Archivo: `app/src/main/java/com/myvu/client/app/GlassesEventHandler.kt`
  - Implementar `executeGeminiLive()` en `createActionExecutor()`.

### Fase 5: Pruebas Unitarias y Verificación
- Archivo: `app/src/test/java/com/myvu/client/app/TouchGestureManagerTest.kt`
  - Actualizar pruebas de doble toque por defecto y soporte de `LAUNCH_GEMINI_LIVE`.
- Ejecutar `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.

### Fase 6: Documentación y CodeGraph
- Actualizar `docs/PROJECT_MEMORY.md`, `README.md` y `docs/ARCHITECTURE.md`.
- Ejecutar `codegraph sync`.
