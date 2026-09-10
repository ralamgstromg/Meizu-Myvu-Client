# Plan de Implementación: Re-Bloqueo Automático del Celular tras Gemini, Llamadas y Acciones de Voz

> **Objetivo:** Permitir que el teléfono se bloquee de nuevo automáticamente cuando se complete una consulta de Gemini, una llamada de WhatsApp/VoIP o un envío de mensaje, evitando que la pantalla permanezca encendida o desbloqueada en el bolsillo del usuario.

---

## 1. Análisis Técnico y Factibilidad

### ¿Es Posible en Android?
**¡SÍ, 100% FACTIBLE Y NATIVO!**
A partir de **Android 9 (API 28 - Pie)**, Android incorporó la acción global de accesibilidad:
```kotlin
accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
```
- **Ventajas críticas:**
  1. **Sin permisos de administrador de dispositivo (Device Admin)** ni necesidad de Root o ADB.
  2. **Conserva Biometría y Smart Lock**: A diferencia de las APIs antiguas que forzaban PIN, esta acción bloquea la pantalla exactamente igual que presionar el botón físico de encendido.
  3. **Servicio Ya Activo**: El proyecto ya cuenta con `AutoSendAccessibilityService` corriendo permanentemente en el celular del usuario (`Universal Mode`).

---

## 2. Flujo de Re-Bloqueo por Escenario

### Escenario A: Llamadas y Mensajes de WhatsApp / Telegram
1. El usuario pide por voz desde las gafas: *"Llama a mamá por WhatsApp"* o *"Envía WhatsApp a Juan..."*.
2. El sistema verifica si el teléfono estaba bloqueado (`LockScreenHelper.isDeviceLocked(context)`).
3. Se despierta la pantalla y se despacha la llamada o mensaje.
4. `AutoSendAccessibilityService` detecta el botón y ejecuta el clic de envío o llamada:
   - **En Mensajes**: Tras el clic de enviar (espera de 1.5s para confirmar salida del paquete), se ejecuta `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`.
   - **En Llamadas VoIP**: Tras iniciar la llamada (espera de 3s para que el canal de audio y la pantalla de llamada se enlacen a los auriculares de las gafas), se bloquea la pantalla. La llamada continúa con audio bidireccional en las gafas con la pantalla del celular apagada y protegida contra toques accidentales en el bolsillo.

### Escenario B: Consulta a Gemini por Doble Toque
1. El usuario hace doble toque en la patilla de las gafas.
2. Si el teléfono estaba bloqueado, se marca la sesión como `geminiActiveFromLock = true`.
3. Se desbloquea/muestra Gemini y escucha la orden por voz.
4. **Detección de Finalización de Respuesta**:
   - **Mecanismo 1 (Evento de Ventana)**: `AutoSendAccessibilityService` monitorea los eventos de `AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED` de `com.google.android.googlequicksearchbox` / `com.google.android.apps.bard`. Cuando el diálogo o tarjeta de Gemini se descarta/cierra, se dispara el re-bloqueo inmediato.
   - **Mecanismo 2 (Temporizador de Inactividad Inteligente)**: Si el usuario no interactúa físicamente con la pantalla del celular en un lapso configurable (ej. 15-20 segundos después de la respuesta de voz), el sistema asume que la consulta finalizó y re-bloquea el teléfono automáticamente.

---

## 3. Arquitectura de Componentes a Modificar

| Componente | Archivo | Modificación |
|---|---|---|
| **API de Bloqueo** | `core/LockScreenHelper.kt` | Métodos `lockDeviceScreen()` y `scheduleAutoLock(delayMs)` utilizando `AutoSendAccessibilityService.activeInstance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`. |
| **Monitoreo de Eventos** | `service/AutoSendAccessibilityService.kt` | Añadir flags `wasLockedOnAction`, monitoreo de ciclo de vida de ventana de Gemini (`com.google.android.googlequicksearchbox`) y disparar re-bloqueo al cerrar la tarjeta o al completar el auto-send/auto-call. |
| **Activación en Gestos** | `app/feature/TouchGestureManager.kt` | Al lanzar Gemini con pantalla bloqueada, registrar `AutoSendAccessibilityService.armGeminiAutoLock()`. |
| **Acciones de Teléfono** | `ai/PhoneActionExecutor.kt` | Al ejecutar `makeWhatsAppCall`, `openWhatsApp` o `sendLocationToContact` con pantalla bloqueada, armar auto-bloqueo post-ejecución. |
| **Configuración de Usuario** | `core/Prefs.kt` & `SettingsActivity.kt` | Preferencia `auto_lock_after_assistant` (habilitada por defecto) para que el usuario pueda activar/desactivar este comportamiento desde Ajustes. |

---

## 4. Tareas Detalladas de Implementación

### Tarea 1: Implementar `lockDeviceScreen()` en `LockScreenHelper.kt`
- Integrar llamada segura con `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`.
- Manejar fallback limpio en caso de que el servicio de accesibilidad esté temporalmente desconectado.

### Tarea 2: Re-bloqueo en Auto-Send y Auto-Call (`AutoSendAccessibilityService.kt`)
- Tras `findAndClickSendButton`: si `wasDeviceLockedOnTrigger == true`, programar `scheduleAutoLock(1500L)`.
- Tras `findAndClickCallButton` o inicio de VoIP: si `wasDeviceLockedOnTrigger == true`, programar `scheduleAutoLock(3000L)`.

### Tarea 3: Re-bloqueo para Gemini (`AutoSendAccessibilityService.kt` + `TouchGestureManager.kt`)
- Armar observador de estado cuando el doble toque despierte el teléfono.
- Detectar cuando la ventana de Gemini pierde foco o vence el timeout de interacción por voz, y re-bloquear la pantalla.

### Tarea 4: Opciones en UI y Preferencias (`Prefs.kt` y `activity_settings.xml`)
- Switch: *"Bloquear celular tras asistente y llamadas"* (por defecto activado para máxima privacidad).
- Tiempo de gracia ajustable (ej. Inmediato / 5s / 15s).

### Tarea 5: Pruebas y Verificación
- Pruebas unitarias de las nuevas rutas en `AutoSendAccessibilityServiceTest.kt` y `LockScreenHelperTest.kt`.
- Ejecutar `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.
- Documentar en `PROJECT_MEMORY.md` y sincronizar con `codegraph sync`.
