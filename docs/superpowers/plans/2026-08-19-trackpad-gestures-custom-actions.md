# Análisis de la API de Trackpad y Plan de Implementación de Gestos y Acciones Personalizadas

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Habilitar el reconocimiento completo de los gestos táctiles de la patilla de las gafas MYVU (tap, doble tap, triple tap, deslizamientos, pulsación larga), reparar el trackpad virtual de la app hacia las gafas, y permitir configurar acciones personalizadas por cada gesto (incluyendo invocar el Asistente de Google / Gemini en el celular con micrófono activo, asistente IA local, control multimedia, clima y teleprompter).

**Architecture:**
1. **Inbound Gesture Decoder (`InboundRouter` & `TouchGestureManager`)**: Interceptar paquetes de telemetría de eventos `event_tracking` (`sync_glass_event`) y `code: 3` del paquete `com.upuphone.star.launcher`, decodificando los códigos de gestos físicos (`action_value`).
2. **Action Dispatcher & Phone Voice Assistant Trigger**: Crear un catálogo extensible de acciones ejecutables (`GestureAction`), incluyendo `LAUNCH_PHONE_ASSISTANT` (que despacha `KEYCODE_VOICE_ASSIST` y `ACTION_VOICE_COMMAND` para abrir Gemini/Google Assistant en escucha activa), `LAUNCH_LOCAL_AI`, `MEDIA_PLAY_PAUSE`, `MEDIA_NEXT`, `MEDIA_PREV`, `WEATHER_SYNC`, `TOGGLE_MIRROR`, `OPEN_TELEPROMPTER`.
3. **Outbound Virtual Trackpad Fix (`Trackpad.kt` & `TrackpadActivity`)**: Garantizar que los eventos `phonepad` se transmitan con la sesión RFCOMM lista y los paquetes de inicio/parada adecuados hacia `com.upuphone.star.launcher`.
4. **Settings UI (`SettingsActivity`)**: Nueva sección de configuración con selectores dedicados para cada gesto físico de la patilla.

**Tech Stack:** Kotlin 2.1+, Android AudioManager / KeyEvents, MediaPipe/LiteRT AI, StarryNet JSON Protocol over RFCOMM/BLE.

## Global Constraints
- El lenguaje de respuesta por defecto es español neutro/regional.
- Toda interacción HUD debe ser texto plano limpio y breve para display micro-LED.
- Los disparadores de gestos deben contar con debounce inteligente (250-400ms) para evitar rebotes de eventos capacitivos.
- Mantener la regla del proyecto: ejecutar `codegraph sync` al iniciar y finalizar cada ciclo de trabajo.

---

### Task 1: Decodificación de Gestos Táctiles Inbound en InboundRouter

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/app/InboundRouter.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/app/GlassesEventHandler.kt`
- Test: `android-kotlin/app/src/test/java/com/myvu/client/app/InboundGestureTest.kt`

**Interfaces:**
- Consumes: JSON entrante de `com.upuphone.star.launcher` con `action = "event_tracking"`.
- Produces: `InboundRouter.TouchGestureListener.onTouchGesture(gestureType: GlassGesture, rawCode: Int)`.

- [ ] **Step 1: Escribir prueba unitaria para decodificar eventos de gestos en InboundRouter**
- [ ] **Step 2: Ejecutar test para verificar que falla**
- [ ] **Step 3: Implementar decodificador de `sync_glass_event` y mapeo de `action_value` en InboundRouter**
- [ ] **Step 4: Ejecutar test unitario para verificar que pasa**
- [ ] **Step 5: Commit**

---

### Task 2: Sistema de Acciones Personalizadas y Lanzador de Asistente/Gemini del Teléfono

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/app/feature/GestureAction.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/core/Prefs.kt`
- Test: `android-kotlin/app/src/test/java/com/myvu/client/app/TouchGestureManagerTest.kt`

**Interfaces:**
- Consumes: `GlassGesture` detectado y preferencias del usuario.
- Produces: Ejecución de la acción asignada, incluyendo activación del Asistente Google / Gemini mediante `AudioManager.dispatchMediaKeyEvent(KEYCODE_VOICE_ASSIST)` y fallback a `Intent(ACTION_VOICE_COMMAND)`.

- [ ] **Step 1: Escribir pruebas unitarias para mapeo y ejecución de acciones de gestos**
- [ ] **Step 2: Ejecutar test para verificar que falla**
- [ ] **Step 3: Implementar enum `GestureAction`, métodos en `TouchGestureManager` y soporte de activación de Gemini**
- [ ] **Step 4: Ejecutar test unitario para verificar que pasa**
- [ ] **Step 5: Commit**

---

### Task 3: Diagnóstico y Reparación del Trackpad Virtual de la App hacia las Gafas

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/app/feature/Trackpad.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ui/TrackpadActivity.kt`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt`
- Test: `android-kotlin/app/src/test/java/com/myvu/client/TrackpadTest.kt`

**Interfaces:**
- Consumes: Gestos táctiles en la pantalla del móvil (`TrackpadView`).
- Produces: Payloads `phonepad` transmitidos correctamente por RFCOMM a `com.upuphone.star.launcher` con confirmación de estado de sesión.

- [ ] **Step 1: Escribir prueba unitaria para validar formato wire de Trackpad y ciclo de vida start/stop**
- [ ] **Step 2: Ejecutar test para verificar que pasa o falla**
- [ ] **Step 3: Asegurar enrutamiento de paquetes `phonepad` a `PKG_LAUNCHER` y activación de modo de control táctil en `ConnectionManager`**
- [ ] **Step 4: Ejecutar test unitario para verificar que pasa**
- [ ] **Step 5: Commit**

---

### Task 4: Interfaz de Configuración de Gestos en Ajustes

**Files:**
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/ui/SettingsActivity.kt`
- Modify: `android-kotlin/app/src/main/res/layout/activity_settings.xml`
- Test: `android-kotlin/app/src/test/java/com/myvu/client/ui/SettingsActivityTest.kt`

**Interfaces:**
- Consumes: Preferencias de gestos guardadas en `Prefs`.
- Produces: UI para configurar de forma independiente: Toque Simple, Doble Toque, Triple Toque, Pulsación Larga, Deslizar Adelante y Deslizar Atrás.

- [ ] **Step 1: Añadir controles de selección para cada gesto en el layout de ajustes**
- [ ] **Step 2: Vincular eventos y persistencia en `SettingsActivity.kt`**
- [ ] **Step 3: Ejecutar pruebas unitarias para validar integridad**
- [ ] **Step 4: Commit**

---

### Task 5: Documentación y Sincronización Final

**Files:**
- Modify: `README.md`
- Modify: `android-kotlin/README.md`

- [ ] **Step 1: Documentar el funcionamiento del Trackpad, eventos de la patilla y acciones asignables**
- [ ] **Step 2: Ejecutar `codegraph sync`**
- [ ] **Step 3: Commit de documentación**
