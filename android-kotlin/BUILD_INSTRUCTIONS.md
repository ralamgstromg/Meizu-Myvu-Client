# Guía de Construcción y Despliegue de APK (Kotlin) — Meizu Myvu Client

Esta documentación detalla los pasos para compilar, generar y firmar los paquetes APK de la aplicación Android en Kotlin alojada en el directorio `android-kotlin/`.

---

## 1. Requisitos Previos

- **JDK**: OpenJDK 25 (`/usr/lib/jvm/java-25-openjdk-amd64`) configurado en `gradle.properties` (`org.gradle.java.home`).
- **Android SDK**: API Level 35 (`compileSdk 35`, `minSdk 26`). Debe especificarse la ruta en el archivo `local.properties` mediante `sdk.dir=/home/rcastro/Android/Sdk` (o mediante la variable de entorno `ANDROID_HOME`).
- **Gradle**: Gradle 8.14.3+ (incluido en el proyecto mediante el Gradle Wrapper `./gradlew`).
- **Herramienta Keytool / JKS**: Para generación del keystore de firma (incluida en JDK 25).

---

## 2. Generación de APK de Depuración (Debug)

El APK de debug se genera con la firma de desarrollo predeterminada de Android.

### Comando de Compilación:
Ejecutar desde el directorio `android-kotlin/`:

```bash
./gradlew assembleDebug
```

### Ubicación del APK Generado:
```
android-kotlin/app/build/outputs/apk/debug/app-debug.apk
```

---

## 3. Generación de APK de Producción (Release)

### Opción A: Release sin firmar (Unsigned Release)
Si no se ha proporcionado un archivo `keystore.properties`, Gradle construirá el APK de release sin firmar.

#### Comando:
```bash
./gradlew assembleRelease
```

#### Ubicación del APK sin firmar:
```
android-kotlin/app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## 4. Generación de APK Firmado para Despliegue (Signed Release)

Para generar un APK firmado de producción listo para distribuir o instalar en dispositivos:

### Paso 1: Generar la Clave de Firma (Keystore)
Si aún no posees una clave JKS de producción, genera una ejecutando en tu terminal:

```bash
keytool -genkeypair -v \
  -keystore my-release-key.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias my-alias
```

### Paso 2: Configurar `keystore.properties`
Crea un archivo llamado `keystore.properties` en la raíz del proyecto `android-kotlin/` (o en la raíz del repositorio) con el siguiente formato:

```properties
storeFile=../my-release-key.jks
storePassword=tu_contraseña_de_almacen
keyAlias=my-alias
keyPassword=tu_contraseña_de_clave
```

> ⚠️ **Nota de Seguridad**: El archivo `keystore.properties` y las claves `.jks` están excluidos en `.gitignore` para no ser subidos al repositorio de código.

### Paso 3: Compilar el APK Firmado
Con el archivo `keystore.properties` configurado en su lugar, ejecuta:

```bash
./gradlew assembleRelease
```

### Ubicación del APK Firmado Final:
```
android-kotlin/app/build/outputs/apk/release/app-release.apk
```

---

## 5. Ejecución de Pruebas Unitarias (Unit Tests)

Para verificar que todos los codecs TLV/Protobuf, la capa de transporte Coroutine y las características funcionan correctamente:

```bash
./gradlew test
```

### Reporte de Pruebas:
```
android-kotlin/app/build/reports/tests/testDebugUnitTest/index.html
```

---

## 6. Instalación en Dispositivo y Permisos

Para instalar el APK compilado directamente en un terminal Android conectado:

```bash
# Instalar APK de debug otorgando permisos estándar de tiempo de ejecución
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
```

Para la configuración de permisos especiales indispensables (Acceso a notificaciones para HUD, Servicio de accesibilidad para Auto-Send de WhatsApp, Desbloqueo extendido Smart Lock, Asistente de voz del sistema y batería sin restricciones), consulta la guía detallada:
👉 **[Guía de Instalación y Configuración de Permisos en Android](docs/ANDROID_SETUP_GUIDE.md)**.
