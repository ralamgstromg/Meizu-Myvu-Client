# Plan de Optimización de la Aplicación Android (MYVU Client)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Optimizar el rendimiento de la aplicación Android MYVU Client reduciendo el consumo de memoria, extendiendo la duración de la batería del teléfono y de las gafas, mejorando el canal de notificaciones y optimizando las peticiones a servicios API de terceros.

**Architecture:** Introducción de pools de memoria para la recodificación de paquetes/audio, gestión adaptativa de Heartbeat BLE y GPS para ahorro de energía, un motor de notificaciones inteligente con debouncing y deduplicación, y un cliente HTTP centralizado con caché TTL de respuestas y reintentos con backoff exponencial.

**Tech Stack:** Java 17, Android SDK 35 (minSdk 26), FusedLocationProvider, Android NotificationListenerService, JUnit 4.

---

## Estructura de Tareas de Optimización

### Fase 1: Optimización de Recursos (Memoria, Threads y Presión de GC)

#### Task 1: Reutilización de Buffers de Memoria en Decodificación de Audio y Tramas RFCOMM/BLE
- **Files:**
  - Create: `android/app/src/main/java/com/myvu/client/core/BufferPool.java`
  - Create: `android/app/src/test/java/com/myvu/client/core/BufferPoolTest.java`
  - Modify: `android/app/src/main/java/com/myvu/client/transport/ble/BleReassembler.java`
  - Modify: `android/app/src/main/java/com/myvu/client/transport/bt/FrameReassembler.java`
  - Modify: `android/app/src/main/java/com/myvu/client/ai/OpusDecoderStream.java`

- **Step 1: Crear la prueba unitaria para `BufferPool`**
  Diseñar un pool concurrente de buffers `byte[]` de tamaños comunes (e.g. 256, 1024, 4096 bytes) para evitar la instanciación repetida en bucles de recepción de datos.

- **Step 2: Implementar `BufferPool`**
  Implementar asignación y reciclado con límites máximos para evitar fugas de memoria.

- **Step 3: Refactorizar `BleReassembler`, `FrameReassembler` y `OpusDecoderStream`**
  Sustituir la asignación `new byte[...]` dentro de los callbacks de recepción por buffers obtenidos y devueltos al `BufferPool`.

- **Step 4: Ejecutar pruebas unitarias**
  Verificar que la reensamblación de tramas de datos y streaming de audio Opus funciona sin corrupción de datos.

---

### Fase 2: Optimización de Batería (Celular y Gafas)

#### Task 2: Duty-Cycling Adaptativo de Heartbeat BLE y Gestión Eficiente de GPS
- **Files:**
  - Modify: `android/app/src/main/java/com/myvu/client/transport/ble/BleHeartbeat.java`
  - Modify: `android/app/src/main/java/com/myvu/client/nav/FusedLocationSource.java`
  - Test: `android/app/src/test/java/com/myvu/client/transport/ble/BleHeartbeatTest.java`

- **Step 1: Implementar frecuencia adaptativa en `BleHeartbeat`**
  Cuando el canal RFCOMM está activo enviando tráfico o notificaciones, extender el intervalo de Heartbeat BLE de 5s a 30s o pausarlo temporalmente, ya que la conexión está viva. Disminuirá drásticamente el consumo de los radios Bluetooth de ambos dispositivos.

- **Step 2: Implementar intervalos de GPS dinámicos en `FusedLocationSource`**
  Ajustar el polling de ubicación FusedLocation según el estado:
  - Usuario detenido / velocidad ~0 km/h: Polling cada 15 segundos.
  - Usuario en movimiento: Polling cada 3-5 segundos.
  - Sin navegación activa: Desactivar actualizaciones GPS por completo.

- **Step 3: Ejecutar pruebas de ciclo de vida**
  Verificar la reconexión y mantenimiento de sesión tras los cambios de intervalo.

---

### Fase 3: Mejoras en las Notificaciones a las Gafas

#### Task 3: Sistema de Debouncing, Deduplicación y Filtro Inteligente de Notificaciones
- **Files:**
  - Create: `android/app/src/main/java/com/myvu/client/service/NotificationFilter.java`
  - Create: `android/app/src/test/java/com/myvu/client/service/NotificationFilterTest.java`
  - Modify: `android/app/src/main/java/com/myvu/client/service/MirrorNotificationListener.java`

- **Step 1: Crear pruebas unitarias para `NotificationFilter`**
  Verificar que:
  - Ráfagas de notificaciones del mismo paquete (e.g., ráfaga de WhatsApp en <2 segundos) se unifican o descartan.
  - Notificaciones en segundo plano o persistentes (`FLAG_ONGOING_EVENT`, reproductores de música) no se envían repetidamente.
  - Títulos/textos duplicados se ignoran dentro de una ventana de tiempo (Deduplicación).

- **Step 2: Implementar `NotificationFilter`**
  Crear la lógica de filtrado, debouncing (agrupación temporal) y recorte de texto inteligente (máximo 120 caracteres para la pantalla del lente).

- **Step 3: Integrar en `MirrorNotificationListener`**
  Conectar `NotificationFilter` antes de invocar el envío por canal RFCOMM en `ConnectionManager`.

---

### Fase 4: Optimización de APIs y Servicios de Terceros

#### Task 4: Cliente HTTP Unificado con Cache de Respuestas, Backoff Exponencial y Cancelación
- **Files:**
  - Create: `android/app/src/main/java/com/myvu/client/core/HttpCache.java`
  - Modify: `android/app/src/main/java/com/myvu/client/ai/AiHttpClient.java`
  - Modify: `android/app/src/main/java/com/myvu/client/ai/HttpRetry.java`
  - Modify: `android/app/src/main/java/com/myvu/client/nav/Osrm.java`
  - Test: `android/app/src/test/java/com/myvu/client/core/HttpCacheTest.java`

- **Step 1: Crear `HttpCache` para consultas repetidas**
  Implementar un caché en memoria con TTL (Time-To-Live) para respuestas de OSRM (rutas) y APIs del clima, reduciendo llamadas de red redundantes.

- **Step 2: Mejorar `HttpRetry` con Backoff Exponencial con Jitter**
  Añadir tiempo de espera exponencial con variación aleatoria (Jitter) para peticiones fallidas a Groq/Anthropic/OpenAI, evitando la saturación del procesador en zonas con poca cobertura de red.

- **Step 3: Soporte de cancelación de peticiones en vuelo**
  Permitir cancelar peticiones HTTP pendientes si el usuario interrumpe la consulta de voz o detiene la navegación.

---

## Verificación Final

1. **Pruebas unitarias completas:**
   ```bash
   cd android
   ./gradlew test
   ```
2. **Prueba de compilación de depuración y producción:**
   ```bash
   ./gradlew assembleDebug assembleRelease
   ```
