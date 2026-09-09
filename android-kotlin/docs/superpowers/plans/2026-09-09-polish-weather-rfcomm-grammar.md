# Plan: Pulido de Latencia Clima, Programación de Relay RFCOMM, Gramática y Sincronización Horaria

## Objetivo
Resolver las 4 oportunidades de mejora identificadas en el log `myvu_client_log.txt`:
1. **Cache de Geocodificación en OpenMeteo**: Reducir la latencia de consulta meteorológica de ~3.6s a ~1.1s para evitar que el firmware de las gafas active el mensaje en inglés *"Just a moment, please"*.
2. **Gramática en Resumen de Correos**: Pluralizar correctamente los mensajes en `MirrorNotificationListener` cuando el conteo de correos o mensajes es 0 o > 1.
3. **Programación Inteligente de Conexión RFCOMM**: Espaciar el arranque del supervisor de RFCOMM 800ms tras el burst inicial de BLE para evitar saturar la cola del firmware y eliminar el timeout de 6s.
4. **Desduplicación de SyncOffSetTime**: Evitar el envío consecutivo doble de paquetes de sincronización de hora en un intervalo menor a 4 segundos.

## Fases

### Fase 1: Optimización de Clima (OpenMeteo Cache)
- Modificar `OpenMeteo.kt` para mantener una caché concurrente en memoria de `GeoLocation`.
- Agregar método para invalidar/limpiar y probar la resolución instantánea.

### Fase 2: Corrección Gramatical en Notificaciones
- Modificar `MirrorNotificationListener.kt` en `getUnreadSummary` para pluralizar adecuadamente `correo` -> `correos` y `mensaje` -> `mensajes`.

### Fase 3: Conexión y Handshake RFCOMM Suave
- En `ConnectionManager.kt`, demorar el `supervisor?.wake()` 800ms tras `applyDefaults()`.
- Agregar filtro de desduplicación de `ClockSync` / `SyncOffSetTime` (4s throttle).

### Fase 4: Verificación y Pruebas
- Ejecutar tests unitarios de Gradle (`./gradlew testDebugUnitTest`).
- Compilar APK de depuración (`./gradlew assembleDebug`).
- Registrar cambios en `docs/PROJECT_MEMORY.md`.
