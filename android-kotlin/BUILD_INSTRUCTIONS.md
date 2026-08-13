# Guía de Construcción y Despliegue de APK (Kotlin) — Meizu Myvu Client

Esta documentación detalla los pasos para compilar, generar y firmar los paquetes APK de la aplicación Android en Kotlin alojada en el directorio `android-kotlin/`.

---

## 1. Requisitos Previos

- **JDK**: Java 17 o superior (`OpenJDK 17`).
- **Android SDK**: API Level 35 (`compileSdk 35`, `minSdk 26`).
- **Gradle**: Gradle 8.14+ (incluido en el proyecto mediante el Gradle Wrapper `./gradlew`).
- **Herramienta Keytool / JKS**: Para generación del keystore de firma (incluida en JDK).

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
