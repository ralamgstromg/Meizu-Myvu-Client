# Plan: Análisis de Log y Plan Integral de Mejoras (Meizu Myvu Client)

## 1. Diagnóstico del Log (`myvu_client_log.txt`)
- **Estado General**: Estable y operativo.
- **Bucle Infinito**: Totalmente erradicado. La sincronización inicial y el paso a BLE y RFCOMM concluyen limpiamente.
- **Flujo de Voz y Asistente**: Funcionando de extremo a extremo. El comando de voz fue reconocido y respondido por TTS en el HUD.

## 2. Puntos de Mejora Identificados

### Mejora 1: Diagnóstico y Resiliencia en AudioProfiles (`getProfileProxy`)
- **Hallazgo**: `AudioProfiles: HFP/A2DP proxy binding in progress` se registra dos veces pero `onServiceConnected` no llega a registrarse en el log.
- **Acción**:
  1. Capturar y loguear el retorno booleano de `adapter.getProfileProxy(context, ...)`.
  2. Si retorna `false` o no responde en 2 segundos, reintentar la vinculación o utilizar el contexto directo del Servicio (`Service`).
  3. Asegurar `BluetoothManager.getAdapter()` y fallback directo por `BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED`.

### Mejora 2: Optimización de Latencia en STT (Ahorro de ~1 segundo por turno de voz)
- **Hallazgo**: En cada pulsación, STT inicia con `es-CO` y `preferOffline=true`, arrojando `code=12 (Language not supported)` tras 880ms, antes de reintentar con `es` (online).
- **Acción**:
  1. Recordar en memoria o preferencias (`Prefs`) el último dialecto y modo offline que falló con `code 12`.
  2. Si `es-CO` offline no está instalado en el sistema Android del usuario, pasar directamente a `es` (online) en el primer intento, ahorrando ~900ms de latencia en cada pulsación.

### Mejora 3: Precisión Temporal en Clima ("mañana" vs Clima Actual)
- **Hallazgo**: El usuario preguntó *"el pronóstico del clima para mañana en Barranquilla"*. El `VoiceActionRouter` interceptó la petición por fast-path y devolvió el clima actual de hoy (33°C), ignorando el modificador temporal *"para mañana"*.
- **Acción**:
  1. En `VoiceActionRouter.kt`, si la consulta contiene palabras temporales ("mañana", "fin de semana", "pronóstico para..."), no usar el fast-path estático de clima actual.
  2. Derivar la consulta al agente LLM con la herramienta nativa `weather_forecast` (que consulta la API de pronóstico extendido) o ajustar el fast-path para incluir el día siguiente.

### Mejora 4: Filtro Antirráfaga para Notificaciones de WhatsApp
- **Hallazgo**: En 20 segundos se enviaron 4 notificaciones de grupo consecutivas de WhatsApp con contadores incrementales (`2 mensajes`, `3 mensajes`, `4 mensajes`), seguidas de una ráfaga de 4 `DISMISS_NOTIFICATION` en 1.2 segundos, causando parpadeo visual en las gafas.
- **Acción**:
  1. Implementar un debounce (de 2 a 3 segundos) en `MirrorNotificationListener` para notificaciones del mismo remitente/grupo de WhatsApp.
  2. Filtrar resúmenes redundantes de recuento de mensajes que no aportan contenido nuevo.
