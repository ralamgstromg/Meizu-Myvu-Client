# Plan de Implementación: Interfaces Dedicadas de Configuración por Dispositivo y Catálogo Unificado de Acciones

## 1. Contexto y Objetivos
- **Desacoplamiento de Configuraciones**:
  - Mantener las configuraciones generales (Modelos de IA, STT, TTS, Clima, Backup Google Drive, Perfil de Usuario, Auto-bloqueo) en `SettingsActivity`.
  - Crear interfaces dedicadas de configuración para cada tipo de dispositivo según sus capacidades específicas de hardware e interacción:
    1. **Gafas AR (`GlassesSettingsActivity`)**:
       - Personalización de los gestos táctiles de las patillas ("patas"): Toque simple, Doble toque, Triple toque, Deslizar adelante, Deslizar atrás, Pulsación larga.
       - Configuración / estado del botón físico de acción de la montura.
       - Ajustes de HUD (brillo, teleprompter, etc.).
    2. **Auriculares Bluetooth (`HeadphoneSettingsActivity`)**:
       - Personalización de gestos de botón/táctil: 1 toque, 2 toques, 3 toques, pulsación larga.
       - Ajustes de audio y voz: TTS en audífonos, lectura automática de notificaciones, prueba de voz.
    3. **Wearables Genéricos (`GenericDeviceSettingsActivity`)**:
       - Mapeo de botón / interacción básica, alertas y lectura de notificaciones.
- **Catálogo Unificado de Acciones Comunes (`CommonDeviceActions`)**:
  - Las acciones a realizar son comunes a todos los dispositivos (Gemini, Gemini Live, Asistente del Teléfono, Abrir Apps, Crear Nota con IA, Resumen del día, Notificaciones en voz alta, Controles multimedia, Clima, Teleprompter, Modo Zen, Ninguna).
  - Unificar este catálogo en una fuente de verdad compartida (`CommonDeviceActions.kt`) utilizada por todas las pantallas.
- **Ruteo Inteligente desde Companion Hub y Panel de Dispositivos**:
  - En `ChatActivity`: click en widget de Gafas abre `GlassesSettingsActivity`; click en widget de Auriculares abre `HeadphoneSettingsActivity`.
  - En `DeviceManagementBottomSheet`: el botón de configuración de cada tarjeta rutea a la actividad correspondiente según el tipo del dispositivo.
  - En `SettingsActivity`: sección de acceso directo a las configuraciones de dispositivos vinculados.

---

## 2. Fases de Ejecución

### Fase 1: Catálogo Unificado de Acciones y Modelo de Datos
1. **`CommonDeviceActions.kt`**:
   - Lista unificada de pares `(displayName, actionId)`.
   - Métodos helper para resolver nombres amigables, índices en spinners y descripciones.
   - Acciones soportadas: `MEDIA_PLAY_PAUSE`, `LAUNCH_GEMINI`, `LAUNCH_GEMINI_LIVE`, `LAUNCH_PHONE_ASSISTANT`, `CREATE_AI_NOTE`, `DAILY_BRIEFING`, `READ_UNREAD_NOTIFICATIONS`, `MEDIA_NEXT`, `MEDIA_PREV`, `WEATHER_SYNC`, `OPEN_TELEPROMPTER`, `LAUNCH_APP`, `ZEN_MODE`, `NONE`.
2. **Ampliación de `BluetoothDeviceEntity.kt` y `AppDatabase.kt`**:
   - Agregar campos en `BluetoothDeviceEntity`:
     - `swipeForwardAction: String` (por defecto `MEDIA_NEXT`)
     - `swipeBackwardAction: String` (por defecto `MEDIA_PREV`)
     - `actionButtonAction: String` (por defecto `VOICE_AI_FIXED`)
     - `hudBrightness: Int` (por defecto 80)
   - Incrementar versión de `AppDatabase` a 3 (con migración destructiva fallback segura).

### Fase 2: Interfaz Propia de Configuración para Gafas AR (`GlassesSettingsActivity`)
1. **Layout `activity_glasses_settings.xml` (Diseño iOS Cupertino)**:
   - Inset grouped cards (`bg_ios_card.xml`):
     - Tarjeta de Dispositivo: Nombre, MAC, estado de enlace y batería.
     - Tarjeta de Gestos de Patillas ("Patas"): Toque simple, Doble toque, Triple toque, Deslizar adelante, Deslizar atrás, Pulsación larga.
     - Tarjeta de Botón de Acción Físico: Modo de activación de IA de hardware.
     - Tarjeta de HUD: Ajustes de teleprompter y brillo.
2. **Controlador `GlassesSettingsActivity.kt`**:
   - Carga y enlace reactivo desde `BluetoothDeviceDao` y `Prefs`.
   - Spinners configurados con `CommonDeviceActions`.
   - Guardado sincronizado en Room y `Prefs` para reflejo inmediato en `TouchGestureManager`.

### Fase 3: Modernización de Auriculares y Wearables Genéricos
1. **Actualizar `HeadphoneSettingsActivity.kt` y `activity_headphone_settings.xml`**:
   - Usar `CommonDeviceActions` unificado.
   - Ajustar diseño a los tokens iOS Cupertino Pro Native (`bg_ios_card.xml`, `ios_system_bg`, etc.).
2. **Crear `GenericDeviceSettingsActivity.kt` y `activity_generic_device_settings.xml`**:
   - Interfaz dedicada para otros wearables Bluetooth (relojes, pulseras, etc.).

### Fase 4: Enrutamiento en Hub, Bottom Sheet y Ajustes Generales
1. **Actualizar `DeviceManagementBottomSheet.kt`**:
   - Ruteo por tipo de dispositivo al pulsar ajustes (`SMART_GLASSES` -> `GlassesSettingsActivity`, `HEADPHONES` -> `HeadphoneSettingsActivity`, `GENERIC` -> `GenericDeviceSettingsActivity`).
2. **Actualizar `ChatActivity.kt`**:
   - El widget de Gafas AR en el carrusel abre `GlassesSettingsActivity`.
   - El widget de Auriculares abre `HeadphoneSettingsActivity`.
3. **Actualizar `SettingsActivity.kt`**:
   - Sección "Gestión por Dispositivo" con acceso a las pantallas específicas.
4. **Registrar nuevas actividades en `AndroidManifest.xml`**.

### Fase 5: Pruebas, Compilación y Documentación
1. Compilación y ejecución de pruebas unitarias (`./gradlew testDebugUnitTest`).
2. Compilación de APK (`./gradlew assembleDebug`).
3. Sincronización de grafo (`codegraph sync`).
4. Actualización de `docs/PROJECT_MEMORY.md`, `docs/ARCHITECTURE.md` y `README.md`.
