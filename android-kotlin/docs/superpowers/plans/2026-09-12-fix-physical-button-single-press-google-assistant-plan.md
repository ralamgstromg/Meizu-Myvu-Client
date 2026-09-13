# Plan de Implementación: Corrección del Botón Físico de Montura (Google Assistant No Deseado) y Optimización de Telemetría

**Fecha**: 12 de Septiembre de 2026  
**Autor**: Kog (Caveman Agent)  
**Estado**: Pendiente de Aprobación por Usuario  

---

## 1. Contexto y Diagnóstico Forense en `/home/rcastro/Descargas/myvu_client_log.txt`

El usuario reporta:
> *"Aun se sigue activando el agente de Google cuando presiono el boton fisico de las gafas 1 vez. Valida el log y propon un plan de mejora para todo esto."*

### Diagnóstico Técnico de Causa Raíz:

1. **Clasificación Errónea de Keycode 202 en `GlassGesture.kt`**:
   - En el log oficial (`myvu_client_log.txt`), en las marcas de tiempo `21:43:14.095` y `21:43:20.191`, cuando el usuario presiona el botón físico de la montura una vez, el firmware Flyme XR de las gafas envía:
     ```json
     {"action":"event_tracking","data":{"action":"sync_glass_event","value":[
       {"_event_name_":"key_event","_event_attr_value_":{"key_code":"200","down_or_up":1,"key_event_sender":2}},
       {"_event_name_":"key_event","_event_attr_value_":{"key_code":"202","down_or_up":1,"key_event_sender":2}}
     ]}}
     ```
   - En `GlassGesture.kt`:
     ```kotlin
     2, 202, 211 -> DOUBLE_TAP
     ```
   - El keycode **`202`** estaba catalogado como `DOUBLE_TAP`.
   - En la configuración del usuario / base de datos Room, `DOUBLE_TAP` de las patillas táctiles estaba asignado a `phone_assistant` (`LAUNCH_PHONE_ASSISTANT`).
   - Consecuencia inmediata: La app interpretó la pulsación física simple (`202`) como un **Doble Toque** en la patilla táctil, invocando `TouchGestureManager.launchPhoneAssistant(context)` que lanzó el **Asistente de Google**:
     ```
     [2026-09-12 21:43:14.103] Touch gesture received: DOUBLE_TAP (code=202, name=, sender=2)
     [2026-09-12 21:43:14.104] Touchpad gesture received (DOUBLE_TAP, code=202) -> Action: phone_assistant (LAUNCH_PHONE_ASSISTANT)
     [2026-09-12 21:43:14.122] Dispatched KEYCODE_VOICE_ASSIST for Phone Assistant
     [2026-09-12 21:43:14.139] Launched Phone Assistant via ACTION_VOICE_COMMAND
     ```

2. **Falta de Consolidación de Key Down (200) ante Keycode 202 en `InboundRouter.kt`**:
   - En `InboundRouter.dispatchGestureBatch()`:
     ```kotlin
     val withoutDownUpNoise = deduplicated.filterNot { item ->
         (item.actionValue == 200 || item.actionValue == 203) && deduplicated.any { other ->
             (other.actionValue == 210 || other.actionValue == 230) &&
                     (other.sender == 0 || item.sender == 0 || other.sender == item.sender)
         }
     }
     ```
   - Sólo eliminaba el evento de inicio de pulsación `200` (Key Down) si `210` o `230` estaban presentes. Como llegó `202`, no se eliminó `200`, ejecutando primero `202` (Google Assistant) y 36ms después `200` (Tap secundario).

3. **Invocación Indebida de `wakeRelay()` en `MirrorNotificationListener`**:
   - En `MirrorNotificationListener.kt` (línea 174): al recibir una notificación para el HUD, se ejecutaba `connection.wakeRelay()`. Las notificaciones visuales se transmiten al HUD exclusivamente vía BLE (`sendActionNow`). Despertar forzadamente el relay RFCOMM reactivaba el bucle de reconexión SPP que las gafas habían cerrado para ahorrar batería.

---

## 2. Solución Propuesta

