# Plan de Implementación: Alineación de UI, Diseño Apple (iOS HIG) y Diagnóstico Visual de Logs

**Fecha**: 12 de Septiembre de 2026  
**Autor**: Kog (Caveman Agent)  
**Estado**: Pendiente de Aprobación por Usuario

---

## 1. Contexto y Objetivos

El usuario ha solicitado:
1. **Validar y ajustar todas las interfaces (UI)** de la aplicación que presenten deformaciones, desbordamientos, cortes de texto o desalineaciones visuales.
2. **Mejorar la interfaz de logs (`ActivityLogActivity`)** para que visualmente sea intuitivo e inmediato identificar errores, advertencias y problemas específicos de cada dispositivo o del sistema.
3. **Implementar de forma rigurosa y uniforme el patrón de diseño de interfaces de Apple (iOS Human Interface Guidelines)** en todas las pantallas.

---

## 2. Diagnóstico de Problemas Detectados en UI

### 2.1. Interfaz de Logs (`activity_log.xml` y `item_activity_log.xml`)
- **Inconsistencia de Tema (Hardcoding Oscuro)**: Uso de colores fijos como `@color/obsidian_bg`, `@color/obsidian_border` y `@color/cyber_teal`. En modo claro de Android, la pantalla permanece negra y con contrastes rotos.
- **Detección Pobre de Errores**: Los registros de error (`ERROR` / `WARN`) solo muestran una pequeña etiqueta de texto. No hay llamada visual de atención (sin borde acentuado, sin fondo tintado suave, sin icono distintivo de fallo).
- **Falta de Métricas y Filtro Rápido de Salud**: No existe una barra de resumen superior que indique rápidamente cuántos errores hay (`[Total: 142] [⚠️ Errores: 5] [⚡ Warn: 2] [👓 Gafas: 80] [🎧 Audio: 62]`), ni filtrado con un toque sobre errores.
- **Iconografía Cyberpunk Incoherente**: Uso de `ic_arrow_back_cyber`, `ic_share_cyber` y `ic_delete_cyber` que rompen la estética Cupertino/SF Symbols.

### 2.2. Deformaciones y Desalineaciones en Pantallas Secundarias
- **`activity_settings.xml` y `activity_notes.xml`**:
  - Tienen `android:background="@color/obsidian_bg"` en el layout raíz, contrastando bruscamente con las actividades que usan `@color/ios_system_bg`.
  - Tarjetas con radios y paddings dispares (algunas 12dp, otras 16dp, márgenes dispares de 8dp y 16dp).
- **`activity_glasses_settings.xml` y `activity_headphone_settings.xml`**:
  - Los `<Spinner>` tienen fondo `bg_ios_card_secondary` plano sin icono de chevron/flecha desplegable a la derecha, pareciendo cajas de texto muertas.
  - Los sliders de brillo y volumen no tienen indicadores de icono (mínimo/máximo) ni etiquetas numéricas inline consistentes con iOS Control Center / Settings.
  - Faltan divisores de 0.5dp entre celdas dentro del mismo grupo de tarjetas.
- **`view_dashboard.xml` y `activity_connect.xml`**:
  - Botones con `android:insetTop` / `android:insetBottom` variables que causan recorte de texto o botones deformados en pantallas con escalado de fuente aumentado (Accessibility font size).
  - Barras de navegación superiores con alturas heterogéneas (algunas 56dp fijas, otras `wrap_content`, con y sin `fitsSystemWindows`).

---

## 3. Principios de Diseño Apple (iOS HIG) a Aplicar

1. **Inset Grouped Table Style**:
   - Fondo de pantalla: `ios_system_bg` (dinámico: gris claro suave `#F2F2F7` en día / `#000000` o `#1C1C1E` en noche).
   - Tarjetas agrupadas: `bg_ios_card` con esquinas redondeadas continuas de `16dp`, márgenes laterales de `16dp`, y fondo `ios_card` (`#FFFFFF` en día / `#1C1C1E` en noche).
   - Celdas internas separadas por líneas divisorias de `0.5dp` (`ios_separator`) con margen izquierdo de `16dp` o `56dp` (si hay icono).
