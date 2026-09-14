# Plan de Implementación: Corrección de Prueba de Voz TTS y Notificaciones Multi-Dispositivo (HUD, TTS, Ambas)

## 1. Diagnóstico del Problema
1. **Fallo en "Probar Voz del Asistente (TTS)" en `HeadphoneSettingsActivity`**:
   - `TextToSpeechHelper` requiere una inicialización previa mediante `TextToSpeechHelper.init(context)`.
   - `HeadphoneSettingsActivity` llamaba directamente a `TextToSpeechHelper.speak(...)` en el listener de `btnTestVoice` sin asegurarse de invocar `init(this)` en `onCreate()`.
   - Si `MyvuService` o `MirrorNotificationListener` no se encontraban en ejecución activa, el motor de TTS de Android era `null` o no estaba inicializado, por lo que la petición de hablar quedaba encolada o no se escuchaba.
   - Si el motor aún se está inicializando al hacer clic, debe asegurarse de encolar y reproducir al completar `onInit()`, y además forzar la inicialización proactiva en `onCreate()` de la actividad.

2. **Requerimiento: Notificaciones vía HUD si está disponible, o Auditiva (TTS), o Ambas para Cualquier Dispositivo**:
   - `HeadphoneSettingsActivity`: La UI actual solo ofrecía o restringía la percepción a TTS (`AUDIO_ONLY` o `NONE`), y si un usuario seleccionaba unos auriculares u otro dispositivo BT, también debe poder optar por ver notificaciones en HUD si las gafas están disponibles/conectadas, o solo auditivas, o ambas, o ninguna.
   - `DeviceNotificationMode`: Ya cuenta con `BOTH`, `VISUAL_ONLY`, `AUDIO_ONLY`, `NONE`.
   - `MirrorNotificationListener`:
     - Al procesar una notificación entrante, actualmente busca si `activeDevices.firstOrNull { it.isAudioNotificationEnabled() }` existe para TTS.
     - Y para HUD, comprueba si `connection = MyvuService.activeConnection()` está activo y si las gafas tienen habilitado visual.
     - Si cualquier dispositivo conectado o el dispositivo activo/primario configurado tiene modo `BOTH` o `VISUAL_ONLY`, y las gafas están conectadas, la notificación debe mostrarse en el HUD de las gafas sin importar desde qué dispositivo se configuró la preferencia de notificación, o si el usuario configuró sus auriculares para que el HUD también proyecte la alerta mientras ellos escuchan el TTS.
     - Igualmente, el botón de "Probar Notificación" en `ConnectActivity` (`btnNotify`) debe despachar tanto a gafas HUD (si está conectado el servicio) como a TTS (si el dispositivo activo o auriculares tienen audio habilitado), para que el usuario pueda verificar instantáneamente la recepción visual y auditiva sin esperar un SMS o mensaje real.

## 2. Cambios a Realizar

### Paso 1: `TextToSpeechHelper.kt` y `HeadphoneSettingsActivity.kt`
- En `HeadphoneSettingsActivity.onCreate()`:
  - Invocar explícitamente `TextToSpeechHelper.init(this)`.
- En `btnTestVoice.setOnClickListener`:
  - Asegurar `TextToSpeechHelper.init(this)`.
  - Notificar vía Toast o feedback visual el estado del motor TTS.
  - Reproducir el mensaje de prueba.

### Paso 2: Opciones de Notificación en `HeadphoneSettingsActivity`
- Permitir los 4 modos completos en el spinner de `HeadphoneSettingsActivity` (`BOTH`, `VISUAL_ONLY`, `AUDIO_ONLY`, `NONE`).
- Sincronizar el switch `switchAutoReadNotifications` con el modo seleccionado (`isAudioNotificationEnabled()`).
- Explicar claramente que si se elige HUD o Ambas, se mostrará en el visor de las gafas inteligentes si están disponibles y conectadas.

### Paso 3: Enrutamiento en `MirrorNotificationListener.kt`
- Verificar si cualquier dispositivo activo/conectado requiere notificación auditiva (TTS): si es así, sintetizar por voz.
- Verificar si cualquier dispositivo activo/conectado (o las gafas) requiere notificación visual (HUD): si las gafas están conectadas (`MyvuService.activeConnection() != null`), proyectar en el HUD.
- Si las gafas están conectadas pero el dispositivo activo es un auricular con modo `BOTH`, proyectar en HUD y hablar por auricular.

### Paso 4: Notificación de Prueba en `ConnectActivity.kt` (`btnNotify`)
- En `ConnectActivity.kt`, al presionar el botón de probar notificación:
  - Si el HUD de gafas está conectado, enviar al HUD.
  - Si el dispositivo activo o conectado tiene audio/TTS activo, reproducir también por `TextToSpeechHelper.speak(...)`.
  - Si no hay gafas conectadas pero sí auriculares o audio, reproducir por TTS y toast local.

### Paso 5: Pruebas Unitarias y Verificación
- Actualizar y ejecutar los tests unitarios en `BluetoothDeviceManagerTest` y `SettingsGestureConfigTest`.
- Compilar con `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.
- `rtk codegraph sync`.
- Documentar en `docs/PROJECT_MEMORY.md`, `docs/ARCHITECTURE.md` y `README.md`.
