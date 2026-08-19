# Optimización de Prompts Locales, Eliminación de Tasker y Búsqueda Externa en Google - Plan de Implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Optimizar los prompts del sistema para máxima compatibilidad con el modelo local Gemma 2B en gafas AR MEIZU MYVU, eliminar completamente toda la lógica y dependencias de integración con Tasker, e integrar un motor de búsqueda web/Google en tiempo real para clima, divisas y consultas externas con retorno simultáneo en texto (HUD) y voz (TTS).

**Architecture:** 
1. **Prompts y Formato Gemma Local**: Rediseñar el `DEFAULT_SYSTEM_PROMPT` para reducir tokens innecesarios, enfocarse en la visualización HUD (conciso, 1-2 oraciones cortas, sin markdown/emojis) y soporte para etiquetas `ACTION:SEARCH={query}`. Formatear adecuadamente los turnos en `GemmaLocalClient` y `AiConversation`.
2. **Eliminación Total de Tasker**: Remover el paquete `com.myvu.client.plugin.tasker`, sus vistas XML, sus actividades/receptores en `AndroidManifest.xml`, los emisores de eventos en `TouchGestureManager` y `ConnectionManager`, y sus tests unitarios asociados.
3. **Servicio de Búsqueda Externa (Google / Web / Clima / Divisas)**: Implementar `ExternalInfoService` para resolver consultas meteorológicas específicas por ciudad (Open-Meteo Geocoding + Google), conversión de divisas en tiempo real y respuestas instantáneas de Google. Conectar este servicio tanto al fast-path de `VoiceActionRouter` como a la ejecución de acciones en `PhoneActionExecutor` y `AiConversation`, retornando el resultado en texto al HUD de las gafas y por voz mediante el pipeline TTS del móvil.

**Tech Stack:** Kotlin 2.1+, Android SDK (API 26-35), MediaPipe LLM Inference / LiteRT, OkHttp / HttpURLConnection, Open-Meteo API / Google Search Instant Query, JUnit 4, Robolectric.

## Global Constraints
- El lenguaje de respuesta por defecto es español neutro/regional adaptado a la configuración del usuario.
- Todo texto retornado a las gafas debe ser texto plano limpio (sin markdown `*`, `#`, viñetas ni emojis).
- Las respuestas deben ser breves (máximo 1-2 oraciones) aptas para lectura rápida en display micro-LED y síntesis TTS.
- Todas las consultas de red deben ejecutarse en hilos de fondo (`Dispatchers.IO` / `ExecutorService`) con timeouts estrictos (máx 5-8s).
- Mantener la regla del proyecto: ejecutar `codegraph sync` al iniciar y finalizar cada ciclo de trabajo.

---

### Task 1: Eliminación Completa de la Integración con Tasker

**Files:**
- Modify: `android-kotlin/app/src/main/AndroidManifest.xml`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt`
- Delete: `android-kotlin/app/src/main/java/com/myvu/client/plugin/tasker/` (directorio completo)
- Delete: `android-kotlin/app/src/main/res/layout/activity_tasker_action.xml`
- Delete: `android-kotlin/app/src/main/res/layout/activity_tasker_event.xml`
- Delete: `android-kotlin/app/src/test/java/com/myvu/client/plugin/tasker/` (directorio completo de tests)
- Delete: `android-kotlin/docs/TASKER_INTEGRATION.md`
- Modify: `README.md`
- Modify: `android-kotlin/README.md`

**Interfaces:**
- Consumes: `TouchGestureManager.handleTrigger`, `ConnectionManager` state callbacks.
- Produces: Base de código libre de referencias a Tasker y compilable sin dependencias residuales.

- [ ] **Step 1: Eliminar llamadas a TaskerEventBroadcaster en TouchGestureManager y ConnectionManager**

En `android-kotlin/app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt`:
Remover `import com.myvu.client.plugin.tasker.event.TaskerEventBroadcaster`, la constante `ACTION_TASKER_EVENT = "tasker_event"`, y las llamadas a `TaskerEventBroadcaster.sendGestureEvent(...)` y `TaskerEventBroadcaster.sendAiButtonEvent(...)`.

En `android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt`:
Remover `import com.myvu.client.plugin.tasker.event.TaskerEventBroadcaster` y las llamadas a `TaskerEventBroadcaster.sendConnectionStateEvent(...)` y `TaskerEventBroadcaster.sendBatteryEvent(...)`.

- [ ] **Step 2: Eliminar actividades y receptores de Tasker en AndroidManifest.xml**

Remover los bloques `<activity>` y `<receiver>` asociados a `.plugin.tasker.action.TaskerActionActivity`, `.plugin.tasker.action.TaskerActionReceiver`, `.plugin.tasker.event.TaskerEventActivity` y `.plugin.tasker.event.TaskerConditionReceiver`.

- [ ] **Step 3: Borrar archivos físicos de código, layouts, tests y documentación de Tasker**

Eliminar:
- `android-kotlin/app/src/main/java/com/myvu/client/plugin/tasker/`
- `android-kotlin/app/src/main/res/layout/activity_tasker_action.xml`
- `android-kotlin/app/src/main/res/layout/activity_tasker_event.xml`
- `android-kotlin/app/src/test/java/com/myvu/client/plugin/tasker/`
- `android-kotlin/docs/TASKER_INTEGRATION.md`

- [ ] **Step 4: Actualizar README.md y android-kotlin/README.md para remover menciones a Tasker**

Limpiar las tablas y diagramas de arquitectura en ambos READMEs.

- [ ] **Step 5: Ejecutar suite de pruebas unitarias para confirmar que no quedan dependencias rotas**

Run: `cd android-kotlin && ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL con 0 errores de compilación o test.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: remove all Tasker plugin integration and related assets"
```

---

### Task 2: Actualización de Prompts del Sistema para Modelo Local Gemma 2B en Gafas AR

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ai/AiClient.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ai/GemmaLocalClient.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ai/AiConversation.kt`
- Test: `android-kotlin/app/src/test/java/com/myvu/client/ai/SystemPromptTest.kt`

