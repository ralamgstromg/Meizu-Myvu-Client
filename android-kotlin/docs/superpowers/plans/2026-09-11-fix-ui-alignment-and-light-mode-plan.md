# Plan de Corrección: Alineación de Interfaces, Botones/Iconos y Modo Claro

## 1. Diagnóstico del Problema (Basado en Captura de Pantalla Real)
1. **Cabecera Saturada y Cortada (`topBar`)**:
   - Altura fija de `48dp` con 6 elementos horizontales (`btnNavigationDrawer`, logo, título de dos líneas + subtítulo con dot, `btnThemeToggle`, `btnDevices` y `btnChatSettings`).
   - El texto "Companion Hub" se quiebra y el subtítulo "Sincronizado" queda cortado verticalmente a la mitad.
   - Los botones e iconos a la derecha colisionan entre sí.
2. **Botones e Iconos Corridos / Cortados en Píldoras**:
   - `MaterialButton` en `scrollAiQuickBar` y `scrollQuickSkills` (`btnAllSkills`, etc.) usan alturas fijas de 30-32dp sin `app:insetTop="0dp"`, `app:insetBottom="0dp"`, ni `android:minHeight="0dp"`.
   - Esto causa que el inset interno de 6dp de Material3 recorte el texto inferior ("⚡ Skills", "☀️ Resumir Día") y desplace los iconos.
3. **Colisión de Barra de Navegación del Sistema (Gestos de Android) contra `cupertinoTabBar`**:
   - `EdgeToEdgeHelper.setupEdgeToEdge` aplicaba `insets.bottom` a `bottomBar` (la barra de escribir mensajes) en lugar de a `cupertinoTabBar` (la barra de 5 pestañas).
   - Como consecuencia, la barra de gestos blanca del sistema Android se superpone directamente sobre las etiquetas "AR HUD", "Hub", "Equipos", "Traducir", "Notas", tapándolas y cortándolas.
4. **Fallo en Modo Claro ("no soporta el modo claro")**:
   - `AndroidManifest.xml` declaraba `android:configChanges="...|uiMode"` en todas las actividades.
   - Al cambiar el tema mediante `AppCompatDelegate.setDefaultNightMode()`, Android NO recrea la actividad, manteniendo los colores y drawables oscuros ya inflados.
   - Falta recreación explícita (`recreate()`) al pulsar el botón de alternancia de tema.
   - Insets controller de la barra de estado y barra de navegación deben garantizar iconos oscuros en modo claro e iconos claros en modo oscuro.
5. **Revisión de Interfaces Secundarias**:
   - Ajustar `activity_settings.xml`, `activity_glasses_settings.xml`, `activity_headphone_settings.xml`, `bottom_sheet_devices.xml` para garantizar consistencia total con el tema claro/oscuro y padding seguro.

---

## 2. Plan de Acción Detallado

### Fase 1: Corrección de Manifiesto y Ciclo de Vida de Tema
- En `AndroidManifest.xml`, remover `|uiMode` de `android:configChanges` en las actividades (`ChatActivity`, `SettingsActivity`, `ConnectActivity`, `GlassesSettingsActivity`, `HeadphoneSettingsActivity`, `NotesActivity`, `VoiceRecorderActivity`).
- En `ChatActivity.kt`:
  - En `btnThemeToggle.setOnClickListener`: cambiar el modo en `Prefs`, aplicar con `AppCompatDelegate.setDefaultNightMode`, actualizar el icono del botón dinámicamente y forzar `recreate()`.
  - Asegurar que `onCreate` lea el tema y configure el icono adecuado (sol en modo oscuro, luna en modo claro).

### Fase 2: Corrección de EdgeToEdge e Insets de Sistema
- En `EdgeToEdgeHelper.kt`:
  - Ajustar para que si se pasa `bottomBar` y `navTabBar`, el inset de navegación (`insets.bottom`) se aplique como padding al elemento más inferior de la pantalla (`navTabBar` / `cupertinoTabBar`), y no como margen a la barra de entrada de texto intermedia.
  - Asegurar que el scroll view o RecyclerView tenga el padding adecuado.
  - Sincronizar dinámicamente `controller.isAppearanceLightStatusBars` y `controller.isAppearanceLightNavigationBars` según el modo claro/oscuro real.

### Fase 3: Rediseño y Desahogo de la Cabecera (`topBar`)
- En `activity_chat.xml`:
  - Cambiar la fila superior a `layout_height="wrap_content"`, `minHeight="54dp"`, con padding vertical equilibrado.
  - Dar a la columna de título y estado espacio suficiente sin forzar saltos de línea antiestéticos.
  - Convertir `btnDevices` en un botón compacto y elegante o reubicar su telemetría para que `btnNavigationDrawer`, título, `btnThemeToggle`, `btnDevices` y `btnChatSettings` convivan con proporciones simétricas y sin desbordamientos.

### Fase 4: Corrección de Insets y Alineación en Botones y Píldoras
- En `activity_chat.xml`:
  - Añadir `app:insetTop="0dp"`, `app:insetBottom="0dp"`, `android:minHeight="0dp"` a todas las píldoras `MaterialButton`: `btnQuickAiNotes`, `btnQuickRecordMeeting`, `btnQuickDailyBriefing`, `btnQuickTasks`, `btnQuickDevicesShortcut`, `btnAllSkills`.
  - Ajustar altura a `34dp` o `36dp` con centrado vertical limpio (`android:gravity="center"`).
  - En los widgets del carrusel (`cardWearableGlasses`, `cardWearableHeadphones`): ajustar anchos a `200dp` y corregir espaciado para evitar que el porcentaje de batería y el estado colisionen.
  - En `cupertinoTabBar`: fijar altura a `54dp` y permitir que el padding de insets de `EdgeToEdgeHelper` eleve los iconos y etiquetas por encima de la barra de gestos de Android.

### Fase 5: Validación de Pantallas Secundarias y Pruebas
- Verificar `activity_settings.xml`, `activity_glasses_settings.xml`, `activity_headphone_settings.xml`, `bottom_sheet_devices.xml`.
- Ejecutar `rtk ./gradlew testDebugUnitTest`.
- Ejecutar `rtk ./gradlew assembleDebug`.
- Sincronizar con `rtk codegraph sync`.
- Actualizar `docs/PROJECT_MEMORY.md`, `docs/ARCHITECTURE.md`, `README.md`.
