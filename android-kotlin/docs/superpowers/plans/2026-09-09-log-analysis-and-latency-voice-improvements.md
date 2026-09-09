# Plan de Solución y Mejoras Basado en Análisis del Log del Cliente Myvu

## 1. Diagnóstico Detallado del Log (`myvu_client_log.txt`)

A partir del análisis cronológico de los 350 eventos registrados entre las 11:48:53 y las 11:51:03:

### A. Éxitos Confirmados
1. **Audio Bluetooth Classic (HFP + A2DP)**: Conexión automática inmediata y sin fallos a las 11:48:56.
2. **Skills y Handlers**: 30 skills y 29 handlers inicializados correctamente.
3. **Pronóstico del Clima ("mañana")**: Fast-path detectó "mañana", consultó OpenMeteo y respondió "Para mañana en Barranquilla, Colombia se espera Tormenta eléctrica con máxima de 36°C".
4. **Resumen de Notificaciones**: Fast-path identificó "tengo notificaciones pendientes por revisar" y extrajo correctamente el mensaje pendiente.

### B. Cuellos de Botella y Defectos Detectados
1. **Latencia Excesiva en Reconocimiento de Voz (8 a 10 segundos de retraso inútil)**:
   - Al pulsar el botón de las gafas (`code=3`), la app intenta usar `AndroidSpeechRecognizer` (`STT_ANDROID_START`).
   - Falla con error 12 ("language not supported") o error 7 ("Android speech returned no text") repetidamente porque el usuario habla al micrófono de las gafas, no al del teléfono.
   - Tras reintentar 2 veces y perder 8 segundos, conmuta al stream de audio Opus de las gafas, el cual decodifica y transcribe instantáneamente (`AI heard: temperatura del día de mañana...`).
2. **Mensaje de Activación en Chino (`"唤醒成功"`)**:
   - `AiProtocol.sessionAck()` envía a las gafas `{"hasNetwork":true,"message":"唤醒成功"}` en chino mandarín.
3. **Timeout del Handshake de App Relay RFCOMM (30 segundos de espera)**:
   - A las 11:49:02 el socket RFCOMM conecta, pero la respuesta de handshake no llega de inmediato debido al burst inicial de BLE.
   - El timeout actual espera 30 segundos antes de reintentar. A las 11:49:32 reintenta y conecta en 200 ms. El visor HUD y teleprompter quedan inoperativos durante ese medio minuto.
4. **Duplicación de `DISMISS_NOTIFICATION` en ráfaga**:
   - En mensajes de WhatsApp, el temporizador de auto-dismiss y el callback `onNotificationRemoved` colisionan, enviando `DISMISS_NOTIFICATION` repetido en milisegundos (msgId 68 y 69).
5. **Fallo Gramatical en Resumen de Notificaciones**:
   - `"Tienes 1 notificaciones pendientes"` en lugar de singular ("1 notificación pendiente").

---

## 2. Plan de Acción de Mejoras

### Fase 1: Latencia Cero en Voz desde Gafas (`AiConversation.kt`)
- Detectar si la sesión fue iniciada por hardware de gafas (`triggerCode == CODE_START_VR_REQ` o hardware trigger 3) o si hay paquetes Opus activos.
- En caso de hardware de gafas o si Android STT no tiene audio directo de BT SCO, priorizar el flujo Opus de las gafas con Groq/Whisper/Gemini sin esperar el timeout de 8 segundos del micrófono del móvil.
- Reducir latencia de interacción por voz de ~11s a ~1.8s.

### Fase 2: Localización y Limpieza de Protocolo (`AiProtocol.kt`)
- Reemplazar `"唤醒成功"` por `"Escuchando..."` o `"Asistente listo"`.

### Fase 3: Aceleración de Reconexión de App Relay RFCOMM (`ConnectionManager.kt`)
- Reducir `RELAY_ESTABLISH_TIMEOUT_MS` de 30.000 ms a 6.000 ms.
- Reenviar paquete `sendAbility` a los 2.000 ms si no se ha recibido `ability reply`.

### Fase 4: Idempotencia en Notificaciones y Corrección Gramatical
- En `MirrorNotificationListener.kt`:
  - Agregar filtro de idempotencia para descartes de notificaciones con ventana de 2 segundos.
  - Corregir singular/plural en `getUnreadSummary()`: "1 notificación pendiente" vs "X notificaciones pendientes".

### Fase 5: Verificación y Compilación
- Ejecutar tests unitarios `./gradlew testDebugUnitTest`.
- Compilar APK `./gradlew assembleDebug`.
- Actualizar `docs/PROJECT_MEMORY.md` y sincronizar con `codegraph sync`.
