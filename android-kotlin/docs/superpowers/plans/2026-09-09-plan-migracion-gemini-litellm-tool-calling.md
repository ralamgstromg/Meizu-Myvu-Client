# Plan de Arquitectura y Migración: Gemini + LiteLLM con Tool Calling Nativo

> **Para trabajadores agenticos:** SUB-SKILL REQUERIDA: Usar superpowers:executing-plans o ejecución sistemática por fases con verificación continua y guardado en memoria.

**Meta:** Migrar la integración de IA en el cliente Android (`LocalAiClient`, `AiConversation`, `SkillExecutor`, `NoteAiProcessor`, `MeetingAiProcessor`, `ChatActivity`) desde el esquema legacy de inyección de texto (`[SKILL: ...]`) y parsing regex hacia llamadas a herramientas nativas (**Native Tool Calling / Function Calling**), salidas estructuradas (**Structured Outputs**) y optimización de prompts para modelos Gemini desplegados vía LiteLLM.

**Arquitectura:** 
- **Capa de Transporte IA (`LocalAiClient`)**: Extender `LocalAiClient` para soportar especificaciones OpenAI-compatible de `tools`, `tool_choice`, `tool_calls` y roles (`system`, `user`, `assistant`, `tool`).
- **Esquema de Herramientas Dinámico (`SkillToolConverter`)**: Transformar dinámicamente las 30 habilidades registradas en `SkillRegistry` a especificaciones JSON Schema de herramientas OpenAI/LiteLLM.
- **Bucle Agéntico ReAct (`AgenticToolExecutor`)**: Orquestar el ciclo multi-paso de llamada de herramientas -> ejecución de `SkillHandler` -> retorno de resultado como mensaje `tool` -> respuesta final condensada para el HUD de las gafas.
- **Procesadores Estructurados**: Migrar `NoteAiProcessor` y `MeetingAiProcessor` a `response_format: {"type": "json_object"}` para garantizar JSON válido sin regex ni fallbacks frágiles.
- **Optimización de Prompts**: Limpiar el system prompt de Aura, eliminando el voluminoso texto descriptivo de las 30 skills y optimizando las directivas para síntesis ultra-rápida apta para pantalla HUD micro-LED.

**Stack Tecnológico:** Kotlin 2.1.10, LiteLLM OpenAI-Compatible Proxy, Modelos Google Gemini (Gemini 2.0 Flash / 1.5 Pro), OkHttp / HttpURLConnection, Android SDK 35, Jetpack Room.

## Restricciones Globales
- Idioma de respuesta con el usuario: Modo Cavernícola (Kog).
- Sincronización obligatoria: Ejecutar `codegraph sync` al inicio y fin de cada tarea.
- Compatibilidad hacia atrás: Si el proveedor seleccionado no es `LOCAL` con soporte de herramientas (ej. endpoints sin tool-calling), mantener fallback transparente.
- Latencia crítica: El tiempo de respuesta para las gafas AR debe ser menor a 1.5s en voz; no hacer roundtrips innecesarios.
- Preservar estado en memoria (`docs/PROJECT_MEMORY.md` y `codebase-memory-mcp`).

---

## 1. Análisis del Estado Actual vs. Arquitectura Propuesta

| Dimensión | Estado Actual (Legacy) | Nueva Arquitectura (Gemini + LiteLLM Tool Calling) |
|---|---|---|
| **Definición de Acciones** | Manifiestos de texto pegados al final del system prompt (~1500 tokens por petición). | Parámetro nativo `tools` en `/v1/chat/completions` generado desde `Skill.parameters`. |
| **Disparo de Herramientas** | Pseudo-tokens en texto: `[SKILL: id {json}]` propensos a alucinaciones sintácticas. | Objeto nativo `tool_calls` en `choices[0].message` garantizado por el motor de Gemini. |
| **Ejecución Multi-Paso (ReAct)** | Solo 1 acción por turno, sin retorno de datos al LLM; flujo roto. | Bucle agéntico completo: el resultado del handler se retroalimenta con role `tool` para razonamiento continuo. |
| **Acciones Paralelas** | Imposible; solo detecta el primer regex match. | Soporte nativo de `tool_calls` paralelos (ej. clima + alarma + recordatorio en un solo turno). |
| **Extracción en Notas/Reuniones** | Prompt pide JSON en texto plano y usa regex `sanitizeJsonObject` con riesgo de error. | `response_format: {"type": "json_object"}` asegurando JSON 100% estricto sin fallbacks sucios. |
| **Roles de Mensajes** | Concatena `systemPrompt` dentro del mensaje `user`. | Mensaje nativo con `role: "system"`, activando el motor de System Instructions de Gemini. |
| **Consumo de Tokens y Latencia** | Alto overhead por texto repetitivo de 30 skills en cada llamada. | Reducción del ~60% de tokens de prompt; menor tiempo al primer token (TTFT). |

---

## 2. Fases de Implementación Detalladas

### [x] Fase 1: Extensión del Cliente IA (`LocalAiClient` & Modelos de Datos)
- Creado `ToolCallModels.kt` con `ToolDefinition`, `ToolCall`, `ChatMessage` y `ChatCompletionResult`.
- Extendido `AiClient.kt` con `chat(messages, tools, jsonMode)` y `supportsToolCalling()`.
- Implementado en `LocalAiClient.kt`: construcción de payload OpenAI/LiteLLM con `messages`, `tools`, `tool_choice: "auto"` y extracción de `tool_calls`.

### [x] Fase 2: Conversor de Habilidades a Tools (`SkillToolConverter`)
- Creado `SkillToolConverter.kt` transformando las 30 habilidades de `SkillRegistry` a especificaciones JSON Schema compatibles con OpenAI/Gemini.
- Actualizado `SkillRegistry.kt` con `getToolDefinitions()` y `buildNativeToolsSystemPrompt()`.

### [x] Fase 3: Orquestador Agéntico ReAct (`AgenticToolExecutor`)
- Creado `AgenticToolExecutor.kt` con bucle ReAct multi-paso autónomo, ejecución de handlers y retroalimentación con role `tool`.

### [x] Fase 4: Integración en Asistente de Gafas (`AiConversation`) y Chat Móvil (`ChatActivity`)
- Integrado `AgenticToolExecutor` en `AiConversation.askAi()` con respuesta final directa para el HUD.
- Integrado `AgenticToolExecutor` en `ChatActivity.kt`.

### [x] Fase 5: Estructuración Nativa en Procesadores de Notas y Reuniones
- `NoteAiProcessor.kt` y `MeetingAiProcessor.kt` migrados a `chat(..., jsonMode = true)` para garantizar JSON puro.

### [x] Fase 6: Optimización de System Prompts para Gafas AR
- `DEFAULT_SYSTEM_PROMPT` y prompts de Aura optimizados para HUD, TTS y uso de herramientas nativas.

---

## 3. Matriz de Verificación y Pruebas

- [x] **Test de Serialización de Tools**: `ToolCallingIntegrationTest.testToolDefinitionSerialization` completado con éxito.
- [x] **Test de Mensajes y Roles**: `ToolCallingIntegrationTest.testChatMessageSerialization` completado con éxito.
- [x] **Test de Conversión de Habilidades**: `ToolCallingIntegrationTest.testSkillToolConverter` completado con éxito.
- [x] **Test de Cliente LiteLLM y Parseo de Tool Calls**: `ToolCallingIntegrationTest.testLocalAiClientChatBodyConstructionAndResponseParsing` completado con éxito.
- [x] **Verificación de Compilación General**: `./gradlew assembleDebug` completado con éxito (`BUILD SUCCESSFUL in 753ms`).
