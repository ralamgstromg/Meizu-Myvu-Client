# Plan de Acción: Corrección de Hallazgos en Logs de Ejecución (Notificaciones, Colisión de Turnos, Noticias y TRM)

## 1. Diagnóstico Detallado de Hallazgos en `myvu_client_log.txt`

### Hallazgo 1: Colisión de Turno Huérfano y Carrera de TTS (`AiConversation.kt`)
- **Evidencia en Log (Líneas 253-321)**:
  - 13:50:35: Se inició Turno 1 (`sessionId=6e5df65b...`). El servidor LLM remoto (`soft-ia.co`) quedó atascado esperando respuesta.
  - 13:51:29: El usuario se impacientó y pulsó de nuevo el botón para hacer otra consulta (`sessionId=44bf4abc...`). Se registró: `AI: new press while a turn was open -- restarting`.
  - 13:51:37.249: El Turno 1 viejo falló por `SocketTimeoutException: timeout`.
  - **Fallo Crítico**: El callback de error del Turno 1 no validó si su `sessionId` seguía activo. Al estar `active = true` (por el Turno 2), adoptó el `sessionId` del Turno 2, despachó el mensaje de error *"No pude procesar la consulta con el agente."* y arrancó `TTS_REQUEST_STARTED generation=2`.
  - 13:51:37.946: El Turno 2 terminó de transcribir: *"¿A cómo está el dólar el día de hoy?"* y resolvió la respuesta en 300ms. Sin embargo, el TTS del Turno 1 abortó/sobreescribió el Turno 2 al terminar la generación 2 (`AI_TURN_FINISHED sessionId=44bf4abc...`).
- **Causa Raíz**:
  - `abandon()` no llama a `tts.stop()`.
  - `askAi`, `deliverFinal` y `deliverError` no aíslan el `sessionId` del turno que originó la petición. Los hilos en segundo plano que fallan tarde entregan sus errores a sesiones nuevas.

---

### Hallazgo 2: Consulta de Notificaciones no entró al Fast-Path y Colapsó el LLM (`VoiceActionRouter.kt`)
- **Evidencia en Log (Líneas 244-290)**:
  - 13:50:34: Whisper transcribió: *"Notificaciones tengo pendientes por leer."*
  - Omitió el Fast-Path de notificaciones y cayó al modelo remoto `soft-ia.co/litellm`, el cual estuvo 60 segundos colgado y terminó en timeout.
- **Causa Raíz**:
  - La expresión regular en `VoiceActionRouter.kt` requería verbos al inicio (`resume|leer|revisa`) seguidos inmediatamente de `notificaciones`, o la frase fija `"notificaciones pendientes"`.
  - Al decir *"Notificaciones tengo pendientes..."*, el verbo `tengo` en medio rompió la coincidencia.

---

### Hallazgo 3: Extracción Sucia de Tópicos en Búsqueda de Noticias (`ExternalInfoService.kt`)
- **Evidencia en Log (Líneas 185-206)**:
  - 13:49:43: Whisper transcribió: *"Noticias relevantes hay en Barranquilla el día de hoy."*
  - Fast-Path extrajo como tema: `"relevantes hay en Barranquilla el día"` y buscó esa cadena literal en Google News.
  - La respuesta devuelta a las gafas fue: *"Noticias de hoy (relevantes hay en Barranquilla el día): 1) Clima hoy en Colombia..."*.
- **Causa Raíz**:
  - El regex de limpieza de noticias solo quitaba prefijos como `"noticias "` y la frase `" de hoy"`, dejando partículas de relleno como `"relevantes"`, `"hay en"`, `"el día"`, estropeando la consulta y la etiqueta visual en el HUD.

---

