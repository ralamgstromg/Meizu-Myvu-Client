# Plan de Mejora: Optimización de Batería y Recursos (Celular y Gafas Meizu Myvu)

## 1. Diagnóstico y Hallazgos del Log (`myvu_client_log.txt`)

En la sesión registrada de 1 hora y 38 minutos (10:11:58 a 11:49:41):
- **Batería de las Gafas**:
  - 10:55:06 -> 100% (desconectada del cargador)
  - 11:17:19 -> 99%
  - 11:22:39 -> 98%
  - 11:27:44 -> 97%
  - 11:32:35 -> 96%
  - 11:37:45 -> 95%
  - 11:43:00 -> 94%
  - 11:48:05 -> 93%
  - **Tasa de Drenaje**: 7% en 53 minutos (~**8.0% por hora**) en reposo pasivo. En unas gafas con batería pequeña (~200 mAh), esto limita la autonomía a menos de 10-12 horas sin que el usuario las use activamente.

### Fugas Críticas Detectadas:
1. **Fuga de Escucha Activa en `captured_init.txt` / `InitBurst` (Gafas)**:
   - En `captured_init.txt`, la trama 1117 (mensaje 24) contiene en código duro:
     `{"code":2,"payload":{"isContinuousDialogueEnable":true,"isLowPowerWakeupEnable":true,...}}`.
   - Cuando el relay RFCOMM se conecta, `sendInitBurst` retransmite esta trama y luego omite `applyDefaults()` (`applyDefaults skipped on relay session — BLE already applied settings`).
   - Esto reactiva el DSP de micrófonos de las gafas en escucha continua permanente tras cada reconexión del relay.
2. **Polling Excesivo de `get_device_info` (Gafas + Celular)**:
   - Se emitieron 11 consultas de `get_device_info` en 50 minutos desde `queryBatteryInfo()` (`ConnectActivity`, `NotesActivity` en `onResume`, y `batteryQueryTask`).
   - Las gafas **ya envían push espontáneo** de batería mediante `sync_glass_battery_info` cada vez que cambia 1% (comprobado en 20+ líneas del log). Pollear `get_device_info` despierta innecesariamente el procesador de las gafas y transmite paquetes RFCOMM redundantes.
3. **Muestreo de Sensores a Frecuencia de UI en Segundo Plano (Celular)**:
   - `HealthService.kt` registra `TYPE_STEP_COUNTER` y `TYPE_STEP_DETECTOR` con `SensorManager.SENSOR_DELAY_UI` (60 Hz / 60 ms).
   - Esto impide que el CPU del celular entre en suspensión profunda (deep sleep / C-states). Registrar `STEP_DETECTOR` al mismo tiempo que `STEP_COUNTER` dispara una interrupción por cada paso.
4. **Heartbeat BLE Rígido sin Coalescencia de Tráfico (Gafas + Celular)**:
   - `BleHeartbeat.kt` dispara un paquete al canal urgente cada 10 segundos fijos.
   - Aunque tiene soporte para `isDataActive` y `notifyDataActivity()`, este método jamás es invocado en todo el proyecto. Se despierta la radio BLE incluso si se acaban de transmitir datos.
5. **Watchdog con `setAndAllowWhileIdle` que Rompe el Modo Doze (Celular)**:
   - `ServiceWatchdogReceiver` utiliza `alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, ...)` cada 15 minutos.
   - Esto despierta forzosamente el procesador del teléfono de Doze Mode para un servicio en primer plano que ya es persistente.
6. **Tempestad de Reconexiones RFCOMM sin Backoff Progresivo**:
   - Tras el cierre del servidor SPP por las gafas, se inician reintentos inmediatos (`attempt 1/6`) saturando el socket Bluetooth antes de que las gafas terminen de reciclar el servicio.

---

## 2. Objetivos de Optimización

1. **Gafas**:
   - Reducir el drenaje en standby de **~8% / hora** a menos de **~2-3% / hora**.
   - Erradicar la reactivación de escucha continua eliminando la trama sucia de `captured_init.txt` o filtrándola en `InitBurst.kt`.
   - Asegurar que `applyDefaults` siempre imponga la configuración de bajo consumo del usuario tras cualquier enlace de transporte.
   - Eliminar el polling forzado de batería si las gafas ya proporcionan telemetría push de batería.
   - Promover el uso de `Wear Detection` (`set_wear_detection_mode: true`).

