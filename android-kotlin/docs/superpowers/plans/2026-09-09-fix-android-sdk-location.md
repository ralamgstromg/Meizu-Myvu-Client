# Plan de Implementación: Configuración de Ubicación del Android SDK (local.properties)

> **Para trabajadores agenticos:** SUB-SKILL REQUERIDA: Usar superpowers:executing-plans o ejecución paso a paso con registro riguroso. Cada tarea incluye verificación y guardado en memoria.

**Meta:** Resolver el fallo `SDK location not found` configurando `local.properties` con `sdk.dir=/home/rcastro/Android/Sdk` y verificando la compilación con `./gradlew assembleDebug`.

**Arquitectura:** Los proyectos Android Gradle requieren resolver la ubicación del SDK a través de `local.properties` (`sdk.dir`) o variables de entorno (`ANDROID_HOME`). El SDK instalado en el entorno se encuentra en `/home/rcastro/Android/Sdk`.

**Stack Tecnológico:** Android SDK (`/home/rcastro/Android/Sdk`), Gradle 8.14.3, OpenJDK 25.

## Restricciones Globales
- Idioma de comunicación con el usuario: Modo Cavernícola (Kog).
- Sincronización obligatoria: Ejecutar `codegraph sync` al inicio y final de cada tarea.
- Guardar memoria viva en `docs/PROJECT_MEMORY.md` y `codebase-memory-mcp`.
- Actualizar documentación al final.

---

### Tarea 1: Generar `local.properties` con `sdk.dir`
**Archivos:**
- Crear: `local.properties` en la raíz de `android-kotlin/`

**Pasos:**
- [x] 1. Crear `local.properties` con `sdk.dir=/home/rcastro/Android/Sdk`.
- [x] 2. Verificar que el archivo no sea rastreado por git si está en `.gitignore`.

### Tarea 2: Probar la Compilación con `./gradlew assembleDebug`
**Archivos:**
- Ejecutar en terminal: `./gradlew assembleDebug`

**Pasos:**
- [x] 1. Ejecutar `./gradlew assembleDebug` y verificar resolución del SDK.
- [x] 2. Manejar descargas automáticas de plataformas o licencias si el SDK lo requiere.

### Tarea 3: Actualizar Documentación y Memoria
**Archivos:**
- Modificar: `BUILD_INSTRUCTIONS.md` (mencionar `local.properties` y `sdk.dir`).
- Modificar: `docs/PROJECT_MEMORY.md` (registrar solución del error).
- Actualizar: ADR en `codebase-memory-mcp`.

**Pasos:**
- [x] 1. Añadir instrucción de `local.properties` en `BUILD_INSTRUCTIONS.md`.
- [x] 2. Registrar en `docs/PROJECT_MEMORY.md` y `manage_adr`.

### Tarea 4: Ritual de Cierre `codegraph sync`
**Archivos:**
- Ejecutar: `codegraph sync`

**Pasos:**
- [x] 1. Ejecutar `codegraph sync`.
- [x] 2. Responder al usuario en cavernícola con la solución y resultados.
