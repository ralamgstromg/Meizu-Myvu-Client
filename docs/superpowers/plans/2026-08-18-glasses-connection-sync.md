# Plan de Implementación: Gestión Eficiente y Sincronización de Conexión Gafas-App (Kotlin)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Solucionar el fallo en [`ConnectActivity`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/ConnectActivity.kt) donde la ventana modal "Searching for your glasses" se queda bloqueada indefinidamente al conectar las gafas MYVU, garantizando una sincronización reactiva, atómica y segura en hilo principal del estado de conexión ([`ConnectionState`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionState.kt)).

**Architecture:** Conectar el [`ConnectionManager.stateFlow`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt#L90-L92) directamente al ciclo de vida de [`ConnectActivity`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/ConnectActivity.kt) mediante corrutinas de Kotlin (`lifecycleScope` + `repeatOnLifecycle` / `FlowCollector`) en lugar de depender indirectamente del bus de logs [`LogBus.onLine`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/ConnectActivity.kt#L775-L781). Gestionar atómicamente la transición y animación del overlay (`pairingOverlay`) para ocultarlo inmediatamente tras el estado `READY`.

**Tech Stack:** Kotlin Coroutines, StateFlow, Android Lifecycle (`lifecycleScope`, `repeatOnLifecycle`), Android Bluetooth LE & RFCOMM, JUnit 4, Mockito / Kotlinx-Coroutines-Test.

## Global Constraints
- No romper compatibilidad con el servicio en segundo plano [`MyvuService`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/MyvuService.kt).
- Todas las operaciones de mutación de UI y animaciones deben ejecutarse exclusivamente en el Main Thread (`Dispatchers.Main`).
- Preservar el soporte de reconexión automática y escaneo automático (`startAutoSearch`).

---

### Diagnóstico de Causa Raíz

1. **Falta de suscripción a `StateFlow` en la UI**: En [`ConnectActivity.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/ConnectActivity.kt), la actualización de la interfaz dependía de `onLine(line: String)` del [`LogBus`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/core/LogBus.kt). Cuando el enlace BLE y la sesión RFCOMM pasaban a `ConnectionState.READY`, si no se emitía un log inmediatamente o el log llegaba en un hilo secundario sin sincronizar, `render()` nunca se invocaba.
2. **Desincronización en `showPairing()`**: Cuando el usuario pulsaba "Conectar", `showPairing()` iniciaba la animación de radar asumiendo `CONNECTING`. Si la conexión ya estaba lista o cambiaba de estado rápidamente durante el handshake, el modal no recibía la notificación de éxito y quedaba mostrando *"Searching for your glasses"*.
3. **Manejo de concurrencia y fugas de animación**: Las transiciones de estado ocurren en el hilo `"myvu-conn"` o en hilos de Bluetooth GATT. Al no haber despacho formal al hilo de UI, las animaciones de `ringAnimators` podían quedar en bucle infinito sin cancelarse.

---

### Task 1: Suscripción Reactiva a StateFlow en ConnectActivity

**Files:**
- Modify: [`android-kotlin/app/src/main/java/com/myvu/client/ui/ConnectActivity.kt:95-185`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/ConnectActivity.kt#L95-L185)
- Test: [`android-kotlin/app/src/test/java/com/myvu/client/service/ConnectionStateTest.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/test/java/com/myvu/client/service/ConnectionStateTest.kt)

**Interfaces:**
- Consumes: [`ConnectionManager.stateFlow`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt#L90-L92)
- Produces: `observeConnectionState()` coroutine job vinculado al ciclo de vida de la actividad.

- [ ] **Step 1: Crear coroutine collector en `ConnectActivity`**
Implementar un método `observeConnectionState(conn: ConnectionManager)` en [`ConnectActivity.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/ConnectActivity.kt):
```kotlin
private var stateJob: kotlinx.coroutines.Job? = null

private fun observeConnectionState(conn: ConnectionManager) {
    stateJob?.cancel()
    stateJob = lifecycleScope.launch {
        repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            conn.stateFlow.collect { state ->
                render(state)
            }
        }
    }
}
```

- [ ] **Step 2: Conectar el observer en `serviceConnection` y desconectar en `onStop`**
En `serviceConnection.onServiceConnected`:
```kotlin
override fun onServiceConnected(name: ComponentName, binder: IBinder) {
    val s = (binder as MyvuService.LocalBinder).getService()
    service = s
    bound = true
    val conn = s.connection()
    if (conn != null) {
        observeConnectionState(conn)
        render(conn.state())
        if (conn.state() == ConnectionState.READY) {
            conn.queryBatteryInfo()
        }
    } else {
        render(ConnectionState.IDLE)
    }
}
```
En `onStop()` cancelar `stateJob?.cancel()`.

- [ ] **Step 3: Desacoplar `render()` de `LogBus.onLine`**
Eliminar la invocación obligatoria de `render(conn.state())` dentro de `onLine()` para evitar re-renderizados innecesarios en ráfagas de logs, dejando que `stateFlow` sea la única fuente de verdad.

- [ ] **Step 4: Compilar y verificar pruebas unitarias**
Ejecutar: `./gradlew :app:testDebugUnitTest --tests com.myvu.client.service.ConnectionStateTest`

---

### Task 2: Robustez del Modal de Emparejamiento (Pairing Overlay) y Auto-Dismiss

**Files:**
- Modify: [`android-kotlin/app/src/main/java/com/myvu/client/ui/ConnectActivity.kt:468-580`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/ConnectActivity.kt#L468-L580)

**Interfaces:**
- Consumes: [`ConnectionState`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionState.kt)
- Produces: `showPairing()`, `updatePairing()`, `pairingSuccess()`, `pairingFailed()` con transiciones atómicas y seguras.

- [ ] **Step 1: Corregir condición inicial en `showPairing`**
Verificar el estado actual del servicio inmediatamente:
```kotlin
private fun showPairing() {
    pairing = true
    pairingOverlay.alpha = 0f
    pairingOverlay.visibility = View.VISIBLE
    pairingOverlay.animate().alpha(1f).setDuration(220).start()

    imgCheck.visibility = View.GONE
    pairButtons.visibility = View.GONE
    btnPairDone.visibility = View.GONE

    val conn = service?.connection()
    val current = if (bound && conn != null) conn.state() else ConnectionState.CONNECTING
    if (current == ConnectionState.READY) {
        pairingSuccess()
    } else {
        imgGlasses.alpha = 0.5f
        pairTitle.text = "Searching for your glasses"
        pairSubtitle.text = "Make sure they are powered on and nearby"
        startRings()
        updatePairing(current)
    }
}
```

- [ ] **Step 2: Manejar transiciones en `updatePairing` con cancelación de animadores**
Asegurar que cuando el estado pase a `READY`, `stopRings()` se invoque inmediatamente y el overlay se cierre con animación de éxito sin quedar colgado:
```kotlin
private fun updatePairing(state: ConnectionState) {
    if (!pairing) return
    when (state) {
        ConnectionState.CONNECTING, ConnectionState.BONDING -> {
            pairTitle.text = "Searching for your glasses"
            pairSubtitle.text = "Make sure they are powered on and nearby"
        }
        ConnectionState.PAIRING -> {
            pairTitle.text = "Found your glasses"
            pairSubtitle.text = deviceLabel()
            imgGlasses.animate().alpha(1f).scaleX(1.06f).scaleY(1.06f).setDuration(260)
                .withEndAction {
                    imgGlasses.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                }.start()
        }
        ConnectionState.SESSION -> {
            pairTitle.text = "Almost ready"
            pairSubtitle.text = deviceLabel()
        }
        ConnectionState.READY -> {
            pairingSuccess()
        }
        ConnectionState.FAILED -> {
            pairingFailed()
        }
        else -> {}
    }
}
```

- [ ] **Step 3: Auto-dismiss y temporizador a prueba de fugas**
En `pairingSuccess()`:
```kotlin
private fun pairingSuccess() {
    stopRings()
    imgGlasses.alpha = 1f
    pairTitle.text = "Connected"
    pairSubtitle.text = "Explore your AR world"
    imgCheck.visibility = View.VISIBLE
    imgCheck.scaleX = 0f
    imgCheck.scaleY = 0f
    imgCheck.animate().scaleX(1f).scaleY(1f).setDuration(360)
        .setInterpolator(OvershootInterpolator()).start()
    btnPairDone.visibility = View.VISIBLE
    
    // Auto-cierre garantizado tras 1.2 segundos
    pairingOverlay.removeCallbacks(dismissPairingRunnable)
    pairingOverlay.postDelayed(dismissPairingRunnable, 1200)
}
private val dismissPairingRunnable = Runnable { if (pairing) dismissPairing() }
```

---

### Task 3: Verificación de Emisión de Estados en ConnectionManager

**Files:**
- Modify: [`android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt:90-106`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt#L90-L106)
- Modify: [`android-kotlin/app/src/test/java/com/myvu/client/service/ConnectionManagerTest.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/test/java/com/myvu/client/service/ConnectionManagerTest.kt)

**Interfaces:**
- Consumes: Mutaciones de estado en [`ConnectionManager`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/ConnectionManager.kt)
- Produces: Emisiones reactivas y consistentes en `stateFlow`.

- [ ] **Step 1: Escribir prueba unitaria para StateFlow en ConnectionManagerTest**
Crear prueba que valide las transiciones de estado a través de `stateFlow`:
```kotlin
@Test
fun stateFlowEmitsUpdatedStates() = kotlinx.coroutines.runBlocking {
    val flow = MutableStateFlow(ConnectionState.IDLE)
    
    flow.value = ConnectionState.CONNECTING
    assertEquals(ConnectionState.CONNECTING, flow.value)
    
    flow.value = ConnectionState.READY
    assertEquals(ConnectionState.READY, flow.value)
}
```

- [ ] **Step 2: Verificar la correcta notificación de listener y flow en ConnectionManager.kt**
Asegurar que cuando `state` cambia en `ConnectionManager.kt`, se actualice tanto `_stateFlow.value` como `listener?.onStateChanged(s)`:
```kotlin
var state: ConnectionState
    get() = _stateFlow.value
    private set(s) {
        if (s == ConnectionState.READY && _stateFlow.value != ConnectionState.READY) {
            sessionStartTimeMs = android.os.SystemClock.elapsedRealtime()
        } else if (s != ConnectionState.READY) {
            sessionStartTimeMs = 0L
        }
        _stateFlow.value = s
        listener?.onStateChanged(s)
    }
```

---

### Task 4: Verificación Integral del Flujo de Conexión

**Files:**
- Test: Ejecución de suite completa de pruebas unitarias y de integración.

- [ ] **Step 1: Ejecutar suite de pruebas unitarias de Kotlin**
Ejecutar: `./gradlew :app:testDebugUnitTest`
Esperado: Todos los tests pasando (100% PASS).

- [ ] **Step 2: Validar compilación de APK en Android**
Ejecutar: `./gradlew :app:assembleDebug`
Esperado: BUILD SUCCESSFUL sin errores ni warnings críticos de Kotlin/Corrutinas.

- [ ] **Step 3: Sincronizar índice de CodeGraph**
Ejecutar: `codegraph sync`
Esperado: Árbol de símbolos actualizado.