2. **Celular**:
   - Optimizar `HealthService` cambiando el sensor delay a `SENSOR_DELAY_NORMAL` (o con batching de hardware de 60s) y eliminando `STEP_DETECTOR` redundante cuando `STEP_COUNTER` está disponible.
   - Reemplazar la alarma `setAndAllowWhileIdle(WAKEUP)` del watchdog por una alarma no-wakeup estándar o WorkManager, permitiendo que el teléfono duerma en Doze Mode.
   - Implementar coalescencia en `BleHeartbeat`: invocar `notifyDataActivity()` al transferir tramas y espaciar el heartbeat a 20-25 segundos de silencio.
   - Agregar backoff exponencial suave en reintentos de RFCOMM para evitar saturación de CPU y radio Bluetooth.

---

## 3. Plan de Acción Detallado por Fases

### Fase 1: Limpieza del Init Burst y Configuración de Bajo Consumo (Gafas)
- **Archivo**: `app/src/main/java/com/myvu/client/protocol/InitBurst.kt`
  - Filtrar en `InitBurst.load()` cualquier mensaje dirigido a `com.upuphone.ai.assistant` o que contenga `isContinuousDialogueEnable` o `assistantConfig`.
  - Asegurar que solo se envíen tramas de inicialización de UI indispensables (launcher, dock, etc.).
- **Archivo**: `app/src/main/java/com/myvu/client/service/ConnectionManager.kt`
  - En `onRelayReady` y `onBleReady`, asegurar que `applyDefaults()` sincronice siempre la configuración real de bajo consumo (`continuousDialogueEnabled = false`, `voiceWakeupEnabled = false`).
  - Asegurar que si el usuario tiene `wearDetectionEnabled`, se aplique siempre `true`.

### Fase 2: Supresión del Polling Agresivo de Batería (Gafas + Celular)
- **Archivo**: `app/src/main/java/com/myvu/client/service/ConnectionManager.kt`
  - Eliminar el timer periódico `batteryQueryTask` (15 min) o convertirlo en una verificación de seguridad espaciada (e.g. cada 2 horas) solo si no se han recibido eventos push `sync_glass_battery_info`.
  - En `queryBatteryInfo()`, verificar si el valor de batería es reciente (< 10 minutos). Si ya es reciente, omitir la consulta `get_device_info`.
- **Archivos**: `ConnectActivity.kt`, `NotesActivity.kt`
  - Evitar invocar `queryBatteryInfo()` ciegamente en `onResume` si ya se dispone de `glassesInfoVal?.battery`.

### Fase 3: Coalescencia y Adaptabilidad del Heartbeat BLE (Gafas + Celular)
- **Archivo**: `app/src/main/java/com/myvu/client/transport/ble/BleHeartbeat.kt`
  - Aumentar el intervalo base de 10s a 20s.
  - Al recibir cualquier notificación de actividad (`notifyDataActivity()`), posponer el siguiente tick de heartbeat otros 20 segundos.
- **Archivos**: `BleTransport.kt`, `ConnectionManager.kt`
  - Conectar llamadas a `heartbeat?.notifyDataActivity()` en cada envío y recepción exitosa de tramas BLE.

### Fase 4: Optimización de Sensores de Salud y Background Keep-Alive (Celular)
- **Archivo**: `app/src/main/java/com/myvu/client/health/HealthService.kt`
  - Cambiar `SensorManager.SENSOR_DELAY_UI` a `SensorManager.SENSOR_DELAY_NORMAL`.
  - Si `stepCounter` es soportado y registrado, NO registrar `stepDetector` (evita interrupciones continuas por paso en CPU).
  - Usar batching de eventos (`maxReportLatencyUs = 60_000_000` / 1 minuto) en Android 4.4+ si está soportado por el hardware.
- **Archivo**: `app/src/main/java/com/myvu/client/service/ServiceWatchdogReceiver.kt`
  - Cambiar `setAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)` a `alarmManager.set(AlarmManager.ELAPSED_REALTIME, ...)` o WorkManager sin flag de despertar (sin `_WAKEUP`).
  - Evitar que el watchdog despierte al teléfono de Doze Mode cuando el servicio foreground ya está saludable.

### Fase 5: Estabilización de Reconexión RFCOMM
- **Archivo**: `app/src/main/java/com/myvu/client/service/RelaySupervisor.kt`
  - Incorporar un backoff exponencial mínimo tras desconexión abrupta de SPP (e.g. 2s, 4s, 8s, 16s) para dar tiempo a la pila Bluetooth de las gafas de liberar el canal antes de reintentar el handshake.

### Fase 6: Pruebas, Verificación y Memoria
- Ejecutar suite de pruebas unitarias (`./gradlew testDebugUnitTest`).
- Compilar APK (`./gradlew assembleDebug`).
- Sincronizar grafo con `codegraph sync`.
- Registrar cambios en `docs/PROJECT_MEMORY.md` y actualizar `README.md` y `docs/ARCHITECTURE.md`.
