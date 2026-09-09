# Guía de Instalación y Configuración de Permisos en Android — Meizu Myvu Client

Esta guía proporciona el procedimiento detallado para instalar la aplicación en un dispositivo Android y conceder todos los permisos estándar y especiales necesarios para el funcionamiento óptimo de las gafas **Meizu Myvu Smart Glasses** (incluyendo proyección HUD, control de voz tipo Google Assistant, envío manos libres de WhatsApp/Telegram y control de hardware con pantalla bloqueada).

---

## 📋 Índice

1. [Instalación del APK](#1-instalación-del-apk)
2. [Permisos de Tiempo de Ejecución Estándar](#2-permisos-de-tiempo-de-ejecución-estándar)
3. [Permisos Especiales del Sistema (Cruciales)](#3-permisos-especiales-del-sistema-cruciales)
   - [A. Acceso a Notificaciones (Mirroring en HUD)](#a-acceso-a-notificaciones-mirroring-en-hud)
   - [B. Servicio de Accesibilidad (Auto-Send de Mensajería)](#b-servicio-de-accesibilidad-auto-send-de-mensajería)
   - [C. Desbloqueo Extendido / Smart Lock (Manos Libres en Bolsillo)](#c-desbloqueo-extendido--smart-lock-manos-libres-en-bolsillo)
   - [D. Aplicación de Asistente Digital Predeterminada](#d-aplicación-de-asistente-digital-predeterminada)
   - [E. Optimización de Batería (Segundo Plano Sin Restricciones)](#e-optimización-de-batería-segundo-plano-sin-restricciones)
4. [Configuración Rápida por Comandos ADB (Para Desarrolladores)](#4-configuración-rápida-por-comandos-adb-para-desarrolladores)
5. [Verificación de Estado](#5-verificación-de-estado)

---

## 1. Instalación del APK

### Opción A: Instalación Vía ADB (Recomendada si el móvil está conectado por USB/Wi-Fi)
Desde la terminal en el directorio del proyecto:

```bash
# Instalar APK de depuración reemplazando versiones previas y otorgando permisos básicos de tiempo de ejecución
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
```

### Opción B: Instalación Manual en el Teléfono
1. Transfiere el archivo `app-debug.apk` al almacenamiento interno del móvil (vía cable USB, Google Drive, WhatsApp o Telegram).
2. Abre el **Administrador de Archivos** o la app de descargas en el teléfono y pulsa sobre el archivo `.apk`.
3. Si el sistema muestra el aviso *"Por motivos de seguridad, tu teléfono no tiene permiso para instalar aplicaciones desconocidas de esta fuente"*:
   - Pulsa en **Ajustes**.
   - Activa el interruptor **Permitir desde esta fuente**.
   - Regresa y presiona **Instalar**.

---

## 2. Permisos de Tiempo de Ejecución Estándar

Al abrir la aplicación por primera vez, o ingresando a:
> **Ajustes de Android ➔ Aplicaciones ➔ Ver todas las aplicaciones ➔ MyVU Client ➔ Permisos**

Asegúrate de otorgar los siguientes permisos en estado **Permitido**:

| Permiso | Nombre Técnico | Propósito |
|---|---|---|
| **Dispositivos cercanos** | `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` | Enlace Bluetooth SPP (RFCOMM) y BLE con las gafas Myvu. |
| **Ubicación** | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Requerido por Android para escaneo BLE, reporte meteorológico local y navegación GPS HUD. Configurar en *"Permitir todo el tiempo"* o *"Permitir solo mientras la app está en uso"*. |
| **Contactos** | `READ_CONTACTS` | Búsqueda difusa y coincidencia de contactos para comandos de llamadas y envío de mensajes (WhatsApp/Telegram/SMS). |
| **Teléfono** | `CALL_PHONE` | Realización de llamadas directas desde las gafas mediante comandos de voz. |
| **SMS** | `SEND_SMS` | Envío directo de mensajes de texto en segundo plano sin requerir interacción visual. |
| **Cámara** | `CAMERA` | Control de la linterna del móvil mediante comandos de voz y escaneo OCR/visual. |
| **Micrófono** | `RECORD_AUDIO` | Grabación de notas de voz, comandos de voz para el asistente IA y transcripción local/nube. |
| **Calendario** | `READ_CALENDAR`, `WRITE_CALENDAR` | Consulta y programación de eventos y recordatorios sincronizados con el HUD. |
| **Notificaciones** | `POST_NOTIFICATIONS` | Alertas del sistema y mantenimiento visible del servicio persistente en primer plano (`MyvuService`). |

---

## 3. Permisos Especiales del Sistema (Cruciales)

Para que todas las funciones avanzadas operen automáticamente con el teléfono en el bolsillo o bloqueado, es **obligatorio** configurar estas 5 opciones especiales de Android:

### A. Acceso a Notificaciones (Mirroring en HUD)
Permite que las notificaciones de llamadas entrantes, mensajes de WhatsApp, correos y alertas se proyecten en tiempo real en la pantalla de las gafas.

1. Ve a **Ajustes ➔ Notificaciones ➔ Acceso a notificaciones** (o busca *"Acceso a notificaciones"* en la barra de búsqueda de Ajustes).
2. Localiza **MyVU Client** (`MirrorNotificationListener`).
3. Activa el interruptor y confirma en **Permitir**.

### B. Servicio de Accesibilidad (Auto-Send de Mensajería)
Permite que las gafas redacten y envíen mensajes de WhatsApp y Telegram de manera 100% autónoma presionando el botón de enviar sin que el usuario tenga que tocar la pantalla.

1. Ve a **Ajustes ➔ Accesibilidad ➔ Aplicaciones instaladas** (o *Servicios descargados* según la marca del fabricante: Xiaomi, Samsung, Motorola, Pixel).
2. Pulsa en **MyVU Auto Send Service** (`AutoSendAccessibilityService`).
3. Activa la opción **Usar MyVU Auto Send Service** y acepta los permisos solicitados.

> ⚠️ **Solución para Android 13 y Android 14+ ("Ajuste restringido")**:
> Si al intentar activar la accesibilidad aparece el aviso *"Ajuste restringido: Por razones de seguridad, este ajuste no está disponible"*:
> 1. Ve a **Ajustes ➔ Aplicaciones ➔ MyVU Client**.
> 2. Pulsa el **menú de 3 puntos** vertical en la esquina superior derecha.
> 3. Toca **Permitir ajustes restringidos** (te solicitará tu huella o PIN).
> 4. Regresa a **Accesibilidad** y activa el servicio normalmente.

### C. Desbloqueo Extendido / Smart Lock (Manos Libres en Bolsillo)
> [!IMPORTANT]
> **¿Por qué es necesario?**
> Por diseño estricto de seguridad de Android, aplicaciones de terceros como WhatsApp no pueden mostrar su interfaz de envío sobre una pantalla de bloqueo protegida por PIN o huella digital. Para que el envío funcione **con el teléfono dentro del bolsillo sin pedir PIN**:

1. Ve a **Ajustes ➔ Seguridad y privacidad ➔ Más ajustes de seguridad ➔ Desbloqueo extendido** (en algunas versiones: *Smart Lock*).
2. Ingresa tu PIN, patrón o contraseña de seguridad.
3. Pulsa en **Dispositivos de confianza** (*Trusted devices*).
4. Selecciona **Añadir dispositivo de confianza**.
5. Escoge las gafas **MYVU** (o tu pulsera/reloj inteligente Bluetooth de uso diario).
6. Acepta y confirma.

*Resultado*: Mientras las gafas estén vinculadas y conectadas por Bluetooth, el teléfono se mantiene desbloqueado de forma segura en tu bolsillo. Cuando pidas enviar un mensaje o abrir una app, el sistema despachará la acción instantáneamente sin bloquearse pidiendo PIN.

### D. Aplicación de Asistente Digital Predeterminada
Permite que MyVU Client tome el rol de asistente de voz del sistema (sustituyendo a Google Assistant / Gemini Assistant) para responder ante pulsaciones prolongadas de botones de auriculares o disparadores del sistema operativo.

1. Ve a **Ajustes ➔ Aplicaciones ➔ Aplicaciones predeterminadas**.
2. Pulsa en **Aplicación de asistente digital** (o *Asistente y entrada de voz*).
3. Toca en **Aplicación de asistencia predeterminada** y selecciona **MyVU Client**.

### E. Optimización de Batería (Segundo Plano Sin Restricciones)
Evita que el sistema de gestión de energía agresivo de Android cierre el servicio `MyvuService` o desconecte el socket RFCOMM Bluetooth cuando el teléfono entra en reposo profundo (*Doze mode*).

1. Ve a **Ajustes ➔ Aplicaciones ➔ MyVU Client ➔ Batería**.
2. Cambia la selección de *Optimizado* a **Sin restricciones** (*Unrestricted*).
3. Si tu dispositivo es Xiaomi/POCO (MIUI/HyperOS):
   - Activa también **Inicio automático** en la información de la aplicación.
   - En *Ahorro de batería*, selecciona **Sin restricciones**.

---

## 4. Configuración Rápida por Comandos ADB (Para Desarrolladores)

Si configuras el dispositivo mediante depuración USB, puedes otorgar de golpe la mayoría de los permisos y accesos especiales con el siguiente bloque de comandos:

```bash
# 1. Otorgar permisos estándar de tiempo de ejecución
adb shell pm grant com.myvu.client android.permission.BLUETOOTH_CONNECT
adb shell pm grant com.myvu.client android.permission.BLUETOOTH_SCAN
adb shell pm grant com.myvu.client android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.myvu.client android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant com.myvu.client android.permission.READ_CONTACTS
adb shell pm grant com.myvu.client android.permission.CALL_PHONE
adb shell pm grant com.myvu.client android.permission.SEND_SMS
adb shell pm grant com.myvu.client android.permission.CAMERA
adb shell pm grant com.myvu.client android.permission.RECORD_AUDIO
adb shell pm grant com.myvu.client android.permission.READ_CALENDAR
adb shell pm grant com.myvu.client android.permission.WRITE_CALENDAR
adb shell pm grant com.myvu.client android.permission.POST_NOTIFICATIONS

# 2. Habilitar Acceso a Notificaciones (Mirroring)
adb shell cmd notification set_notification_listener_access_granted --package com.myvu.client --user 0 true

# 3. Habilitar Servicio de Accesibilidad (Auto-Send)
adb shell settings put secure enabled_accessibility_services com.myvu.client/com.myvu.client.service.AutoSendAccessibilityService
adb shell settings put secure accessibility_enabled 1

# 4. Desactivar optimización de batería (Segundo plano ilimitado)
adb shell dumpsys deviceidle whitelist +com.myvu.client
```

---

## 5. Verificación de Estado

Una vez completada la configuración:
1. Abre la aplicación **MyVU Client** en el teléfono.
2. Comprueba que el estado de conexión con las gafas figure en **Conectado (SPP/BLE)**.
3. Apaga la pantalla del teléfono.
4. Di a las gafas: *"Enciende la linterna"* ➔ Debe encender el flash al instante sin encender la pantalla del teléfono.
5. Di a las gafas: *"Envía un mensaje de WhatsApp a [Contacto] diciendo que llegaré en 5 minutos"* ➔ Debe despachar el mensaje en WhatsApp y presionar enviar automáticamente.
