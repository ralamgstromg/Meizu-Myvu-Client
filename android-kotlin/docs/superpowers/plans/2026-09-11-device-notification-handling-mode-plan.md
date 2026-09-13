# Plan de Implementación: Manejo de Notificaciones por Dispositivo (HUD, TTS, Ambos)

## 1. Contexto y Requerimiento
El usuario requiere:
> "Por cada dispositivo debo poder configurar el manejo que se le daran a las notificaciones, sean visuales (HUD), sonoras (TTS), o ambos."

Cada dispositivo Bluetooth (Gafas Inteligentes MYVU, Auriculares Bluetooth, Dispositivos Genéricos) debe permitir elegir cómo se procesarán y entregarán las notificaciones recibidas:
- **Visuales (HUD)**: Mostrar en el visor o pantalla si el dispositivo lo soporta.
- **Sonoras (TTS)**: Leer en voz alta a través del motor Text-To-Speech hacia los altavoces/auriculares del dispositivo.
- **Ambos**: Mostrar en HUD y leer simultáneamente por TTS.
- **Desactivadas (Ninguno)**: Silenciar y no reenviar alertas a ese dispositivo.

---

## 2. Componentes a Modificar

### A. Modelo de Datos y Base de Datos Room
1. **`com.myvu.client.data.DeviceNotificationMode`** (Nuevo enum en `BluetoothDeviceEntity.kt`):
   - `BOTH`: Visual (HUD) y Sonora (TTS).
   - `VISUAL_ONLY`: Solo Visual (HUD).
   - `AUDIO_ONLY`: Solo Sonora (TTS).
   - `NONE`: Desactivadas.
   - Funciones helper para labels, descripciones y mapeo por ID / posición.

2. **`BluetoothDeviceEntity.kt`**:
   - Agregar campo `val notificationMode: String = DeviceNotificationMode.BOTH.name`.
   - Agregar helpers:
     - `fun isVisualNotificationEnabled(): Boolean`
     - `fun isAudioNotificationEnabled(): Boolean`
   - Mantener retrocompatibilidad con `autoReadNotifications` y `ttsEnabled`.

3. **`BluetoothDeviceDao.kt`**:
   - Agregar `@Query("SELECT * FROM bluetooth_devices WHERE isConnected = 1") suspend fun getConnectedDevices(): List<BluetoothDeviceEntity>`.

4. **`AppDatabase.kt`**:
   - Incrementar versión de la base de datos a `version = 4`.

### B. Gestor de Dispositivos (`BluetoothDeviceManager.kt`)
- Soportar actualización del campo `notificationMode` en `updateGlassesGestures`, `updateHeadphoneSettings`, y un nuevo método `updateDeviceNotificationMode(mac: String, mode: String)`.

### C. Lógica de Enrutamiento de Notificaciones (`MirrorNotificationListener.kt`)
- Evaluar los dispositivos conectados:
  1. **Sonora (TTS)**: Si cualquier dispositivo conectado activo tiene `isAudioNotificationEnabled() == true`, reproducir el texto con `TextToSpeechHelper.speak(...)`.
  2. **Visual (HUD)**: Para Smart Glasses (`SMART_GLASSES`), verificar si tiene `isVisualNotificationEnabled() == true`. Si el modo es `AUDIO_ONLY` o `NONE`, omitir el envío del comando JSON/Proto al HUD de las gafas.

### D. Interfaz de Usuario
1. **`GlassesSettingsActivity.kt` & `activity_glasses_settings.xml`**:
   - Agregar Card "MANEJO DE NOTIFICACIONES EN GAFAS AR".
   - Añadir `Spinner` con opciones:
     - *Ambas (Visual HUD + Sonora TTS)*
     - *Solo Visual (HUD en Pantalla)*
     - *Solo Sonora (Lectura por Voz TTS)*
     - *Desactivadas (Ninguna)*
   - Cargar y guardar `notificationMode` en la entidad del dispositivo en Room y persistir.

2. **`HeadphoneSettingsActivity.kt` & `activity_headphone_settings.xml`**:
   - Adaptar sección de notificaciones para incluir el selector de modo de notificaciones:
     - *Lectura Sonora por Voz (TTS)*
     - *Desactivadas*
   - Sincronizar con `notificationMode` (`AUDIO_ONLY` vs `NONE`) y `autoReadNotifications`.

---

## 3. Estrategia de Pruebas y Verificación
1. **Pruebas Unitarias**:
   - Test en `BluetoothDeviceEntityTest` o similar verificando la lógica de `DeviceNotificationMode`, `isVisualNotificationEnabled()`, `isAudioNotificationEnabled()`.
   - Verificar que `BOTH`, `VISUAL_ONLY`, `AUDIO_ONLY` y `NONE` se comportan según lo especificado.
2. **Compilación y Linters**:
   - `rtk ./gradlew testDebugUnitTest`
   - `rtk ./gradlew assembleDebug`
3. **Sincronización y Memoria**:
   - `rtk codegraph sync`
   - Actualizar `PROJECT_MEMORY.md`, `ARCHITECTURE.md` y `README.md`.
