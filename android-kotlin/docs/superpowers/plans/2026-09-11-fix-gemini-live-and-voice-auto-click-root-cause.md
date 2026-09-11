# Plan: Corrección de Raíz de la Activación Automática de Micrófono y Gemini Live

## 1. Diagnóstico de Causa Raíz

A pesar de lanzarse la app de Gemini, el micrófono y el modo Live no se activaban automáticamente debido a dos causas críticas encontradas en el código:

1. **Falta de `android:canPerformGestures="true"` en `accessibility_service_config.xml`**:
   - En Android, la API `dispatchGesture(...)` para simular toques de pantalla por coordenadas **está bloqueada por el sistema operativo** si no se declara explícitamente `android:canPerformGestures="true"` en el archivo XML de configuración del servicio de accesibilidad.
   - Como la app de Gemini está desarrollada en **Jetpack Compose**, las vistas no responden a las llamadas estándar de accesibilidad `performAction(ACTION_CLICK)`. El servicio intentaba el fallback por coordenadas (`dispatchGesture`), pero Android lo rechazaba silenciosamente al faltar la declaración de permisos en el manifiesto XML.

2. **Poda de Árbol en el Bucle BFS de Búsqueda de Botones**:
   - En `findAndClickGeminiMicButton` y `findAndClickGeminiLiveButton`:
     ```kotlin
     val viewId = node.viewIdResourceName?.lowercase() ?: ""
     if (viewId.contains("widget") || viewId.contains("searchbox") || viewId.contains("ghost_voice") || viewId.contains("search_plate")) {
         continue
     }
     ...
     for (i in 0 until node.childCount) {
         node.getChild(i)?.let { queue.add(it) }
     }
     ```
   - Al ejecutar `continue` antes de encolar los hijos (`getChild(i)`), si un contenedor padre en la app de Google/Gemini contenía `"searchbox"` o `"search_plate"` en su ID de layout, **se descartaba toda la rama completa de hijos**, impidiendo que el algoritmo llegara a ver los botones de micrófono o Live que estaban dentro de ese contenedor.

3. **Selectores de eventos y ventanas**:
   - Si `rootInActiveWindow` era el SystemUI o un decorador transitorio, `event.source` contenía la vista real de Gemini, pero solo se consultaba si `root` era nulo.

---

## 2. Acciones Propuestas

### A. En `app/src/main/res/xml/accessibility_service_config.xml`:
- Añadir `android:canPerformGestures="true"`. Esto desbloquea legalmente en Android la capacidad del servicio de accesibilidad para inyectar gestos táctiles por coordenadas.

### B. En `AutoSendAccessibilityService.kt`:
1. **Corregir el recorrido BFS en `findAndClickGeminiMicButton` y `findAndClickGeminiLiveButton`**:
   - Encolar **siempre y primero** a todos los hijos del nodo (`for (i in 0 until node.childCount) queue.add(node.getChild(i))`) antes de evaluar el nodo actual.
   - Así, aunque un contenedor padre tenga un ID no clicable (o contenga `"search"`), sus hijos sí serán explorados y evaluados.
2. **Exclusión no destructiva de widgets del Launcher**:
   - Solo descartar el nodo individual si su viewId es un widget de búsqueda (`search_widget`, `ghost_voice_search`) o su descripción es `"búsqueda por voz"` / `"voice search"`.
3. **Pulsación dual garantizada (Compose + Accesibilidad)**:
   - Ejecutar `performAction(ACTION_CLICK)` y despachar inmediatamente el toque por coordenadas físicas (`dispatchGesture`) con `GestureResultCallback` en el centro exacto del botón (`rect.centerX()`, `rect.centerY()`).
4. **Búsqueda completa de ventana**:
   - Evaluar `rootInActiveWindow`, `event.source` y todas las ventanas interactivas en `service.windows`.

---

## 3. Verificación
1. Ejecutar pruebas unitarias: `rtk ./gradlew testDebugUnitTest`.
2. Sincronizar índice: `rtk codegraph sync`.
3. Actualizar memoria y documentación (`docs/PROJECT_MEMORY.md`, `README.md`, `docs/ARCHITECTURE.md`).
