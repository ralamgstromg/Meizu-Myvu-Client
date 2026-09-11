# Plan de Ajuste: Detección de Gestos en Patilla Táctil y Activación de Micrófono en Gemini

## 1. Diagnóstico del Log (`/home/rcastro/Descargas/myvu_client_log.txt`)

### 1.1 Detección de Gestos en Patilla Táctil
1. **Comportamiento en el Log**:
   - Se observan recepciones de toques físicos tanto de la patilla derecha (`key_code: 210`, `key_event_sender: 1`) como de la patilla izquierda (`key_code: 200`, `key_event_sender: 2`), además de deslizamientos (`key_code: 206`, `207`).
   - Sin embargo, **todas** las acciones resultaron en:
     `Touchpad gesture received (TAP, code=210) -> Action: none (NONE)`
     `Touchpad gesture received (SWIPE_FORWARD, code=206) -> Action: none (NONE)`
     `Touchpad gesture received (SWIPE_BACKWARD, code=207) -> Action: none (NONE)`
2. **Causa Raíz de Gestos No Reconocidos o que Requieren Múltiples Intentos**:
   - **Preferencias en "none" para Toque Simple**: En `Prefs.kt`, `touchpadTapAction` tiene por defecto `"none"`. Un toque intencional no produce respuesta visual ni sonora en la app, haciendo que el usuario sienta que no fue detectado.
   - **Falta de Síntesis de Doble y Triple Toque entre Paquetes Separados**:
     - En el log, los eventos táctiles consecutivos llegaron en paquetes Bluetooth distintos (`msgId=6140` y `msgId=6142`).
     - `InboundRouter.kt` únicamente intentaba sintetizar doble toque si venían en el *mismo* lote de un solo paquete.
     - `TouchGestureManager.kt` dependía de `System.currentTimeMillis()` en el teléfono con un límite rígido de 800ms (`DOUBLE_TAP_MAX_INTERVAL_MS`). Si las gafas estaban suspendidas o había latencia Bluetooth, la diferencia de llegada al teléfono superaba los 800ms (en el log llegó a 2.927ms a pesar de haberse producido con 1.0s de diferencia en las gafas).
     - Peor aún: `TouchGestureManager` no tenía ninguna lógica para sintetizar `TRIPLE_TAP` a partir de toques que llegasen en paquetes separados.
   - **Ignorancia del Timestamp de Hardware de las Gafas**: Las tramas de telemetría incluyen `_event_time_` o `key_event_time` en milisegundos reales de las gafas, pero `InboundRouter` no pasaba este timestamp al listener ni a `TouchGestureManager`.
   - **Suspensión Profunda de las Gafas (`screen_off_time: 5`)**: Las gafas se apagan a los 5 segundos y entran en `suspend_stats: type 1` a los pocos segundos. El primer toque con las gafas suspendidas despierta el hardware y sufre latencia de envío por Bluetooth.

### 1.2 Activación del Micrófono en Gemini
1. **Lanzamiento de Activity Inactiva**:
   - `TouchGestureManager.launchGeminiAssistant` abría la actividad principal de `com.google.android.apps.bard` (`getLaunchIntentForPackage`), la cual inicia la pantalla de chat en silencio sin activar el micrófono.
   - Inmediatamente antes se despachaba `KEYCODE_VOICE_ASSIST`, pero al superponer de inmediato la Activity de Gemini, el asistente del sistema era reemplazado por la ventana pasiva de la app.
2. **Falla de Click en Jetpack Compose en `AutoSendAccessibilityService`**:
   - La UI moderna de Gemini está escrita en Jetpack Compose.
   - `findAndClickGeminiMicButton` solo intentaba `node.performAction(ACTION_CLICK)`, que en muchos componentes de Compose devuelve `false` porque la interacción táctil no se expone como un AccessibilityAction estándar.
   - A diferencia de `findAndClickSendButton` (que cuenta con un fallback de toque por coordenadas `dispatchGesture`), `findAndClickGeminiMicButton` no implementaba toque por coordenadas sobre el centro del botón.
   - Selectores de descripción y texto incompletos para variantes en español (por ejemplo, `"usar el micrófono"`, `"habla para enviar una instrucción"`, `"escribe, habla o comparte"`, `"búsqueda por voz"`).
   - Si `rootInActiveWindow` era `null` durante la transición de apertura, el servicio se detenía de inmediato en lugar de inspeccionar `service.windows` o `event.source`.

---

## 2. Plan de Implementación Propuesto

