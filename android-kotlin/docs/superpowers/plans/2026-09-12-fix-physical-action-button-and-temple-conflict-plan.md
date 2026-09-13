# Plan: Corrección de Conflicto entre Botón Físico de Acción y Patilla Táctil de las Gafas

## 1. Diagnóstico del Problema

### Síntoma Reportado:
Al presionar el botón físico de acción en la montura de las gafas Meizu Myvu AR:
1. El sistema confunde los eventos de presión del botón físico con gestos de la patilla táctil (touchpad de las varillas).
2. Se ejecutan **dos acciones consecutivas** ante una sola pulsación del botón físico.

### Causa Raíz Técnica:
1. **Doble Despacho en `InboundRouter` y `ConnectionManager`**:
   - Al pulsar/mantener el botón físico de la montura, el firmware de las gafas Meizu Myvu emite dos mensajes casi simultáneos:
     1. Un mensaje directo IPC/SPP `com.upuphone.ai.assistant` con `code: 3` (o `code: 7`). Este evento es atendido por `checkAiTrigger`, que invoca `aiListener?.onAiTrigger(code, payload)`, disparando la acción configurada para el botón de acción (`Prefs.glassesActionButtonAction`, por ejemplo Aura AI, Gemini, Gemini Live o Asistente del Teléfono).
     2. Un paquete de telemetría de hardware `sync_glass_event` que contiene un `key_event` con `key_code` (por ejemplo `212`, `231`, `210`, `230` o `3`).
   - `InboundRouter.checkGestureTracking` procesaba este `key_event` sin discriminar si provenía del botón físico o de la patilla táctil:
     - Si el keycode era `212` o `231`, `GlassGesture.fromCode` lo catalogaba como `LONG_PRESS`.
     - Si el keycode era `3`, `GlassGesture.fromCode(3)` lo catalogaba erróneamente como `TRIPLE_TAP`.
     - Si el keycode era `210` o `230`, lo catalogaba como `TAP`, acumulándolo para síntesis de `DOUBLE_TAP`.
   - `InboundRouter` reenviaba este evento a `touchGestureListener`, el cual llamaba a `TouchGestureManager.handleGesture()`.
   - `TouchGestureManager` procedía a ejecutar la acción asignada a la **patilla táctil** (`Prefs.touchpadLongPressAction`, `touchpadTapAction` o `touchpadDoubleTapAction`).
   - **Resultado**: La app ejecutaba consecutivamente la Acción 1 (del Botón de Acción) y la Acción 2 (de la Patilla Táctil).

2. **Falta de Coordinación y Supresión en `TouchGestureManager`**:
   - `TouchGestureManager` administraba su propio `lastTriggerTime`, pero no era notificado cuando el botón físico disparaba una acción vía `aiListener`.
   - Al llegar el evento de tecla milisegundos después (o antes), `TouchGestureManager` lo trataba como un gesto nuevo e independiente de la patilla táctil, ejecutándolo de inmediato o sumándolo al acumulador de doble toque.

3. **Mapeo Inadecuado de Keycodes en `GlassGesture.kt`**:
   - `3 -> TRIPLE_TAP`: El código de hardware 3 del botón de IA era convertido en un triple toque táctil de la patilla.
   - `230 -> TAP` y `231 -> LONG_PRESS`: Los keycodes específicos del botón físico de hardware se equiparaban con gestos táctiles de la varilla.

---

## 2. Objetivos

1. **Aislamiento Total entre Botón Físico y Patilla Táctil**:
   - El botón físico de la montura debe ejecutar única y exclusivamente la acción configurada en `Prefs.glassesActionButtonAction` (Aura AI, Gemini, Gemini Live, Asistente de teléfono) ante una pulsación larga / trigger, o mostrar el HUD ante una pulsación corta (`HUD_DASHBOARD`).
   - El botón físico NUNCA debe disparar acciones configuradas para la patilla táctil (`Prefs.touchpad...Action`).
   - La patilla táctil (varillas de las gafas) debe responder únicamente a toques y deslizamientos en la superficie táctil capacitiva, sin verse alterada por pulsaciones en el botón físico.

