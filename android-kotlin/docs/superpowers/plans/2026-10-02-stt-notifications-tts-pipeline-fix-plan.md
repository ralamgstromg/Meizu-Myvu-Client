# Plan: Corrección Integral del Servicio STT, Procesamiento de Notificaciones y Lectura de Respuestas TTS del Agente

## Contexto y Diagnóstico
El usuario reporta tres fallas críticas en el sistema:
1. **El servicio de STT no está funcionando correctamente**: No transcribe adecuadamente las consultas o comandos en los diferentes puntos de entrada (pantalla de Chat, panel lateral, audio de gafas).
2. **Las notificaciones llegan a las gafas pero no se están procesando correctamente**: En el HUD de las gafas se muestra la notificación, pero cuando el firmware solicita la lectura de la notificación (`com.upuphone.ai.ttsengine` con `caller: com.tts.notification`), la aplicación descarta el mensaje y no se ejecuta la acción.
3. **Las respuestas del agente no se están leyendo**: A pesar de que el log muestra que la IA genera la respuesta y que `TTS_PLAYBACK_STARTED` se ejecuta, el audio no llega a las gafas porque el stream de audio no está asignado a `STREAM_MUSIC` / canal multimedia A2DP, provocando que se disipe en el canal interno del teléfono o sea silenciado.

---

## Causas Raíz Identificadas

### 1. STT (Speech-to-Text)
- **Tipo de dato erróneo en `RecognizerIntent`**: En [`ChatActivity.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/chat/ChatActivity.kt) y [`ChatSidebarBottomSheet.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/chat/ChatSidebarBottomSheet.kt), se pasa `Locale.getDefault()` (objeto) en vez de `Locale.getDefault().toLanguageTag()` (String) a `EXTRA_LANGUAGE`.
- **Forzado Offline en `AndroidSpeechRecognizer`**: `cachedPreferOffline` inicia en `true`, causando que en teléfonos sin el paquete offline en español de Google la llamada falle inmediatamente (códigos 11/12) y genere retardos de 8 segundos en bucle.
- **Tiempos de silencio agresivos**: 500ms y 700ms cortan al usuario antes de terminar la frase.
- **`AndroidSpeechErrorPolicy`**: `ERROR_NO_MATCH` (7) y `ERROR_CLIENT` (5) causan reintentos cíclicos en vez de terminación limpia.
- **Truncado destructivo en `AudioOptimizer`**: En [`AudioOptimizer.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/AudioOptimizer.kt), cuando el audio es suave o silencioso (`start > end`), el cálculo de recorte trunca a 100ms de audio residual, corrompiendo la entrada enviada a Whisper.

### 2. Notificaciones en Gafas
- **Omisión de IPC `com.upuphone.ai.ttsengine` en [`InboundRouter.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/InboundRouter.kt)**: Las gafas envían mensajes de solicitud de lectura (`{"caller":"com.tts.notification"}`). `InboundRouter.handle()` descarta estos paquetes sin procesarlos ni notificar a los servicios.
- **Falta de retención de última notificación**: [`MirrorNotificationListener.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/MirrorNotificationListener.kt) no preserva la última notificación formateada para ser leída por voz bajo demanda.
- **Campos de compatibilidad en JSON**: [`Notifications.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/feature/Notifications.kt) usa `"crateTime"`, debiendo incluir también `"createTime"`.

### 3. Lectura de Respuestas del Agente (TTS)
- **Falta de Parámetros de Stream en `TtsPlayer.speakSystemTts`**: [`TtsPlayer.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/TtsPlayer.kt) invoca `tts?.speak(chunk, mode, null, id)` con `params = null`. En Android 8+, esto dirige el audio al canal de accesibilidad en lugar de `STREAM_MUSIC`, impidiendo que se escuche por los parlantes A2DP de las gafas Meizu MYVU.
- **Falta de `AudioAttributes`**: Ni `TtsPlayer` ni `TextToSpeechHelper` configuran `AudioAttributes` (`USAGE_MEDIA`, `CONTENT_TYPE_SPEECH`) al inicializar `TextToSpeech`.
- **`MediaPlayer` sin `AudioAttributes`**: En `playWavBytes()`, la reproducción HTTP no asigna canal de medios.

---

## Plan de Implementación Detallado

### Fase 1: Corrección del Motor STT y Entrada de Voz
1. **`ChatActivity.kt` y `ChatSidebarBottomSheet.kt`**:
   - Corregir `EXTRA_LANGUAGE` pasando `Locale.getDefault().toLanguageTag()` (String).
   - Asegurar idioma español preferente (`"es-CO"`, `"es"`).
2. **`AndroidSpeechRecognizer.kt`**:
   - Cambiar `cachedPreferOffline` a `false` por defecto para usar reconocimiento estándar online de alta precisión.
   - Ajustar `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` a `1500L` y posiblemente completo a `1200L`.
   - Limpiar `AndroidSpeechErrorPolicy` para no iterar lenguajes cuando el error es falta de voz (`ERROR_NO_MATCH`).
3. **`AudioOptimizer.kt`**:
   - Corregir `findSpeechBounds`: si `start > end`, retornar `Pair(0, shorts.size - 1)` para preservar el audio íntegro sin recortar destructivamente.

### Fase 2: Procesamiento y Lectura de Notificaciones
1. **`MirrorNotificationListener.kt`**:
   - Mantener en memoria la última notificación enviada (`lastSpokenDigest` / `lastSpokenNotification`).
   - Exponer método público para sintetizarla bajo demanda cuando las gafas lo soliciten.
2. **`InboundRouter.kt`**:
   - Agregar método `checkTtsEngineRequest(msg: JSONObject)`.
   - Si `caller == "com.tts.notification"`, disparar la lectura de la última notificación mediante `TextToSpeechHelper.speak(digest)`.
   - Si `caller == "com.tts.assistant"`, leer el texto contenido en `"read"` si no está siendo ya reproducido.
3. **`Notifications.kt`**:
   - Asegurar tanto `"createTime"` como `"crateTime"` en el payload JSON.

### Fase 3: Enrutamiento y Reproducción de Audio TTS hacia las Gafas
1. **`TtsPlayer.kt`**:
   - En `init()`, aplicar `AudioAttributes` (`USAGE_MEDIA`, `CONTENT_TYPE_SPEECH`).
   - En `speakSystemTts()`, pasar `Bundle` con `KEY_PARAM_STREAM = AudioManager.STREAM_MUSIC`.
   - En `playWavBytes()`, configurar `AudioAttributes` con `USAGE_MEDIA` en el `MediaPlayer`.
2. **`TextToSpeechHelper.kt`**:
   - Configurar `AudioAttributes` (`USAGE_MEDIA`, `CONTENT_TYPE_SPEECH`) en `onInit()`.
   - Garantizar que todas las emisiones mantengan `STREAM_MUSIC` hacia A2DP.

### Fase 4: Pruebas y Verificación
1. Ejecutar pruebas unitarias de audio y TTS (`TtsRegressionTest`, `SpeechNormalizerTest`, etc.).
2. Agregar pruebas específicas para:
   - `AudioOptimizerTest` (validando comportamiento ante silencios y audio suave).
   - `InboundRouterTest` (validando recepción y despacho de `com.upuphone.ai.ttsengine` tanto para notificaciones como para asistente).
3. Compilar suite completa con `./gradlew testDebugUnitTest` y verificar `assembleDebug`.
4. Sincronizar `codegraph sync`, documentar en `PROJECT_MEMORY.md` y actualizar `ARCHITECTURE.md`.
