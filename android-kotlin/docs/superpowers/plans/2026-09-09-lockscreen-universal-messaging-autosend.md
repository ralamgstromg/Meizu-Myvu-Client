# Plan de Mejora: Desbloqueo de Pantalla y Envío Automático Universal Manos Libres

## Contexto y Diagnóstico del Log (14:35 - 14:37)

Del análisis de `/home/rcastro/Descargas/myvu_client_log.txt`:

1. **Separación de Contacto y Mensaje (100% Exitosa)**:
   - Audio escuchado por IA: `"Envía mensaje de whatsapp a Matías Castro. Hola socio, ¿cómo estás? ¿Cómo te fue?"`
   - Parser [`ContactHelper.extractRecipientAndMessage`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/core/ContactHelper.kt):
     - Destinatario: `"Matías Castro"` (coincidencia con `"Matías Castro Nuevo"`, score 1045)
     - Mensaje limpio preservado: `"Hola socio, ¿cómo estás? ¿Cómo te fue?"`
   - La separación implementada en la fase previa funcionó a la perfección.

2. **Causa Raíz de por qué no se envió automáticamente**:
   - Log 14:36:35.422: `LockScreenHelper: Screen awakened for agent interaction (MYVU:WhatsApp)`
   - Log 14:36:36.109: `AutoSendAccessibilityService -> Triggered auto-send for WhatsApp (active=true)`
   - Log 14:36:36.127: `voice action -> opened WhatsApp for Matías Castro Nuevo (573142773279) with text: Hola socio, ¿cómo estás? ¿Cómo te fue?`
   - Log 14:36:43.110: `AutoSendAccessibilityService -> WhatsApp auto-send window timed out`
   - **Causas Técnicas Identificadas**:
     1. **Bloqueo por Keyguard (Pantalla Bloqueada)**: El teléfono estaba en el bolsillo con bloqueo de pantalla. En Android, las aplicaciones de terceros (WhatsApp, Telegram, Mensajes) **no** tienen permiso para desplegarse sobre la pantalla bloqueada (`showWhenLocked` es falso en WhatsApp). Por tanto, la ventana activa en accesibilidad seguía siendo la pantalla de bloqueo (SystemUI Keyguard) y no WhatsApp.
     2. **Expiración Prematura de la Ventana de Auto-Envío (7 segundos)**: `AutoSendAccessibilityService` solo esperaba 7 segundos. Como el teléfono estaba bloqueado, pasaron los 7 segundos y la bandera `shouldAutoSendWhatsApp` se apagó a las 14:36:43.
     3. **Desbloqueo Tardío sin Re-Activación**: Cuando el usuario sacó el móvil y lo desbloqueó manualmente, WhatsApp apareció en pantalla, pero el servicio de accesibilidad ya no estaba buscando el botón de envío porque la ventana de tiempo ya había vencido, obligando al usuario a pulsar "Enviar" manualmente.
     4. **Filtro de Paquetes en Accesibilidad Limitado**: [`accessibility_service_config.xml`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/res/xml/accessibility_service_config.xml) tenía `android:packageNames="com.whatsapp,com.whatsapp.w4b,org.telegram.messenger"`, ignorando por completo cualquier otra app de mensajería (Google Messages, Samsung Messages, Signal, etc.).

---

## Arquitectura de Solución: Envío Universal Manos Libres