**Interfaces:**
- Consumes: `AiClient.DEFAULT_SYSTEM_PROMPT`, `Prefs.systemPrompt(context)`.
- Produces: Prompts estructurados, concisos y compatibles con el formato de turnos de Gemma 2B (`<start_of_turn>user\n...<end_of_turn>\n<start_of_turn>model\n`), optimizados para micro-pantalla AR y síntesis de voz.

- [ ] **Step 1: Escribir prueba unitaria para validar el nuevo system prompt y formateador Gemma**

Crear `android-kotlin/app/src/test/java/com/myvu/client/ai/SystemPromptTest.kt`:
```kotlin
package com.myvu.client.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptTest {
    @Test
    fun testDefaultSystemPromptIsConciseAndContainsActionTags() {
        val prompt = AiClient.DEFAULT_SYSTEM_PROMPT
        assertTrue(prompt.contains("MEIZU MYVU"))
        assertTrue(prompt.contains("ACTION:SEARCH="))
        assertTrue(prompt.contains("ACTION:CALL="))
        assertTrue(prompt.contains("ACTION:WHATSAPP="))
        // Asegurar que no supere los 1000 caracteres para no saturar modelos 2B
        assertTrue(prompt.length < 1500)
    }

    @Test
    fun testGemmaTurnFormatting() {
        val formatted = GemmaLocalClient.formatPrompt("Instrucciones de sistema", "Hola")
        assertTrue(formatted.contains("<start_of_turn>user"))
        assertTrue(formatted.contains("<end_of_turn>"))
        assertTrue(formatted.contains("<start_of_turn>model"))
    }
}
```

- [ ] **Step 2: Ejecutar test para verificar que falla**

Run: `cd android-kotlin && ./gradlew testDebugUnitTest --tests "com.myvu.client.ai.SystemPromptTest"`
Expected: FAIL (falta método formatPrompt o campos en AiClient).

- [ ] **Step 3: Actualizar DEFAULT_SYSTEM_PROMPT y formateo en GemmaLocalClient / AiConversation**

En `android-kotlin/app/src/main/java/com/myvu/client/ai/AiClient.kt`:
```kotlin
package com.myvu.client.ai

import java.io.IOException

interface AiClient {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT: String =
            "Eres el asistente de voz de las gafas AR MEIZU MYVU.\n" +
            "Reglas obligatorias:\n" +
            "1. Responde SIEMPRE en {LANGUAGE_NAME}, en texto plano conversacional directo (máximo 1 o 2 oraciones breves).\n" +
            "2. Prohibido usar formato markdown (*, #, viñetas -, negritas), emojis o introducciones de cortesía largas.\n" +
            "3. Para clima, divisas, noticias o datos externos en tiempo real, responde confirmando y anexa: ACTION:SEARCH={consulta}\n" +
            "4. Para control del teléfono, anexa la acción al final:\n" +
            "  - Llamar: ACTION:CALL={Nombre}\n" +
            "  - WhatsApp: ACTION:WHATSAPP={Nombre}: {Mensaje}\n" +
            "  - Notas: ACTION:NOTE={Texto}\n" +
            "  - Recordatorio: ACTION:REMINDER={Hora}: {Mensaje}\n" +
            "  - Tareas: ACTION:TODO_ADD={Lista}: {Tarea}\n" +
            "  - Música: ACTION:APP_PLAY={App}: {Canción}\n" +
            "  - Apps: ACTION:APP_OPEN={App}\n" +
            "  - HUD Teleprompter: ACTION:TELEPROMPTER={Texto}\n" +
            "  - Navegación GPS: ACTION:NAVIGATE={Destino}"
    }

    fun isConfigured(): Boolean

    @Throws(IOException::class)
    fun ask(question: String): String
}
```

