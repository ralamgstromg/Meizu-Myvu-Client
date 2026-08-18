# Plan de Implementación: Integración Bidireccional de Plugin Tasker para MYVU Glasses (Kotlin)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar un plugin nativo de Tasker (Locale/Tasker Plugin Standard API y Broadcasts Intent) que permita interacción bidireccional entre las gafas Meizu MYVU, la app Android y Tasker: disparar tareas en Tasker desde gestos/estados de las gafas, y ejecutar acciones en las gafas (HUD, teleprompter, brillo, ajustes, notificaciones) desde perfiles/tareas de Tasker.

**Architecture:** 
1. **Canal Gafas -> Tasker (Eventos/Condiciones):** [`TouchGestureManager`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt), [`InboundRouter`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/app/InboundRouter.kt) y [`ConnectionManager`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt) notifican a un despachador unificado (`TaskerEventBroadcaster`). Este emite Intents estándar de Tasker (`net.dinglisch.android.tasker.ACTION_OPEN_EVENT`) y Broadcasts globales (`com.myvu.client.event.*`) con variables de Tasker (`%myvu_event`, `%myvu_battery`, `%myvu_state`).
2. **Canal Tasker -> Gafas (Acciones):** Configuración mediante `TaskerActionActivity` (Locale `EDIT_SETTING`) y ejecución en segundo plano mediante `TaskerActionReceiver` (Locale `FIRE_SETTING`) que interactúa con [`MyvuService`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/MyvuService.kt) y [`ConnectionManager`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt) para renderizar mensajes HUD, abrir teleprompter, cambiar brillo/volumen/modo zen, o enviar comandos JSON personalizados.

**Tech Stack:** Kotlin, Android BroadcastReceivers, Tasker / Locale Plugin Protocol, Material 3 UI Components, Kotlin Coroutines, JUnit 4, Roboelectric / MockK.

## Global Constraints
- Cumplir la especificación estándar de plugins Locale/Tasker (`com.twofortyfouram.locale.intent.action.FIRE_SETTING` y `EDIT_SETTING`).
- Permitir resolución de variables dinámicas de Tasker (e.g. `%antigravity_var`) en títulos, textos y comandos.
- Soporte para fallback directo mediante Intent Broadcasts explícitos/implícitos para usuarios avanzados.
- Preservar compatibilidad total con el ciclo de vida del [`MyvuService`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/MyvuService.kt).

---

### File Structure & Responsibilities

- **`android-kotlin/app/src/main/java/com/myvu/client/plugin/tasker/`**:
  - `TaskerConstants.kt`: Constantes de acciones, extras, tipos de evento y bundles de Tasker.
  - `TaskerBundleManager.kt`: Helper de serialización/deserialización y extracción de variables dinámicas de Tasker.
  - **`action/`**:
    - `TaskerActionActivity.kt`: Actividad UI para seleccionar y configurar la acción a ejecutar en las gafas desde Tasker.
    - `TaskerActionReceiver.kt`: BroadcastReceiver que recibe el disparo de Tasker y ejecuta la acción sobre `MyvuService`/`ConnectionManager`.
  - **`event/`**:
    - `TaskerEventActivity.kt`: Actividad UI para configurar el disparador de eventos (gesto táctil, botón AI, cambio de batería, conexión).
    - `TaskerEventBroadcaster.kt`: Emisor de eventos hacia Tasker cuando ocurren acciones en las gafas.
- **Modificaciones en Core:**
  - `android-kotlin/app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt`: Soporte de acción `ACTION_TASKER_EVENT` y emisión automática de gestos.
  - `android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt`: Notificación de cambios de estado y batería a `TaskerEventBroadcaster`.
  - `android-kotlin/app/src/main/AndroidManifest.xml`: Declaración de actividades de configuración y receivers de Tasker con filtros de intent requeridos.

---

### Task 1: Protocolo y Constantes de Tasker & Gestor de Bundles

**Files:**
- Create: `android-kotlin/app/src/main/java/com/myvu/client/plugin/tasker/TaskerConstants.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/plugin/tasker/TaskerBundleManager.kt`
- Test: `android-kotlin/app/src/test/java/com/myvu/client/plugin/tasker/TaskerBundleManagerTest.kt`

**Interfaces:**
- Consumes: Tipos de acciones (`SHOW_HUD`, `SHOW_TELEPROMPTER`, `SET_BRIGHTNESS`, `SET_VOLUME`, `TOGGLE_WIFI`, `SET_ZEN_MODE`, `SEND_RAW_ACTION`).
- Produces: `TaskerBundleManager.buildActionBundle(...)`, `TaskerBundleManager.extractAction(...)`, `TaskerBundleManager.extractVariables(...)`.