### Fase 1: Trampolín de Desbloqueo (`SendTrampolineActivity`)
- Crear [`SendTrampolineActivity.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ui/SendTrampolineActivity.kt):
  - Configurada en `AndroidManifest.xml` con `showWhenLocked="true"`, `turnScreenOn="true"`, tema translúcido y `excludeFromRecents="true"`.
  - Cuando el dispositivo está bloqueado (`LockScreenHelper.isDeviceLocked(context)`):
    - Se despierta la pantalla con `LockScreenHelper.wakeUpScreen`.
    - Se inicia `SendTrampolineActivity` transportando el `targetIntent` (WhatsApp, Telegram, SMS, etc.).
    - Invoca `KeyguardManager.requestDismissKeyguard`:
      - **Con Smart Lock (Gafas MYVU conectadas por Bluetooth o deslizar)**: El sistema desbloquea inmediatamente sin pedir PIN, lanza la app de mensajería al frente y cierra el trampolín.
      - **Con PIN/Huella**: Presenta de inmediato el prompt de autenticación biométrica/PIN en la pantalla.
      - Al desbloquearse (`onDismissSucceeded`), lanza la app de mensajería y se finaliza.

### Fase 2: Detección de Desbloqueo (`ACTION_USER_PRESENT`) y Ventana Dinámica
- En [`AutoSendAccessibilityService.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/AutoSendAccessibilityService.kt):
  - Extender la ventana de espera a **45 segundos** cuando el dispositivo esté bloqueado (y 15 segundos cuando esté desbloqueado).
  - Registrar dinámicamente un `BroadcastReceiver` para `Intent.ACTION_USER_PRESENT`.
  - En el instante exacto en que el usuario desbloquea el móvil (con huella, rostro o PIN), el receiver detecta el evento y dispara una ráfaga inmediata de intentos de clic (a los 150ms, 400ms, 800ms, 1500ms, 2500ms).
  - De este modo, tan pronto como el móvil se desbloquee y WhatsApp/Telegram/Mensajes suba al frente, el servicio presiona "Enviar" automáticamente en milisegundos, sin que el usuario tenga que tocar el botón.

### Fase 3: Soporte Universal para Cualquier App de Mensajería
- En [`accessibility_service_config.xml`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/res/xml/accessibility_service_config.xml):
  - Eliminar el atributo `android:packageNames` para permitir que el servicio de accesibilidad escuche eventos de cualquier app de mensajería configurada.
- En [`AutoSendAccessibilityService.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/AutoSendAccessibilityService.kt):
  - Generalizar `triggerAutoSend(targetPackage: String? = null, isLocked: Boolean)` para manejar:
    - WhatsApp (`com.whatsapp`, `com.whatsapp.w4b`)
    - Telegram (`org.telegram.messenger`, `org.telegram.messenger.web`)
    - Google Messages (`com.google.android.apps.messaging`)
    - Samsung Messages (`com.samsung.android.messaging`)
    - Signal (`org.thoughtcrime.securesms`)
    - App genérica de SMS / mensajería
  - Expandir el detector BFS de botón de envío:
    - IDs: `send`, `send_button`, `conversation_send_button`, `send_message_button`, `send_message_button_icon`, `compose_send_button`, `btn_send`, `action_send`.
    - Descripciones: `"enviar"`, `"send"`, `"enviar sms"`, `"enviar mms"`, `"enviar mensaje"`, `"send message"`.
    - Textos: `"enviar"`, `"send"`.
    - **Protección anti falsos positivos**: Excluir estrictamente nodos de notas de voz o micrófono (`"voz"`, `"voice"`, `"audio"`, `"grabar"`, `"record"`, `"mic"`).
    - **Fallback de Gesto**: Si `performAction(ACTION_CLICK)` no responde, usar `dispatchGesture()` en Android 7.0+ para simular un toque táctil directo en las coordenadas centrales del botón.

### Fase 4: Integración en `PhoneActionExecutor` y Rutas de Mensajería
- En [`PhoneActionExecutor.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/PhoneActionExecutor.kt):
  - Actualizar `openWhatsApp`, `openTelegram` y `sendSms` para usar el trampolín si el teléfono está bloqueado y disparar `AutoSendAccessibilityService.triggerAutoSend(...)`.
  - Asegurar que para SMS con permiso `SEND_SMS`, el envío ocurra 100% en segundo plano vía `SmsManager` sin requerir encendido ni desbloqueo de pantalla.

### Fase 5: Verificación y Pruebas Unitarias
- Ejecutar tests existentes en `VoiceActionRouterTest` y `ContactHelperTest`.
- Crear pruebas unitarias para `AutoSendAccessibilityService` y lógica de detección de botones de mensajería.
- Compilar con `./gradlew assembleDebug` y verificar logs.

---

## Criterios de Éxito
1. Al dictar envío de WhatsApp con el móvil bloqueado y Smart Lock activo, se desbloquea, abre WhatsApp y pulsa "Enviar" de forma 100% autónoma.
2. Si el móvil requiere huella/PIN, al desbloquearlo el servicio detecta `ACTION_USER_PRESENT` y pulsa "Enviar" al instante.
3. El envío automático funciona no solo para WhatsApp, sino para Telegram, Google Messages y cualquier app de mensajería compatible.
