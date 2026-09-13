# Plan de Implementación: Sistema Unificado e Independiente de Activity Log

**Fecha:** 2026-09-12  
**Módulo:** `app/src/main/java/com/myvu/client/ui/`, `com/myvu/client/core/`  
**Estado:** Pendiente de Aprobación

---

## 1. Contexto y Objetivos

Actualmente, el visor de registros ("Activity log") está incrustado dentro de `ConnectActivity` como una pestaña secundaria de un `TabLayout` ("Controls" / "Log"). Presenta varias limitaciones críticas:
1. **Acoplado únicamente a las gafas**: Su ciclo de vida, visualización y controles solo existen dentro de la pantalla de conexión de las gafas MYVU.
2. **Sin diferenciación de dispositivos**: No identifica si un evento proviene de las gafas, de auriculares Bluetooth, del sistema/teléfono o de las acciones de IA.
3. **Poco práctico e inaccesible**: Si el usuario está en el Chat de IA (`ChatActivity`), al pulsar "Logs y Actividad" intenta seleccionar la pestaña 1 de `ConnectActivity`.
4. **Falta de configuración independiente**: No tiene controles independientes de activación/desactivación, filtrado por tipo de dispositivo, búsqueda en vivo ni exportación avanzada de registros.

### Requerimientos Solicitados
1. **Interfaz independiente**: Crear una pantalla dedicada (`ActivityLogActivity`) para consultar y monitorear toda la actividad de la aplicación y sus dispositivos.
2. **Información unificada de todos los dispositivos**: Categorizar y registrar eventos no solo de las gafas (`SMART_GLASSES`), sino también de auriculares/dispositivos Bluetooth (`BLUETOOTH`), teléfono/sistema (`PHONE`) y asistente de IA (`AI`).
3. **Configuración independiente**: Permitir activar o desactivar el registro de actividad de manera independiente directamente desde la interfaz y preferencias, además de limpiar o compartir los logs.
4. **Remoción de interfaz vieja**: Eliminar completamente la pestaña y vistas de log de `ConnectActivity`, liberando espacio y simplificando esa pantalla para que se enfoque exclusivamente en el control de las gafas.

---

## 2. Arquitectura de la Solución

```
   ┌────────────────────────────────────────────────────────┐
   │                  Dispositivos y Fuentes                │
   │  ┌──────────────┐  ┌───────────────┐  ┌─────────────┐  │
   │  │ Gafas MYVU   │  │ Dispositivos  │  │ Teléfono /  │  │
   │  │ (Firmware/   │  │ Bluetooth     │  │ Sistema /   │  │
   │  │  Touchpad)   │  │ (A2DP/Headset)│  │ IA / Room   │  │
   │  └──────┬───────┘  └───────┬───────┘  └──────┬──────┘  │
   └─────────┼──────────────────┼─────────────────┼─────────┘
             │                  │                 │
             ▼                  ▼                 ▼
   ┌────────────────────────────────────────────────────────┐
   │                       LogBus.kt                        │
   │  - Buffer histórico estructurado: List<LogEntry>       │
   │  - Categorías: DeviceSource (GLASSES, BT, PHONE, AI)   │
   │  - SharedFlow<LogEntry> + SharedFlow<LogMessage>       │
   │  - Inferencia inteligente de origen de dispositivo     │
   │  - Switch maestro & Filtros por fuente en Prefs        │
   └────────────────────────────┬───────────────────────────┘
                                │
                                ▼
   ┌────────────────────────────────────────────────────────┐
   │             ActivityLogActivity (NUEVA)                │
   │  - Switch maestro: Activar / Pausar registro           │
   │  - Filtros rápidos: [Todos] [Gafas] [BT] [Teléfono]    │
   │  - Búsqueda en vivo por texto                          │
   │  - Acciones: Compartir (archivo/texto), Limpiar        │
   │  - RecyclerView con badges de dispositivo y sintaxis   │
   └────────────────────────────────────────────────────────┘
```

---

## 3. Plan de Cambios Detallado

### Fase 1: Modelo de Datos y Mejoras en `LogBus.kt` y `Prefs.kt`
1. **Enum `DeviceSource`**:
   - `ALL` ("Todos")
   - `GLASSES` ("Gafas MYVU")
   - `BLUETOOTH` ("Dispositivos BT / Auriculares")
   - `PHONE` ("Teléfono / Sistema")
   - `AI` ("Asistente de IA")
2. **Data Class `LogEntry`**:
   - `id: Long`
   - `timestamp: Long`
   - `source: DeviceSource`
   - `level: Int` (Log.INFO, WARN, ERROR, DEBUG)
   - `tag: String`
   - `message: String`
   - `throwable: Throwable?`
   - `deviceName: String?`
   - `formattedLine: String`
3. **Evolución de `LogBus`**:
   - Mantener compatibilidad absoluta con `LogBus.log(msg)`, `warn(msg)`, `error(msg, t)`, `history()`, `addListener()`.
   - Incorporar buffer circular `ENTRIES: Deque<LogEntry>` (capacidad 2000).
   - Añadir `logEntryFlow: SharedFlow<LogEntry>` y `EntryListener`.
   - Detección automática de `DeviceSource` analizando contenido y tag cuando no se especifica explícitamente.
   - Métodos dedicados:
     - `LogBus.glasses(msg, level)`
     - `LogBus.bluetooth(msg, deviceName, level)`
     - `LogBus.phone(msg, level)`
     - `LogBus.ai(msg, level)`
   - Método de consulta filtrada: `getFilteredEntries(source, minLevel, query)`.
