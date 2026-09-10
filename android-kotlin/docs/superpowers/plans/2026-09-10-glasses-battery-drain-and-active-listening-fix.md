# Plan: Análisis de Drenaje de Batería de Gafas y Desactivación de Escucha Activa por Defecto

## 1. Análisis del Log (`myvu_client_log.txt`)
En la sesión analizada (08:52:42 a 09:22:25, ~30 minutos):
- **Evolución del nivel de batería de las gafas**:
  - 08:52:42 -> 23%
  - 08:55:09 -> 22%
  - 08:58:16 -> 21%
  - 09:02:22 -> 20%
  - 09:06:24 -> 19%
  - 09:10:25 -> 18%
  - 09:14:06 -> 17%
  - 09:18:07 -> 16%
  - 09:21:39 -> 15%
  - **Drenaje neto**: 8% de batería consumido en 29 minutos (aprox. **16.5% por hora**) en modo prácticamente pasivo/reposo (solo notificaciones recibidas de Telegram y WhatsApp).
- **Causa Raíz Principal**:
  - En la línea 110 del log:
    ```
    08:52:48.726 -> action msgId=40 [ble] {"code":2,"payload":{"hasWakeupVoicePrint":false,"isAsrResultScreenEnable":true,"isChatGptCardDisplayEnable":true,"isChatGptTTSPlayEnable":true,"isContinuousDialogueEnable":true,"isLowPowerWakeupEnable":false,"isLowPowerWakeupScreenOffEnable":false,"isNetworkAvailable":true,"isWakeupVoiceRecording":false,"ttsTimbreValue":0}}
    ```
  - **`isContinuousDialogueEnable`: true** está fijado en código duro (`hardcoded`) dentro de `AiProtocol.assistantConfig`.
  - En el firmware FlymeAR de Meizu Myvu, `isContinuousDialogueEnable: true` activa el modo de diálogo continuo / escucha activa permanente en el DSP de audio de las gafas, manteniendo el subsistema de reconocimiento y micrófono en estado de alerta constante esperando que el usuario continúe hablando sin tocar la patilla.
  - **`isLowPowerWakeupEnable`**: Ya estaba en `false` porque `Prefs.voiceWakeupEnabled(context)` tiene como valor por defecto `false`. Sin embargo, `isContinuousDialogueEnable` no estaba controlado por preferencias y se enviaba siempre activo (`true`).
  - No existía control visual en `SettingsActivity` para permitir al usuario ver y alternar el estado de la escucha activa continua ni del despertar por voz.

## 2. Objetivos
1. Deshabilitar por defecto la escucha activa continua (`isContinuousDialogueEnable = false`) en `AiProtocol.assistantConfig`.
2. Crear la preferencia `continuousDialogueEnabled` en `Prefs.kt`, inicializada por defecto en `false`.
3. Conectar `Prefs.continuousDialogueEnabled` y `Prefs.voiceWakeupEnabled` en `ConnectionManager.kt` y `AiConversation.kt`.
4. Añadir controles en `activity_settings.xml` y `SettingsActivity.kt` para que el usuario pueda activar/desactivar explícitamente:
   - "Escucha activa continua" (`swContinuousDialogue`), con advertencia de consumo de batería.
   - "Activación por palabra clave / Wake word" (`swVoiceWakeup`), con advertencia de consumo de batería.
5. Transmitir inmediatamente la nueva configuración `assistantConfig` a las gafas cuando el usuario modifique cualquiera de estos switches en Ajustes, sin necesidad de reconectar manualmente.
6. Añadir pruebas unitarias que garanticen que la configuración por defecto de `AiProtocol.assistantConfig()` envía `isContinuousDialogueEnable: false` e `isLowPowerWakeupEnable: false`.
7. Ejecutar suite de pruebas y compilación de APK debug.
8. Actualizar la memoria y documentación viva del proyecto.

## 3. Fases de Implementación
- **Fase 1: Preferencias de Usuario (`Prefs.kt`)**
  - Añadir `continuousDialogueEnabled(Context): Boolean` (default `false`).
  - Añadir `setContinuousDialogueEnabled(Context, Boolean)`.
- **Fase 2: Protocolo de IA (`AiProtocol.kt`)**
  - Modificar `assistantConfig(lowPowerWakeupEnabled: Boolean = false, continuousDialogueEnabled: Boolean = false): String`.
  - Asegurar que `isContinuousDialogueEnable` use el parámetro (default `false`).
- **Fase 3: Transmisión en Conexión y Conversación**
  - En `ConnectionManager.kt`: enviar `AiProtocol.assistantConfig(Prefs.voiceWakeupEnabled(context), Prefs.continuousDialogueEnabled(context))`.
  - En `AiConversation.kt`: sincronizar en `begin()` y `askText()`.
- **Fase 4: Interfaz de Usuario (`activity_settings.xml` & `SettingsActivity.kt`)**
  - Diseñar sección en Ajustes con switches Material 3 para Escucha Activa Continua y Activación por Voz.
  - Enlazar bindings y listeners con actualización en tiempo real a las gafas vía `MyvuService`.
- **Fase 5: Pruebas Unitarias y Compilación**
  - Crear `AiProtocolTest.kt` validando los payloads JSON generados y sus valores por defecto.
  - Correr `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.
- **Fase 6: Sincronización y Memoria Viva**
  - Ejecutar `codegraph sync`.
  - Actualizar `docs/PROJECT_MEMORY.md`, `README.md` y `docs/ARCHITECTURE.md`.
