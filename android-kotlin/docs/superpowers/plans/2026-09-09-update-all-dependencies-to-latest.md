# Plan de Actualización Integral de Paquetes y Dependencias a Última Versión

## 1. Contexto y Objetivos
El usuario solicita actualizar todos los paquetes/dependencias utilizados por la aplicación Android Kotlin a la última versión disponible en los repositorios oficiales (Google Maven y Maven Central), ajustando el código fuente en caso de ser necesario por breaking changes o APIs deprecadas.

## 2. Inventario de Dependencias y Versiones Objetivo

| Paquete / Artefacto | Versión Actual | Versión Objetivo | Repositorio |
|---|---|---|---|
| `androidx.core:core-ktx` | `1.15.0` | `1.19.0` (o compatible con compileSdk 35) | Google Maven |
| `androidx.appcompat:appcompat` | `1.7.0` | `1.8.0` | Google Maven |
| `androidx.lifecycle:lifecycle-runtime-ktx` | `2.8.7` | `2.11.0` | Google Maven |
| `com.google.android.material:material` | `1.12.0` | `1.14.0` | Google Maven |
| `com.google.android.gms:play-services-location` | `21.3.0` | `21.4.0` | Google Maven |
| `com.google.android.gms:play-services-auth` | `21.3.0` | `22.0.0` | Google Maven |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.10.1` | `1.11.0` | Maven Central |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | `1.10.1` | `1.11.0` | Maven Central |
| `com.google.mediapipe:tasks-genai` | `0.10.20` | `0.10.35` | Google Maven |
| `androidx.room:room-*` (runtime, ktx, compiler) | `2.6.1` | `2.8.4` (o 2.7.x) | Google Maven |
| `junit:junit` | `4.13.2` | `4.13.2` | Maven Central |
| `org.robolectric:robolectric` | `4.14.1` | `4.16.1` | Maven Central |
| `org.json:json` | `20260522` | `20260814` | Maven Central |
| `kotlin` & `ksp` | `2.1.10` / `2.1.10-1.0.31` | `2.1.21` / `2.1.21-2.0.2` (o 2.2.x) | Maven Central / Google |
| `agp` (Android Gradle Plugin) | `8.8.0` | `8.13.2` / `8.9.3` | Google Maven |

## 3. Estrategia de Rollout y Verificación por Fases
1. **Fase 1: Bibliotecas de Runtime y UI Android**
   - Actualizar `coreKtx`, `appcompat`, `lifecycleRuntimeKtx`, `material`.
   - Probar compilación con `./gradlew assembleDebug`.
2. **Fase 2: Google Play Services y Utilidades**
   - Actualizar `playServicesLocation`, `playServicesAuth`, `coroutines`, `json`, `mediapipeGenai`.
   - Probar compilación.
3. **Fase 3: Persistencia Room y Testing**
   - Actualizar `room` a la última versión compatible con KSP.
   - Actualizar `robolectric` a `4.16.1`.
   - Ejecutar pruebas unitarias `./gradlew testDebugUnitTest`.
4. **Fase 4: Toolchain (Kotlin, KSP, AGP)**
   - Actualizar `kotlin` y `ksp` manteniendo coherencia con el plugin del compilador.
   - Validar compatibilidad con OpenJDK 25 y AGP.
5. **Fase 5: Verificación Integral y Ritual Kog**
   - Ejecutar suite completa de tests (`ToolCallingIntegrationTest`).
   - Generar APK debug limpio (`./gradlew assembleDebug`).
   - Actualizar `docs/PROJECT_MEMORY.md` y sincronizar con `codegraph sync`.
