# Plan de Implementación: Acceso Rápido a Funcionalidades de IA y Menú Lateral (Sidebar Navigation Drawer)

## 1. Contexto y Objetivos
El usuario solicita:
- Ajustar la interfaz de usuario para acceder a las funcionalidades de IA de forma inmediata (Notas de IA, Grabación de Reuniones, Resumen del Día, etc.).
- Habilitar el menú de la barra lateral (Sidebar Navigation Drawer) para acceder rápidamente a todas las funciones disponibles desde la pantalla principal de la app (`ChatActivity`).

## 2. Componentes a Modificar / Crear

### 2.1 Menú de Navegación Lateral (`menu/menu_navigation_drawer.xml` y `nav_header_drawer.xml`)
- Actualizar `menu_navigation_drawer.xml` con opciones claras y modernas:
  - 💬 `nav_chat`: Chat IA Aura (Principal)
  - 📝 `nav_notes`: Notas de IA y Tareas
  - 🎙️ `nav_voice_recorder`: Grabadora de Voz & Reuniones IA
  - 🎧 `nav_devices`: Dispositivos Bluetooth (Gafas, Auriculares)
  - 👓 `nav_dashboard`: Gafas AR (Dashboard y Conexión)
  - ⚙️ `nav_ai_config`: Ajustes de IA y Perfil
  - 🖲️ `nav_trackpad`: Control Trackpad
  - 🔔 `nav_notifications`: Filtro de Notificaciones
  - 📋 `nav_logs`: Logs y Telemetría

### 2.2 Pantalla Principal (`activity_chat.xml` y `ChatActivity.kt`)
- Transformar la raíz de `activity_chat.xml` en un `androidx.drawerlayout.widget.DrawerLayout` (`chatDrawerLayout`).
- Reemplazar el botón de regreso (`btnBackChat`) por un botón de menú lateral (`btnNavigationDrawer`) usando el icono `@drawable/ic_menu_hamburger`.
- Incorporar `com.google.android.material.navigation.NavigationView` (`chatNavigationView`) anclado a `layout_gravity="start"` con `@layout/nav_header_drawer` y `@menu/menu_navigation_drawer`.
- Agregar una **Barra de Acceso Rápido de IA** (`layAiQuickActions`) con accesos directos de 1-toque:
  - 📝 **Notas IA** -> Abre `NotesActivity`
  - 🎙️ **Reunión IA** -> Lanza `VoiceRecorderActivity` con inicio automático de grabación de reunión.
  - ☀️ **Mi Día** -> Ejecuta el Daily Briefing ejecutivo en audio/chat.
  - 🎧 **Dispositivos** -> Abre `DeviceManagementBottomSheet`.
  - 📋 **Mis Tareas** -> Abre `NotesActivity` enfocado en recordatorios/tareas.
- En `ChatActivity.kt`:
  - Vincular apertura del drawer al presionar `btnNavigationDrawer`.
  - Configurar `setNavigationItemSelectedListener` para navegar de forma fluida a cada sección.
  - Configurar listeners para cada botón/chip de la barra de acceso rápido de IA.
  - Actualizar el estado de conexión del dispositivo en el header del drawer.

### 2.3 Grabadora de Voz y Reuniones (`VoiceRecorderActivity.kt`)
- Soportar parámetros de intent `AUTO_START_RECORDING` y `CATEGORY = "MEETING"` en `onCreate` para abrir directamente la hoja de grabación en vivo con la categoría "Reunión".

### 2.4 Actualización de `ConnectActivity.kt`
- Asegurar compatibilidad bidireccional con los nuevos IDs de menú (`nav_chat`, `nav_devices`).

## 3. Pruebas y Verificación
- Ejecutar `./gradlew testDebugUnitTest` con `rtk`.
- Ejecutar `./gradlew assembleDebug` con `rtk`.
- Ejecutar `rtk codegraph sync`.
- Registrar en `docs/PROJECT_MEMORY.md`, `docs/ARCHITECTURE.md` y `README.md`.
