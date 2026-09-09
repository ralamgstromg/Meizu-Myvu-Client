# Plan de Mejora: Notas, Recordatorios y Grabadora de Voz IA Multimodal
**Fecha:** 2026-09-09  
**Autor:** Kog (Caveman Agent)  
**Proyecto:** Meizu Myvu AR Smart Glasses Client (Android Kotlin)

---

## 1. Diagnóstico y Oportunidades Multimodales

### Estado Actual:
1. **Multimodalidad Incompleta**:
   - `ChatMessage` y `LocalAiClient.buildChatBody` solo serializan cadenas de texto planas (`String`).
   - `NoteAiProcessor` y `MeetingAiProcessor` procesan adjuntos, pero para imágenes (`AttachmentType.IMAGE`) solo agregan la etiqueta de texto `"[Imagen adjunta: archivo.jpg]"`. El modelo de IA (`gafas` / Gemini en LiteLLM) nunca recibe los bytes visuales de la imagen.
2. **Resúmenes y Extracción Ejecutiva**:
   - El prompt actual de `NoteAiProcessor` y `MeetingAiProcessor` puede enriquecerse para generar resúmenes con estructura ejecutiva de alto impacto (Objetivos, Puntos Críticos, Acuerdos, Tareas con plazos y Mapa Mental Mermaid).
   - Falta vinculación directa entre las tareas detectadas por IA (`action_items`) y la programación automática de recordatorios en el sistema Android.
3. **Proyección en Gafas AR (HUD)**:
   - En `NoteDetailActivity` y `RecordingDetailActivity`, la acción de enviar a las gafas envía una notificación convencional truncada a 2 líneas, en lugar de invocar `openTeleprompter()` en `ConnectionManager`, impidiendo la lectura cómoda de resúmenes y notas extensas en el visor microLED.

---

## 2. Fases de Implementación

### Fase 1: Soporte Multimodal Real (Visión + Texto) en `ToolCallModels` y `LocalAiClient`
- [ ] Ampliar `ChatMessage` para admitir `images: List<Pair<String, String>>?` (MIME type y Base64).
- [ ] Actualizar `ChatMessage.toJsonObject()` para construir el array de partes OpenAI/LiteLLM:
  `[{"type": "text", "text": ...}, {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}}]` cuando existan imágenes.
- [ ] Actualizar `LocalAiClient.buildChatBody()` y `parseChatCompletion()` para operar fluidamente con mensajes multimodales.
- [ ] Añadir helper de compresión y codificación Base64 en `DocumentExtractor` o `ImageHelper` (escalado máximo a 1024x1024 para optimizar latencia y consumo de tokens).

### Fase 2: Potenciación Multimodal en "Notas y Recordatorios" (`NoteAiProcessor`)
- [ ] En `NoteAiProcessor.kt`, detectar adjuntos de tipo `IMAGE` en la nota o recordatorio.
- [ ] Cargar los bitmaps locales, codificarlos a Base64 y enviarlos a la API multimodal de LiteLLM/Gemini en el mensaje de usuario.
- [ ] Enriquecer el prompt del sistema para que Gemini interprete gráficos, recibos, documentos fotografiados, pizarras y diagramas.
- [ ] Guardar resumen enriquecido, mapa mental y tareas de acción extraídas tanto del texto como de las imágenes.

### Fase 3: Potenciación Multimodal en "Grabadora de Voz IA" (`MeetingAiProcessor`)
- [ ] En `MeetingAiProcessor.kt`, integrar los adjuntos de imágenes (fotos de la reunión, diapositivas, notas en pizarra) dentro del payload multimodal junto a la transcripción de audio.
- [ ] Optimizar la diarización de interlocutores y la estructura de acuerdos y tareas pendientes.

### Fase 4: Experiencia de Usuario y Proyección HUD en Gafas AR
- [ ] En `NoteDetailActivity.kt`:
  - Mejorar `sendToGlasses()` para enviar tanto notificación breve como la nota/resumen completo a `openTeleprompter()` si las gafas están conectadas.
  - Agregar botón de acción rápida para exportar tareas extraídas como Recordatorios reales del sistema.
- [ ] En `RecordingDetailActivity.kt`:
  - Permitir enviar el resumen ejecutivo de la reunión directamente al Teleprompter de las gafas para consulta durante el día.

---

## 3. Protocolo de Verificación
1. **Compilación**: `./gradlew assembleDebug` en OpenJDK 25.
2. **Tests Unitarios**: Validar serialización multimodal en `ToolCallingIntegrationTest`.
3. **Ritual Codegraph**: `codegraph sync` al inicio y fin.