2. **Jerarquía Tipográfica y de Secciones**:
   - Cabeceras de sección: Texto en mayúsculas pequeñas (`13sp`), `ios_text_secondary`, `letterSpacing="0.05"`, `paddingStart="16dp"`, `paddingBottom="6dp"`.
   - Notas al pie de sección (Footers): Texto `13sp`, `ios_text_secondary`, `paddingStart="16dp"`, `paddingTop="6dp"`.
3. **Controles Nativos Estilo Cupertino**:
   - Botones de acción principales: Fondo azul iOS (`#007AFF`), esquinas `12dp` o píldora completa (`24dp`), altura táctil mínima de `44dp` a `48dp`.
   - Spinners / Selectores: Estilo celda iOS con etiqueta a la izquierda, valor seleccionado a la derecha en color secundario y chevron `>`, o tarjeta desplegable estilizada con chevron `ic_ios_chevron_down`.
   - Sliders: Control deslizante con extremos limpios y valor porcentual inline.
4. **Paleta Semántica de Estado (Apple Palette)**:
   - Error / Falla Crítica: `ios_red` (`#FF3B30`) con fondo de tinte suave `ios_red_tint` (`#1AFF3B30`).
   - Advertencia / Precaución: `ios_orange` (`#FF9500`) con tinte `ios_orange_tint` (`#1AFF9500`).
   - Éxito / Conectado: `ios_green` (`#34C759`) con tinte `ios_green_tint` (`#1A34C759`).
   - Dispositivos Gafas: `ios_blue` (`#007AFF`).
   - Dispositivos Audio: `ios_purple` (`#AF52DE`).
   - Sistema / Core: `ios_gray` (`#8E8E93`).

---

## 4. Plan de Fases de Implementación

### Fase 1: Rediseño Integral de Activity Log con Diagnóstico Visual
1. **Actualizar Colores y Drawables Semánticos (`colors.xml`, `drawables`)**:
   - Definir `ios_red_tint` (`#1AFF3B30`), `ios_orange_tint` (`#1AFF9500`), `ios_blue_tint` (`#1A007AFF`), `ios_purple_tint` (`#1AAF52DE`).
   - Crear drawables de tarjeta de log:
     - `bg_ios_log_card.xml`: Fondo neutro con bordes redondeados `12dp` y borde fino `0.5dp`.
     - `bg_ios_log_card_error.xml`: Fondo tintado suave rojo `#12FF3B30`, borde izquierdo de acento de `4dp` en rojo `#FF3B30`, borde redondeado general `12dp`.
     - `bg_ios_log_card_warning.xml`: Fondo tintado ámbar `#12FF9500`, borde izquierdo de `4dp` en ámbar `#FF9500`.
   - Crear drawables de badges y tags de origen (Gafas, Auriculares, App).
2. **Rediseñar Cabecera de `activity_log.xml`**:
   - Barra superior Cupertino con botón volver (chevron `<`, título centrado / inline, acciones de Compartir y Borrar con iconos Apple).
   - **Barra de Métricas y Filtros Rápidos (Segmented Counters)**:
     - Fila horizontal de píldoras táctiles estilo iOS:
       - `[Todos: N]`
       - `[🚨 Errores: X]` (con badge rojo brillante si > 0)
       - `[⚠️ Avisos: Y]`
       - `[👓 Gafas: Z]`
       - `[🎧 Audio: W]`
     - Al tocar `[🚨 Errores: X]`, filtra instantáneamente la lista a registros con error.
3. **Rediseñar Elemento de Lista `item_activity_log.xml`**:
   - Indicador visual prominente: Borde izquierdo rojo para errores y ámbar para warnings.
   - Badge squircle del dispositivo emisor (`[👓 GAFAS]`, `[🎧 AUDIO]`, `[📱 APP]`) con colores semánticos.
   - Hora exacta y componente en fuente monospaced compacta (`SF Pro Text` / `Roboto Mono`).
   - Si el registro contiene stack trace o detalles largos, contenedor colapsable con indicador `"Ver detalles del error ⌄"`.
