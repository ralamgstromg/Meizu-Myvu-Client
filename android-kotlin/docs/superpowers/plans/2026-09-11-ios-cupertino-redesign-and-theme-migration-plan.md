# Plan de Migración: Rediseño Integral UI Estilo iOS Cupertino Pro Native, Modo Claro/Oscuro y Nuevo Icono de Identidad

## 1. Contexto y Objetivos
- **Rediseño Completo de la UI**: Migrar la interfaz Android hacia el diseño "Cupertino Pro Native" basado en las especificaciones y prototipos de `@design/stitch_smartwear_ai_hub` (Apple Human Interface Guidelines, squircles de 16dp, divisores de 0.5px, tipografía Manrope/SF Pro, paleta semántica iOS, barra de pestañas translúcida y barra de acciones de mensaje estilo iMessage).
- **Modo Claro / Oscuro Dinámico**: Implementar soporte semántico dual completo con `res/values/colors.xml` y `res/values-night/colors.xml`, permitiendo conmutar dinámicamente entre Sistema, Claro y Oscuro con persistencia en `Prefs`.
- **Nuevo Icono de Aplicación**: Reemplazar el icono obsoleto por una nueva identidad gráfica vectorial de Squircle continuo iOS (`ic_launcher_hub.xml`), representando el agente inteligente y wearable companion hub universal.
- **Coherencia en Todas las Pantallas**: Aplicar el sistema de diseño iOS a las pantallas principales:
  1. `ChatActivity` (Companion Hub principal: widgets de wearables conectados, stream iMessage, quick action pills, input bar iOS, bottom navigation bar de 5 pestañas).
  2. `ConnectActivity` / `view_dashboard.xml` (Centro de dispositivos con inset grouped cards, switches iOS, medidores de batería y estado).
  3. `NotesActivity` (Tarjetas agrupadas, segmented controls y buscador estilo iOS).
  4. `VoiceRecorderActivity` (Controles de grabación de audio con diales y estética Apple).
  5. `SettingsActivity` y sub-pantallas de ajustes.
  6. `DeviceManagementBottomSheet` (Control modal y emparejamiento de wearables).

---

## 2. Fases de Ejecución

### Fase 1: Sistema de Color Semántico y Motor de Temas Dual (Light / Dark)
1. **Definición de Colores Semánticos en `res/values/colors.xml` (Modo Claro)**:
   - `ios_system_bg`: `#F2F2F7` (Gris suave característico iOS)
   - `ios_card_bg` / `ios_surface`: `#FFFFFF`
   - `ios_card_secondary`: `#F8F8FA`
   - `ios_label`: `#1C1C1E`
   - `ios_secondary_label`: `#8E8E93`
   - `ios_tertiary_label`: `#C7C7CC`
   - `ios_separator`: `#E5E5EA`
   - `ios_border`: `#1F000000` (12% black)
   - `ios_blue`: `#007AFF`
   - `ios_indigo`: `#5856D6`
   - `ios_green`: `#34C759`
   - `ios_orange`: `#FF9500`
   - `ios_red`: `#FF3B30`
   - `ios_bubble_ai`: `#E9E9EB`
   - `ios_bubble_user`: `#007AFF`
   - `ios_nav_bg`: `#D9FFFFFF` (85% blanco translúcido)
   - Alias de retrocompatibilidad mapeados a los tokens semánticos claros para no romper componentes existentes.

2. **Definición de Sobrescrituras en `res/values-night/colors.xml` (Modo Oscuro)**:
   - `ios_system_bg`: `#000000`
   - `ios_card_bg` / `ios_surface`: `#1C1C1E`
   - `ios_card_secondary`: `#2C2C2E`
   - `ios_label`: `#FFFFFF`
   - `ios_secondary_label`: `#8E8E93`
   - `ios_tertiary_label`: `#48484A`
   - `ios_separator`: `#38383A`
   - `ios_border`: `#2EFFFFFF` (18% white)
   - `ios_blue`: `#0A84FF`
   - `ios_indigo`: `#5E5CE6`
   - `ios_green`: `#30D158`
   - `ios_orange`: `#FF9F0A`
   - `ios_red`: `#FF453A`
   - `ios_bubble_ai`: `#26252A`
   - `ios_bubble_user`: `#0A84FF`
   - `ios_nav_bg`: `#D9161618` (85% oscuro translúcido)

3. **Ajuste de Estilos en `themes.xml` y `values-night/themes.xml`**:
   - `Theme.Myvu`: Herencia de `Theme.Material3.DayNight.NoActionBar`.
   - `android:colorBackground` -> `@color/ios_system_bg`.
   - `colorSurface` -> `@color/ios_card_bg`.
   - `colorOnSurface` -> `@color/ios_label`.
   - `colorOutline` -> `@color/ios_separator`.
   - `android:windowLightStatusBar` ajustado dinámicamente según modo.
   - Definición de estilos `Card.Ios`, `Switch.Ios`, `Button.Ios.Primary`, `Button.Ios.Secondary`.

