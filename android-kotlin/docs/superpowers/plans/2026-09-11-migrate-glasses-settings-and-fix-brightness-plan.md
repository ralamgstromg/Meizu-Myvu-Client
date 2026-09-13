# Plan: Corrección de Brillo de Pantalla AR y Migración de Ajustes de Gafas a GlassesSettingsActivity

## Contexto y Diagnóstico
1. **Problema con el Brillo de Pantalla**:
   - En `GlassesSettingsActivity.kt`, el slider `sliderHudBrightness` utilizaba rango 10..100 (como porcentaje).
   - Al pulsar "Guardar", solo actualizaba el campo `hudBrightness` en la entidad Room de `BluetoothDeviceManager`.
   - **NUNCA** invocaba `GlassesConfig.setBrightness()` ni `ConnectionManager.setBrightness()`, por lo que el comando de hardware `set_brightness` de Flyme XR nunca se transmitía a las gafas.
   - El firmware de las gafas Meizu MYVU espera valores enteros en el rango `1..5` (donde 1 = 20%, 2 = 40%, 3 = 60%, 4 = 80%, 5 = 100%).
2. **Ajustes de Gafas Aislados en Configuración General (`SettingsActivity`)**:
   - `SettingsActivity` contenía múltiples parámetros exclusivos del hardware de las gafas que debían pertenecer a la configuración dedicada del periférico:
     - Pantalla y Audio: Brillo (1..5), Volumen (0..15), Posición del Dashboard en FOV (0..3), Tiempo de pantalla activa (3..60s).
     - Escucha y Ahorro de Batería de Gafas: Escucha activa continua (`swContinuousDialogue`) y Activación por voz / Wake word (`swVoiceWakeup`).
     - Modo de Respuesta de IA para Gafas: Solo voz, Solo pantalla HUD, Voz y pantalla (`btnAiResponseModeGroup`).
     - Duración de Notificaciones en HUD (`sliderNotifDuration`).
     - Tarjeta obsoleta de gestos táctiles de patillas y switch de micrófono SCO de Gemini (`swForceGeminiSco`).

---

## Plan de Implementación

### Fase 1: Enriquecer `GlassesSettingsActivity` y su Layout (`activity_glasses_settings.xml`)
1. **Ajustar Control de Brillo**:
   - Configurar `sliderHudBrightness` con escala de hardware `valueFrom="1"`, `valueTo="5"`, `stepSize="1"`, `value="3"`.
   - Mostrar indicador de nivel y porcentaje: `"Nivel $level (${level * 20}%)"`.
   - Actualizar en vivo y al pulsar Guardar invocando `GlassesConfig.setBrightness(this, level)` y `MyvuService.activeConnection()?.setBrightness(level)`.
2. **Migrar Controles de Pantalla y Audio**:
   - **Volumen de Gafas** (`sliderVolume` 0..15).
   - **Posición FOV del Dashboard** (`sliderStandbyPos` 0..3: Centro, Superior, Inferior, Lateral).
   - **Tiempo de Pantalla Activa** (`sliderScreenOff` 3..60s).
   - **Duración de Notificaciones en HUD** (`sliderNotifDuration` 1..30s).
3. **Migrar Controles de Escucha y Batería de Gafas**:
   - **Escucha Activa Continua** (`swContinuousDialogue`).
   - **Activación por Voz / Wake Word** (`swVoiceWakeup`).
   - **Modo de Respuesta de IA** (`btnAiResponseVoice`, `btnAiResponseVisual`, `btnAiResponseBoth`).
   - **Forzar Micrófono SCO para Gemini** (`swForceGeminiSco`).
4. **Carga y Guardado en `GlassesSettingsActivity.kt`**:
   - En `loadDevice()`: poblar todos los controles con sus valores guardados desde `GlassesConfig`, `Prefs` y `currentDevice`.
   - En `saveSettings()`: persistir todos los valores en `GlassesConfig`, `Prefs`, Room y transmitirlos de inmediato a las gafas conectadas mediante `ConnectionManager`.

### Fase 2: Limpieza de `SettingsActivity` y `activity_settings.xml`
1. **Eliminar de `activity_settings.xml`**:
   - Card "Glasses display & audio" (`sliderBrightness`, `sliderVolume`, `sliderStandbyPos`, `sliderScreenOff`).
   - Card "Escucha y Ahorro de Batería" (`swContinuousDialogue`, `swVoiceWakeup`).
   - Card "AI response mode" (`btnAiResponseModeGroup`).
   - Control `sliderNotifDuration` y `lblNotifDuration` dentro de la tarjeta de notificaciones (conservando `swMirror` y `btnPickApps`).
   - Card obsoleta "Gestos del Touchpad de las Gafas" (`actTouchpadTap`..`LongPress`, `swForceGeminiSco`).
2. **Limpiar Código en `SettingsActivity.kt`**:
   - Remover llamadas y métodos: `wireGlassesSettings()`, `configureResponseMode()`, `configureListeningSettings()`, `wireTouchpad()`.
   - Remover referencias huérfanas de vistas eliminadas.

### Fase 3: Pruebas y Verificación
1. Ejecutar pruebas unitarias con `rtk ./gradlew testDebugUnitTest`.
2. Compilar APK con `rtk ./gradlew assembleDebug`.
3. Sincronizar grafo de código con `rtk codegraph sync`.
4. Actualizar documentación en `docs/PROJECT_MEMORY.md`, `docs/ARCHITECTURE.md` y `README.md`.
