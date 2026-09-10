# Plan de Implementación: Optimización de Audio para Gemini y Aceleración de Reconexión de Relay

> **Objetivo:** Eliminar la caída temporal del socket SPP tras invocar a Gemini mediante doble toque, evitando la conmutación forzada de Bluetooth SCO por defecto y acelerando la reconexión de RelaySupervisor en caso de desconexiones imprevistas.

---

## 1. Diagnóstico Clínico de la Causa Raíz

En el último log (`16:47:18.227`):
- `16:47:13.624`: `TouchGestureManager` ejecuta `am.mode = AudioManager.MODE_IN_COMMUNICATION` y `am.setCommunicationDevice(btScoDevice)`.
- `16:47:18.141`: Al vencer el temporizador de 4.5s (`GEMINI_SCO_CAPTURE_WINDOW_MS`), se ejecuta `clearCommunicationDevice()` y `am.mode = AudioManager.MODE_NORMAL`.
- `16:47:18.227` (exactamente 86ms después): `<- SPP server closed by the glasses -- dropping the relay`.

**Causa Raíz:**
El firmware de las gafas Meizu Myvu (Flyme XR) reinicia su subsistema de socket SPP cuando Android finaliza abruptamente la sesión telefónica HFP/SCO para regresar al perfil A2DP. Dado que Android y la app de Google / Gemini ya tienen soporte nativo para micrófonos Bluetooth vía `ACTION_VOICE_SEARCH_HANDS_FREE` y `KEYCODE_VOICE_ASSIST`, forzar externamente el modo SCO es innecesario en la mayoría de los casos y perjudicial para la estabilidad del socket SPP.

---

## 2. Acciones Propuestas

### Tarea 1: Añadir Preferencia para Enrutamiento SCO en `Prefs.kt`
- Añadir `KEY_GEMINI_FORCE_SCO = "gemini_force_sco"`.
- `isGeminiForceScoEnabled(c: Context): Boolean` con valor predeterminado `false` (Recomendado para máxima estabilidad del enlace).
- `setGeminiForceScoEnabled(c: Context, enabled: Boolean)`.

### Tarea 2: Condicionar Enrutamiento Forzado de SCO en `TouchGestureManager.kt`
- Evaluar `Prefs.isGeminiForceScoEnabled(appContext)`.
- Si está desactivado (`false`): omitir `setCommunicationDevice` y la programación del temporizador `clearCommunicationDevice()`. Registrar en `LogBus`: *"Using native system Bluetooth routing for Gemini (SCO force disabled, preserving SPP stability)"*.
- Si está activado (`true`): mantener la lógica de conmutación SCO existente para usuarios que requieran forzar el canal de micrófono.

### Tarea 3: Interruptor en Pantalla de Ajustes (`activity_settings.xml` y `SettingsActivity.kt`)
- Añadir `swForceGeminiSco` en la sección de gestos de `activity_settings.xml`.
- Enlazar el estado del switch con `Prefs.isGeminiForceScoEnabled` en `SettingsActivity.kt`.

### Tarea 4: Acelerar Intervalo Inicial de Reconexión en `RelaySupervisor.kt`
- Reducir `INITIAL_DISCONNECTED_POLL_MS` de `5000L` a `2000L` en `RelaySupervisor.kt`.
- Actualizar `RelaySupervisorTest.kt` para reflejar el nuevo cálculo de backoff inicial (2s, 4s, 8s, 16s, 32s, 60s).
- Esto permite que, si el socket SPP se cierra por cualquier motivo de hardware o rango, el relay se reconecte en **2 segundos** en lugar de esperar 5 segundos fijos.

### Tarea 5: Verificación y Pruebas
- Ejecutar pruebas unitarias completas: `./gradlew testDebugUnitTest`.
- Compilar APK debug: `./gradlew assembleDebug`.
- Actualizar documentación y bitácora en `PROJECT_MEMORY.md`.
- Sincronizar con `codegraph sync`.
