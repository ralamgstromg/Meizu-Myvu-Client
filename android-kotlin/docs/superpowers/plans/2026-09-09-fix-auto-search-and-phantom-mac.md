# Plan: Fix Bluetooth Auto-Search & Purge Phantom Hardcoded MAC Address

## Problema Detectado
1. **MAC fantasma pre-configurada**: `Prefs.DEFAULT_MAC` tenía hardcodeada la MAC personal del creador del repositorio (`"2C:6F:4E:00:DC:47"`).
2. **Auto-search secuestrado**: Cuando el usuario dejaba el campo MAC vacío en `ConnectActivity`, la actividad no enviaba `EXTRA_MAC` y no borraba la preferencia guardada en `SharedPreferences`. Al arrancar `MyvuService`, el servicio leía la MAC por defecto de `Prefs.targetMac(this)` (`"2C:6F:4E:00:DC:47"`) e intentaba conectar directamente vía BLE a esa dirección que no existe cerca del usuario.
3. **Filtros de hardware demasiado restrictivos en `GlassesScanner`**: `GlassesScanner.start` aplicaba filtros de hardware que exigían `ADV_SERVICE (0x0bd3)` y `GATT_SERVICE (0x0bd1)`. Si las gafas anuncian su nombre ("MYVU") sin incluir esos UUIDs en el paquete primario de anuncio, el hardware de Android descartaba los paquetes antes de llamar a `matches()`.
4. **Falta de descripción para status 147**: `BleTransport.describeDisconnect` no mapeaba `147` (`GATT_CONNECTION_TIMEOUT`), mostrando un mensaje genérico.

## Fases de Implementación

### Fase 1: Limpieza de MAC Fantasma en `Prefs.kt`
- Cambiar `DEFAULT_MAC = ""` (vacío).
- Añadir constante `LEGACY_DEFAULT_MAC = "2C:6F:4E:00:DC:47"`.
- En `targetMac(c: Context)`: Si el valor almacenado en `SharedPreferences` es igual a `LEGACY_DEFAULT_MAC` o está en blanco, devolver `""` y purgar la clave obsoleta.

### Fase 2: Corrección del Flujo de Conexión en `ConnectActivity.kt`
- Al pulsar Conectar con la caja MAC vacía (`auto == true`):
  - Llamar explícitamente a `Prefs.setTargetMac(this, "")`.
  - Enviar `start.putExtra(MyvuService.EXTRA_MAC, "")`.

### Fase 3: Corrección en `MyvuService.kt`
- En `onStartCommand`:
  - Si `intent.hasExtra(EXTRA_MAC)` está presente:
    - Si es blanco o nulo, ejecutar directamente `connection?.startAutoSearch()`.
    - No recurrir a `Prefs.targetMac` si el intent vino explícitamente con `EXTRA_MAC` vacío.

### Fase 4: Optimización de `GlassesScanner.kt`
- En `start()`: Permitir escaneo abierto (filtros abiertos o `emptyList()`) para que el callback de software `matches(result)` pueda evaluar tanto el nombre `"MYVU"` como los UUIDs de servicio `0x0bd3` y `0x0bd1` sin ser bloqueado por filtros de hardware de Android.
- Añadir timeout defensivo y logueo detallado de dispositivos detectados.

### Fase 5: Mapeo de `status=147` en `BleTransport.kt`
- En `describeDisconnect`: mapear `147` como `"GATT_CONNECTION_TIMEOUT (147) -- tiempo de espera agotado (30s); las gafas están apagadas, fuera de rango o no están en modo anuncio"`.

### Fase 6: Verificación y Compilación
- Ejecutar pruebas unitarias: `./gradlew testDebugUnitTest`.
- Compilar APK: `./gradlew assembleDebug`.
- Sincronizar memoria y documentación.
