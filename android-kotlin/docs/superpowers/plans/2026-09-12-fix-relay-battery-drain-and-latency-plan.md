# Plan de Corrección: Drenaje Crítico de Batería, Bucle RFCOMM, Latencia y Pruebas

## 1. Diagnóstico Detallado del Log (`myvu_client_log.txt`)

Tras un análisis exhaustivo de los 390 eventos registrados en la sesión:

### 1.1 Fuga Crítica de Batería: Bucle Infinito de Reconexión RFCOMM SPP (Gafas + Teléfono)
- **Evidencia en el Log**:
  - Desde `20:45:33` hasta `20:47:00`, cada 2.5 - 3.5 segundos exactos se repite el ciclo:
    ```
    app relay down -- reconnecting (attempt 1/6)
    opening the app relay: RFCOMM -> 00009121-0000-1000-8000-00805f9b34fb
    RFCOMM connected (...)
    app relay ready (...)
    <- SPP server closed by the glasses -- dropping the relay
    app relay closed
    ```
  - Las gafas reportan en su telemetría (`suspend_stats`):
    - `20:45:42`: `suspend_time: 09:45:33.222`, `wakeup_time: 09:45:36.081` (tiempo en reposo: solo 2.8s)
    - `20:46:54`: `suspend_time: 09:46:45.879`, `wakeup_time: 09:46:47.545` (tiempo en reposo: solo 1.6s)
  - **Tasa de Drenaje**: La batería de las gafas cae de 76% (`20:45:27`) a 75% (`20:46:42`) en apenas **75 segundos**. A este ritmo, la batería se agota en menos de 2 horas en reposo pasivo (~48% por hora).
- **Causas Raíz Identificadas**:
  1. **Violación del protocolo de suspensión de Flyme XR**: Las gafas Meizu MYVU cierran deliberadamente su servidor SPP (`CMD_SPP_SERVER_REQUEST_STATE_CLOSE`) cuando no hay tareas de alto tráfico (como navegación HUD o streaming de pantalla), para permitir que su radio Bluetooth entre en ultra bajo consumo. Al recibir este comando, `ConnectionManager.kt:719` llama a `supervisor?.onRelayLost()`, forzando una reconexión inmediata.
  2. **Reinicio prematuro del contador en `RelaySupervisor.kt`**: Cuando el socket RFCOMM se abre y completa el handshake brevemente (~50ms), `check()` detecta `delegate.isRelayConnected() == true` y resetea `attempt = 0`. Cuando las gafas cierran el socket 50ms después, `onRelayLost()` calcula el retardo con `attempt = 0`, resultando en un reintento a los 2 segundos exactos (`attempt 1/6` perpetuo), anulando el backoff exponencial.

---

### 1.2 Retraso Crítico de 9 Segundos en Entrega de Notificaciones
- **Evidencia en el Log**:
  - A las `20:46:16.384`:
    `!! app relay not ready -- queued notification for RFCOMM delivery`
  - La notificación quedó congelada en cola esperando por el relay RFCOMM durante **8.85 segundos**, entregándose tardíamente a las `20:46:25.235` cuando el relay conectó efímeramente.
- **Causa Raíz**:
  - En `ConnectionManager.kt:1248-1258`, si `transport == null` (relay no conectado) pero `canConnectRelay()` es verdadero (la UUID de la sesión existe), el código deliberadamente **retiene** la notificación y bloquea su envío inmediato sobre BLE.
  - Las notificaciones son tramas JSON pequeñas (<500 bytes) que funcionan de forma inmediata y nativa sobre BLE (`bleSession`), con latencia < 50ms.

---

### 1.3 Saturación y Paquetes Duplicados de Configuración (`code: 2` y Ajustes)
- **Evidencia en el Log**:
  - A las `20:46:01.228`, se emitieron ráfagas duplicadas simultáneas:
    - Dos paquetes idénticos de `set_brightness`
    - Dos paquetes idénticos de `set_volume`
    - Dos paquetes idénticos de `set_standby_position`
    - Dos paquetes idénticos de `set_screen_off_time`
  - Cada vez que el relay intentó conectar (cada 3 segundos), `ConnectionManager.kt:1077-1090` disparó un paquete redundante de `AiProtocol.assistantConfig` (`code: 2`), sumando más de 20 paquetes innecesarios.
