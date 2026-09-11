# Plan: Reversión de Re-bloqueo Automático Inteligente de Pantalla

## 1. Contexto y Diagnóstico
- **Motivo de reversión**: El usuario reporta que el re-bloqueo automático (`lockDeviceScreen()` vía `GLOBAL_ACTION_LOCK_SCREEN`) está cancelando las sesiones de Gemini de manera prematura e interrumpiendo tareas que requieren que la pantalla permanezca desbloqueada/activa para ejecutar ciertas acciones (ej. llamadas, transcripciones o envíos pendientes).
- **Alcance**: Retirar por completo el mecanismo de re-bloqueo automático y sus disparadores asociados en `AutoSendAccessibilityService`, `TouchGestureManager`, `PhoneActionExecutor`, `LockScreenHelper`, `Prefs`, `SettingsActivity` y `activity_settings.xml`. Mantener intactas las mejoras de estabilidad SPP, debouncing y Bluetooth SCO.

---

## 2. Archivos a Modificar y Cambios Específicos

### 1. `app/src/main/java/com/myvu/client/core/LockScreenHelper.kt`
- Eliminar los métodos `lockDeviceScreen()` y `scheduleAutoLock()`.
- Mantener las funciones originales de encendido de pantalla (`wakeUpScreen`), bypass de keyguard (`unlockKeyguard`, `setupShowWhenLocked`, `isDeviceLocked`).

### 2. `app/src/main/java/com/myvu/client/core/Prefs.kt`
- Eliminar `KEY_AUTO_LOCK_AFTER_VOICE_ACTION`, `isAutoLockAfterActionEnabled(c: Context)` y `setAutoLockAfterActionEnabled(c: Context, enabled: Boolean)`.
- Mantener `KEY_GEMINI_FORCE_SCO`, `isGeminiForceScoEnabled`, etc.

### 3. `app/src/main/java/com/myvu/client/service/AutoSendAccessibilityService.kt`
- Eliminar `isGeminiWatchActive`, `geminiWatchRunnable`, `geminiHadFocus`, `armGeminiAutoLock`, `cancelGeminiAutoLock`.
- Eliminar `wasLockedOnTrigger` y las llamadas a `scheduleAutoLock` en `scheduleBurstRetries`, `scheduleBurstCallRetries` y `onAccessibilityEvent`.
- Eliminar `isTransientSystemUi(pkg: String)`.
- Revertir `onAccessibilityEvent` para procesar únicamente el click de botón de llamada y mensaje sin programar bloqueos de pantalla.

### 4. `app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt`
- En `launchGeminiAssistant`, remover la comprobación de `wasDeviceLocked` y la invocación a `AutoSendAccessibilityService.armGeminiAutoLock(appContext, 18000L)`.

### 5. `app/src/main/java/com/myvu/client/ai/PhoneActionExecutor.kt`
- En `makeWhatsAppCall`, remover `scheduleAutoLock` tras invocar `SendTrampolineActivity.launchWithKeyguardDismiss`.

### 6. `app/src/main/res/layout/activity_settings.xml`
- Remover el switch `swAutoLockAfterAction` y su TextView descriptivo.
- Conservar `swForceGeminiSco` y su descripción.

### 7. `app/src/main/java/com/myvu/client/ui/SettingsActivity.kt`
- Remover el binding y listener de `swAutoLockAfterAction`.

### 8. Documentación (`docs/PROJECT_MEMORY.md`, `docs/ARCHITECTURE.md`, `README.md`)
- Actualizar notas en memoria viva indicando la reversión del auto-lock por interferir con el ciclo de vida de Gemini y actividades de desbloqueo.
- Limpiar referencias en arquitectura y README.

---

## 3. Plan de Verificación
1. Ejecutar `./gradlew testDebugUnitTest` para certificar que todos los tests unitarios pasen sin errores.
2. Ejecutar `./gradlew assembleDebug` para certificar la compilación limpia del APK.
3. Ejecutar `codegraph sync`.
