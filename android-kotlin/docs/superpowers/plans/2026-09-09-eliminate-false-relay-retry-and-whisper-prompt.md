# Plan: Eliminación de Reintento Falso de Handshake en Relay, Tolerancia RFCOMM y Context Prompt en Whisper

## Objetivo
Resolver los puntos de fricción detectados en el log `myvu_client_log.txt`:
1. **Eliminar Reintento Falso de Handshake**: Cambiar la comprobación en `ConnectionManager.kt` de `rfSession?.ready != true` a `rfSession?.authConfirmed != true` para no saturar la ráfaga inicial de 29 mensajes con un reintento innecesario.
2. **Ajuste de Tolerancia y Gracia de RFCOMM**: Ampliar el timeout de establecimiento de RFCOMM de 6s a 10s para acomodar la búsqueda SDP de Android y extender la espera tras el burst BLE a 2500ms para que el demonio SPP de las gafas esté listo.
3. **Prompt de Contexto en Whisper STT**: Añadir un campo multipart `prompt` con contexto en español en `OpenAiTranscriptionClient.kt` para evitar que Whisper recorte palabras iniciales como "¿Cuál..." o "Cómo...".

## Fases de Implementación

### Fase 1: ConnectionManager.kt
- Cambiar la condición de reintento en `relayListener.onConnected`.
- Aumentar `RELAY_ESTABLISH_TIMEOUT_MS` a 10000L.
- Aumentar el retardo antes de `supervisor?.wake()` a 2500ms.

### Fase 2: OpenAiTranscriptionClient.kt
- Añadir el campo multipart `prompt` con valor `"Preguntas, comandos y consultas en español para asistente de voz en gafas inteligentes AR."`.

### Fase 3: Verificación
- Ejecutar `./gradlew testDebugUnitTest`.
- Ejecutar `./gradlew assembleDebug`.
- Documentar en `docs/PROJECT_MEMORY.md`.
- Ejecutar `codegraph sync`.
