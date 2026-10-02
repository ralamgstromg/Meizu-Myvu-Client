# Plan de Implementación: Conectar `GlassesEventHandler` a `ConnectionManager` y eliminar la política de gestos duplicada

**Fecha:** 2026-10-02
**Módulo:** `app/src/main/java/com/myvu/client/service/`, `com/myvu/client/app/`
**Estado:** Pendiente de Aprobación
**Origen:** Análisis del grafo de conocimiento (graphify): `ConnectionManager` es el hub de acoplamiento real (143 aristas, 26 comunidades, 38 imports internos).

---

## 1. Contexto y Hallazgo

`ConnectionManager.kt` (1581 líneas) mezcla transporte BLE/RFCOMM, relay, fachada de comandos y **política de entrada de las gafas** (qué hacer ante el botón de IA, gestos del touchpad, petición de clima y batería).

La política de entrada ya fue extraída a `app/GlassesEventHandler.kt` (233 líneas) con una interfaz `Delegate`, y tiene tests (`InboundGestureTest`, `AppLayerTest`). **Pero `GlassesEventHandler` nunca se instancia en código de producción.** Producción sigue usando la copia inline de `ConnectionManager`:

| Lógica | Copia en producción (`ConnectionManager.kt`) | Copia testeada, no usada (`GlassesEventHandler.kt`) |
|---|---|---|
| Listener del botón de IA (code 3/7, `control:0`, supresión 500 ms, mapeo `glassesActionButtonAction`) | L202–243 | L40–68 |
| Listener de gestos del touchpad | L245–247 | L70–72 |
| Clima / batería | L249–255 | L74–81 |
| `ActionExecutor` (15 acciones) | `createGestureActionExecutor()` L262–374 | `createActionExecutor()` L84–232 |

Consecuencias:
1. **Los tests dan falsa confianza**: validan una copia que no corre en producción.
2. **Divergencia silenciosa**: cada fix de gestos (botón físico contra temple, Gemini, Zen…) hay que aplicarlo dos veces. Si solo se aplica en `ConnectionManager`, los tests no lo cubren.
3. Unas 175 líneas de lógica de aplicación viven dentro de la capa de transporte y comparten el hilo `myvu-conn`.

### Diferencias de comportamiento detectadas entre las dos copias

| Punto | `ConnectionManager` | `GlassesEventHandler` | Resolución |
|---|---|---|---|
| `control:0` (cierre de página) | `ai?.onPageClosed()`: no crea `AiConversation` | `delegate.pageClosed()` | El delegate debe usar `ai?.onPageClosed()`, nunca `ai()` |
| `executeHudDashboard` | Escribe un log | No hace nada | Añadir el `LogBus.log` a `GlassesEventHandler` |
| `executeZenMode` | `setZenMode(enabled)`, que es `safeSend(SystemSettings.setZenMode)` | `delegate.sendAction(SystemSettings.setZenMode(enabled))` | Equivalente (el try/catch envuelve ambos). Sin cambios |
| `context` nulo | Nunca nulo | Con guardas `?: return` | Mantener las guardas (permiten tests JVM con `null`) |
| Gesto desde el teclado del teléfono | `executeGesture(gesture, rawCode, eventTime)` es pública y la usa `MyvuService` (L225, L264–282) | No expone un método público | Añadir `fun handleGesture(gesture, rawCode, eventTime)` público |

---

## 2. Objetivo

Que `GlassesEventHandler` sea la **única** implementación de la política de entrada y que `ConnectionManager` solo le pase capacidades mediante `Delegate`. No hay cambio de comportamiento visible.

Fuera de alcance: extraer la fachada de comandos, el relay o la batería (pasos 2–4 del refactor de `ConnectionManager`, en planes separados).

---

## 3. Pasos

### Paso 1: Alinear `GlassesEventHandler` con producción
Archivo: `app/GlassesEventHandler.kt`
1. En `executeHudDashboard()`, añadir `LogBus.log("HUD Dashboard action: letting glasses display native HUD dashboard")`.
2. Añadir este método público, que reutiliza el mismo executor:
   ```kotlin
   fun handleGesture(gesture: GlassGesture, rawCode: Int = gesture.code, eventTime: Long = -1L) {
       TouchGestureManager.handleGesture(context, gesture, rawCode, createActionExecutor(), eventTime)
   }
   ```
3. Revisar que el comentario sobre `control:0` (no abortar un turno en curso) quede copiado desde `ConnectionManager` L205–208.