- **Causa Raíz**:
  - En `GlassesSettingsActivity.kt:348-383`, el método de guardado invoca `GlassesConfig.setBrightness()` (que internamente llama a `activeConn?.setBrightness()`) y luego líneas más abajo vuelve a invocar manualmente `activeConn?.setBrightness()`.
  - En `ConnectionManager.kt:1077-1090`, el callback retrasado (300ms) de `assistantConfig` se ejecuta en cada reconexión del relay aunque BLE ya sincronizó toda la configuración.

---

### 1.4 Latencia Excesiva de Audio/VAD: Utterance Cap de 19.3 Segundos
- **Evidencia en el Log**:
  - `20:46:15.803`: `AI: speech detected`
  - El usuario dijo una consulta corta: "¿Cuál es la TRM del día de hoy?" (~1.8 segundos de voz).
  - El sistema no cerró el micrófono por silencio y siguió grabando hasta `20:46:35.019`:
    `AI: utterance hit the length cap` (19.36 segundos, 324 paquetes Opus, 929,281 muestras).
  - El usuario tuvo que esperar 20 segundos para recibir su respuesta rápida.
- **Causa Raíz**:
  - En `AiConversation.kt:209-230`, `speechThreshold` tiene un valor base bajo (`SPEECH_ENERGY = 75.0`) y tope de 200.0. El micrófono de las gafas Meizu MYVU con ganancia de hardware entrega un piso de ruido ambiente de 70 a 100 RMS.
  - Una vez `speechStarted = true`, el código nunca adapta el umbral de silencio respecto al pico de voz del usuario (`peakEnergy`). Dado que el ruido ambiente se mantiene >= 75.0, la condición `level >= speechThreshold` se cumple continuamente, actualizando `lastSpeechAt` en cada paquete e impidiendo que se active `SILENCE_HOLD_MS` (1200ms).
  - Además, `silenceTimeout` se remueve al detectar voz y no existe un watchdog periódico que corte si el nivel baja relativo al pico de voz.

---

## 2. Plan de Solución Arquitectural y de Rendimiento

### Fase 1: Control Inteligente del Servidor SPP y Supresión del Bucle de Batería
- **Archivos**:
  - `app/src/main/java/com/myvu/client/service/RelaySupervisor.kt`
  - `app/src/main/java/com/myvu/client/service/ConnectionManager.kt`
- **Acciones**:
  1. **Respetar el Estado Cerrado del Servidor SPP**:
     - Agregar estado `sppServerOpen: Boolean` en `ConnectionManager`.
     - Cuando las gafas envíen `CMD_SPP_SERVER_REQUEST_STATE_CLOSE`, marcar `sppServerOpen = false` e indicar a `RelaySupervisor.onSppServerClosed()`.
     - El supervisor entrará en modo pasivo/dormido y **NO intentará reconectar** hasta que:
       - Las gafas envíen explícitamente `CMD_SPP_SERVER_REQUEST_CONNECT` (cmd 71), `CMD_SPP_SERVER_REQUEST_STATE_OPEN`, o un nuevo `CMD_SPP_SERVER_UUID_SYNC`.
       - O una función que requiera el relay (ej. Navegación HUD) sea activada por el usuario (`wake()`).
  2. **Corregir el Reseteo Prematuro de Reintentos en `RelaySupervisor`**:
     - Exigir que el relay permanezca conectado y estable durante al menos 30 segundos (`STABLE_RELAY_THRESHOLD_MS = 30000L`) antes de resetear `attempt = 0`.
     - Si el relay se desconecta antes de ese umbral, `attempt` no se resetea y el backoff exponencial progresa adecuadamente (2s -> 4s -> 8s -> 16s -> 32s -> 60s) hasta dormirse.

---

### Fase 2: Entrega Inmediata de Notificaciones sin Bloqueo por Relay
- **Archivo**:
  - `app/src/main/java/com/myvu/client/service/ConnectionManager.kt`
