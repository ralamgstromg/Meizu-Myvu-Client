# Plan Detallado: Restaurar Botón Físico (STT -> Modelo) y Habilitar Gestos de las Patas para Gemini

## 1. Diagnóstico del Problema y Hallazgos en el Log

### 1.1 Hallazgos Críticos en `/home/rcastro/Descargas/myvu_client_log.txt`
1. **Botón Físico Secuestrado**:
   - En `myvu_client_log.txt`:
     ```
     14:14:58.744  Hardware trigger: code=3 (button/deep-touch)
     14:14:58.747  Touchpad gesture received (LONG_PRESS, code=3) -> Action: none (NONE)
     ```
   - Al pulsar el botón de las gafas, el sistema emite el trigger `code=3`.
   - En la implementación anterior, `code=3` fue interceptado y mapeado a `GlassGesture.LONG_PRESS` en `ConnectionManager.kt` y `GlassesEventHandler.kt`.
   - Como la acción de pulsación larga estaba configurada en `NONE` (o modificada), el botón físico dejó de ejecutar el flujo nativo **STT -> Modelo configurado**.
   - **Requisito expreso del usuario**: El botón físico de las gafas DEBE regresar a su comportamiento original: ejecutar siempre el modelo STT -> IA configurado (`ai().onTrigger(code)`), sin ser interceptado por la personalización de gestos.

2. **Descubrimiento de la Telemetría de las Patas (Touchpad) de las Gafas**:
   - El log reveló que Flyme XR distingue claramente entre el botón y la patilla táctil:
     - `key_event_sender: 1`: **Botón Físico** (keycodes 206, 207, 210, 212).
     - `key_event_sender: 2`: **Patas / Varilla Táctil (Touchpad)** (keycodes 200, 201, 202, 203, 237).
   - Los keycodes táctiles de las patas (`key_code`) vienen dentro del objeto anidado `"_event_attr_value_"`:
     ```json
     {
       "_event_type_": "action_x",
       "_event_name_": "key_event",
       "_event_attr_value_": {
         "key_code": "200",
         "down_or_up": 1,
         "key_event_sender": 2
       }
     }
     ```
   - `InboundRouter.kt` solo buscaba `key_code` en la raíz del objeto `item`, omitiendo `_event_attr_value_`. Por lo tanto, los toques en las patas no se extraían correctamente.

3. **Eventos de Sistema Confundidos con Gestos**:
   - `iot_voice_wakeup` y `iot_voice_quit` fueron interpretados erróneamente como gestos `LONG_PRESS` porque contenían la palabra *"voice"*. Deben filtrarse como eventos de sistema no táctiles.

---

## 2. Objetivos y Alcance

1. **Restaurar el Botón Físico**:
   - Restaurar en [`ConnectionManager.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt) y [`GlassesEventHandler.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/GlassesEventHandler.kt) la ejecución directa de `ai().onTrigger(code)` ante los triggers de hardware (`code: 3` y `code: 7`).
   - El botón físico de los lentes ejecutará siempre su flujo original STT -> Modelo configurado (OpenAI, Claude, Groq, MediaPipe local, etc.).