4. **Configuración en `Prefs.kt`**:
   - `loggingEnabled(context)` y `setLoggingEnabled(context, boolean)` integrados con `LogBus.isEnabled`.

### Fase 2: Creación de la Interfaz Independiente `ActivityLogActivity`
1. **Diseño de Layouts**:
   - `app/src/main/res/layout/activity_log.xml`:
     - Barra superior estilo Obsidian con botón volver, título "Registro de Actividad", contador de eventos en vivo.
     - Switch de configuración: Activar / Pausar registro.
     - Botones de acción: Compartir (`ic_share_cyber`), Limpiar (`ic_delete_cyber`).
     - Campo de búsqueda en vivo con filtro en tiempo real.
     - Chips de filtrado horizontal: `[Todos]`, `[👓 Gafas]`, `[🎧 Bluetooth]`, `[📱 Teléfono]`, `[🤖 Asistente IA]`, `[⚠️ Solo Errores]`.
     - Toggle de auto-scroll (Auto-scroll ON/OFF).
     - `RecyclerView` (`rvActivityLogs`).
     - `emptyView` contextual para cuando no hay logs o el registro está pausado.
   - `app/src/main/res/layout/item_activity_log.xml`:
     - Badge con icono y nombre de la fuente (Gafas, BT, Teléfono, IA).
     - Badge de nivel de severidad (INFO, WARN, ERROR).
     - Hora con milisegundos (`HH:mm:ss.SSS`).
     - Mensaje con tipografía monoespaciada y coloración semántica.
     - Contenedor expandible para stack traces si hay excepciones.
     - Soporte para copiar al portapapeles al mantener presionado.
2. **Lógica de `ActivityLogActivity.kt`**:
   - `ActivityLogAdapter` con DiffUtil o actualización eficiente.
   - Conexión reactiva a `LogBus.logEntryFlow` y carga del historial al abrir.
   - Filtrado instantáneo por texto y por chip de dispositivo.
   - Compartir: Genera archivo `myvu_activity_log.txt` en `cacheDir`, compartido mediante `FileProvider` con fallback a texto plano.
   - Limpiar: Diálogo Material de confirmación antes de vaciar el buffer.
   - Switch de estado: Sincronizado en tiempo real con `Prefs.setLoggingEnabled()` y `LogBus.isEnabled`.
3. **Registro en `AndroidManifest.xml`**:
   - Declarar `com.myvu.client.ui.ActivityLogActivity`.

### Fase 3: Remoción de la Interfaz Vieja de `ConnectActivity`
1. **Modificación de `activity_connect.xml`**:
   - Eliminar el `TabLayout` (`R.id.tabs`) con las pestañas "Controls" y "Log".
   - Eliminar el contenedor `pageLog` y sus vistas (`rvLog`, `btnShareLog`, `btnClearLog`).
   - Reestructurar el layout para que `pageControls` (`NestedScrollView`) sea la vista directa principal.
   - Añadir botón de acceso rápido `btnOpenActivityLog` en la barra superior junto al botón de dispositivos Bluetooth y ajustes de IA.
2. **Limpieza en `ConnectActivity.kt`**:
   - Remover implementación de `LogBus.Listener` de `ConnectActivity`.
   - Remover variables `rvLog`, `logAdapter`.
   - Remover métodos `wireTabs()`, `crossFade()`, `shareLog()`, `logAtBottom()`, `scrollToBottom()`, `onLine()`.
   - Conectar botón `btnOpenActivityLog` para iniciar `ActivityLogActivity`.
   - Actualizar menú lateral: `R.id.nav_logs` ahora lanza `Intent(this, ActivityLogActivity::class.java)`.

### Fase 4: Actualización de Accesos Globales
1. **`ChatActivity.kt`**:
   - Actualizar `R.id.nav_logs` en el drawer de navegación para abrir directamente `ActivityLogActivity`.
2. **`SettingsActivity.kt`**:
   - Asegurar que la opción de Registro de actividad incluya un acceso directo a "Ver Registro de Actividad" (`ActivityLogActivity`).

### Fase 5: Pruebas y Validación
1. **Tests Unitarios**:
   - Actualizar y expandir `LogBusTest.kt`:
     - Test de almacenamiento estructurado de `LogEntry`.
     - Test de inferencia automática de `DeviceSource` (Gafas, BT, Teléfono, IA).
     - Test de métodos explícitos (`glasses()`, `bluetooth()`, `phone()`, `ai()`).
     - Test de filtrado por dispositivo y nivel.
     - Test de activación/desactivación y limpieza de logs.
2. **Compilación y Suite Completa**:
   - Ejecutar `./gradlew testDebugUnitTest`.
   - Ejecutar `./gradlew assembleDebug`.
3. **Sincronización y Memoria**:
   - `codegraph sync`.
   - Registrar en `docs/PROJECT_MEMORY.md`, `README.md` y `docs/ARCHITECTURE.md`.
