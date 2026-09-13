# Plan de Implementación: Corrección de Insets Edge-to-Edge y Ajuste Cupertino en Notas y Logs

**Fecha**: 12 de Septiembre de 2026  
**Autor**: Kog (Caveman Agent)  
**Estado**: Pendiente de Aprobación por Usuario  

---

## 1. Contexto y Diagnóstico del Problema

El usuario reporta que las siguientes pantallas aparecen visualmente corridas o desfasadas en la parte superior o inferior:
1. **Notas de IA y Tareas (`NotesActivity` / `activity_notes.xml`)**
2. **Logs y Actividad (`ActivityLogActivity` / `activity_log.xml`)**

### Diagnóstico Técnico de Raíz:

1. **`ActivityLogActivity`**:
   - **Ausencia total de `EdgeToEdgeHelper`**: La actividad nunca configuraba `EdgeToEdgeHelper.setupEdgeToEdge()`.
   - **Colisión Superior (Notch / Barra de Estado)**: En Android 14/15 (API 34/35), la ventana se dibuja debajo de la barra de estado transparente. La barra superior (`LinearLayout` de 56dp) no recibía insets de ventana, haciendo que el título "Registro de Actividad", el subtítulo y los botones de volver, compartir y limpiar queden directamente montados sobre el reloj del sistema, iconos de señal y recorte de cámara frontal (notch).
   - **Solapamiento Inferior (Barra de Navegación / Píldora de Gestos)**: El `rvActivityLogs` no recibía insets de navegación inferior, provocando que los últimos logs queden tapados por la píldora de gestos del sistema.

2. **`NotesActivity`**:
   - **Toolbar aplastada por altura rígida**: `toolbarNotes` tenía `layout_height="56dp"` estático. Al aplicarse `EdgeToEdgeHelper.setupEdgeToEdge(this, toolbar)`, el helper sumaba padding superior dinámico (`+ ~36-48dp`), reduciendo el espacio útil interno a ~8-16dp. Esto causaba que el título, botón de navegación, menú y batería quedaran cortados, aplastados o desplazados verticalmente.
   - **Cluster FAB solapado con barra de navegación**: El contenedor flotante de botones (`fabMenu` + `fabMain`) tenía `layout_margin="16dp"` fijo en la esquina inferior derecha y **no** fue registrado como `bottomBar` en `setupEdgeToEdge`. En dispositivos con navegación por gestos o 3 botones, el FAB quedaba montado encima de la barra de navegación del sistema.
   - **Listas con padding inferior ciego**: `rvNotes` y `rvReminders` tenían `paddingBottom="80dp"` estático sin compensar la altura de navegación del sistema, cortando o impidiendo ver el último recordatorio o nota al scrollear.

---

## 2. Solución Propuesta (Diseño Apple HIG / Cupertino)

Siguiendo las directrices de diseño de Apple (iOS 18 Human Interface Guidelines / Cupertino):
- **Safe Area Insets Fluidos**: Tanto la barra superior como las listas y controles flotantes deben respetar milimétricamente las áreas seguras (`WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()`).
- **Barra de Navegación Cupertino Dinámica**:
  - `layout_height="wrap_content"`, `minHeight="52dp"` o `56dp`.
  - El padding superior se ajusta fluidamente con la altura del notch y status bar sin deformar los iconos, títulos ni botones de acción.
- **Floating Action Cluster con Safe Area Margin**:
  - El cluster flotante del FAB se registra en el `EdgeToEdgeHelper` para que su margen inferior se adapte dinámicamente: `16dp + insets.bottom`, asegurando que siempre flote elegantemente por encima de la píldora de gestos.
- **Scroll Edge-to-Edge con Insets Bottom**:
  - `rvActivityLogs`, `rvNotes` y `rvReminders` deben mantener `clipToPadding="false"` y recibir la compensación de insets inferior dinámica para permitir un scroll limpio y completo.

---

## 3. Fases de Implementación

### Fase 1: Corrección de `activity_log.xml` y `ActivityLogActivity.kt`
1. **En `activity_log.xml`**:
   - Asignar `android:id="@+id/topBarLog"` al contenedor superior de navegación.
   - Cambiar su altura de `android:layout_height="56dp"` a `android:layout_height="wrap_content"` con `android:minHeight="52dp"`.
   - Garantizar que los botones `btnBack`, `btnShareLogs` y `btnClearLogs` mantengan centrado vertical limpio.
2. **En `ActivityLogActivity.kt`**:
   - En `onCreate()`, invocar `EdgeToEdgeHelper.setupEdgeToEdge(this, topBar = findViewById(R.id.topBarLog), scrollContent = rvLogs)`.
   - Ajustar el padding inferior de `layoutEmptyLogs` si es necesario para que el estado vacío tampoco colisione con la navegación.

### Fase 2: Corrección de `activity_notes.xml` y `NotesActivity.kt`
1. **En `activity_notes.xml`**:
   - En `toolbarNotes`: cambiar `android:layout_height="56dp"` a `android:layout_height="wrap_content"` con `android:minHeight="56dp"`.
   - Asignar `android:id="@+id/fabCluster"` al `LinearLayout` contenedor de `fabMenu` y `fabMain`.
   - Verificar que `rvNotes` y `rvReminders` cuenten con `clipToPadding="false"`.
2. **En `NotesActivity.kt`**:
   - Actualizar la llamada de `EdgeToEdgeHelper`:
     ```kotlin
     val toolbar = findViewById<MaterialToolbar>(R.id.toolbarNotes)
     val fabCluster = findViewById<View>(R.id.fabCluster)
     val rvNotes = findViewById<RecyclerView>(R.id.rvNotes)
     EdgeToEdgeHelper.setupEdgeToEdge(
         activity = this,
         topBar = toolbar,
         bottomBar = fabCluster,
         scrollContent = rvNotes
     )
     ```
   - Aplicar el listener de insets también a `rvReminders` para que cuando el usuario cambie a la pestaña de "Recordatorios", el scroll inferior no quede truncado tras la barra de gestos.

### Fase 3: Verificación y Pruebas
1. Ejecutar compilación completa y pruebas unitarias con `rtk proxy ./gradlew testDebugUnitTest assembleDebug`.
2. Verificar que los 308+ tests pasen al 100% y que no existan advertencias de layout o compilación.

### Fase 4: Documentación y Memoria
1. Ejecutar `codegraph sync`.
2. Actualizar `README.md`, `docs/ARCHITECTURE.md` y `docs/PROJECT_MEMORY.md` detallando las correcciones de insets edge-to-edge y diseño Cupertino en ambas pantallas.

---

## 4. Archivos Afectados

- `app/src/main/res/layout/activity_log.xml`
- `app/src/main/java/com/myvu/client/ui/ActivityLogActivity.kt`
- `app/src/main/res/layout/activity_notes.xml`
- `app/src/main/java/com/myvu/client/ui/NotesActivity.kt`
- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/PROJECT_MEMORY.md`