4. **Actualizar Lógica en `ActivityLogActivity.kt` y `ActivityLogAdapter.kt`**:
   - Calcular dinámicamente conteos de errores, avisos y por dispositivo para los contadores superiores.
   - Manejar el filtrado rápido desde las píldoras de resumen.
   - Manejar expansión/colapso de detalles de error.

### Fase 2: Estandarización de Pantallas de Ajustes (`GlassesSettings`, `HeadphoneSettings`, `Settings`)
1. **Formato Inset Grouped TableView**:
   - Migrar `activity_settings.xml`, `activity_glasses_settings.xml`, `activity_headphone_settings.xml` al contenedor estándar de grupos iOS.
   - Cada grupo de opciones dentro de un `<LinearLayout>` o `MaterialCardView` con `bg_ios_card`, radio `16dp`, márgenes horizontales de `16dp`.
   - Separadores `0.5dp` entre filas dentro de cada tarjeta.
2. **Mejora de Spinners y Selectores**:
   - Envolver spinners en contenedores con estilo iOS (icono representativo a la izquierda, título, valor actual a la derecha y chevron `ic_ios_chevron_down` / selector flotante limpio).
   - Eliminar cajas de texto toscas o bordes rectos desalineados.
3. **Mejora de Sliders y Switches**:
   - En `activity_glasses_settings.xml`: Añadir iconos de sol mínimo y sol máximo a los lados del Slider de brillo (`ic_brightness_low` / `ic_brightness_high`).
   - En `activity_headphone_settings.xml`: Añadir iconos de volumen y ecualizador estilizados.
   - Interruptores tipo Switch estilo iOS (`useMaterialThemeColors="false"` o tintes iOS verde `#34C759`).

### Fase 3: Corrección de Deformaciones en Dashboard, Conexión y Notas
1. **`activity_notes.xml`**:
   - Reemplazar fondo negro plano por `ios_system_bg`.
   - Ajustar campo de entrada de notas y lista de recordatorios a tarjetas agrupadas tipo Apple Notes.
2. **`activity_connect.xml` y `view_dashboard.xml`**:
   - Normalizar alturas de botones (`minHeight="44dp"`, `insetTop="0dp"`, `insetBottom="0dp"`) para evitar cortes en fuentes accesibles grandes.
   - Alinear la barra de navegación superior y la tarjeta de estado de conexión bluetooth (badges redondeados, textos sin solapamientos).

### Fase 4: Pruebas y Verificación
1. Ejecutar pruebas unitarias existentes (`./gradlew testDebugUnitTest`).
2. Probar compilación completa del APK (`./gradlew assembleDebug`).
3. Verificar que los tests de `ActivityLogActivityTest` y adapters pasen al 100%.
4. Validar contraste de accesibilidad en modo claro y modo oscuro.

### Fase 5: Memoria, Documentación y Codegraph Sync
1. Ejecutar `codegraph sync`.
2. Registrar en `docs/PROJECT_MEMORY.md` los cambios en UI, mejoras visuales en logs y componentes iOS HIG.
3. Actualizar `README.md` y `docs/ARCHITECTURE.md` detallando el nuevo sistema de diseño unificado Cupertino y diagnóstico visual de errores.

---

## 5. Criterios de Aceptación

- [ ] `ActivityLogActivity` resalta visualmente los errores con borde rojo, fondo tintado y badge de dispositivo.
- [ ] Los contadores superiores permiten filtrar errores con un solo toque.
- [ ] No existen fondos oscuros fijos en pantallas secundarias cuando la app corre en tema claro.
- [ ] Los spinners y selectores tienen indicador de chevron y formato Inset Grouped iOS.
- [ ] Las pantallas no presentan textos desbordados ni botones deformados con escalado de fuente del sistema.
- [ ] Compilación y suite de pruebas pasan limpias (`BUILD SUCCESSFUL`).
