# Plan: Activar Modo Gemini Live a través del Asistente del Teléfono / Asistente Gemini

## 1. Contexto y Hallazgo Técnico

El usuario indica:
> *"Validando me doy cuenta que cuando configuro 'Asistente del Telefono (Google)' habilita el microfono y puedo interactuar correctamente. Deseo que cuando configure Gemini Live ejecute el asistente pero habilite automaticamente el modo Live del Asistente."*

### Análisis Técnico:
1. **Diferencia entre `LAUNCH_PHONE_ASSISTANT` y `LAUNCH_GEMINI_LIVE` actual**:
   - `LAUNCH_PHONE_ASSISTANT`:
     - Emite `KEYCODE_VOICE_ASSIST` (vía `am.dispatchMediaKeyEvent`) y `ACTION_VOICE_COMMAND` / `ACTION_ASSIST`.
     - Esto invoca directamente la interfaz **overlay / bottom sheet del Asistente** del teléfono (que en teléfonos con Gemini activado despliega el asistente con el micrófono ya abierto para escuchar).
   - `LAUNCH_GEMINI_LIVE` actual:
     - Intentaba abrir la app completa (`com.google.android.apps.bard`) con una Activity tradicional en lugar del overlay del Asistente del sistema.
     - En muchos dispositivos (especialmente bloqueados o con launchers personalizados), la app completa se abre en la pantalla principal pero el overlay del Asistente con el botón de Live/Micrófono es el que realmente reacciona a los comandos del sistema (`KEYCODE_VOICE_ASSIST` / `ACTION_ASSIST`).

2. **Doble Disparo y Transición a Live**:
   - Al invocar el Asistente del teléfono (con `KEYCODE_VOICE_ASSIST` y `ACTION_VOICE_COMMAND` o `ACTION_ASSIST` de igual forma que `launchPhoneAssistant`), el Asistente de Google / Gemini se despliega de inmediato.
   - Si la acción seleccionada es **Gemini Live** (`LAUNCH_GEMINI_LIVE`):
     - Activamos el temporizador y el modo `isGeminiLiveActive = true` en `AutoSendAccessibilityService`.
     - Invocamos el Asistente con el mecanismo confiable que funciona en el móvil (`KEYCODE_VOICE_ASSIST` / `ACTION_VOICE_COMMAND` / `ACTION_ASSIST` + fallback explícito a `com.google.android.apps.bard`).
     - Al levantarse el overlay del Asistente, `AutoSendAccessibilityService` busca de inmediato el botón de **Gemini Live** (icono de waveform / chispas / "Live") tanto en la app como en el overlay de `googlequicksearchbox` y realiza el clic automático (dual: accesibilidad + coordenadas).
     - Se mantiene el canal de audio SCO abierto continuamente para la conversación fluida.

---

## 2. Cambios Propuestos

### A. `TouchGestureManager.kt`
- En `launchGeminiAssistant(context, isLive)`:
  - Cuando `isLive == true`:
    - Despertar la pantalla (`LockScreenHelper.wakeUpScreen`).
    - Activar el servicio de accesibilidad para Live (`AutoSendAccessibilityService.triggerGeminiLiveAutoStart`).
    - Enrutar el micrófono Bluetooth SCO de las gafas (manteniéndolo continuo para la conversación).
    - Despachar `KEYCODE_VOICE_ASSIST` (igual que en `launchPhoneAssistant`), para que el sistema abra de inmediato el Asistente del teléfono en primer plano.
    - Como fallback adicional rápido, lanzar `Intent.ACTION_VOICE_COMMAND` / `Intent.ACTION_ASSIST` y la app `com.google.android.apps.bard` a través de `SendTrampolineActivity`.

### B. `AutoSendAccessibilityService.kt`
- Ampliar los selectores y la compatibilidad de ventanas en `findAndClickGeminiLiveButton`:
  - Permitir que el botón de Live se detecte no solo si el paquete es `bard`, sino en la ventana overlay del asistente (`com.google.android.googlequicksearchbox`).
  - Añadir selectores adicionales comunes en la barra del Asistente de Gemini:
    - Textos / ContentDescriptions: `"live"`, `"gemini live"`, `"iniciar live"`, `"hablar en directo"`, `"conversación en tiempo real"`, `"modo conversación"`, `"open live"`, `"start live"`, `"live mode"`, `"waveform"`, `"onda"`.
    - IDs: `live_button`, `btn_live`, `gemini_live`, `waveform`, `sparkle`, `voice_mode`, `assistant_live`, `chat_live_button`.
  - Asegurar que `scheduleBurstGeminiLiveRetries` comience de inmediato con ráfaga acelerada (100ms, 250ms, 500ms...) para pulsar el botón en cuanto aparezca el overlay del asistente.

---

## 3. Plan de Verificación
1. Ejecutar `./gradlew testDebugUnitTest` para asegurar que todas las pruebas pasen (cero regresiones).
2. Ejecutar `rtk codegraph sync`.
3. Actualizar `PROJECT_MEMORY.md`, `ARCHITECTURE.md` y `README.md`.