### Hallazgo 4: Optimización de Consulta de Divisas / TRM Oficial en Colombia
- **Evidencia en Log (Líneas 368, 424)**:
  - El usuario probó tres veces la consulta del dólar en Colombia: *"¿A cómo está el dólar el día de hoy?"*, *"¿Cómo está el dólar el día de hoy en Colombia?"*, *"¿A cómo está el dólar hoy en pesos colombianos?"*.
  - `open.er-api.com` devuelve la tasa del feed general (`3,127.34 COP`).
  - Para Colombia, la fuente de verdad es la TRM oficial de la Superintendencia Financiera disponible mediante la API pública de datos abiertos (`ceyp-9c7c.json`).

---

## 2. Plan de Acción y Soluciones

### Pista 1: Aislamiento Estricto de Sesión y Silenciado de TTS en [`AiConversation.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/AiConversation.kt)
1. **Captura de `turnSessionId` Inmutable**:
   - En `askAi`, congelar `val currentTurnId = sessionId`.
   - En todos los despachos (`deliver`, `deliverFinal`, `deliverError`, `pendingWeatherResponse`, `pendingExternalSearchResponse`):
     `if (!active || currentTurnId != sessionId) return@post`
   - Si la sesión actual cambió porque el usuario presionó el botón de nuevo, descartar la respuesta o error huérfano en silencio absoluto.
2. **Parada Inmediata en `abandon()`**:
   - Agregar `try { tts.stop() } catch (_: Exception) {}` y cancelar cualquier corrutina o handler pendiente para liberar el canal de audio al instante.

### Pista 2: Fast-Path Flexible e Integral de Notificaciones en [`VoiceActionRouter.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/VoiceActionRouter.kt)
1. **Detección Ampliada de Intención de Notificaciones**:
   - Si el texto normalizado contiene `notificacion` o `notificaciones`:
     - Y contiene partículas como `tengo`, `pendientes`, `leer`, `lee`, `revisa`, `revisar`, `resumen`, `resume`, `cuales`, `que`, `hay`, `nuevas`, `recientes`, `por leer`.
   - Si contiene `mensajes pendientes`, `correos pendientes`, `chats pendientes`, `mensajes por leer`.
   - Ejecutar inmediatamente Fast-Path en **<15ms** con `MirrorNotificationListener.getUnreadSummary()`, impidiendo que caiga al LLM remoto y evitando timeouts.

### Pista 3: Limpieza Semántica de Tópicos de Noticias en [`ExternalInfoService.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/ExternalInfoService.kt)
1. **Purgado de Rellenos y Adjetivos Conversacionales**:
   - Limpiar palabras como: `relevantes`, `importantes`, `destacadas`, `principales`, `actuales`, `que hay en`, `que hay`, `hay en`, `hay`, `el dia`, `del dia`.
   - Extraer limpiamente la entidad geográfica o temática (ej. `"Barranquilla"`).
   - Generar respuesta limpia: *"Noticias de hoy (Barranquilla): 1) Titular A (Medio). 2) Titular B (Medio)..."*.

### Pista 4: Soporte para TRM Oficial de Colombia vía Superfinanciera (`ExternalInfoService.kt`)
1. **Integración con Dataset Oficial de Datos Abiertos**:
   - Para consultas de `USD -> COP` o términos como `"trm"` / `"dolar en colombia"`, intentar primero consultar `https://www.datos.gov.co/resource/ceyp-9c7c.json?$limit=1&$order=vigenciadesde%20DESC`.
   - Extraer el valor exacto vigente de la TRM oficial.
   - Si la red o el servicio falla, fallback transparente a `open.er-api.com` y `Yahoo Finance`.

### Pista 5: Pruebas Unitarias y Verificación
1. Actualizar `VoiceActionRouterTest.kt` para validar:
   - `"Notificaciones tengo pendientes por leer"`
   - `"Qué notificaciones tengo por leer"`
   - `"Tengo notificaciones pendientes"`
2. Actualizar `ExternalInfoServiceTest.kt` para validar la extracción de tópico limpio `"Barranquilla"` a partir de *"Noticias relevantes hay en Barranquilla el día de hoy"*.
3. Ejecutar `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.
