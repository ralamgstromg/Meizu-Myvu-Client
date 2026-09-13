# Plan: Gafas MYVU Botón de Acción HUD + Auriculares Agente STT/API + Lectura de Notificaciones TTS

## Objetivo
1. **Gafas MYVU Botón de Acción**:
   - Evitar que 1 toque / pulsación corta abra Gemini por falsos positivos de doble toque o mapeos agresivos.
   - 1 toque: Ver/consultar el HUD Dashboard de las gafas (dejar pasar o no interferir con el HUD nativo).
   - Mantener presionado (Pulsación Larga / `code: 3` / `212` / `231`): Llamar al agente de las gafas (Aura / Local AI).
   - Ajustar umbrales de síntesis de doble toque en `InboundRouter` y `TouchGestureManager` (mínimo 180ms, máximo 500ms) y filtrar down/click duplicados (200 + 210).
2. **Auriculares Bluetooth y Configuración de Agente**:
   - Agregar acción `VOICE_AGENT_AURA` ("Agente de Voz Aura (STT + API)") en `CommonDeviceActions`.
   - Permitir en `HeadphoneSettingsActivity` y `GlassesSettingsActivity` seleccionar entre `VOICE_AGENT_AURA`, `LAUNCH_GEMINI`, `LAUNCH_GEMINI_LIVE`, `LAUNCH_PHONE_ASSISTANT`, etc.
   - Manejar `VOICE_AGENT_AURA` en `HeadphoneGestureManager` activando `ChatActivity` con escucha STT automática y respuesta leída por TTS.
3. **Lectura Automática de Notificaciones en Auriculares**:
   - En `MirrorNotificationListener`, verificar si hay un auricular Bluetooth activo (`BluetoothDeviceType.HEADPHONES`).
   - Si está activo y tiene `autoReadNotifications` o `ttsEnabled` activo, sintetizar y reproducir la notificación con `TextToSpeechHelper.speak()`.
   - Funcionar tanto si las gafas están conectadas como si solo los auriculares están conectados.

## Tareas Detalladas
1. **`InboundRouter.kt` & `TouchGestureManager.kt` & `GlassGesture.kt`**:
   - En `GlassGesture.kt`: mapear código 230 como `TAP` (botón de acción corto) y asegurar 231 como `LONG_PRESS`.
   - En `InboundRouter.kt`:
     - Filtrar pares 200 (down) y 210 (tap) del mismo toque para que no se sinteticen en doble toque erróneo.
     - Ajustar rango de síntesis de `DOUBLE_TAP`: ventana humana real `180L..500L` (no 30L..800L).
   - En `TouchGestureManager.kt`:
     - Ajustar `DOUBLE_TAP_MIN_INTERVAL_MS = 180L` y `DOUBLE_TAP_MAX_INTERVAL_MS = 500L`.
     - Agregar acción `HUD_DASHBOARD` / `none` que nunca dispare Gemini.
2. **`CommonDeviceActions.kt`**:
   - Agregar `VOICE_AGENT_AURA` ("Agente de Voz Aura (STT + API)").
   - Agregar `HUD_DASHBOARD` ("Ver Dashboard / HUD de Gafas").
3. **`HeadphoneGestureManager.kt` & `ChatActivity.kt`**:
   - En `HeadphoneGestureManager`: al recibir `VOICE_AGENT_AURA`, iniciar `ChatActivity` con `EXTRA_AUTO_START_STT`.
   - En `ChatActivity`: al recibir `EXTRA_AUTO_START_STT`, activar reconocimiento de voz inmediato y hablar respuesta con `TextToSpeechHelper`.
4. **`MirrorNotificationListener.kt`**:
   - No retornar anticipadamente si no hay conexión a gafas si hay auriculares activos.
   - Si hay auriculares activos (`HEADPHONES`) con lectura activada: leer con `TextToSpeechHelper.speak("De $appName: $title, $text")`.
   - Si hay gafas conectadas, mantener envío visual al HUD.
5. **Verificación y Pruebas**:
   - Ejecutar pruebas unitarias (`rtk ./gradlew testDebugUnitTest`).
   - Compilar build debug (`rtk ./gradlew assembleDebug`).
   - Ejecutar `rtk codegraph sync`.
   - Actualizar memoria y documentación (`docs/PROJECT_MEMORY.md`, `docs/ARCHITECTURE.md`, `README.md`).
