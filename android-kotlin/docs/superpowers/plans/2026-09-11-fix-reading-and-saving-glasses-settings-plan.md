# Plan de Mejora: Corrección de Lectura, Guardado y Aplicación Integral de Parámetros en Gafas AR (Meizu MYVU)

## 1. Diagnóstico y Causas Raíz

Al presionar el botón de "Guardar Cambios" en `GlassesSettingsActivity`:
1. **Falta de Asignación de `currentDevice`**:
   - En `GlassesSettingsActivity.loadDevice()`, se consultaba la entidad `val dev = ...`, pero la variable de instancia `currentDevice = dev` **nunca se ejecutaba**.
   - Por tanto, `currentDevice` permanecía perpetuamente en `null`.
   - Al cargar la pantalla, los gestos (`tap`, `doubleTap`, etc.) y el modo de notificación (`currentNotifMode`) leían `null` de `currentDevice`, haciendo que el selector de modo de notificaciones se reiniciara a `BOTH` y los gestos no se sincronizaran desde la base de datos Room.
2. **`targetMac` Vacío y Omisión de Guardado en Room**:
   - Cuando se abre `GlassesSettingsActivity` desde `SettingsActivity` o `ChatActivity` sin el extra `DEVICE_MAC` (o con string vacío), `targetMac` quedaba en blanco (`""`).
   - Al guardar, `BluetoothDeviceManager.updateGlassesGestures(mac = targetMac, ...)` buscaba `dao.getDevice("")` que retornaba `null`, y **omitía por completo la inserción o actualización** en la base de datos Room (`if (dev != null)`).
3. **Mapeo Incompleto de Acciones en `CommonDeviceActions`**:
   - Acciones históricas o variantes en minúsculas en `Prefs` (`"phone_assistant"`, `"ai_assistant"`, `"launch_local_ai"`, `"gemini_live"`) no coincidían directamente con los IDs en mayúsculas de `CommonDeviceActions` (`"LAUNCH_PHONE_ASSISTANT"`, `"VOICE_AGENT_AURA"`, `"LAUNCH_GEMINI_LIVE"`), provocando que `getIndexForAction` retornara el índice de `"NONE"`.
4. **Despacho Directo al Hardware en `saveSettings`**:
   - Al presionar guardar, se debe asegurar que todos los comandos (`set_brightness`, `set_volume`, `set_standby_position`, `set_screen_off_time`, `set_music_tp_control`, `assistantConfig`) se emitan directamente hacia `MyvuService.activeConnection()` sin depender únicamente de reflexión.
5. **Comportamiento del Botón Guardar y Confirmación Visual**:
   - En lugar de cerrar intempestivamente la pantalla sin certeza, se debe garantizar la persistencia atómica en Room, Prefs y Hardware, actualizando `currentDevice` en memoria, mostrando Toast claro de éxito y permitiendo que el usuario verifique sus ajustes.

---

## 2. Plan de Acción

### Paso 1: Robustecer `CommonDeviceActions.getIndexForAction`
- En `CommonDeviceActions.kt`:
  - Agregar normalización mediante `GestureAction.fromId(actionId)` para reconocer sinónimos y variantes (`"phone_assistant"` -> `"LAUNCH_PHONE_ASSISTANT"`, `"launch_local_ai"` / `"ai_assistant"` -> `"VOICE_AGENT_AURA"`, `"gemini_live"` -> `"LAUNCH_GEMINI_LIVE"`, `"none"` -> `"NONE"`).

### Paso 2: Blindar `BluetoothDeviceManager.updateGlassesGestures`
- En `BluetoothDeviceManager.kt`:
  - Resolver la dirección MAC de destino: si `mac` es blanca, utilizar `Prefs.targetMac(context)`, o buscar el dispositivo de tipo `SMART_GLASSES` en `dao.getAllDevices()`, o fallback a `"MYVU-GLASSES-01"`.
  - Si no existe entidad en Room, crear una nueva `BluetoothDeviceEntity` con tipo `SMART_GLASSES` y persistirla inmediatamente (`dao.insertOrUpdate`).
  - Persistir también en `Prefs` todas las acciones y gestos para que `TouchGestureManager` y `ConnectionManager` respondan al instante.

### Paso 3: Corregir Lectura (`loadDevice`) y Guardado (`saveSettings`) en `GlassesSettingsActivity`
- En `GlassesSettingsActivity.kt`:
  - En `loadDevice()`:
    - Asignar `currentDevice = dev`.
    - Si `targetMac` viene vacío, resolverlo con `dev?.macAddress` o `Prefs.targetMac(this)`.
    - Asignar valores a todos los controles (Spinners, Sliders de brillo, volumen, standby, timeout de pantalla, duración notificaciones, toggle group de respuesta IA, y switches).
    - Leer el brillo inicial considerando `currentDevice?.hudBrightness?.let { (it / 20).coerceIn(1, 5) }` o `GlassesConfig.getBrightness(this)`.
  - En `saveSettings()`:
    - Persistir en `GlassesConfig` y `Prefs` todas las variables (brillo, volumen, standby, screen off, notif duration, modo respuesta IA, diálogo continuo, wake word, SCO Gemini, gestos y botón de acción).
    - Emitir directamente a `MyvuService.activeConnection()`:
      - `conn?.setBrightness(brightness)`
      - `conn?.setVolume(volume)`
      - `conn?.setStandbyPosition(standbyPos)`
      - `conn?.setScreenOffTime(screenOff)`
      - `conn?.setMusicTpControl(true)`
      - `conn?.sendAction(...)` con `AiProtocol.assistantConfig(...)`.
    - Persistir en Room DB vía DAO / `BluetoothDeviceManager`:
      - Guardar la entidad completa `BluetoothDeviceEntity`.
      - Actualizar `currentDevice` en memoria.
    - Notificar con Toast de éxito visible.

### Paso 4: Pruebas Unitarias y Validación
- Actualizar `SettingsGestureConfigTest.kt` para comprobar:
  - Carga de entidad existente y lectura correcta en spinners y sliders.
  - Guardado con MAC resuelta y verificación en Room DB, `GlassesConfig` y `Prefs`.
  - Re-lectura simulada tras guardar para confirmar que los valores persisten al 100%.
- Ejecutar `./gradlew testDebugUnitTest`.
- Ejecutar `./gradlew assembleDebug`.
- Ejecutar `codegraph sync`.
- Actualizar memoria del proyecto en `docs/PROJECT_MEMORY.md`.
