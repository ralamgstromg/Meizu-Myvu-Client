# Plan de Trabajo: Optimización de Audio Gemini (SCO/A2DP), Batería y Recursos

## 1. Diagnóstico Exhaustivo del Log (`myvu_client_log.txt`)

### 1.1 "Se va la voz de la respuesta de Gemini y después de unos segundos regresa"
- **Causa Raíz**: 
  - `TouchGestureManager.launchGeminiAssistant` activa Bluetooth SCO (`am.startBluetoothSco()`, `am.isBluetoothScoOn = true`, `am.setCommunicationDevice(...)`).
  - El micrófono de las gafas capta la orden del usuario con éxito.
  - **Fallo Crítico**: La aplicación **nunca liberaba SCO** (`stopBluetoothSco()`, `clearCommunicationDevice()`).
  - Al responder Gemini: intenta reproducir audio por el canal multimedia (A2DP).
  - Pero la pila de audio de Android queda bloqueada en modo llamada/comunicación SCO.
  - El firmware de las gafas reporta suspensión de A2DP:
    - `14:59:35.875: iot_a2dp_status_change -> a2dp_status: 1` (A2DP suspendido -> silencio absoluto).
    - `14:59:41.757: iot_a2dp_status_change -> a2dp_status: 2` (A2DP recuperado tras 6 segundos de timeout).
  - Durante esos 6 segundos la respuesta de voz de Gemini no se escucha. Cuando Android/gafas fuerzan la desconexión de SCO, A2DP regresa y la voz vuelve.
- **Interferencia de Notificación TTS de las Gafas**:
  - Al disparar Gemini, la app enviaba `executeShowNotification("MYVU", "Gemini escuchando...")`.
  - En el log (`com.upuphone.ai.ttsengine.phone {"caller":"com.tts.notification"}`), el motor TTS nativo de las gafas leyó en voz alta esa notificación por los altavoces en el exacto instante en que Gemini intentaba escuchar la voz del usuario y preparar su respuesta.

### 1.2 Saturación de Radio y Caídas de Relay SPP ("SPP server closed by the glasses")
- Mantener el canal síncrono SCO abierto de forma indefinida satura la radio Bluetooth del procesador ultra-bajo-consumo de las gafas Meizu Myvu, forzándolo a cerrar el servidor de sockets RFCOMM SPP (`14:59:33.888: <- SPP server closed by the glasses`).
- Al caer SPP, la app reconectaba a los 55ms y enviaba una ráfaga completa de 27 mensajes (`init burst`), consumiendo batería y CPU en bucle.

---

## 2. Plan de Implementación por Fases

### Fase 1: Ciclo de Vida Inteligente de Audio Bluetooth SCO (`GeminiAudioRouter`)
- En `TouchGestureManager.kt`:
  - Implementar liberación programada automática de SCO mediante temporizador (ventana de 4.5 segundos, tiempo suficiente para capturar la orden vocal de Gemini).
  - Al transcurrir el timeout de escucha:
    - `am.stopBluetoothSco()`
    - `am.isBluetoothScoOn = false`
    - En Android 12+ (`VERSION_CODES.S`): `am.clearCommunicationDevice()`
    - Restaurar `am.mode = AudioManager.MODE_NORMAL`
  - Evitar pasar `TYPE_BLUETOOTH_A2DP` a `setCommunicationDevice` (es inválido y causa conflictos en el subsistema de audio).
  - Con esto, tan pronto como Gemini empieza a responder (segundo 3 o 4), el canal A2DP está 100% limpio y la voz de Gemini se escucha de inmediato con máxima fidelidad estéreo, sin pausas ni silencios de 6 segundos.

### Fase 2: Silenciar Notificación Audible al Invocar Gemini
- En `TouchGestureManager.launchGeminiAssistant`:
  - Retirar el envío de `SHOW_NOTIFICATION` que disparaba el molesto TTS de las gafas (`com.tts.notification`), eliminando la competencia entre altavoces y micrófono.
  - En su lugar, proyectar texto silencioso si es necesario o confiar en la UI activa de Gemini en el teléfono y audio limpio en las gafas.

### Fase 3: Optimización de Batería y Radio en `RelaySupervisor` y `InboundRouter`
- **Backoff tras cierre de servidor SPP**:
  - En `RelaySupervisor.onRelayLost()`, asegurar que el primer reintento tras el cierre del socket por las gafas espere al menos 3 segundos en vez de reintentar en <100ms, permitiendo que la radio Bluetooth de las gafas se estabilice sin saturar el procesador.
- **Filtro de Telemetría Ruido en `InboundRouter`**:
  - Añadir a `nonTouchTelemetry`:
    - `iot_a2dp_status_change`
    - `audio_stats`
    - `air_starrynet_bt`
    - `iot_voice_asr_time`
    - `wear_data_collect`
    - `starrynet_devices_disconnect`
    - `starrynet_devices_reconnect`
    - `screen_off_timeout_change`
    - `standby_position`
  - Esto evita parseos inútiles de JSON, logs repetitivos y gasto de CPU/batería innecesario en segundo plano.

### Fase 4: Pruebas, Verificación y Documentación
- Actualizar y ejecutar pruebas unitarias (`./gradlew testDebugUnitTest`).
- Compilar APK (`./gradlew assembleDebug`).
- Ejecutar `codegraph sync`.
- Actualizar `docs/PROJECT_MEMORY.md`, `docs/ARCHITECTURE.md` y `README.md`.