### Fase 1: Optimización de Detección de Gestos Táctiles (Patilla)
1. **Propagar Timestamp de Hardware de las Gafas**:
   - En `InboundRouter.kt`, actualizar `TouchGestureListener` para recibir `eventTime: Long`.
   - Pasar `eventTime` a través de `ConnectionManager.kt` hacia `TouchGestureManager.kt`.
2. **Máquina de Estados y Acumulador Multi-Toque en `TouchGestureManager.kt`**:
   - Usar `eventTime` de las gafas cuando esté disponible para calcular la diferencia real del toque físico, inmune a latencias de buffer Bluetooth o desuspensión.
   - Aumentar la ventana de detección de doble toque (`DOUBLE_TAP_MAX_INTERVAL_MS`) a 1100ms para acomodar toques pausados y naturales en la montura de las gafas.
   - Implementar soporte completo para **Triple Toque entre paquetes separados**: acumular hasta 3 toques dentro de una ventana de 1250ms antes de resolver la acción definitiva.
   - Evitar falsos disparos de Toque Simple si el usuario está realizando un Doble Toque: si Doble Toque o Triple Toque están configurados con una acción, diferir la ejecución del toque simple brevemente (o ejecutar directamente la acción correspondiente al acumular el segundo toque).
3. **Manejo de Deslizamientos y Sensores (Patilla Izquierda y Derecha)**:
   - Preservar el filtrado de micro-deslizamientos accidentales cuando el dedo aterriza en el sensor al tocar.
   - Asegurar que los gestos de la patilla izquierda (`sender: 2`) y derecha (`sender: 1`) sean interpretados de manera consistente.
4. **Revisión de Parámetros de Pantalla y Suspensión**:
   - Ajustar el tiempo de pantalla activo (`screen_off_time`) por defecto a 15 segundos en `Prefs.kt` para evitar que las gafas entren en suspensión profunda continuamente tras 5 segundos.

### Fase 2: Activación Automática y Confiable del Micrófono en Gemini
1. **Lanzamiento Optimizado de Asistente de Voz**:
   - En `TouchGestureManager.launchGeminiAssistant`:
     - Utilizar en primer lugar `Intent(Intent.ACTION_VOICE_COMMAND)` con flags adecuadas para abrir directamente la interfaz de escucha activa de Gemini/Google Assistant.
     - En caso de abrir la aplicación completa (`com.google.android.apps.bard`), configurar `AutoSendAccessibilityService` con una ventana de auto-activación de micrófono reforzada y ráfagas de reintento.
2. **Refuerzo de `AutoSendAccessibilityService.findAndClickGeminiMicButton`**:
   - Añadir **fallback por coordenadas con `dispatchGesture`**: si `performAction(ACTION_CLICK)` no surte efecto (caso Compose), obtener los límites del nodo en pantalla (`getBoundsInScreen`) y simular el toque en el centro exacto del botón de micrófono.
   - Añadir inspección de `service.windows` y `event.source` si `rootInActiveWindow` es `null` durante el arranque de la app.
   - Ampliar los selectores y descriptores en español e inglés:
     - Español: `"usar el micrófono"`, `"micrófono"`, `"abrir micrófono"`, `"habla para enviar"`, `"escribe, habla o comparte"`, `"dictado"`, `"búsqueda por voz"`, `"grabar audio"`, `"entrada de voz"`.
     - Inglés: `"use microphone"`, `"microphone"`, `"use mic"`, `"voice search"`, `"voice input"`, `"tap to speak"`, `"type, talk or share"`.
     - IDs: `"text_input_voice_icon"`, `"voice_input_button"`, `"mic_button"`, `"action_mic"`, `"voice_search_button"`, `"sparkle_mic"`.

### Fase 3: Pruebas y Validación
1. Actualizar y agregar pruebas unitarias en `InboundGestureTest.kt` y `TouchGestureManagerTest.kt`:
   - Detección de doble toque entre paquetes con timestamps de hardware distantes en llegada de red.
   - Detección de triple toque entre paquetes separados.
   - Verificación de que el enrutamiento y listeners pasen los timestamps correctamente.
2. Ejecutar suite de pruebas con `./gradlew testDebugUnitTest`.

### Fase 4: Documentación y Memoria
1. Ejecutar `rtk codegraph sync`.
2. Actualizar `docs/PROJECT_MEMORY.md` con el análisis del log y las correcciones realizadas.
3. Actualizar `README.md` y `docs/ARCHITECTURE.md`.