En `android-kotlin/app/src/main/java/com/myvu/client/ai/GemmaLocalClient.kt`:
Añadir el formateador de turnos oficial de Gemma:
```kotlin
companion object {
    // ...
    fun formatPrompt(systemPrompt: String?, userQuery: String): String {
        val sys = if (!systemPrompt.isNullOrBlank()) "$systemPrompt\n\n" else ""
        return "<start_of_turn>user\n${sys}${userQuery.trim()}<end_of_turn>\n<start_of_turn>model\n"
    }
}
```
Y en `GemmaLocalClient.ask(question: String)`:
Asegurar que si el prompt entrante no incluye ya los delimitadores de turnos de Gemma, se formateen automáticamente.

- [ ] **Step 4: Ejecutar test unitario para verificar que pasa**

Run: `cd android-kotlin && ./gradlew testDebugUnitTest --tests "com.myvu.client.ai.SystemPromptTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android-kotlin/app/src/main/java/com/myvu/client/ai/AiClient.kt android-kotlin/app/src/main/java/com/myvu/client/ai/GemmaLocalClient.kt android-kotlin/app/src/test/java/com/myvu/client/ai/SystemPromptTest.kt
git commit -m "feat(ai): optimize system prompt and turn templates for Gemma 2B on AR glasses"
```

---

### Task 3: Motor de Búsqueda Externa (Google / Clima / Divisas / Web Search Service)

**Files:**
- Create: `android-kotlin/app/src/main/java/com/myvu/client/ai/ExternalInfoService.kt`
- Create: `android-kotlin/app/src/test/java/com/myvu/client/ai/ExternalInfoServiceTest.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/weather/OpenMeteo.kt`

**Interfaces:**
- Consumes: Cadenas de búsqueda de clima, tasas de cambio de divisas y consultas generales a Google.
- Produces: `ExternalInfoService.search(query: String, callback: (resultText: String, success: Boolean) -> Unit)` con texto plano resumido listo para HUD y TTS.

- [ ] **Step 1: Escribir pruebas unitarias para ExternalInfoService**

Crear `android-kotlin/app/src/test/java/com/myvu/client/ai/ExternalInfoServiceTest.kt`:
```kotlin
package com.myvu.client.ai

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalInfoServiceTest {
    @Test
    fun testDetectExternalQueryTypes() {
        assertTrue(ExternalInfoService.isWeatherQuery("qué temperatura hay en Barranquilla"))
        assertTrue(ExternalInfoService.isWeatherQuery("clima en Bogotá mañana"))
        assertTrue(ExternalInfoService.isCurrencyQuery("cuánto está el dólar en pesos colombianos"))
        assertTrue(ExternalInfoService.isCurrencyQuery("precio del euro a cop"))
        assertTrue(ExternalInfoService.isGeneralSearchQuery("busca en google quién descubrió América"))
    }

    @Test
    fun testFormatCurrencyAnswer() {
        val answer = ExternalInfoService.formatCurrencyResult(1.0, "USD", 4150.0, "COP")
        assertTrue(answer.contains("1 USD equivale a 4150 COP") || answer.contains("4150"))
    }
}
```

- [ ] **Step 2: Ejecutar test para verificar que falla**

Run: `cd android-kotlin && ./gradlew testDebugUnitTest --tests "com.myvu.client.ai.ExternalInfoServiceTest"`
Expected: FAIL (clase ExternalInfoService no existe aún).

- [ ] **Step 3: Implementar ExternalInfoService con búsqueda en Google, OpenMeteo Geocoding y Divisas**

Crear `android-kotlin/app/src/main/java/com/myvu/client/ai/ExternalInfoService.kt`:
- Soporte para geocodificación automática de ciudades con OpenMeteo (`https://geocoding-api.open-meteo.com/v1/search?name=...`) para responder preguntas como "¿Qué temperatura hace en Madrid / Barranquilla / Medellín?".
- Soporte para tasas de cambio y divisas en tiempo real vía API de conversión (`https://api.frankfurter.app/latest?from=USD&to=COP` o Google Search Instant Answer parser).
- Soporte para búsqueda web en Google (`https://www.google.com/search?q=...` / DuckDuckGo / Instant Answers) extrayendo la respuesta directa (`featured snippet` / `calculator` / `knowledge graph`) en texto limpio y conciso de 1-2 oraciones.