- **Acciones**:
  1. Modificar `sendActionNow` para notificaciones:
     - Si el relay está conectado (`transport != null`), enviar por el relay.
     - Si el relay **NO** está conectado (`transport == null`), pero la sesión BLE está lista (`bleSession.ready`), **despachar la notificación inmediatamente a través de BLE**, sin encolarla ni forzar un retardo de 9 segundos.

---

### Fase 3: Eliminación de Ráfagas y Ajustes Duplicados
- **Archivos**:
  - `app/src/main/java/com/myvu/client/ui/GlassesSettingsActivity.kt`
  - `app/src/main/java/com/myvu/client/service/ConnectionManager.kt`
- **Acciones**:
  1. En `GlassesSettingsActivity.kt`:
     - Remover el doble despacho de `activeConn?.setBrightness()`, `setVolume()`, `setStandbyPosition()`, `setScreenOffTime()` en el guardado, ya que `GlassesConfig` ya realiza el envío por hardware.
  2. En `ConnectionManager.kt`:
     - Remover el envío redundante de `AiProtocol.assistantConfig` en la ráfaga retrasada del relay (`applyDefaults skipped on relay session`), ya que BLE ya lo sincronizó completamente.

---

### Fase 4: VAD Adaptativo y Detección Inmediata de Silencio en `AiConversation`
- **Archivo**:
  - `app/src/main/java/com/myvu/client/ai/AiConversation.kt`
- **Acciones**:
  1. **Umbral Dinámico de Silencio Post-Voz**:
     - Mientras el usuario habla, registrar `peakEnergy` de la voz.
     - Una vez `speechStarted == true`, calcular el umbral de actividad de voz dinámicamente como:
       `val dynamicThreshold = max(speechThreshold, peakEnergy * 0.22)`
       Cualquier audio con energía inferior a `dynamicThreshold` se clasificará como silencio/ruido ambiente.
  2. **Watchdog Periódico de Silencio**:
     - Implementar un chequeo periódico en el Handler principal (cada 250ms) que verifique si `speechStarted && (System.currentTimeMillis() - lastSpeechAt > SILENCE_HOLD_MS)`.
     - Esto garantiza que cuando el usuario termine de hablar, la grabación se detenga en ~1.2 segundos sin depender únicamente de la llegada de paquetes y sin alcanzar los 20 segundos de límite forzado.

---

### Fase 5: Ampliación Exhaustiva del Plan de Pruebas Unitarias
- **Archivos**:
  - `app/src/test/java/com/myvu/client/service/RelaySupervisorTest.kt`
  - `app/src/test/java/com/myvu/client/service/ConnectionManagerTest.kt`
  - `app/src/test/java/com/myvu/client/ai/AiConversationSttTest.kt`
- **Casos de Prueba a Implementar**:
  1. **`RelaySupervisorTest`**:
     - Verificar que `onSppServerClosed()` desactiva los reintentos automáticos hasta nuevo aviso (`wake()`).
     - Verificar que desconexiones rápidas (<30s) preservan el contador de intentos y aplican backoff exponencial estricto en lugar de resetearse a `attempt 0`.
     - Verificar que una conexión estable (>30s) resetea exitosamente el contador a 0.
  2. **Pruebas de Despacho de Notificaciones**:
     - Verificar que ante un relay no conectado (`transport == null`), las notificaciones se emiten directamente sobre BLE sin quedar retenidas en la cola.
  3. **Pruebas de VAD en `AiConversation`**:
     - Simular ráfaga de paquetes Opus con ruido de fondo (energía 85) -> el habla inicia con energía 500 -> luego vuelve a nivel 85 -> verificar que el fin de la elocución (`endUtterance`) se dispara dentro del tiempo de silencio (`SILENCE_HOLD_MS = 1200ms`) y no en 20s.

---

## 3. Plan de Verificación y Compilación
1. Ejecutar `./gradlew testDebugUnitTest` mediante `rtk proxy`.
2. Verificar que las 302+ pruebas existentes más las nuevas pruebas pasen con 100% de éxito.
3. Ejecutar `./gradlew assembleDebug` para confirmar integridad binaria.
4. Actualizar `docs/PROJECT_MEMORY.md`, `README.md` y `docs/ARCHITECTURE.md`.
5. Ejecutar `rtk proxy codegraph sync`.