1. **Reclasificar Keycode 202 como `GlassGesture.ACTION_BUTTON`**:
   - En `GlassGesture.kt`: Retirar `202` de `DOUBLE_TAP`. Mapear `202`, `230` y `231` como `ACTION_BUTTON`.
   - Distinguir en `TouchGestureManager.kt`:
     - **Pulsación Corta (`202` o `230`)**: Ejecutar `executor.executeHudDashboard()` (o la acción configurada para un toque corto del botón de montura). Esto abre el HUD nativo de las gafas en lugar de invocar Google Assistant en el celular.
     - **Pulsación Larga (`231` o trigger `code: 3`)**: Ejecutar el asistente configurado para el botón físico (`LAUNCH_GEMINI`, `LAUNCH_GEMINI_LIVE`, `VOICE_AI_FIXED` / Aura, o `LAUNCH_PHONE_ASSISTANT`).
2. **Consolidar Ruido Down (200) con 202 en `InboundRouter.kt`**:
   - Agregar `202` a la condición de consolidación: si `202` está presente junto a `200` del mismo sender, el evento `200` se descarta como ruido mecánico de Key Down.
3. **Asegurar Supresión de Gestos de Patilla tras Pulsación de Montura**:
   - Mantener activa la ventana de supresión de 1200ms (`PHYSICAL_BUTTON_SUPPRESSION_MS`) cuando se procese `202` para ignorar cualquier toque involuntario en la patilla táctil capacitiva.
4. **Remover Despertar Forzado de RFCOMM en `MirrorNotificationListener.kt`**:
   - Eliminar `connection.wakeRelay()` en la entrega de notificaciones HUD para preservar el reposo profundo de la radio SPP de las gafas.

---

## 3. Fases de Implementación

### Fase 1: Corrección de Mapeos de Códigos y Consolidación de Lote
1. **En `GlassGesture.kt`**:
   - Remover `202` de `DOUBLE_TAP`.
   - Incluir `202` en `ACTION_BUTTON`:
     ```kotlin
     2, 211 -> DOUBLE_TAP
     202, 230, 231 -> ACTION_BUTTON
     ```
2. **En `InboundRouter.kt`**:
   - En `dispatchGestureBatch()`: añadir `other.actionValue == 202` al filtro de consolidación de `200` y `203`.
3. **En `TouchGestureManager.kt`**:
   - En `handleGesture()`: asegurar que `rawCode == 202` se identifique inequívocamente como pulsación corta del botón físico (`rawCode == 202 || rawCode == 230`), invocando `executor.executeHudDashboard()`.

### Fase 2: Optimización de Notificaciones y Ahorro de Batería
1. **En `MirrorNotificationListener.kt`**:
   - Remover la llamada a `connection.wakeRelay()`. La notificación se empaqueta y envía de forma óptima a través de BLE sin perturbar el estado de suspensión de SPP.

### Fase 3: Pruebas Unitarias y Verificación
1. Actualizar y ampliar `PhysicalActionButtonConflictTest.kt` e `InboundGestureTest.kt`:
   - Probar que `key_code: 202` con `sender: 2` resuelve a `GlassGesture.ACTION_BUTTON` y no a `DOUBLE_TAP`.
   - Probar que el lote `[ { key_code: 200, sender: 2 }, { key_code: 202, sender: 2 } ]` consolida descartando `200` y despacha únicamente `ACTION_BUTTON`.
   - Probar que la pulsación corta no ejecuta `executePhoneAssistant()`.
2. Compilar y verificar con `rtk proxy ./gradlew testDebugUnitTest assembleDebug`.

### Fase 4: Documentación y Memoria
1. Ejecutar `codegraph sync`.
2. Actualizar `docs/PROJECT_MEMORY.md`, `README.md` y `docs/ARCHITECTURE.md`.

---

## 4. Archivos Afectados

- `app/src/main/java/com/myvu/client/app/feature/GlassGesture.kt`
- `app/src/main/java/com/myvu/client/app/InboundRouter.kt`
- `app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt`
- `app/src/main/java/com/myvu/client/service/MirrorNotificationListener.kt`
- `app/src/test/java/com/myvu/client/app/InboundGestureTest.kt`
- `app/src/test/java/com/myvu/client/app/PhysicalActionButtonConflictTest.kt`
- `docs/PROJECT_MEMORY.md`
- `README.md`
- `docs/ARCHITECTURE.md`