2. **Eliminación del Doble Disparo Consecutivo**:
   - Establecer una ventana de supresión y sincronización de debounce entre el disparador del botón físico y el gestor de gestos táctiles.
   - Al activarse el botón físico, suprimir cualquier evento táctil concurrente o residual dentro de una ventana de seguridad de 1200ms.
   - Limpiar el acumulador de multi-toque (`accumulatedTapCount`) para que las pulsaciones del botón físico no sinteticen dobles toques accidentales en la patilla.

---

## 3. Plan de Cambios Paso a Paso

### Paso 1: Mapeo Limpio en `GlassGesture.kt`
- Eliminar `3 -> TRIPLE_TAP`. El código 3 es exclusivo del trigger del botón físico de IA.
- Separar keycodes 230 y 231 de los gestos táctiles de la patilla (`TAP` y `LONG_PRESS`).
- Introducir `ACTION_BUTTON` o manejar 230 y 231 como eventos exclusivos del botón de acción.

### Paso 2: Sincronización y Supresión en `TouchGestureManager.kt`
- Añadir método `notifyPhysicalButtonPressed(context: Context?)`:
  - Actualiza `lastTriggerTime = timeProvider()`.
  - Establece `lastPhysicalButtonTime = timeProvider()`.
  - Reinicia `accumulatedTapCount = 0`, `lastTapTime = 0L`, `lastTapEventTime = -1L`.
- En `handleGesture()`:
  - Si `now - lastPhysicalButtonTime < PHYSICAL_BUTTON_SUPPRESSION_MS` (1200ms), ignorar cualquier gesto táctil entrante catalogándolo en logs como evento suprimido por pulsación de botón físico.
  - Si `rawCode == 230`: tratar como pulsación corta del botón de acción -> ejecutar `executor.executeHudDashboard()` directamente sin tocar el acumulador de toques de la patilla.
  - Si `rawCode == 231` o `rawCode == 3`: tratar como pulsación del botón de acción -> no despachar como gesto de patilla táctil.

### Paso 3: Filtrado y Despacho Seguro en `InboundRouter.kt`
- En `checkAiTrigger`:
  - Cuando se detecte `code == 3 || code == 7`:
    - Llamar a `TouchGestureManager.notifyPhysicalButtonPressed(null)`.
    - Disparar `aiListener?.onAiTrigger(code, payload)`.
- En `checkGestureTracking`:
  - Si un objeto o elemento de telemetría tiene `actionValue == 3`, no procesarlo como gesto táctil de patilla.
  - Si tiene `actionValue == 230` o `actionValue == 231`: marcarlo o despacharlo como evento de botón físico, asegurando que no se mezcle con los gestos de patilla.

### Paso 4: Ajustes en `ConnectionManager.kt` y `GlassesEventHandler.kt`
- En `inbound.setAiTriggerListener`:
  - Notificar a `TouchGestureManager.notifyPhysicalButtonPressed(this.context)`.
  - Continuar ejecutando la acción configurada para `glassesActionButtonAction`.
- En el listener de gestos táctiles:
  - Asegurar que eventos filtrados o suprimidos no ejecuten acciones duplicadas.

### Paso 5: Pruebas Unitarias y Verificación
- Crear pruebas unitarias específicas en `PhysicalActionButtonConflictTest.kt` y actualizar `InboundGestureTest.kt` / `TouchGestureManagerTest.kt`:
  - Verificar que presionar el botón físico (`code: 3`) active solo la acción del botón de acción y NO despache acciones de la patilla táctil.
  - Verificar que una ráfaga que contenga `code: 3` y un evento `key_event` con keycode `212`, `231`, `210` o `230` NO ejecute dos acciones consecutivas.
  - Verificar que pulsaciones cortas en el botón de acción (keycode 230) muestren el HUD sin acumularse como doble toque en la patilla táctil.
  - Verificar que los gestos reales en la patilla táctil (deslizamientos 201/206/207/237 y toques táctiles 200/202/211) continúen funcionando con total precisión.
- Ejecutar `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.
- Ejecutar `codegraph sync`.
- Actualizar `PROJECT_MEMORY.md`, `ARCHITECTURE.md` y `README.md`.
