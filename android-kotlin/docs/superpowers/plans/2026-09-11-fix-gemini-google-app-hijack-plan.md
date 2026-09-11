# Plan: Corrección del Secuestro de Gemini y Gemini Live por la App de Google

## 1. Diagnóstico del Log (`/home/rcastro/Descargas/myvu_client_log.txt`)

En los registros más recientes del log se identificó exactamente por qué se abría la app de Google en lugar de Gemini / Gemini Live:

```text
08:30:14.103  Touchpad gesture received (DOUBLE_TAP, code=211) -> Action: launch_gemini (LAUNCH_GEMINI)
08:30:14.188  Dispatched KEYCODE_VOICE_ASSIST
08:30:14.251  Launched active voice assistant via ACTION_VOICE_COMMAND
08:30:14.859  SendTrampolineActivity: Fallback onResume dismissal trigger
08:30:15.108  SendTrampolineActivity: Target intent launched successfully
08:30:16.185  AutoSendAccessibilityService -> Clicked Gemini Voice mic button (viewId='com.google.android.googlequicksearchbox:id/googleapp_search_widget_ghost_voice_search', desc='', text='')
```

### Causas Raíz Identificadas:
1. **`ACTION_VOICE_COMMAND` sin paquete explícito**:
   - `Intent(Intent.ACTION_VOICE_COMMAND)` es resuelto por el sistema operativo hacia `com.google.android.googlequicksearchbox` (la app de Google / Google Assistant antiguo), **NO** hacia Gemini (`com.google.android.apps.bard`).
   - Al tener `return` inmediato si resolvía la actividad, el código **nunca llegaba a lanzar `com.google.android.apps.bard`**.
2. **Emisión de `KEYCODE_VOICE_ASSIST` al `AudioManager`**:
   - Se disparaba `am.dispatchMediaKeyEvent(KeyEvent(..., KEYCODE_VOICE_ASSIST, ...))` incondicionalmente.
   - Android intercepta este evento de hardware a nivel de sistema y abre inmediatamente el asistente del sistema (Google App), compitiendo o cubriendo la pantalla antes de que `SendTrampolineActivity` levante la app deseada.
3. **Servicio de Accesibilidad sin validación de paquete y con inclusión de `googlequicksearchbox`**:
   - En `AutoSendAccessibilityService.kt`:
     `val isTargetGemini = target == null || pkg == target || pkg.contains("bard") || pkg.contains("googlequicksearchbox")`
     ¡Se consideraba a `googlequicksearchbox` como Gemini válido!
   - `findAndClickGeminiMicButton` busca `"voice_search"` en `targetMicIds`.
   - Cuando el teléfono se desbloquea, la pantalla activa durante los primeros milisegundos sigue siendo el Launcher de Android (pantalla de inicio), donde está el widget de Google: `com.google.android.googlequicksearchbox:id/googleapp_search_widget_ghost_voice_search`.
   - El servicio de accesibilidad lo encontraba y le hacía clic, abriendo la búsqueda de Google en lugar de esperar a que cargue Gemini.

---

## 2. Acciones Propuestas

### A. En `TouchGestureManager.kt`:
1. **Priorizar SIEMPRE el intent explícito de Gemini (`com.google.android.apps.bard`)**:
   - Obtener `packageManager.getLaunchIntentForPackage("com.google.android.apps.bard")`.
   - Si la app de Gemini está instalada, lanzarla **siempre y directamente** a través de `SendTrampolineActivity.launchWithKeyguardDismiss`.
   - Dejar `ACTION_VOICE_COMMAND` y `ACTION_ASSIST` únicamente como **fallback final** en caso de que Gemini no esté instalado.
2. **Eliminar `KEYCODE_VOICE_ASSIST` de `launchGeminiAssistant`**:
   - No emitir `am.dispatchMediaKeyEvent(KEYCODE_VOICE_ASSIST)` cuando el usuario solicita Gemini o Gemini Live.
   - Mantener `KEYCODE_VOICE_ASSIST` únicamente en `launchPhoneAssistant` (acción `LAUNCH_PHONE_ASSISTANT`).

### B. En `AutoSendAccessibilityService.kt`:
1. **Restringir estrictamente a `com.google.android.apps.bard`**:
   - Eliminar `pkg.contains("googlequicksearchbox")` de `isTargetGemini`.
   - En `findAndClickGeminiMicButton` y `findAndClickGeminiLiveButton`, verificar que el `packageName` del nodo (o del árbol) coincida exactamente con `"com.google.android.apps.bard"`.
   - Ignorar explícitamente cualquier elemento que pertenezca a `com.google.android.googlequicksearchbox` o launchers del sistema (`com.google.android.apps.nexuslauncher`, etc.).
2. **En las ráfagas de reintentos (`scheduleBurstGeminiVoiceRetries` y `scheduleBurstGeminiLiveRetries`)**:
   - Verificar `root.packageName?.toString() == "com.google.android.apps.bard"`.
   - Si la ventana activa todavía es el launcher o la pantalla de bloqueo, ignorar el intento y esperar al siguiente tick programado (100ms, 250ms, 500ms, etc.) hasta que Gemini esté efectivamente en primer plano.

---

## 3. Plan de Pruebas y Verificación

1. **Pruebas Unitarias**:
   - Actualizar/crear pruebas en `TouchGestureManagerTest.kt` para verificar que `launchGeminiAssistant` prioriza `com.google.android.apps.bard` y no despacha `KEYCODE_VOICE_ASSIST`.
   - Ejecutar suite de pruebas completa: `rtk ./gradlew testDebugUnitTest`.
2. **Sincronización y Memoria**:
   - Ejecutar `rtk codegraph sync`.
   - Actualizar `docs/PROJECT_MEMORY.md`, `README.md` y `docs/ARCHITECTURE.md`.
