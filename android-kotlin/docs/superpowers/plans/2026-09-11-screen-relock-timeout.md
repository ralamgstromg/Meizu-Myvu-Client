# Plan: Tiempo Máximo Parametrizable para Volver a Bloquear Pantalla tras Acciones

## 1. Contexto y Requerimiento

El usuario solicita:
> *"has un ajuste cuando se lancen acciones que habilita la pantalla, se debe configurar un tiempo maximo para volver a bloquear, que sea parametrizable pero que inicie en 1 minuto por defecto"*

Actualmente:
- Cuando se lanzan acciones desde las gafas (Gemini, Gemini Live, Asistente de teléfono, Abrir apps, WhatsApp, etc.), la app invoca `LockScreenHelper.wakeUpScreen()` y `SendTrampolineActivity` para encender la pantalla y descartar el keyguard.
- El teléfono queda despierto, pero el bloqueo posterior depende exclusivamente del timeout inactivo global del sistema operativo Android (que puede ser 5, 10 o 30 minutos o "nunca"), dejando el teléfono encendido y vulnerable en el bolsillo tras interactuar con las gafas.
- Se requiere que el usuario pueda **parametrizar el tiempo máximo para volver a bloquear la pantalla** (ej. slider de 15s a 300s, por defecto **60 segundos / 1 minuto**).
- Cuando transcurra ese tiempo tras haber despertado la pantalla por una acción de las gafas, la aplicación debe volver a apagar y bloquear el dispositivo automáticamente si no está en uso activo.

---

## 2. Solución Técnica

### A. Mecanismo de Bloqueo en Android
1. **Servicio de Accesibilidad (`AutoSendAccessibilityService`)**:
   - Cuenta con `GLOBAL_ACTION_LOCK_SCREEN` (disponible desde Android 9 / API 28+).
   - Es el estándar oficial y más seguro para bloquear la pantalla sin requerir permisos invasivos de Device Admin (`DevicePolicyManager.lockNow()`).
   - El servicio ya está integrado y funcionando en la app (`AutoSendAccessibilityService.activeInstance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`).
2. **Fallback Device Policy / PowerManager**:
   - `LockScreenHelper.lockDevice(context)`:
     - Intenta primero `AutoSendAccessibilityService.activeInstance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)`.
     - Si no está disponible, registra advertencia en LogBus.

### B. Gestor de Temporizador de Auto-Bloqueo (`LockScreenHelper`)
- Variable `reLockRunnable` y `reLockHandler` en `LockScreenHelper`.
- Método `scheduleReLock(context: Context, timeoutMs: Long = 0L)`:
  - Cancela cualquier temporizador previo (`cancelScheduledReLock()`).
  - Obtiene el tiempo parametrizado en `Prefs.screenReLockTimeoutSeconds(context)` (por defecto: **60 segundos**).
  - Programa la ejecución de `lockDevice(context)` después de `timeoutMs` (o del valor de `Prefs`).
- Método `cancelScheduledReLock()`: para pausar o resetear si el usuario interactúa manualmente.
- Actualizar `wakeUpScreen(...)`:
  - Al despertar la pantalla para una acción (o al disparar acciones en `TouchGestureManager`, `PhoneActionExecutor`, etc.), programar automáticamente `scheduleReLock(context)`.

### C. Persistencia en `Prefs.kt`
- `KEY_SCREEN_RELOCK_TIMEOUT_SECONDS = "screen_relock_timeout_sec"`
- Valor por defecto: `DEFAULT_SCREEN_RELOCK_TIMEOUT_SECONDS = 60` (1 minuto).
- Métodos `@JvmStatic`:
  - `screenReLockTimeoutSeconds(c: Context): Int`
  - `setScreenReLockTimeoutSeconds(c: Context, seconds: Int)`

### D. Interfaz en `activity_settings.xml` y `SettingsActivity.kt`
- En la tarjeta **"Gestos de la Patilla Táctil (Touchpad)"** de `activity_settings.xml`:
  - Añadir encabezado y etiqueta: `lblScreenReLockTimeout` ("Tiempo para volver a bloquear: 60s (1 minuto)").
  - Añadir `Slider` (`sliderScreenReLockTimeout`):
    - `valueFrom="15"`
    - `valueTo="300"` (5 minutos)
    - `stepSize="15"` (intervalos de 15s: 15s, 30s, 45s, 60s, 75s, 90s, 120s, 180s, 240s, 300s)
    - `value="60"`
    - Helper text: *"Bloquea automáticamente la pantalla tras realizar acciones desde las gafas para ahorrar batería y proteger tu teléfono en el bolsillo."*
- En `SettingsActivity.kt`:
  - Vincular el slider en `wireTouchpad()` para leer y persistir el valor, actualizando el texto dinámicamente (`lblScreenReLockTimeout.text = "Tiempo para volver a bloquear: ${valInt}s ${if (valInt == 60) "(1 min)" else ""}"`).

---

## 3. Plan de Verificación
1. Pruebas unitarias de `Prefs.kt` y `TouchGestureManagerTest.kt`: `rtk ./gradlew testDebugUnitTest`.
2. Sincronizar CodeGraph: `rtk codegraph sync`.
3. Actualizar documentación del proyecto: `PROJECT_MEMORY.md`, `ARCHITECTURE.md`, `README.md`.