- [ ] **Step 4: Ejecutar test unitario para verificar que pasa**

Run: `cd android-kotlin && ./gradlew testDebugUnitTest --tests "com.myvu.client.ai.ExternalInfoServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android-kotlin/app/src/main/java/com/myvu/client/ai/ExternalInfoService.kt android-kotlin/app/src/test/java/com/myvu/client/ai/ExternalInfoServiceTest.kt
git commit -m "feat(ai): add ExternalInfoService for live Google search, city weather and currency exchange"
```

---

### Task 4: Integración de Búsqueda Externa en VoiceActionRouter, PhoneActionExecutor y AiConversation

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ai/VoiceActionRouter.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ai/PhoneActionExecutor.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ai/AiConversation.kt`
- Test: `android-kotlin/app/src/test/java/com/myvu/client/ai/VoiceActionRouterTest.kt`

**Interfaces:**
- Consumes: Comandos por voz del usuario y etiquetas `ACTION:SEARCH={query}` generadas por el modelo local.
- Produces: Respuestas inmediatas en texto (enviadas a `AiProtocol.chatReply` en HUD) y voz (`TtsPlayer.speak` en el teléfono/gafas).

- [ ] **Step 1: Escribir prueba unitaria en VoiceActionRouterTest para consultas externas de clima y divisas**

En `android-kotlin/app/src/test/java/com/myvu/client/ai/VoiceActionRouterTest.kt`:
Añadir pruebas para:
- "¿Qué temperatura era mañana en Barranquilla?" -> `handled = true, isAsyncExternalSearch = true`
- "¿A cómo está el dólar hoy?" -> `handled = true, isAsyncExternalSearch = true`
- "Busca en google las últimas noticias de tecnología" -> `handled = true, isAsyncExternalSearch = true`

- [ ] **Step 2: Ejecutar test para verificar fallo**

Run: `cd android-kotlin && ./gradlew testDebugUnitTest --tests "com.myvu.client.ai.VoiceActionRouterTest"`
Expected: FAIL.

- [ ] **Step 3: Actualizar VoiceActionRouter, PhoneActionExecutor y AiConversation**

1. En `VoiceActionRouter.kt`:
   Ampliar los patrones de enrutamiento rápido para capturar preguntas de clima ("qué temperatura...", "va a llover en...", "clima en..."), divisas ("a cómo está el dólar...", "precio del euro...", "tasa de cambio...") y búsquedas ("busca...", "consultar...", "quién es..."). Retornar `isAsyncExternalSearch = true`.

2. En `PhoneActionExecutor.kt`:
   Actualizar la acción `ACTION:SEARCH=` para invocar `ExternalInfoService.search(...)` en lugar de sólo abrir un navegador pasivo, y procesar los resultados directamente.

3. En `AiConversation.kt`:
   Si el enrutador o la acción `ACTION:SEARCH` disparan una búsqueda externa:
   - Consultar `ExternalInfoService`.
   - Al recibir el resultado, llamar a `deliverFinal(resultadoTexto, AiResponse.Source.AI)`.
   - `deliverFinal` automáticamente proyecta el texto en el HUD de las gafas (`AiProtocol.chatReply`) e inicia la reproducción TTS por voz (`TtsPlayer.speak`).

- [ ] **Step 4: Ejecutar pruebas unitarias para validar la integración**

Run: `cd android-kotlin && ./gradlew testDebugUnitTest`
Expected: PASS en toda la suite.

- [ ] **Step 5: Commit**

```bash
git add android-kotlin/app/src/main/java/com/myvu/client/ai/VoiceActionRouter.kt android-kotlin/app/src/main/java/com/myvu/client/ai/PhoneActionExecutor.kt android-kotlin/app/src/main/java/com/myvu/client/ai/AiConversation.kt android-kotlin/app/src/test/java/com/myvu/client/ai/VoiceActionRouterTest.kt
git commit -m "feat(ai): route weather, currencies and external search queries directly to HUD and TTS"
```

---

### Task 5: Actualización de Documentación y Sincronización Final

**Files:**
- Modify: `android-kotlin/README.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: Todo el conjunto de cambios realizados.
- Produces: Documentación completa del proyecto actualizada con detalles de la arquitectura de prompts locales, eliminación de Tasker y motor de búsqueda externa en tiempo real.

- [ ] **Step 1: Documentar los cambios en detalle en README.md y android-kotlin/README.md**
- [ ] **Step 2: Ejecutar codegraph sync para actualizar el grafo de conocimiento**
Run: `codegraph sync`
- [ ] **Step 3: Commit final de documentación**
```bash
git add README.md android-kotlin/README.md
git commit -m "docs: document local Gemma prompt optimization, Google search engine and Tasker removal"
```
