# Plan: Corrección Integral de STT, Supresión de Gestos Parásitos en Grabación y Optimización de Latencia en Gafas

> **Fecha**: 2026-10-02  
> **Autor**: Kog (Caveman Assistant)  
> **Objetivo**: Corregir la falla donde el STT no se refleja o se interrumpe en las gafas, provocada por disparos de gestos parásitos (`MEDIA_NEXT`) durante el habla que ocultan el HUD de IA, truncamiento excesivo en `AudioOptimizer`, retardo artificial en `sendGrowingCaption` que dispara el timeout "Just a moment, please", y desconexión del proveedor nativo de Android en `SttProvider` / `Prefs`.

---

## 1. Diagnóstico Forense del Log (`/home/rcastro/Descargas/myvu_client_log.txt`)

1. **Interrupción del HUD de IA por Gesto Parásito (`MEDIA_NEXT`)**:
   - A las `18:00:41.375`, el usuario pulsó el botón físico (`code: 3`). La app inició escucha (`AI listening`).
   - A las `18:00:42.472`, se detectó habla (`speech detected`, `code: 104`).
   - A las `18:00:42.641` (solo 169ms después), mientras el usuario hablaba o sostenía la montura, las gafas enviaron `key_code: 201` (`SWIPE_FORWARD`).
   - Como `PHYSICAL_BUTTON_SUPPRESSION_MS` era solo de 1200ms, expiró exactamente a los 1270ms.
   - `TouchGestureManager` procesó `SWIPE_FORWARD` -> ejecutó `MEDIA_NEXT` y envió `SHOW_NOTIFICATION` ("MYVU - Media Next") por BLE al HUD de las gafas.
   - En el firmware Flyme XR, recibir una notificación interrumpe y oculta la pantalla de asistencia de voz ("Escuchando..."), haciendo que el usuario vea "Media Next" en vez de su transcripción.
2. **Distorsión Acústica en Transcripción ("hará" -> "era")**:
   - `AudioOptimizer`: `SILENCE_THRESHOLD_SHORT = 350` y el filtro paso alto recortaron fonemas suaves iniciales. Whisper transcribió *"¿Qué temperatura era mañana en Barranquilla?"* en lugar de *"¿Qué temperatura hará mañana en Barranquilla?"*.
   - El prompt previo de Whisper carecía de vocabulario de contexto meteorológico y formas verbales comunes en español ("hará", "habrá", "clima", "pronóstico").
3. **Retardo Artificial en `sendGrowingCaption` y Timeout de Gafas ("Just a moment, please")**:
   - Al recibir la transcripción, `sendGrowingCaption` dividía el texto en palabras y esperaba `180ms` por palabra antes de invocar `askAi(text)`.
   - Para 7 palabras, esto agregó 1.3 segundos de retraso inútil antes de consultar el clima/IA.
   - Por esta demora, las gafas Meizu MYVU alcanzaron su timeout interno a las `18:00:50.925` y emitieron `com.upuphone.ai.ttsengine` con `read: "Just a moment, please"`.
   - `InboundRouter` reprodujo ese texto en inglés por TTS.
4. **Desconexión de `SttProvider` y STT Nativo de Android**:
   - `Prefs.sttProvider()` reescribía `"android"` a `"local"`.
   - `SttProvider` no tenía la opción `ANDROID` y su propiedad `isNative` era siempre `false`.
   - `VoiceNoteRecorder` no activaba el recognizer nativo.

---

## 2. Cambios Propuestos

### Fase 1: Blindaje contra Gestos y Notificaciones durante Conversación IA
- **`AiConversation.kt`**: Exponer `AiConversation.isActive(): Boolean` para saber en tiempo real si hay una conversación de IA escuchando o respondiendo.
- **`GlassesEventHandler.kt` y `TouchGestureManager.kt`**:
  - Si `AiConversation.isActive()` es verdadero, suprimir los gestos táctiles (swipes, taps) para que no cambien canciones, ni abran apps, ni envíen notificaciones que pisen el HUD de IA mientras el usuario habla.
  - Aumentar `PHYSICAL_BUTTON_SUPPRESSION_MS` de 1200ms a 3000ms.
- **`MirrorNotificationListener.kt`**:
  - No proyectar notificaciones en el HUD de las gafas si `AiConversation.isActive()` está en curso, para no tapar la interfaz de voz.

### Fase 2: Optimización de Audio y Prompt de Whisper
- **`AudioOptimizer.kt`**:
  - Reducir `SILENCE_THRESHOLD_SHORT` de 350 a 180 para no cercenar sílabas iniciales suaves.
  - Ampliar el padding de inicio a 3200 samples (200ms) para garantizar que consonantes y sílabas iniciales no se pierdan.
- **`OpenAiTranscriptionClient.kt`**:
  - Enriquecer el prompt contextual de Whisper en español: `"Transcripción precisa de consultas, preguntas y comandos en español: clima, tiempo, temperatura que hará o habrá, mensajes, llamadas, notas y recordatorios."`

### Fase 3: Eliminación de Latencia en `AiConversation` y Supresión de Hint en Inglés
- **`AiConversation.kt`**:
  - Despachar `askAi(text)` de inmediato al obtener la transcripción, sin esperar que termine el bucle de animación de palabras en el HUD.
  - Reducir `CAPTION_WORD_MS` de 180ms a 70ms para un refresco visual fluido.
  - Enviar el resultado final de ASR (`type: 1`) tanto con `"id"` como con `"sessionId"` para máxima compatibilidad con el firmware.
- **`InboundRouter.kt`**:
  - Si llega un request `com.tts.assistant` con id `ai_speech_id_assistant_gpt_domain_hint` o texto `"Just a moment, please"`, ignorarlo o sustituirlo por silencio si la respuesta de IA ya está en procesamiento, evitando el mensaje en inglés.

### Fase 4: Integración Completa de STT Android en `SttProvider` y `Prefs`
- **`SttProvider.kt`**:
  - Añadir `ANDROID("android", "Nativo de Android", "", "", false)` con `isNative = true`.
- **`Prefs.kt`**:
  - Dejar que `sttProvider()` devuelva `"android"` cuando esté seleccionado, sin sobreescribirlo a `"local"`.
- **`ChatActivity.kt`**:
  - Mejorar `launchVoiceStt()` con fallback automático a `AndroidSpeechRecognizer` si el intent del sistema falla.

---

## 3. Plan de Verificación
1. **Pruebas Unitarias**:
   - Probar que `TouchGestureManager` y `GlassesEventHandler` suprimen gestos durante `AiConversation.isActive()`.
   - Probar que `AudioOptimizer` no recorta sílabas suaves.
   - Probar que `SttProvider.fromId("android")` retorna `ANDROID` con `isNative == true`.
   - Probar `./gradlew testDebugUnitTest`.
2. **Compilación**:
   - Ejecutar `./gradlew assembleDebug` verificando éxito.
3. **Protocolo Final**:
   - `codegraph sync`.
   - Actualizar `PROJECT_MEMORY.md`, `ARCHITECTURE.md` y `README.md`.