- [ ] **Step 1: Crear `TaskerConstants.kt`**
Definir intents estándar de Locale/Tasker y acciones de las gafas:
```kotlin
package com.myvu.client.plugin.tasker

object TaskerConstants {
    const val ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
    const val ACTION_EDIT_SETTING = "com.twofortyfouram.locale.intent.action.EDIT_SETTING"
    const val ACTION_EDIT_CONDITION = "com.twofortyfouram.locale.intent.action.EDIT_CONDITION"
    const val ACTION_QUERY_CONDITION = "com.twofortyfouram.locale.intent.action.QUERY_CONDITION"
    const val EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    const val EXTRA_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"

    // Tasker variable replacement
    const val EXTRA_TASKER_PASS_THROUGH = "net.dinglisch.android.tasker.extras.PASS_THROUGH"
    const val EXTRA_VARIABLE_REPLACE_KEYS = "net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS"

    // Tipos de Acción hacia las gafas
    const val TYPE_SHOW_HUD = "show_hud"
    const val TYPE_SHOW_TELEPROMPTER = "show_teleprompter"
    const val TYPE_SET_BRIGHTNESS = "set_brightness"
    const val TYPE_SET_VOLUME = "set_volume"
    const val TYPE_TOGGLE_WIFI = "toggle_wifi"
    const val TYPE_SET_ZEN_MODE = "set_zen_mode"
    const val TYPE_SET_AIR_MODE = "set_air_mode"
    const val TYPE_SET_STANDBY_POS = "set_standby_pos"
    const val TYPE_SEND_RAW = "send_raw"

    // Tipos de Eventos desde las gafas
    const val EVENT_TOUCH_GESTURE = "touch_gesture"
    const val EVENT_AI_BUTTON = "ai_button"
    const val EVENT_CONNECTED = "glasses_connected"
    const val EVENT_DISCONNECTED = "glasses_disconnected"
    const val EVENT_BATTERY_CHANGED = "battery_changed"

    // Direct Intent Broadcasts
    const val BROADCAST_EVENT = "com.myvu.client.TASKER_EVENT"
    const val BROADCAST_ACTION = "com.myvu.client.TASKER_ACTION"
}
```

- [ ] **Step 2: Crear prueba unitaria para `TaskerBundleManagerTest`**
Verificar serialización, deserialización y generación de blurbs descriptivos:
```kotlin
package com.myvu.client.plugin.tasker

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskerBundleManagerTest {
    @Test
    fun bundleSerializationForHudMessage() {
        val bundle = TaskerBundleManager.buildHudBundle(
            title = "Aviso Tasker",
            content = "Batería móvil al 100%"
        )
        val action = TaskerBundleManager.parseAction(bundle)
        assertEquals(TaskerConstants.TYPE_SHOW_HUD, action.type)
        assertEquals("Aviso Tasker", action.title)
        assertEquals("Batería móvil al 100%", action.content)
    }
}
```

- [ ] **Step 3: Implementar `TaskerBundleManager.kt`**
Implementar constructor de Bundles, parseo seguro y generación de texto resumen (blurb).

- [ ] **Step 4: Compilar y verificar pruebas**
Ejecutar: `./gradlew :app:testDebugUnitTest --tests com.myvu.client.plugin.tasker.TaskerBundleManagerTest`

---

### Task 2: Implementación del Plugin de Acción (Tasker -> Gafas)

**Files:**
- Create: `android-kotlin/app/src/main/java/com/myvu/client/plugin/tasker/action/TaskerActionActivity.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/plugin/tasker/action/TaskerActionReceiver.kt`
- Create: `android-kotlin/app/src/main/res/layout/activity_tasker_action.xml`
- Test: `android-kotlin/app/src/test/java/com/myvu/client/plugin/tasker/action/TaskerActionReceiverTest.kt`

**Interfaces:**
- Consumes: Invocación de `com.twofortyfouram.locale.intent.action.FIRE_SETTING` con bundle de acción.
- Produces: Ejecución directa en [`ConnectionManager`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt) (`sendAction`, `Notifications.buildShow`, `Teleprompter.buildOpen`, `SystemSettings.setBrightness`, etc.).

- [ ] **Step 1: Crear layout UI `activity_tasker_action.xml`**
Diseñar interfaz Material 3 con selector desplegable de acción:
1. Mostrar Mensaje HUD / Notificación (Título, Mensaje).
2. Teleprompter (Texto).
3. Cambiar Brillo (Slider 0-10).
4. Cambiar Volumen (Slider 0-15).
5. Ajustes Sistema (WiFi On/Off, Modo Zen On/Off, Posición FOV).
6. Enviar JSON Raw.

- [ ] **Step 2: Implementar `TaskerActionActivity.kt`**
Actividad que carga el Bundle previo o inicializa valores por defecto, valida inputs y devuelve `RESULT_OK` con `EXTRA_BUNDLE` y `EXTRA_BLURB`.

- [ ] **Step 3: Implementar `TaskerActionReceiver.kt`**
BroadcastReceiver que procesa `FIRE_SETTING`, extrae la acción, resuelve variables de Tasker si están presentes (`%var`), obtiene la instancia activa de `ConnectionManager` (o lanza `MyvuService`) y envía el comando a las gafas.

