# Plan de Implementación: Habilidad Gemini Voice Assistant para Meizu MYVU Smart Glasses

## 1. Visión General y Factibilidad Técnica

**¡SÍ ES COMPLETAMENTE POSIBLE!** 
La arquitectura actual de `Meizu-Myvu-Client` en Android Kotlin ya posee las bases fundamentales necesarias:
1. `GlassesMicStream.kt`: Captura tramas de audio transmitidas por Bluetooth desde el micrófono dual de las gafas AR Meizu MYVU.
2. `AudioPipeline.kt`: Procesa, remuestra a 16kHz PCM, limpia ruido y normaliza el volumen del micrófono de las gafas.
3. `SkillEngine.kt`: Motor ejecutor de 21+ habilidades nativas (WhatsApp, Telegram, Llamadas, Clima, Calendario, Navegación HUD).
4. `GeminiLiveService.kt` / `GeminiClient.kt`: Cliente de API para interacción con Gemini.

Esta propuesta expande el cliente para integrar la habilidad **`gemini-live-assistant`**, permitiendo activar Gemini mediante voz o gesto en las gafas y sostener conversaciones continuas manos libres.

---

## 2. Arquitectura del Flujo de Datos

```
 [ Micrófono de Gafas MYVU ] 
            │ (Bluetooth Code 109 - Protobuf Audio Frames)
            ▼
   [ GlassesMicStream.kt ]
            │ (PCM Frame Buffer)
            ▼
    [ AudioPipeline.kt ] ───▶ Resampling 16kHz PCM + VAD + Normalización
            │
            ▼
  [ GeminiLiveService.kt ] ───▶ WebSocket Bidi / REST Multimodal Live API
            │
      ┌─────┴──────────────────────────┐
      ▼                                ▼
[ Audio Output ]             [ Function Calling ]
(A2DP/SCO Altavoz Gafas)               │
                                       ▼
                              [ SkillEngine.kt ] ──▶ (Llamadas, WhatsApp, HUD Nav, etc.)
                                       │
                                       ▼
                             [ HudDisplayManager ] ──▶ Texto en Pantalla AR de Gafas
```

---

## 3. Plan de Implementación Fase por Fase

### Fase 1: Motor de Activación (Triggers)
- **Activación Física (Touch Gesture)**: Interceptar toque prolongado en la patilla de las gafas (`TouchGestureHandler.kt` / evento `0x10`).
- **Activación por Palabra Clave (Wake-Word)**: Integración de VAD liviano local para detectar "Oye Gemini" o "Hey Gemini" usando audio continuo en buffer circular.
- **Indicador Visual en HUD**: Proyectar icono / animación de micro en pantalla AR cuando Gemini está escuchando.

### Fase 2: Transmisión Dúplex de Audio y Conexión Gemini Multimodal Live API
- **Streaming Bidireccional**: Configurar WebSocket `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent`.
- **Modo Audio In / Audio Out**: Enviar fragmentos PCM 16kHz desde el mic de las gafas y recibir audio PCM 24kHz sintetizado por Gemini directamente al altavoz de las gafas.

### Fase 3: Integración Bidireccional con el Skill Engine (Function Calling)
- **Definición de Herramientas (Tools)**: Mapear el catálogo de 21 habilidades nativas (`SkillEngine.kt`) en esquemas JSON de Function Calling para Gemini.
- **Ejecución Automática**: Cuando Gemini decida invocar una habilidad (ej: `create-reminder`, `send-whatsapp`, `hud-navigation`), `SkillEngine` la ejecuta en Android y retorna el resultado a la sesión activa de Gemini.

### Fase 4: Optimización de Interfaz en Gafas AR (Renderizado de Texto Limpio en HUD)
- **Transmisión Exclusiva de Texto Limpio en HUD**: Para la pantalla frontal HUD de las gafas AR, se filtra dinámicamente la respuesta de Gemini removiendo cualquier etiqueta o sintaxis Markdown (`#`, `*`, `_`, `` ` ``, viñetas, enlaces). Se transmite **única y exclusivamente texto plano y limpio** de máximo 2 a 3 líneas breves a `HudDisplayManager.kt`.
- **Modo Privacidad y Silencio**: Permitir mostrar únicamente la respuesta de texto limpio en el visor HUD sin reproducir audio en entornos ruidosos o privados.

---

## 4. Estrategia para Sacar el Máximo Provecho con Gemini

1. **Contexto Multimodal Hands-Free**:
   - Pasar el estado del teléfono (batería, ubicación GPS, eventos inmediatos de calendario y notificaciones no leídas) como system prompt dinámico a Gemini.
2. **Asistente de Navegación y Productividad en Tiempo Real**:
   - Preguntar a las gafas: *"Gemini, ¿dónde es mi próxima reunión y cómo llego?"* -> Gemini invoca `calendar-events` + `hud-navigation`, iniciando la ruta en la pantalla de las gafas.
3. **Resumen de Grabaciones y Reuniones sobre la Marcha**:
   - Usar `ai-voice-recorder` junto a Gemini para resumir conversaciones capturadas por el mic de las gafas y enviar el resumen en Markdown a WhatsApp o Notas.
4. **Respuesta Rápida y baja Latencia**:
   - Uso de VAD dinámico en `GlassesMicStream.kt` para cortar la captura cuando el usuario deja de hablar, logrando respuestas en menos de 800ms.
