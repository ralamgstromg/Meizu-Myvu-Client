# Plan: Complete Disconnect & Background Battery Saver

## Problema
Cuando el usuario presiona "Disconnect" (o cancela el emparejamiento):
1. La app cancelaba la conexión actual, pero `ServiceWatchdogReceiver` seguía programado en `AlarmManager` para activarse cada 15 minutos e invocar `ServiceKeepAliveHelper.ensureServiceRunning()`.
2. `BootReceiver` y `ServiceKeepAliveHelper` forzaban el reinicio de `MyvuService` ante eventos del sistema (`ACTION_USER_PRESENT`, `POWER_CONNECTED`, etc.) sin comprobar si el usuario había desactivado `autoReconnectEnabled`.
3. En `ConnectionManager.kt`, cuando `ble.close()` disparaba la desconexión asíncrona (`onDisconnected`), no se verificaba el flag `userStopped` antes de pasar al estado `FAILED`, lo que a veces re-encolaba intentos de reconexión o ensuciaba el estado con advertencias.
4. En `MyApp.kt`, el watchdog de 15 minutos se programaba incondicionalmente en cada arranque de la app.

## Objetivos
1. Al presionar **Disconnect**:
   - Poner `Prefs.setAutoReconnectEnabled(context, false)`.
   - Cancelar de inmediato el watchdog en `AlarmManager` mediante `ServiceWatchdogReceiver.cancelWatchdog(context)`.
   - Enviar `ACTION_STOP` a `MyvuService`, deteniendo el servicio en primer plano (`stopForeground(STOP_FOREGROUND_REMOVE)`), cancelando la notificación y finalizando el servicio (`stopSelf()`).
   - Poner `ConnectionManager` en estado `IDLE` de forma atómica y cancelar de raíz `reconnectRunnable` y cualquier escaneo o hilo activo.
2. Blindar los receivers y helpers en segundo plano:
   - `ServiceKeepAliveHelper.ensureServiceRunning()`: si `!Prefs.autoReconnectEnabled(context)`, abortar inmediatamente sin iniciar el servicio.
   - `ServiceWatchdogReceiver.onReceive()`: si `!Prefs.autoReconnectEnabled(context)`, cancelar la alarma y abortar.
   - `ServiceWatchdogReceiver`: implementar `cancelWatchdog(context)`.
   - `MyApp.kt`: solo programar el watchdog si `Prefs.autoReconnectEnabled(this)` es true.
   - `ConnectionManager.kt`: en `onDisconnected()`, `fail()` y `beginConnect()`, comprobar `userStopped || !Prefs.autoReconnectEnabled(context)` para nunca reconectar tras un disconnect del usuario.

## Fases de Implementación
- **Fase 1**: Añadir `cancelWatchdog(context)` y guards en `ServiceWatchdogReceiver.kt`.
- **Fase 2**: Añadir guard `autoReconnectEnabled` en `ServiceKeepAliveHelper.kt`.
- **Fase 3**: Solo programar watchdog en `MyApp.kt` si `autoReconnectEnabled` está activo.
- **Fase 4**: Detener watchdog y limpiar tareas en `ConnectActivity.stopConnection()`.
- **Fase 5**: Fortalecer `ACTION_STOP` en `MyvuService.kt` y guards en `ConnectionManager.kt` (`onDisconnected`, `fail`, `stop`).
- **Fase 6**: Verificación con tests y build de APK.
- **Fase 7**: Actualizar memoria y documentación.
