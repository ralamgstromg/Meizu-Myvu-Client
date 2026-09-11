# Plan: Restaurar Clic Automático de Micrófono y Gemini Live sin Afectar Widgets

## 1. Diagnóstico del Log (`/home/rcastro/Descargas/myvu_client_log.txt`)

En el log se observa:
```text
08:45:55.388  Touchpad gesture received (LONG_PRESS, code=212) -> Action: gemini_live (LAUNCH_GEMINI_LIVE)
08:45:55.407  AutoSendAccessibilityService -> Triggered Gemini Live auto-start (duration=45000ms, ready=true)
08:45:55.522  Launched Gemini app (com.google.android.apps.bard) via SendTrampolineActivity (isLive=true)
08:45:56.353  AutoSendAccessibilityService -> Device unlocked (USER_PRESENT) detected! Bursting auto-send retries
(Fin: la app abre, pero el botón Live o el botón de Micrófono nunca es presionado)
```

### Causa Raíz:
- La aplicación Google Gemini (`com.google.android.apps.bard`) es un wrapper/alias en Android. Su interfaz visual real, ventanas y actividades se ejecutan bajo el proceso y paquete de la plataforma Google: **`com.google.android.googlequicksearchbox`**.
- Al haber bloqueado `com.google.android.googlequicksearchbox` en el commit anterior para evitar tocar el widget de búsqueda del escritorio, **se bloqueó también la propia interfaz de Gemini**.
- En consecuencia:
  1. `onAccessibilityEvent` descartaba todos los eventos de la ventana de Gemini porque `pkg.contains("googlequicksearchbox")`.
  2. `scheduleBurstGeminiLiveRetries` y `scheduleBurstGeminiVoiceRetries` rechazaban la ventana activa porque `rootPkg` no era `bard`.
  3. `findAndClickGeminiLiveButton` y `findAndClickGeminiMicButton` hacían `return false` de inmediato.

---

## 2. Solución Propuesta

Diferenciar con precisión quirúrgica la **interfaz real de Gemini** del **widget de búsqueda del Launcher**, permitiendo que el servicio de accesibilidad actúe dentro de Gemini:

### A. Permitir `com.google.android.googlequicksearchbox` solo cuando NO sea un widget ni el launcher:
1. En `onAccessibilityEvent`:
   - Aceptar eventos donde `pkg` sea `com.google.android.apps.bard` O `com.google.android.googlequicksearchbox`.
   - Descartar si `pkg` contiene `"launcher"` (ej. `nexuslauncher`, `launcher3`, `sec.android.app.launcher`).
2. En las ráfagas de reintentos (`scheduleBurstGeminiLiveRetries` y `scheduleBurstGeminiVoiceRetries`):
   - Inspeccionar ventanas cuyo paquete sea `com.google.android.apps.bard` O `com.google.android.googlequicksearchbox` (siempre que no contenga `"launcher"`).
   - Ampliar la ventana de ráfagas hasta 12.5 segundos: `150ms, 350ms, 700ms, 1200ms, 1800ms, 2500ms, 3500ms, 5000ms, 7000ms, 9500ms, 12500ms`.

### B. Filtro Antirruido en `findAndClickGeminiMicButton`:
1. **Descartar explícitamente widgets y cajas de búsqueda de Google**:
   - Descartar cualquier nodo con `viewId` que contenga `widget`, `search_widget`, `ghost_voice`, `searchbox`, `search_plate` o `search_edit`.
   - Descartar cualquier nodo cuya descripción o texto contenga `buscar` o `search` (el widget de Google siempre dice *"Búsqueda por voz"* o *"Voice search"*).
2. **Detectar el micrófono de entrada de Gemini**:
   - Identificar selectores legítimos de Gemini: `"usar el micrófono"`, `"use microphone"`, `"micrófono"`, `"microphone"`, `"entrada de voz"`, `"voice input"`, `"hablar"`, `"dictar"`.
   - Identificar viewIds del chat de Gemini: `mic`, `microphone`, `sparkle_mic`, `chat_input_voice_button`, `text_input_voice_icon`.
   - Si `ACTION_CLICK` no responde (Jetpack Compose), ejecutar clic por coordenadas en el centro geométrico del botón (`dispatchGesture`).

### C. Detección en `findAndClickGeminiLiveButton`:
1. Confirmar selectores de Gemini Live:
   - `desc`: `"open gemini live"`, `"gemini live"`, `"live"`, `"iniciar live"`, `"conversación live"`, `"start live"`.
   - `viewId`: `live_button`, `btn_live`, `gemini_live`, `live`, `waveform`, `sparkle`, `voice_sheet_live_entrypoint`.
   - Clic directo en el nodo o fallback por coordenadas táctiles.
2. Descartar si el nodo o root pertenece al launcher.

---

## 3. Verificación
1. Ejecutar pruebas unitarias: `rtk ./gradlew testDebugUnitTest`.
2. Ejecutar `rtk codegraph sync`.
3. Actualizar memoria y documentación (`docs/PROJECT_MEMORY.md`, `docs/ARCHITECTURE.md`, `README.md`).
