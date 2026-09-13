# Plan: Deshabilitar Escucha Activa por Defecto con Toggle Individual

## Diagnóstico

### Estado actual:
- `Prefs.continuousDialogueEnabled()` ya retorna `false` por defecto (SharedPrefs key `continuous_dialogue_enabled`)
- `AiProtocol.assistantConfig()` tiene `continuousDialogueEnabled: Boolean = false` por defecto
- `GlassesSettingsActivity` tiene switch `swContinuousDialogue` que lee/escribe de `Prefs` (global)
- `HeadphoneSettingsActivity` NO tiene toggle de escucha activa
- `BluetoothDeviceEntity` NO tiene campo `activeListeningEnabled` per-device
- `ConnectionManager` en L1063 y L1147 envía el flag al firmware desde `Prefs.continuousDialogueEnabled(context)` — es GLOBAL, no per-device
- `AiConversation` L237 y L745 también usa `Prefs.continuousDialogueEnabled(context)` — GLOBAL

### Problema:
El flag es global, no per-device. Se necesita:
1. Campo per-device en la entidad Room
2. Que el toggle en GlassesSettings lea/escriba del campo per-device (no de Prefs global)
3. Toggle nuevo en HeadphoneSettings para audífonos
4. Default `false` en la entidad
5. Los consumers (ConnectionManager, AiConversation) deben leer del device activo, no de Prefs global

## Tareas

### T1: Agregar campo `activeListeningEnabled` a `BluetoothDeviceEntity`
- Nuevo campo: `val activeListeningEnabled: Boolean = false`
- Default `false` = deshabilitado por defecto para TODOS los devices

### T2: Migrar `GlassesSettingsActivity` a per-device
- `loadDevice()`: Leer `activeListeningEnabled` del entity cargado en vez de `Prefs.continuousDialogueEnabled()`
- `saveSettings()`: Guardar en el entity `activeListeningEnabled`, y TAMBIÉN escribir `Prefs.setContinuousDialogueEnabled()` para compatibilidad con code paths legacy

### T3: Agregar switch `switchActiveListening` a layout de HeadphoneSettings
- Agregar `MaterialSwitch` al XML `activity_headphone_settings.xml` en la card de "Voz y Asistencia"
- Texto: "Escucha Activa / Diálogo Continuo" con descripción

### T4: Conectar toggle en `HeadphoneSettingsActivity`
- `findViewById` del nuevo switch
- `loadDevice()`: leer `activeListeningEnabled` del entity
- `saveSettings()`: guardar en entity

### T5: Actualizar consumers para leer per-device
- `ConnectionManager` L1063 y L1147: reemplazar `Prefs.continuousDialogueEnabled(context)` con lectura del device activo de Room
- `AiConversation` L237 y L745: igual
- Crear helper `BluetoothDeviceManager.isActiveListeningEnabled(context)` que lea del device conectado

### T6: Tests
- Verificar que entity nueva compila con default false
- Verificar que el switch en glasses settings persiste per-device
- Verificar que headphone settings tiene y persiste el toggle

### T7: Build + Docs
- `./gradlew assembleDebug`
- `codegraph sync`
- Actualizar PROJECT_MEMORY.md, ARCHITECTURE.md

## Archivos a modificar
1. `data/BluetoothDeviceEntity.kt` — nuevo campo
2. `ui/GlassesSettingsActivity.kt` — migrar a per-device
3. `res/layout/activity_headphone_settings.xml` — nuevo switch
4. `ui/HeadphoneSettingsActivity.kt` — conectar switch
5. `service/BluetoothDeviceManager.kt` — helper `isActiveListeningEnabled()`
6. `service/ConnectionManager.kt` — usar helper per-device
7. `ai/AiConversation.kt` — usar helper per-device
8. Tests — nuevos test cases
