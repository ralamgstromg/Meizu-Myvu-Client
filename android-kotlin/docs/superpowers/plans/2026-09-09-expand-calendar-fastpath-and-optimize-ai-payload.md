# Plan: Expansión de Fast-Path de Calendario (Singular/Plural) y Optimización de Carga Útil en IA Agéntica

## Diagnóstico del Log `myvu_client_log.txt`
1. **Éxitos rotundos**:
   - RFCOMM conectó al primer intento en 459ms (sin error `read ret: -1`, sin timeout de 6s).
   - Ráfaga de 29 mensajes del relay sin interrupción (eliminado reintento falso de ability).
   - Whisper transcribió las palabras iniciales completas gracias al prompt de contexto ("¿Cuál es...", "¿Tengo alguna...").
2. **Problema crítico detectado**:
   - Consultas *"¿Tengo alguna reunión hoy por la tarde?"* y *"Tengo alguna reunión el día de hoy."* cayeron al endpoint remoto de LiteLLM (`soft-ia.co`) y tardaron 45 segundos en dar `SocketTimeoutException`.
   - **Causa**: `VoiceActionRouter.kt` tenía `normalized.contains("reunione")` (solo plural "reuniones"). Al preguntar en singular ("reunión"), no entró al Fast-Path local de calendario que tarda 200ms.
   - **Sobrecarga Agéntica**: Al caer al agente, `AiConversation.kt` enviaba el prompt textual de 30 skills (5.000 chars) MÁS los 30 esquemas JSON nativos de herramientas (10.000 chars), totalizando 15.378 caracteres que saturaron el servidor LiteLLM.

## Mejoras a Implementar

### Fase 1: VoiceActionRouter.kt
- Expandir coincidencia de calendario a singular y plural: `"reunion"`, `"agenda"`, `"calendario"`, `"evento"`, `"cita"`, `"compromiso"`, y patrones naturales como `"que tengo hoy"`, `"tengo algo para hoy"`, etc.

### Fase 2: AiConversation.kt
- Si el proveedor soporta `supportsToolCalling()`, no adjuntar el addendum textual de skills al system prompt; solo pasar `basePrompt` ya que las herramientas viajan de forma nativa como esquemas JSON.

### Fase 3: AiHttpClient.kt
- Reducir `LOCAL_READ_TIMEOUT_MS` de 45.000ms a 20.000ms para evitar bloqueos prolongados en las gafas ante endpoints remotos caídos.

### Fase 4: Pruebas y Verificación
- Añadir pruebas unitarias en `VoiceActionRouterTest.kt`.
- Compilar y verificar con `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.
- Sincronizar memoria y codegraph.
