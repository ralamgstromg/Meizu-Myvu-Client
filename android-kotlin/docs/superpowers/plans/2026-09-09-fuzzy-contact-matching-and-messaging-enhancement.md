# Plan de Trabajo: Búsqueda Inteligente de Contactos por Similitud/Subconjunto en Llamadas, WhatsApp y SMS

## 1. Diagnóstico del Problema y Causas Raíz
- **Solicitud del Usuario**: Mejorar las solicitudes de llamadas, WhatsApp y SMS para que al dictar un nombre corto o parcial (ej. *"matias"* o *"matias castro"*), el sistema busque por coincidencia o similitud y seleccione automáticamente al contacto compuesto más parecido en la agenda (ej. `"Matias Castro hijo"`).
- **Causas Raíz Identificadas**:
  1. **Algoritmo de puntuación rígido en [`ContactHelper.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/core/ContactHelper.kt)**:
     - La coincidencia exacta (`normalizedContact == normalizedSearch`) falla porque `"matias castro hijo" != "matias"`.
     - La coincidencia de subcadena (`contains`) sumaba solo 100 puntos fijos y los tokens sumaban 50 puntos sin considerar la **tasa de cobertura del subconjunto (Token Containment / Jaccard)**. Si el usuario dice 2 palabras (`"matias"`, `"castro"`) y ambas están en el contacto de 3 palabras (`"matias"`, `"castro"`, `"hijo"`), la cobertura de la consulta es del 100%, lo cual debe premiarse con una puntuación determinante (+350 pts).
  2. **Normalización incompleta**:
     - `Normalizer` removía diacríticos (tildes), pero no limpiaba signos de puntuación, emojis ni paréntesis (ej. `"Matias Castro (Hijo)"`, `"Matias Castro - Hijo"` o `"Castro, Matias"`), haciendo que `split("\\s+")` dejara tokens como `"castro,"` o `"(hijo)"`, rompiendo la igualdad de tokens.
  3. **Fragmentación y duplicación en [`PhoneActionExecutor.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/PhoneActionExecutor.kt)**:
     - `PhoneActionExecutor` mantenía su propia función duplicada `lookupContactNumberWithScore`, que no compartía mejoras con `ContactHelper.resolveContactPhone`.
     - En `openWhatsApp`, al buscar en la ventana de 1 a 4 tokens, si encontraba un match difuso guardaba el texto del query (`"matias"` o `"matias castro"`) en vez del nombre y número resueltos, obligando a una segunda consulta SQL en Android Contacts que podía fallar o seleccionar un contacto distinto.
  4. **Falta de soporte y enrutamiento para SMS**:
     - `PhoneActionExecutor` no contaba con método `sendSms`, y `VoiceActionRouter` no tenía expresiones regulares para capturar comandos como *"envía un sms a..."* o *"manda mensaje de texto a..."*.

---

## 2. Solución Integral Propuesta

### Pista 1: Motor de Coincidencia de Contactos Avanzado en [`ContactHelper.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/core/ContactHelper.kt)
1. **Normalización Profunda**:
   - Eliminar tildes/marcas diacríticas (NFD).
   - Reemplazar caracteres no alfanuméricos (puntuación, paréntesis, guiones, comas, emojis) por espacios limpios.
   - Extraer tokens limpios en minúsculas.
2. **Algoritmo de Puntuación Multi-Criterio (`calculateMatchScore`)**:
   - **Coincidencia Exacta Total**: +1000 puntos (emparejamiento idéntico inmediato).
   - **Prefijo de Contacto**: Si el nombre del contacto comienza exactamente con el query del usuario: +500 puntos (ej. `"matias castro hijo"` comienza con `"matias castro"`).
   - **Cobertura Total de Consulta (Subset Containment 100%)**: Si todos los tokens dichos por el usuario están presentes en los tokens del contacto: +350 puntos.
   - **Coincidencia de Tokens Individuales**: +60 puntos por cada token idéntico.
   - **Preservación de Orden**: +80 puntos si los tokens de la consulta aparecen en el mismo orden secuencial dentro del contacto.
   - **Similitud Fonética/Levenshtein**: Si la distancia de edición es <= 1 o 2 para compensar errores de transcripción del STT de Whisper/Android: +20 a +35 puntos.
   - **Penalización por longitud excesiva**: `- (contactTokens.size - queryTokens.size) * 5 puntos` para desempatar a favor del contacto más conciso en caso de múltiples candidatos que contengan los mismos tokens.