### Paso 2: Tests de caracterización antes de tocar `ConnectionManager`
Archivo nuevo: `test/java/com/myvu/client/app/GlassesEventHandlerPolicyTest.kt` (Robolectric, ya está en `testImplementation`).
Fijar el comportamiento actual de:
- `code:3` con `glassesActionButtonAction` = `LAUNCH_GEMINI`, `LAUNCH_GEMINI_LIVE`, `LAUNCH_PHONE_ASSISTANT` y `VOICE_AI_FIXED`: solo el último llama a `triggerAi(3)`.
- `code:3` dentro de los 500 ms posteriores a `lastPhysicalKeyEventTime`: suprimido, sin `wakeRelay` ni `triggerAi`.
- `control:0`: solo `pageClosed()`, sin `wakeRelay`.
- `code:7` (wake word): `wakeRelay` seguido de `triggerAi(7)`, ignorando el mapeo del botón.
- `executeZenMode` y `executeToggleMirror`: alternan el valor en `Prefs` y envían 2 paquetes (Zen) o 1 paquete (Mirror).
- `handleGesture(GlassGesture.SWIPE_FORWARD, …)`: llega al executor.

Ejecutar: `./gradlew :app:testDebugUnitTest --tests "com.myvu.client.app.*"`. Todo debe pasar **antes** del paso 3.

### Paso 3: Instanciar el handler en `ConnectionManager`
Archivo: `service/ConnectionManager.kt`
1. Reemplazar el bloque `init { … }` (L202–256) por:
   ```kotlin
   private val events = GlassesEventHandler(context, inbound, object : GlassesEventHandler.Delegate {
       override fun wakeRelay() { supervisor?.wake(force = true) }
       override fun triggerAi(triggerCode: Int) { ai().onTrigger(triggerCode) }
       override fun pageClosed() { ai?.onPageClosed() }
       override fun refreshWeather() { weather().refresh() }
       override fun updateBattery(battery: Int, isCharging: Boolean) { updateGlassesBattery(battery, isCharging) }
       override fun sendAction(actionJson: String) { this@ConnectionManager.sendAction(actionJson) }
   })
   ```
   Cuidado con el orden de inicialización: declarar `events` **después** de `inbound`, `supervisor`, `ai` y `weather`, o asegurar que el delegate solo los use de forma lazy. Hoy todos se leen dentro de lambdas, así que es seguro mientras `events` vaya después de `inbound`.
2. `executeGesture(...)` (L258–260) pasa a delegar en `events.handleGesture(gesture, rawCode, eventTime)`. La firma pública no cambia y `MyvuService` no se toca.
3. Borrar `createGestureActionExecutor()` (L262–374).
4. Quitar los imports que queden sin uso (`TouchGestureManager`, `Teleprompter.buildOpen`, `Notifications`, si aplica).

Resultado esperado: `ConnectionManager.kt` baja unas 170 líneas.

### Paso 4: Verificación
1. `./gradlew :app:testDebugUnitTest` completo. Prestar atención a `ConnectionManagerTest`, `ConnectionManagerTeardownTest`, `PhysicalActionButtonConflictTest`, `TouchGestureManagerTest` y `InboundRouterTest`.
2. `./gradlew :app:assembleDebug`.
3. Prueba manual con las gafas:
   - Botón de IA con cada mapeo (Voice AI, Gemini, Gemini Live, Asistente del teléfono).
   - Doble tap en la patilla justo después del botón físico: no debe disparar dos veces.
   - Gestos del touchpad: media play/pausa/siguiente, Zen y mirror, con su notificación en el HUD.
   - Soltar el botón de IA durante una respuesta: la respuesta no se corta.
   - Teclas de volumen del teléfono mapeadas (`MyvuService`): siguen funcionando.
4. Revisar en Activity Log que aparecen las líneas `Touchpad gesture -> …` y `HUD Dashboard action…`.

### Paso 5: Limpieza
- `PhysicalActionButtonConflictTest` (L66) reimplementa a mano el listener del botón dentro del test. Migrarlo a `GlassesEventHandler` para que pruebe el código real.
- Actualizar `docs/ARCHITECTURE.md`: `GlassesEventHandler` es el dueño de la política de entrada.

---

## 4. Riesgos y Rollback

| Riesgo | Mitigación |
|---|---|
| Orden de inicialización del `object`/clase: un delegate que usa propiedades aún nulas | Todas las llamadas del delegate ocurren en eventos posteriores al constructor. El test de `ConnectionManagerTest` lo cubre |
| `pageClosed` creando `AiConversation` por error | El delegate usa `ai?.` (nullable), no `ai()`. Hay un test explícito en el paso 2 |
| Hilo: hoy los listeners corren en el hilo que llama `InboundRouter.handle` | No cambia: `GlassesEventHandler` se registra en el mismo `InboundRouter` |
| Regresión en gestos | Los tests de caracterización (paso 2) van antes del cambio. Rollback: revertir un solo commit |

Cada paso va en un commit separado (1 y 2 juntos, luego 3 y luego 5) para poder revertirlos por separado.
