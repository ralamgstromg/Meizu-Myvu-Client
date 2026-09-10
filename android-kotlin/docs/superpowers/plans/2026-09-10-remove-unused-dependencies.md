# Plan de Implementación: Eliminación de Librerías No Utilizadas

> **Objetivo:** Eliminar por completo librerías no requeridas o en desuso del proyecto Meizu MYVU Client, reduciendo drásticamente el peso del APK y eliminando dependencias fantasma.

---

## 1. Diagnóstico y Análisis Detallado

Tras auditar exhaustivamente `app/build.gradle.kts`, `gradle/libs.versions.toml` y todo el árbol `app/src/`:

| Dependencia | Estado | Justificación / Hallazgo |
|---|---|---|
| `androidx.core:core-ktx` | **EN USO** | Extensiones KTX para Context, Bundles, etc. |
| `androidx.appcompat:appcompat` | **EN USO** | `AppCompatActivity` base de todas las pantallas. |
| `androidx.lifecycle:lifecycle-runtime-ktx` | **EN USO** | `lifecycleScope` en Activities y Dialogs. |
| `com.google.android.material:material` | **EN USO** | Material 3 switches, buttons, dialogs, toolbars. |
| `com.google.android.gms:play-services-location` | **EN USO** | `FusedLocationProviderClient` para GPS / HUD. |
| `com.google.android.gms:play-services-auth` | **EN USO** | `GoogleSignIn` en `GoogleDriveSyncHelper`. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | **EN USO** | Coroutines en Dispatchers.Main / IO. |
| **`com.google.mediapipe:tasks-genai`** | **NO UTILIZADA / NO REQUERIDA (108 MB bloat)** | **0 imports, 0 llamadas en todo el código Kotlin/Java.** La inferencia local se realiza mediante `LocalAiClient` (HTTP OpenAI/LiteLLM compatible) y la nube vía `GeminiClient`. Esta librería introduce 4 arquitecturas de binarios nativos C++ (`libllm_inference_engine_jni.so`), inflando el APK de **~9MB a 117MB**. |
| `androidx.room:room-runtime`, `room-ktx`, `room-compiler` | **EN USO** | Base de datos Room (`AppDatabase`, `ChatDao`). |
| `junit:junit`, `robolectric`, `json`, `coroutines-test` | **EN USO** | Suite de pruebas unitarias locales. |

---

## 2. Acciones Propuestas

### Tarea 1: Remover `mediapipe-tasks-genai` de Gradle
- **`app/build.gradle.kts`**: Eliminar `implementation(libs.mediapipe.tasks.genai)`.
- **`gradle/libs.versions.toml`**: Eliminar versión `mediapipeGenai = "0.10.35"` y alias `mediapipe-tasks-genai`.

### Tarea 2: Limpieza de Archivos y Manifiesto Obsoletos
- **`app/src/main/assets/public.libraries.txt`**: Eliminar este archivo (era un workaround para cargar drivers OpenCL/Mali de GPU requeridos únicamente por MediaPipe Tasks GenAI).
- **`app/src/main/AndroidManifest.xml`**: Eliminar las directivas `<uses-feature android:name="android.hardware.vulkan.version" ... />` y `<uses-feature android:name="android.hardware.opengles.aep" ... />` (sombreadores de GPU exclusivas para MediaPipe).

### Tarea 3: Verificación de Compilación y Pruebas
- Ejecutar `./gradlew testDebugUnitTest` para asegurar que las pruebas pasen al 100%.
- Ejecutar `./gradlew assembleDebug` y verificar la reducción masiva del APK (de ~117MB a ~9MB).

### Tarea 4: Actualización de Memoria y Documentación
- Actualizar `README.md`, `docs/ARCHITECTURE.md`, y `docs/PROJECT_MEMORY.md` para reflejar la eliminación de MediaPipe y aclarar que la inferencia local se realiza mediante endpoints compatibles con OpenAI (`LocalAiClient`).
- Ejecutar `codegraph sync`.
