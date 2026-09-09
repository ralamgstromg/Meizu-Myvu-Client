# Plan de Implementación: Actualización de Procesos de Build a OpenJDK 25

> **Para trabajadores agenticos:** SUB-SKILL REQUERIDA: Usar superpowers:executing-plans o ejecución paso a paso con registro riguroso. Cada tarea incluye verificación y guardado en memoria.

**Meta:** Actualizar la configuración de compilación, Gradle properties, scripts de build y documentación del proyecto para utilizar OpenJDK 25 (`/usr/lib/jvm/java-25-openjdk-amd64`).

**Arquitectura:** Meizu Myvu Client requiere que Gradle Daemon y los procesos de compilación de Kotlin/Android utilicen la nueva instalación de OpenJDK 25 instalada por el usuario en `/usr/lib/jvm/java-25-openjdk-amd64`.

**Stack Tecnológico:** OpenJDK 25 (25.0.4 Ubuntu), Gradle 8.14.3, Kotlin 2.1.10, AGP 8.8.0.

## Restricciones Globales
- Idioma de comunicación con el usuario: Modo Cavernícola (Kog).
- Sincronización obligatoria: Ejecutar `codegraph sync` al inicio y final de cada tarea.
- Modificaciones en archivos de configuración y documentación.
- Guardar memoria viva en `docs/PROJECT_MEMORY.md` y `codebase-memory-mcp`.

---

### Tarea 1: Configurar `gradle.properties` para OpenJDK 25
**Archivos:**
- Modificar: `gradle.properties`

**Pasos:**
- [x] 1. Actualizar `org.gradle.java.home=/usr/lib/jvm/java-25-openjdk-amd64`.
- [x] 2. Ajustar `org.gradle.jvmargs` con memoria y configuraciones de compatibilidad necesarias.
- [x] 3. Detener daemons previos (`./gradlew --stop`).

### Tarea 2: Actualizar Configuración de `app/build.gradle.kts`
**Archivos:**
- Modificar: `app/build.gradle.kts`

**Pasos:**
- [x] 1. Configurar compatibilidad de Java y Kotlin para alinearse con OpenJDK 25.
- [x] 2. Verificar o probar evaluación del proyecto mediante `./gradlew help`.

### Tarea 3: Actualizar Documentación del Proyecto
**Archivos:**
- Modificar: `BUILD_INSTRUCTIONS.md`
- Modificar: `README.md`
- Modificar: `docs/ARCHITECTURE.md`
- Modificar: `docs/PROJECT_MEMORY.md`

**Pasos:**
- [x] 1. Actualizar `BUILD_INSTRUCTIONS.md` reflejando OpenJDK 25 como JDK requerido y ruta por defecto.
- [x] 2. Actualizar `README.md` con OpenJDK 25.
- [x] 3. Actualizar `docs/ARCHITECTURE.md` con OpenJDK 25.
- [x] 4. Registrar cambios en `docs/PROJECT_MEMORY.md` y actualizar ADR en `codebase-memory-mcp`.

### Tarea 4: Ritual de Cierre y Verificación
**Archivos:**
- Ejecutar: `codegraph sync`

**Pasos:**
- [x] 1. Ejecutar `codegraph sync` para registrar nuevas dependencias y configuraciones.
- [x] 2. Responder al usuario en cavernícola con los cambios aplicados en detalle.