4. **Gestión en `Prefs.kt` y `MyApp.kt`**:
   - Funciones `getThemeMode(context): String`, `setThemeMode(context, mode: String)`, `applyThemeMode(mode: String)` (`MODE_NIGHT_FOLLOW_SYSTEM`, `MODE_NIGHT_NO`, `MODE_NIGHT_YES`).
   - Inicialización en `MyApp.onCreate()`.

---

### Fase 2: Identidad Gráfica y Drawables iOS (Squircles, Dividers, Iconos)
1. **Nuevo Icono Vectorial / Adaptativo (`ic_launcher_hub`)**:
   - Diseño continuo squircle iOS (`rounded-2xl`).
   - Gradiente de fondo Cupertino Azul Eléctrico (`#007AFF`) a Índigo (`#5856D6`).
   - Iconografía central: Ondas neuronales / anillo holográfico AR con punto central de asistencia inteligente y nodo de wearable.
   - Definir `ic_launcher_hub.xml`, `ic_launcher_hub_foreground.xml`, `ic_launcher_hub_background.xml`.
   - Actualizar `AndroidManifest.xml` (`android:icon="@drawable/ic_launcher_hub"`).

2. **Drawables Base iOS**:
   - `bg_ios_card.xml`: Fondo `@color/ios_card_bg`, radio 16dp, borde 0.5dp `@color/ios_border`.
   - `bg_ios_card_secondary.xml`: Fondo `@color/ios_card_secondary`, radio 12dp.
   - `bg_ios_pill.xml`: Radio 999dp para botones de acción rápida, filtros y badges.
   - `bg_ios_bubble_user.xml`: Burbuja de chat usuario redondeada (radio 20dp, esquina inferior derecha 4dp) con color `@color/ios_bubble_user`.
   - `bg_ios_bubble_ai.xml`: Burbuja de chat IA redondeada (radio 20dp, esquina inferior izquierda 4dp) con color `@color/ios_bubble_ai`.
   - `bg_ios_input_bar.xml`: Barra de entrada con radio completo y trazo sutil.
   - `bg_ios_action_cell.xml`: Celda inset con separador hairline de 0.5dp.
   - `bg_ios_switch_track.xml` y `bg_ios_segmented_control.xml`.

---

### Fase 3: Rediseño de Pantallas Principales Siguiendo `@design`

1. **`activity_chat.xml` & `ChatActivity.kt` (Companion Hub Principal)**:
   - Header iOS con logo, título "Companion Hub", badge de sincronización ("Sincronizado" verde) y botón de cambio rápido de tema Claro/Oscuro.
   - **Carrusel Horizontal de Dispositivos Conectados (iOS Widgets)**:
     - Card 1: Gafas AR (icono gafas, estado de conexión, batería %, indicador "HUD Activo / MicroOLED").
     - Card 2: Auriculares Pro (icono auriculares, batería %, modo ANC / Spatial LDAC).
     - Card 3: Botón "+ Emparejar" dispositivo wearable.
   - **Stream de Conversación Estilo iMessage**:
     - Burbujas de chat con padding ergonómico y esquinas diferenciadas.
     - Bloques de acción de hardware ejecutados (tarjetas inset con iconos de hardware y checks verdes).
   - **Pills de Accesos Rápidos**:
     - "Teleprompter", "Traducir", "Grabar Nota", "Resumir día".
   - **Barra de Entrada iOS**:
     - Botón `+`, input redondeado, botón de micrófono wearable y botón circular de envío azul `#007AFF`.
   - **Cupertino Tab Bar Inferior (5 pestañas fijas)**:
     - 1. Hub (Activo)
     - 2. Dispositivos (`ConnectActivity` o BottomSheet)
     - 3. AR Studio / HUD
     - 4. Traducir
     - 5. Notas (`NotesActivity`)

2. **`activity_connect.xml` & `view_dashboard.xml` (Centro de Dispositivos)**:
   - Fondo `@color/ios_system_bg`.
   - Inset grouped cards (`bg_ios_card.xml`) con esquinas continuas de 16dp.
   - Lista de dispositivos con iconos limpios, indicador de batería, switch iOS de conexión y chevron accesorio.
   - Botón prominent pill para escanear y agregar nuevo dispositivo.

3. **`activity_notes.xml` y `activity_voice_recorder.xml`**:
   - Barra de búsqueda iOS translúcida.
   - Tarjetas agrupadas con tags temáticos y metadatos de IA.
   - Segmented control superior estilo iOS (Todas, Notas, Reuniones).

4. **`activity_settings.xml` y `activity_headphone_settings.xml`**:
   - Inset grouped table style: grupos de celdas con bordes redondeados y separadores hairline.
   - Selector de modo de tema (Claro / Oscuro / Sistema).

---

### Fase 4: Verificación, Compilación y Actualización de Documentación
1. Compilar y verificar con `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.
2. Sincronizar grafo de código con `codegraph sync`.
3. Actualizar memoria del proyecto en `docs/PROJECT_MEMORY.md`.
4. Actualizar arquitectura y README en `docs/ARCHITECTURE.md` y `README.md`.
