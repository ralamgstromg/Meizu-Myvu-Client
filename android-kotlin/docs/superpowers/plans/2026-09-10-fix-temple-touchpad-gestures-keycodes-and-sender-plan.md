# Plan de Trabajo: Corrección de Gestos Táctiles de las Patillas (Keycodes 206, 207, 210, 211, 212 y Senders 1, 2, 4)

## Contexto y Análisis de Causa Raíz
A partir del análisis del log `/home/rcastro/Descargas/myvu_client_log.txt`:
1. El botón físico de la montura funciona correctamente mediante el paquete `com.upuphone.ai.assistant {"code": 3}`, disparando STT -> Modelo IA configurado de forma inmutable.
2. Los toques y gestos en las patillas ("patas") de las gafas envían eventos `sync_glass_event` -> `key_event` con:
   - `key_event_sender`:
     - `1`: Sensor táctil capacitivo de patilla primaria/derecha.
     - `2`: Sensor táctil capacitivo de patilla secundaria/izquierda.
     - `4`: Controlador virtual/phonepad del launcher.
   - `key_code`:
     - `210`: Toque simple / Click (`TAP`)
     - `211`: Doble toque (`DOUBLE_TAP`)
     - `212`: Pulsación larga (`LONG_PRESS`)
     - `206`: Deslizar adelante (`SWIPE_FORWARD`)
     - `207`: Deslizar atrás (`SWIPE_BACKWARD`)
     - (Más los ya conocidos: 200, 203 -> TAP; 202 -> DOUBLE_TAP; 201 -> SWIPE_FORWARD; 237 -> SWIPE_BACKWARD).
3. **Causas del Fallo en la Versión Anterior**:
   - `InboundRouter.kt`: Descartaba erróneamente cualquier evento con `sender == 1` bajo la falsa asunción de que era el botón de la montura. Esto eliminaba el 90% de los toques en la patilla.
   - `GlassGesture.kt`: No incluía los códigos 206, 207, 210, 211 ni 212 en `fromCode()`, resolviendo a `GlassGesture.UNKNOWN` (que no ejecuta ninguna acción).

## Plan de Acción Detallado

### Fase 1: Actualizar Mapeo de Gestos de Hardware en `GlassGesture.kt`
- Añadir en `when (code)`:
  - `TAP`: incluir `210` (junto con 1, 23, 66, 79, 85, 96, 200, 203).
  - `DOUBLE_TAP`: incluir `211` (junto con 2, 202).
  - `LONG_PRESS`: incluir `212` (junto con 4, 219, 231).
  - `SWIPE_FORWARD`: incluir `206` (junto con 5, 19, 22, 87, 90, 92, 201).
  - `SWIPE_BACKWARD`: incluir `207` (junto con 6, 20, 21, 88, 89, 93, 237).

### Fase 2: Ajustar Enrutamiento de Telemetría en `InboundRouter.kt`
- Eliminar el filtro `if (sender == 1) return`.
- Aceptar eventos táctiles de patillas sin importar si el emisor es 1, 2 o 4 (todos corresponden a sensores de interacción táctil).
- Mantener la omisión de `down_or_up == 0` (soltar / keyup) para evitar dobles disparos.
- Registrar claramente en logs el sender y el gesto reconocido.

### Fase 3: Pruebas Unitarias y Validación
- Actualizar `InboundGestureTest.kt` para verificar que:
  - `key_code: 210` con `sender: 1` y `sender: 4` produce `TAP`.
  - `key_code: 211` con `sender: 1` y `sender: 4` produce `DOUBLE_TAP`.
  - `key_code: 212` con `sender: 1` y `sender: 4` produce `LONG_PRESS`.
  - `key_code: 206` con `sender: 1` produce `SWIPE_FORWARD`.
  - `key_code: 207` con `sender: 1` produce `SWIPE_BACKWARD`.
  - El botón físico `code: 3` sigue yendo directamente a `ai().onTrigger(code)`.
- Ejecutar `./gradlew testDebugUnitTest`.
- Ejecutar `./gradlew assembleDebug`.

### Fase 4: Finalización y Protocolo
- Ejecutar `codegraph sync`.
- Actualizar `docs/PROJECT_MEMORY.md`, `docs/ARCHITECTURE.md`, y `README.md`.
- Reportar en modo cavernícola ("Kog") con enlaces clicables `file://`.
