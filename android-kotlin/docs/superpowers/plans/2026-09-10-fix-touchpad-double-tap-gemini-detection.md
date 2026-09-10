# Detección Confiable de Doble Toque en Touchpad para Lanzamiento de Gemini

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Garantizar la detección infalible del doble toque en las patillas de las gafas para lanzar Gemini, eliminando la pérdida de toques causada por el debounce de gestos nulos (`NONE`), micro-swipes parásitos simultáneos y la falta de síntesis software cuando el firmware emite dos toques simples (`210` + `210`) en lugar de `211`.

**Architecture:**
1. `TouchGestureManager`:
   - Eliminar el bloqueo de debounce cuando un gesto ejecuta `Action: NONE`. Acciones nulas (como un micro-swipe no configurado) no deben bloquear los siguientes 350ms a un `TAP` o `DOUBLE_TAP` legítimo.
   - Implementar acumulador/sintetizador de doble toque por software: Si el firmware emite dos eventos `TAP` consecutivos (`210` o `200`) en un intervalo de 60ms a 450ms, y `DOUBLE_TAP` tiene una acción asignada (ej. `LAUNCH_GEMINI`), sintetizar y ejecutar `DOUBLE_TAP` de inmediato.
   - Mantener ejecución inmediata para `DOUBLE_TAP` nativo de hardware (`code 211` o `code 202`).
2. `InboundRouter`:
   - En `processGestureValue`: Al procesar lotes de telemetría (`JSONArray`), si dentro del mismo lote (o ventana <= 50ms) se detectan micro-swipes parásitos (`206`, `207`, `201`) junto con toques (`210`, `211`, `200`, `202`), priorizar el toque intencional sobre el roce parásito.
3. Validación y Pruebas:
   - Tests unitarios en `TouchGestureManagerTest` e `InboundGestureTest` simulando las secuencias exactas del log: `[206, 210]`, ráfagas de dos `210` en 200ms, y toques de ambas patillas (sender 1 y 2).

**Tech Stack:** Kotlin, Android SDK 35, JUnit 4, Robolectric.

**Spec:** Análisis del log `/home/rcastro/Descargas/myvu_client_log.txt` (líneas 361-381 y 289-294).

---

### Task 1: Corrección de Debounce y Sintetizador Software de Doble Toque en `TouchGestureManager`

**Files:**
- Modify: `app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt`
- Test: `app/src/test/java/com/myvu/client/app/TouchGestureManagerTest.kt`

- [x] **Step 1: Escribir tests que fallen reproduciendo la pérdida de toques (dos `TAP` seguidos en 200ms deben promover a `DOUBLE_TAP`, y un swipe con acción `NONE` no debe bloquear un `TAP` 10ms después)**
- [x] **Step 2: Ejecutar tests y verificar fallo**
- [x] **Step 3: Modificar `TouchGestureManager.handleGesture`:**
  * No actualizar `lastTriggerTime` si la acción resuelta fue `GestureAction.NONE`.
  * Implementar detección de doble toque software: si llega un `TAP` y han transcurrido entre 50ms y 450ms desde el último `TAP`, ejecutar la acción configurada para `GlassGesture.DOUBLE_TAP`.
  * Si llega un `DOUBLE_TAP` directo de hardware (`code 211` o `202`), ejecutar de inmediato y resetear el acumulador de taps.
- [x] **Step 4: Ejecutar pruebas unitarias para confirmar que pasen al 100%**

---

### Task 2: Filtrado y Priorización de Lotes de Gestos en `InboundRouter`

**Files:**
- Modify: `app/src/main/java/com/myvu/client/app/InboundRouter.kt`
- Test: `app/src/test/java/com/myvu/client/app/InboundGestureTest.kt`

- [x] **Step 1: Escribir prueba unitaria en `InboundGestureTest` con array `[{"key_code":"206", ...}, {"key_code":"210", ...}]` verificando que el `TAP` no sea ignorado**
- [x] **Step 2: En `InboundRouter.processGestureValue`, cuando `valueRaw` es un array, ordenar/filtrar los eventos del mismo lote dando prioridad a `DOUBLE_TAP` y `TAP` sobre `SWIPE_FORWARD`/`SWIPE_BACKWARD` parásitos con el mismo timestamp**
- [x] **Step 3: Ejecutar pruebas unitarias y verificar éxito**

---

### Task 3: Verificación Completa, Memoria Viva y Documentación

**Files:**
- Modify: `docs/PROJECT_MEMORY.md`
- Modify: `README.md`
- Modify: `docs/ARCHITECTURE.md`
- Command: `codegraph sync`

- [x] **Step 1: Ejecutar suite de pruebas completa `./gradlew testDebugUnitTest` y compilar APK `./gradlew assembleDebug`**
- [x] **Step 2: Actualizar `docs/PROJECT_MEMORY.md`, `README.md` y `docs/ARCHITECTURE.md`**
- [x] **Step 3: Ejecutar `codegraph sync` al finalizar**
