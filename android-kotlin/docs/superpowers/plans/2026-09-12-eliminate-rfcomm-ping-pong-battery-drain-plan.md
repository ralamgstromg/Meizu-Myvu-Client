# Plan: Eliminar Bucle Ping-Pong RFCOMM y Optimizar Batería de Gafas y Teléfono

## 1. Problema Detectado
En los registros de la aplicación (`/home/rcastro/Descargas/myvu_client_log.txt`), se detectó un bucle continuo de reconexión RFCOMM:
- 8 reconexiones en 67 segundos (7 de ellas en 13 segundos).
- Cada ciclo conecta RFCOMM -> handshake exitoso (`AUTH_SUCCESS`) -> las gafas cierran el servidor SPP a los 5ms (`CMD_SPP_SERVER_REQUEST_STATE_CLOSE`) -> a los 2s las gafas envían `CMD_SPP_SERVER_REQUEST_CONNECT` (71) o `CMD_SPP_SERVER_REQUEST_STATE_OPEN` (72) -> `ConnectionManager` llama ciegamente a `supervisor?.wake()` -> `attempt = 0` y reconecta de inmediato (`attempt 1/6`).
- **Consecuencia**: Drena la pequeña batería de las gafas (~170mAh) al impedir deep-sleep del SoC y mantener activo el radio Bluetooth Classic (BR/EDR). Drena la batería del móvil y arriesga saturar la pila de Bluetooth de Android.

## 2. Solución Arquitectural
1. **Cooldown y Descarte de Ráfagas en `ConnectionManager.kt`**:
   - Registrar `lastSppCloseTime`.
   - Cuando se reciba `CMD_SPP_SERVER_REQUEST_CONNECT` (71) o `CMD_SPP_SERVER_REQUEST_STATE_OPEN` (72), verificar si estamos en ventana de cooldown (15s) o si no hay ninguna función activa de alto ancho de banda que requiera RFCOMM (`isRelayFeatureActive`).
   - Si no hay función activa y se está en cooldown o en modo pasivo, descartar la reapertura ciega y registrar en logcat/trace para dejar dormir el hardware de las gafas.
2. **Modo Forzado y Protección en `RelaySupervisor.kt`**:
   - Modificar `wake(force: Boolean = false)` para que, si `sppServerSuspended` está activo y `force` es `false`, respete el modo pasivo de bajo consumo y no despierte innecesariamente.
   - Solo cuando haya una acción explícita (evento AI con micrófono, apertura de Trackpad, inicio de sesión) se invoca con `force = true`.
3. **Control de Estado Activo de Funciones RFCOMM en `ConnectionManager.kt`**:
   - Introducir `trackpadActive` y flag de uso de relay.
   - Asegurar que `wakeRelay(force: Boolean = true)` se use para solicitudes deliberadas del usuario (ej: Trackpad).

## 3. Verificación
- Pruebas unitarias de `RelaySupervisorTest` y `ConnectionManager` para verificar que:
  - `onSppServerClosed()` suspende el supervisor.
  - Llamadas a `wake()` sin `force` respetan la suspensión pasiva.
  - Llamadas a `wake(force = true)` reactivan el supervisor.
  - Recepción de `CMD_SPP_SERVER_REQUEST_CONNECT` durante cooldown no provoca reconexión en bucle.
- Ejecutar suite completa con `rtk proxy ./gradlew test`.
- Compilar APK con `rtk proxy ./gradlew assembleDebug`.
- Sincronizar codegraph y actualizar memoria y documentación.
