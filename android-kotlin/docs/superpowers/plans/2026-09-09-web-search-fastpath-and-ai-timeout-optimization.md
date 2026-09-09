# Plan de Trabajo: Fast-Path de Búsqueda Web ('En Google'), Discriminación de Timeout y Poda de Tools Agénticas

## 1. Contexto y Hallazgos del Log (`myvu_client_log.txt`)

Analizado el log operativo de 394 líneas correspondiente a la última sesión.

### Victorias Confirmadas:
1. **Conexión RFCOMM en 267ms**:
   - `12:50:27.015`: Apertura de socket RFCOMM.
   - `12:50:27.282`: Conexión exitosa (**267 milisegundos**).
   - `12:50:27.356`: Respuesta de handshake `authConfirmed = true` en **74ms**. Cero timeouts, cero errores `read ret: -1`.
2. **Ráfaga de Inicialización (29 mensajes) Limpia**:
   - Mensajes procesados del `msgId=66` al `msgId=94` sin interrupción errónea de timer de reintento.
3. **Fast-Path Clima (Query 1 - `12:51:39`)**:
   - *"¿Cuál es el pronóstico del clima para mañana en Barranquilla?"* -> resuelta en **2.2s** usando OpenMeteo con pronóstico exacto en español, sin que salte el aviso en inglés del firmware.
4. **Fast-Path Calendario (Query 2 - `12:52:05`)**:
   - *"Revisa si tengo alguna reunión para el día de hoy."* -> interceptada por el nuevo enrutador en **13ms**, respondiendo *"No tienes reuniones ni eventos agendados para las próximas 24 horas."* de forma inmediata.
5. **Whisper STT Preciso**:
   - Las tres consultas mantuvieron intactas sus palabras interrogativas de inicio (*"¿Cuál es..."*, *"Revisa si..."*, *"En google cuál es..."*).

---

### Diagnóstico de la Falla en Consulta 3 (`12:53:37.881`):
- **Pregunta del usuario**: *"En google cuál es la manera más rápida de buscar un ítem en una array de python."*
- **Secuencia observada en el log**:
  1. No hizo match con `ExternalInfoService.isGeneralSearchQuery()` porque la frase comenzó con `"En google "`. El método solo evaluaba `norm.startsWith("google ")`, `norm.startsWith("busca en google")`, pero no `"en google"`, ni preguntas directas como `"cual es la manera"`.
  2. Cayó al LLM remoto (`https://soft-ia.co/litellm/v1/chat/completions`, modelo `gafas`).
  3. En `AgenticToolExecutor`, se enviaron las 30 herramientas completas (payload de **15.419 caracteres**).
  4. En `AiHttpClient`, al estar configurado como `provider = LOCAL`, se asignó `LOCAL_READ_TIMEOUT_MS = 20000ms`, a pesar de que el endpoint está en internet.
  5. A los 3 segundos de espera, el watchdog interno del firmware de las gafas reprodujo *"Just a moment, please"* (`12:53:44.126`).
  6. A los 21.7 segundos, el socket HTTP lanzó `SocketTimeoutException: timeout` (`12:54:02.811`), provocando la respuesta de error *"No pude procesar la consulta con el agente."*.

---

## 2. Pistas de Mejora Propuestas

### Pista 1: Cobertura Completa de Fast-Path Web e Información General
- **Archivos**:
  - `ExternalInfoService.kt`
  - `ExternalInfoServiceTest.kt`
- **Cambios**:
  1. En `isGeneralSearchQuery(query)`:
     - Detectar prefijos: `"en google"`, `"googlea"`, `"googlear"`, `"busca en internet"`, `"buscar en internet"`, `"investiga"`, `"averigua"`.
     - Detectar preguntas informativas y técnicas: `"cual es la manera"`, `"cual es la forma"`, `"como se busca"`, `"como buscar"`, `"como se hace"`, `"como hacer"`, `"como programar"`.
  2. En `fetchGoogleOrWebSearch(rawQuery)`:
     - Limpiar con regex exhaustivo los prefijos:
       `(?i)^[¿¡?\s]*(en\s+google|busca\s+en\s+google|buscar\s+en\s+google|google|busca|buscar|googlear|investiga|averigua)\s+`
     - Limpiar puntuación interrogativa y final (`?`, `.`, etc.).
     - Así, la consulta *"En google cuál es la manera más rápida de buscar un ítem en una array de python."* se convierte limpiamente en *"cuál es la manera más rápida de buscar un ítem en una array de python"* y obtiene el extracto web en **1.2 a 2 segundos**.

### Pista 2: Discriminación Inteligente de Timeout en `AiHttpClient.kt`
- **Archivo**: `AiHttpClient.kt`
- **Cambios**:
  1. Actualmente `isLocal` asume que cualquier endpoint con `provider == AiProvider.LOCAL` es una red local y le aplica 20s de timeout (`LOCAL_READ_TIMEOUT_MS`).
  2. Si el endpoint contiene un dominio público HTTPS (ej. `https://soft-ia.co/...`) o no es IP de red privada (`127.0.0.1`, `localhost`, `10.*`, `192.168.*`, `172.16.*`), debe aplicar `READ_TIMEOUT_MS = 60000ms`.
  3. Esto previene falsos timeouts cuando LiteLLM o APIs OpenAI-compatibles remotas tardan un poco más en resolver peticiones complejas.

### Pista 3: Poda Inteligente de Herramientas Agénticas (Smart Tool Pruning)
- **Archivo**: `AgenticToolExecutor.kt`
- **Cambios**:
  1. Enviar 30 herramientas (15.419 caracteres de schema JSON) a modelos pequeños o proxies lentos en cada consulta penaliza severamente la latencia.
  2. Clasificar o podar herramientas: si la consulta del usuario es una pregunta general/técnica/conversacional y no menciona acciones de dispositivo (llamada, alarma, temporizador, linterna, bluetooth, brillo, teleprompter, whatsapp, contactos), filtrar las herramientas enviadas o priorizar únicamente las herramientas de consulta externa (búsqueda web, clima, noticias, divisas), reduciendo el payload de 15KB a < 2.5KB (ahorro de más del 80% de tokens y latencia).

---

## 3. Plan de Verificación

1. **Pruebas Unitarias**:
   - Añadir tests en `ExternalInfoServiceTest.kt` que validen:
     - `"En google cuál es la manera más rápida de buscar un ítem en una array de python."` -> `isGeneralSearchQuery == true`.
     - Limpieza correcta de `"En google "` sin dejar residuos.
     - Detección de patrones `"cómo buscar..."`, `"cuál es la manera..."`.
   - Ejecutar `./gradlew testDebugUnitTest` y asegurar 100% de éxito en todos los tests.
2. **Compilación de Producción / Debug**:
   - Ejecutar `./gradlew assembleDebug` para certificar que el APK compila sin advertencias ni errores.
3. **Sincronización Final**:
   - Ejecutar `codegraph sync` y actualizar `PROJECT_MEMORY.md`.