2. **Habilitar Gestos de las Patas (Varilla Táctil) para Gemini y Apps**:
   - Extraer atributos anidados desde `_event_attr_value_` en [`InboundRouter.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/InboundRouter.kt).
   - Discriminar por `key_event_sender`:
     - `key_event_sender == 1`: Ignorar en gestos táctiles (es el botón físico).
     - `key_event_sender == 2` (o toques AVRCP de audífono Bluetooth): Mapear a gestos de las patas:
       - 200 / 203 -> `TAP` (Toque Simple)
       - 201 -> `SWIPE_FORWARD` (Deslizar Adelante)
       - 202 -> `DOUBLE_TAP` (Doble Toque)
       - 237 -> `SWIPE_BACKWARD` (Deslizar Atrás)
   - Integración dual: Toques por telemetría StarryNet + toques AVRCP por audífono Bluetooth (`MyvuService.MediaSession`).
   - El usuario podrá asignar **"Lanzar Gemini (Manos Libres)"** a cualquier gesto de las patas (ej. Doble Toque o Deslizar Adelante) desde [`SettingsActivity`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/SettingsActivity.kt).
   - Al realizar el gesto de la pata configurado:
     1. Despertar pantalla con [`LockScreenHelper.wakeUpScreen()`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/core/LockScreenHelper.kt).
     2. Desbloquear pantalla con [`SendTrampolineActivity.launchWithKeyguardDismiss()`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/SendTrampolineActivity.kt).
     3. Conectar el micrófono Bluetooth SCO de las gafas.
     4. Abrir Gemini en escucha activa delegándole el control de pantalla y ejecución.
     5. Mostrar en el HUD: *"Gemini escuchando..."*.

3. **Filtrar Telemetría de Voz Interna**:
   - Agregar `iot_voice_wakeup`, `iot_voice_quit` a la lista de telemetría de sistema ignorada para evitar falsos positivos de gestos.

---

## 3. Plan de Implementación Paso a Paso

### Paso 1: Restaurar el Botón Físico en [`ConnectionManager.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt) y [`GlassesEventHandler.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/GlassesEventHandler.kt)
- En `ConnectionManager.setAiTriggerListener`:
  - Eliminar el bloque `if (code == 3) TouchGestureManager.handleGesture(...)`.
  - Restaurar la llamada directa e incondicional: `ai().onTrigger(code)`.
- En `GlassesEventHandler.setAiTriggerListener`:
  - Restaurar la llamada directa: `delegate.triggerAi(code)`.

### Paso 2: Análisis de `_event_attr_value_` y `key_event_sender` en [`InboundRouter.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/InboundRouter.kt)
- En `dispatchGestureItem`:
  - Inspeccionar el objeto anidado `_event_attr_value_` si existe.
  - Extraer `key_event_sender`: si es 1 (botón físico), no procesar como gesto táctil de las patas.
  - Extraer `key_code`, `down_or_up` de `_event_attr_value_`.
  - Ignorar `down_or_up == 0` (liberación/keyup) para evitar dobles disparos y procesar únicamente eventos de pulsación (`down_or_up == 1`).
  - Añadir `iot_voice_wakeup` y `iot_voice_quit` al conjunto de eventos de sistema descartados.

### Paso 3: Mapeo de Códigos de Varilla/Patas en [`GlassGesture.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/feature/GlassGesture.kt)
- En `GlassGesture.fromCode`:
  - Mapear keycodes de la varilla táctil (sender 2):
    - `200, 203` -> `TAP` (o toque en patilla)
    - `202` -> `DOUBLE_TAP`
    - `201` -> `SWIPE_FORWARD`
    - `237` -> `SWIPE_BACKWARD`
  - Eliminar *"voice"* de los sinónimos de `LONG_PRESS` para que eventos internos `iot_voice_...` no activen pulsación larga por error.

### Paso 4: Ajustar Acción por Defecto de Gestos de Patas en [`TouchGestureManager.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt)
- Configurar por defecto:
  - `DOUBLE_TAP`: `LAUNCH_GEMINI` (o personalizable desde Settings)
  - `TAP`: `NONE`
  - `SWIPE_FORWARD`: `MEDIA_NEXT`
  - `SWIPE_BACKWARD`: `MEDIA_PREV`
  - `LONG_PRESS`: `LAUNCH_GEMINI` (para mantener presionada la patilla táctil si el usuario lo desea)

### Paso 5: Pruebas Unitarias y Verificación
- Actualizar [`InboundGestureTest.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/test/java/com/myvu/client/app/InboundGestureTest.kt):
  - Probar que `code=3` en `checkAiTrigger` ejecute `ai().onTrigger(3)` y NO `TouchGestureManager`.
  - Probar que `sync_glass_event` con `key_event_sender: 2` y `key_code: "201"`, `"202"`, `"203"`, `"237"` resuelvan a sus respectivos gestos de patilla.
  - Probar que `key_event_sender: 1` y `iot_voice_wakeup` sean ignorados por el gestor de gestos táctiles.
- Actualizar [`TouchGestureManagerTest.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/test/java/com/myvu/client/app/TouchGestureManagerTest.kt).
- Ejecutar `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.
- Sincronizar codegraph y actualizar memoria y documentación.
