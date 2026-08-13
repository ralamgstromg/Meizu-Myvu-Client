# Meizu Myvu Client (Kotlin Android App)

Cliente Android nativo escrito en **Kotlin 2.1+** para gafas inteligentes Meizu Myvu AR.

## Estructura del Proyecto

- `app/src/main/java/com/myvu/client/`
  - `core/`: Configuración, logs en ring buffer (`LogBus`), gestión de memoria (`BufferPool`), preferencias seguras (`SecurePrefs`).
  - `protocol/`: Codecs binarios de alto rendimiento (TLV `TlvBox`, Protobuf `Pb`, `Session`, `RelayMessage`).
  - `protocol/link/`: Tramado `LinkProtocol`, `DeviceInfo`, comandos de enlace StarryNet.
  - `transport/`: Capa de transporte asíncrona con Kotlin Coroutines y `Flow` para RFCOMM Classic (`bt/BtTransport`) y BLE GATT (`ble/BleTransport`).
  - `service/`: `ConnectionManager` con `StateFlow`, `MyvuService` Foreground Service, `MirrorNotificationListener`.
  - `ai/`, `nav/`, `weather/`, `reminder/`, `database/`, `ui/`: Servicios de IA, navegación OSRM, clima OpenMeteo, recordatorios y actividades.

## Guía de Construcción y Firma

Para ver instrucciones detalladas sobre cómo compilar en debug/release, alojamientos de APK y cómo generar APKs firmados para producción, consulta la guía:
👉 [BUILD_INSTRUCTIONS.md](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/BUILD_INSTRUCTIONS.md)

### Comandos Rápidos:

- **Debug APK**: `./gradlew assembleDebug`
- **Release APK**: `./gradlew assembleRelease`
- **Ejecutar Tests**: `./gradlew test`