- [ ] **Step 4: Escribir prueba unitaria `TaskerActionReceiverTest`**
Verificar despacho de comandos hacia `ConnectionManager` para cada tipo de acción.

- [ ] **Step 5: Compilar y verificar pruebas**
Ejecutar: `./gradlew :app:testDebugUnitTest --tests com.myvu.client.plugin.tasker.action.TaskerActionReceiverTest`

---

### Task 3: Implementación del Plugin de Eventos & Gestor de Disparadores (Gafas -> Tasker)

**Files:**
- Create: `android-kotlin/app/src/main/java/com/myvu/client/plugin/tasker/event/TaskerEventActivity.kt`
- Create: `android-kotlin/app/src/main/java/com/myvu/client/plugin/tasker/event/TaskerEventBroadcaster.kt`
- Create: `android-kotlin/app/src/main/res/layout/activity_tasker_event.xml`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/app/feature/TouchGestureManager.kt:12-65`
- Modify: `android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt:180-255`
- Test: `android-kotlin/app/src/test/java/com/myvu/client/plugin/tasker/event/TaskerEventBroadcasterTest.kt`

**Interfaces:**
- Consumes: Gestos táctiles desde `TouchGestureManager`, disparos de botón AI, cambios de estado en `ConnectionManager`, y eventos de batería en `InboundRouter`.
- Produces: Broadcast de eventos a Tasker (`com.myvu.client.TASKER_EVENT` y `net.dinglisch.android.tasker.ACTION_OPEN_EVENT`) con variables exportables (`%myvu_event`, `%myvu_gesture`, `%myvu_battery`).

- [ ] **Step 1: Implementar `TaskerEventBroadcaster.kt`**
Despachador centralizado de eventos:
```kotlin
object TaskerEventBroadcaster {
    fun sendGestureEvent(context: Context, gestureCode: Int, gestureName: String)
    fun sendAiButtonEvent(context: Context, buttonCode: Int)
    fun sendConnectionStateEvent(context: Context, state: ConnectionState)
    fun sendBatteryEvent(context: Context, batteryLevel: Int, isCharging: Boolean)
}
```

- [ ] **Step 2: Integrar disparadores en `TouchGestureManager.kt` y `ConnectionManager.kt`**
Añadir soporte para disparar `TaskerEventBroadcaster.sendGestureEvent` cada vez que se reciba un gesto del panel táctil o botón hardware (código 3) o trigger de voz (código 7).
En `ConnectionManager.state`, notificar a `TaskerEventBroadcaster.sendConnectionStateEvent`.
En `updateGlassesBattery`, notificar a `TaskerEventBroadcaster.sendBatteryEvent`.

- [ ] **Step 3: Crear UI de configuración `TaskerEventActivity.kt`**
Permitir a Tasker filtrar por evento específico (e.g. "Cualquier Gesto", "Toque Doble", "Pulsación Larga", "Batería < 20%", "Gafas Conectadas").

- [ ] **Step 4: Escribir prueba unitaria `TaskerEventBroadcasterTest`**
Verificar generación correcta de intents con todos los extras requeridos.

---

### Task 4: Registro en AndroidManifest y Verificación E2E

**Files:**
- Modify: `android-kotlin/app/src/main/AndroidManifest.xml`
- Test: Suite completa de integración y compilación APK.

- [ ] **Step 1: Registrar componentes en `AndroidManifest.xml`**
Configurar las actividades y receivers con los filtros de intención de Tasker/Locale:
```xml
<!-- Tasker Action Plugin -->
<activity
    android:name=".plugin.tasker.action.TaskerActionActivity"
    android:icon="@mipmap/ic_launcher"
    android:label="MYVU Gafas Acción"
    android:exported="true">
    <intent-filter>
        <action android:name="com.twofortyfouram.locale.intent.action.EDIT_SETTING" />
    </intent-filter>
</activity>

<receiver
    android:name=".plugin.tasker.action.TaskerActionReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="com.twofortyfouram.locale.intent.action.FIRE_SETTING" />
        <action android:name="com.myvu.client.TASKER_ACTION" />
    </intent-filter>
</receiver>

<!-- Tasker Event / Condition Plugin -->
<activity
    android:name=".plugin.tasker.event.TaskerEventActivity"
    android:icon="@mipmap/ic_launcher"
    android:label="MYVU Gafas Evento"
    android:exported="true">
    <intent-filter>
        <action android:name="com.twofortyfouram.locale.intent.action.EDIT_CONDITION" />
    </intent-filter>
</activity>
```

- [ ] **Step 2: Ejecutar suite de pruebas unitarias**
Ejecutar: `./gradlew :app:testDebugUnitTest`
Esperado: 100% PASS.

- [ ] **Step 3: Validar compilación completa de APK**
Ejecutar: `./gradlew :app:assembleDebug`
Esperado: BUILD SUCCESSFUL.

- [ ] **Step 4: Sincronizar CodeGraph**
Ejecutar: `codegraph sync`