3. **Estructura Enriquecida**:
   - Definir `data class ContactMatch(val number: String, val displayName: String, val score: Int, val isExact: Boolean)`.
   - Exponer `findBestContactMatch(context: Context, query: String): ContactMatch?` y mantener `resolveContactPhone` como wrapper retrocompatible.

---

### Pista 2: Unificación y Optimización en [`PhoneActionExecutor.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/PhoneActionExecutor.kt)
1. **Eliminar Duplicidad**:
   - Reemplazar `lookupContactNumber` y `lookupContactNumberWithScore` para que deleguen directamente al motor optimizado de `ContactHelper`.
2. **Parser Gramatical Inteligente en `openWhatsApp`**:
   - Detectar conectores coloquiales en español: `"que"`, `"que diga"`, `"diciendo"`, `"dile que"`, `"con el mensaje"`.
   - En la ventana heurística (1 a 4 tokens), evaluar los candidatos contra `ContactHelper.findBestContactMatch`. Si un candidato obtiene una puntuación alta (>= 300), fijar inmediatamente el número de teléfono y el nombre formal encontrado.
   - Limpiar el prefijo residual `"que "` del mensaje antes de disparar el envío.
3. **Implementación de Envío de SMS Manos Libres (`sendSms`)**:
   - Extraer destinatario y mensaje con el mismo parser inteligente.
   - Resolver el número telefónico del destinatario vía `ContactHelper`.
   - Si se cuenta con permiso `SEND_SMS`, enviar directamente mediante `SmsManager.sendTextMessage(...)` (100% manos libres, sin tocar el teléfono).
   - Fallback automático mediante Intent `ACTION_SENDTO` con URI `smsto:` y `sms_body` pre-cargado.
4. **Mejora en Llamadas (`makeCall`)**:
   - Resolver el contacto con `ContactHelper.findBestContactMatch`.
   - Discernir entre números telefónicos directos y nombres de contactos difusos.
   - Realizar la llamada directa manos libres vía `TelecomManager` o `ACTION_CALL`.

---

### Pista 3: Enrutamiento Rápido en [`VoiceActionRouter.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/VoiceActionRouter.kt)
1. **Ruta Fast-Path para SMS**:
   - Capturar expresiones como:
     - *"envía un sms a..."*
     - *"manda mensaje de texto a..."*
     - *"envía un texto a..."*
     - *"mándale un sms a..."*
   - Despachar directamente a `actionExecutor.sendSms(payload)` respondiendo al usuario: *"Enviando mensaje de texto a [Nombre Encontrado]..."*.
2. **Ajuste en Rutas de Llamadas y WhatsApp**:
   - Garantizar que los nombres con modificadores o prefijos no se recorten incorrectamente y pasen al resolutor inteligente.

---

### Pista 4: Nueva Habilidad y Handler para Tool Calling de IA
1. Crear [`SendSmsHandler.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/skills/handlers/SendSmsHandler.kt).
2. Registrar `send-sms` en [`SkillRegistry.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/skills/SkillRegistry.kt).
3. Asegurar que si una solicitud llega al modelo LLM/Gemini en vez de Fast-Path, el modelo pueda invocar la herramienta `send-sms` con argumentos estructurados `{"recipient": "matias", "message": "ya voy saliendo"}`.

---

### Pista 5: Pruebas Unitarias y Verificación
1. **Pruebas de Algoritmo de Similitud**:
   - Crear `ContactHelperTest.kt` validando casos específicos:
     - `"matias"` -> `"Matias Castro hijo"` (Match alto por prefijo y contención de token).
     - `"matias castro"` -> `"Matias Castro hijo"` (Match muy alto por contención del 100% de tokens).
     - `"castro matias"` -> `"Matias Castro hijo"` (Match alto sin importar el orden).
     - `"matias"` -> Desempate entre `"Matias Castro"` y `"Matias Castro hijo"` (favorece exacto/conciso sin perder similitud).
     - Caracteres especiales: `"Matias Castro (Hijo)"`, `"Matias - Hijo"`.
2. **Pruebas de Enrutamiento de Voz**:
   - Actualizar `VoiceActionRouterTest.kt` para probar comandos de SMS, llamadas a contactos parciales y WhatsApp.
3. **Verificación de Compilación**:
   - `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.
