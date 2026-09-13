# Plan: Control Maestro de Servicios (Activar/Desactivar) y Garantía de IA Standalone

## 1. Contexto y Objetivos

### Requerimiento 1:
- Adicionar un control explícito (botón / switch maestro) que permita activar o desactivar los servicios según sea el caso, para evitar consumo innecesario de recursos (por ejemplo, los reintentos automáticos de reconexión a dispositivos Bluetooth, watchdogs, escaneos y servicios en segundo plano).

### Requerimiento 2:
- Garantizar que todas las funcionalidades de Inteligencia Artificial (Chat Aura, Notas, Recordatorios, Grabadora de reuniones, Resumen del día / Briefing, etc.) se puedan utilizar al 100% sin necesidad de que haya ningún dispositivo conectado (modo teléfono / standalone).

---

## 2. Diagnóstico del Estado Actual

1. **Servicios y Reintentos**:
   - Cuando el usuario se desconecta o no desea usar dispositivos Bluetooth, no existía un interruptor visible en el Dashboard ni en Ajustes para encender/apagar directamente los servicios en segundo plano y la política de reconexión.
   - `btnDisconnect` realizaba la desconexión total, pero faltaba un switch de estado claro ("Servicios en Segundo Plano: ACTIVO / INACTIVO") que permita volver a encenderlos o mantenerlos apagados con 1 toque.
2. **Funcionalidades de IA en Modo Autónomo (Teléfono)**:
   - `ChatActivity`, `NotesActivity`, `VoiceRecorderActivity` y `DailyBriefingService` no dependen de hardware externo (utilizan almacenamiento Room local, llamadas HTTP directas a APIs de Gemini / LiteLLM, reconocimiento de voz del teléfono y TTS local).
   - Sin embargo, en `ConnectActivity` / `view_dashboard.xml`, algunas acciones rápidas o indicadores podían dar la impresión de requerir conexión para funcionar. Debemos asegurar que todos los botones de IA (Chat, Notas, Dictado, Resumen) tengan acceso directo y señalización clara de que están siempre listos para usarse con o sin dispositivos.

---

## 3. Plan de Implementación Detallado

### Tarea 1: Switch Maestro de Servicios en `view_dashboard.xml` y `ConnectActivity.kt`
- En `view_dashboard.xml`:
  - Agregar un contenedor en la tarjeta de estado con `swMasterService` ("Servicios en segundo plano"):
    - Título: "Servicios y Reconexión"
    - Subtítulo: "Desactiva reconexiones y procesos en segundo plano para ahorrar batería."
    - Interruptor `MaterialSwitch` con `id = swMasterService`.
- En `ConnectActivity.kt`:
  - Enlazar `swMasterService`:
    - Refleja `Prefs.autoReconnectEnabled(this)`.
    - Al apagar (toggle OFF): invoca `TotalDisconnectHelper.performTotalDisconnect(this)` para apagar servicio, watchdogs, reconexiones y actualizar UI a estado reposo.
    - Al encender (toggle ON): activa `Prefs.setAutoReconnectEnabled(this, true)`, inicia `MyvuService` con `ACTION_START` y programa watchdog.
  - Actualizar visualmente la tarjeta de estado según el switch maestro.

### Tarea 2: Control de Servicios y Energía en `SettingsActivity.kt` / `activity_settings.xml`
- En `activity_settings.xml`:
  - Agregar sección / card "Servicios en Segundo Plano y Energía":
    - Switch `switchMasterServices`: Permite habilitar/inhabilitar el servicio en segundo plano y la reconexión automática globalmente.
- En `SettingsActivity.kt`:
  - Enlazar los switches con `Prefs.setAutoReconnectEnabled()` y `TotalDisconnectHelper`.

### Tarea 3: Garantía y Refuerzo de IA Standalone
- En `view_dashboard.xml`:
  - Asegurar que la sección de IA (Notas Recientes, Próximos Recordatorios, Botón de Dictado / Asistente de Voz, Botón de Ajustes IA) mantenga visualización clara ("Siempre disponible en tu teléfono").
- En `ConnectActivity.kt`:
  - Agregar botón de acceso directo a Chat IA en el Dashboard junto al botón de notas/dictado (`btnOpenAiChat`), garantizando acceso directo en 1 toque sin periféricos.
- En `DailyBriefingService.kt`:
  - Asegurar que `generateBriefingText()` maneje cualquier fallback limpiamente sin dispositivos.

### Tarea 4: Pruebas Automatizadas
- Crear test `StandaloneAiAndServiceToggleTest.kt`:
  - Test 1: El toggle maestro de servicios apaga y enciende correctamente `Prefs.autoReconnectEnabled()` y sincroniza con `TotalDisconnectHelper`.
  - Test 2: `DailyBriefingService.generateBriefingText()` funciona 100% de forma autónoma sin dispositivos conectados.
  - Test 3: Las entidades y repositorios de IA (Notas, Recordatorios, Chat) operan de manera autónoma sin requerir `MyvuService` ni periféricos.

### Tarea 5: Verificación y Documentación
- Compilación con `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.
- `codegraph sync`.
- Actualización de `docs/PROJECT_MEMORY.md`, `docs/ARCHITECTURE.md` y `README.md`.
