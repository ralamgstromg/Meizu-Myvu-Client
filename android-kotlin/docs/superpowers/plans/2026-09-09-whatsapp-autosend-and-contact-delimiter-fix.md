# Plan de Mejora: Corrección de Separación Contacto/Mensaje en WhatsApp y Envío Automático Manos Libres

## Contexto y Diagnóstico del Log (14:18 - 14:21)

Del análisis de `/home/rcastro/Descargas/myvu_client_log.txt`:

1. **TRM Fast-Path**: Consulta `"¿Cuál es la TRM de hoy?"` ejecutada en **446ms** con Superfinanciera oficial (`1 USD = 3116.47 COP`). Funcionamiento impecable tras los ajustes anteriores.
2. **Falsa Inclusión del Saludo en el Nombre del Contacto**:
   - Audio escuchado por IA: `"Envía mensaje de WhatsApp a Matías Castro. Hola hijo, ¿cómo vas? ¿Cómo te fue?"`
   - Log del router: `VoiceActionRouter -> Fast-Path WhatsApp: 'Matías Castro. Hola hijo, ¿cómo vas? ¿Cómo te fue?'`
   - Log de emparejamiento: `ContactHelper -> Best fuzzy match 'Matías Castro. Hola hijo' -> 'Matías Castro Nuevo' (314 2773279, score: 300)`
   - Texto enviado: `voice action -> opened WhatsApp for Matías Castro Nuevo (573142773279) with text: ¿cómo vas? ¿Cómo te fue?`
   - **Causa Raíz**:
     En [`PhoneActionExecutor.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/PhoneActionExecutor.kt#L511), el parser separaba indiscriminadamente por comas: `cleanRaw.contains(",")`.
     Como el mensaje decía `"Hola hijo, ¿cómo vas?"`, la coma partió el texto después de `"Hola hijo"`.
     El destinatario buscado fue `"Matías Castro. Hola hijo"`, perdiendo la coincidencia exacta con `"Matías Castro"` y cayendo a un contacto secundario `"Matías Castro Nuevo"`, perdiendo además el saludo inicial en el mensaje.
3. **El Mensaje no se Envió Automáticamente**:
   - El usuario reportó: *"solicite enviar mensaje de whatsapp a matias castro y no lo hizo."*
   - **Causa Raíz A**:
     En [`AutoSendAccessibilityService.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/AutoSendAccessibilityService.kt#L56), la variable `shouldAutoSendWhatsApp` se establecía en `false` en el primer evento de ventana, incluso si WhatsApp aún no había renderizado el botón de envío (condición de carrera).
   - **Causa Raíz B**:
     `findAccessibilityNodeInfosByText("enviar")` busca únicamente texto plano en la propiedad `text` del nodo. En WhatsApp, el botón de envío es un `ImageButton` cuyo texto es nulo y la etiqueta está en `contentDescription="Enviar"`, por lo que la búsqueda por texto no encontraba el botón.
   - **Causa Raíz C**:
     Si el usuario no tiene habilitado el Servicio de Accesibilidad en `Ajustes -> Accesibilidad -> MYVU Auto-Send Assistant`, Android por seguridad solo abre WhatsApp con el texto precargado en el campo de texto pero no puede pulsar "Enviar". Se debe orientar al usuario para activarlo y encender la pantalla con `LockScreenHelper.wakeUpScreen` para permitir la interacción en primer plano.

---

## Fases de Implementación

### Fase 1: Separador Inteligente de Destinatario y Mensaje
- Archivos: [`PhoneActionExecutor.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/ai/PhoneActionExecutor.kt), [`ContactHelper.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/core/ContactHelper.kt)
- Crear función robusta `extractRecipientAndMessage`:
  1. Separación por delimitador de frase `.` (punto seguido de espacio/mayúscula) con validación de contacto en el prefijo.
  2. Separación por conectores gramaticales (`dile que`, `que diga`, `con el mensaje`).
  3. Separación por coma `,` ÚNICAMENTE si la parte previa a la coma es un contacto válido en la libreta.
  4. Ventana deslizante (1 a 4 palabras) contra `ContactHelper` para separar contacto de mensaje sin signos de puntuación.
  5. Limpieza rigurosa de puntuación residual en el nombre del contacto (`.`, `?`, `!`, `,`).

### Fase 2: Robustecer AutoSendAccessibilityService para WhatsApp y Telegram
- Archivo: [`AutoSendAccessibilityService.kt`](file:///home/rcastro/Documentos/negex/Meizu-Myvu-Client/android-kotlin/app/src/main/java/com/myvu/client/service/AutoSendAccessibilityService.kt)
- Búsqueda en árbol por `contentDescription` (`"enviar"`, `"send"`), View IDs (`com.whatsapp:id/send`, `conversation_send_button`) y `text`.
- Mantener activa la bandera de auto-envío con reintentos programados (500ms, 1000ms, 1500ms, 2500ms) hasta que el clic sea exitoso o expire el tiempo límite de 7 segundos.
- Solo desactivar `shouldAutoSendWhatsApp` cuando el botón haya sido presionado exitosamente.
- Encender la pantalla con `LockScreenHelper.wakeUpScreen(context)` antes de lanzar el Intent de WhatsApp.

### Fase 3: Retroalimentación y Detección de Estado del Servicio de Accesibilidad
- Informar claramente en el log y por voz/HUD si el servicio de accesibilidad está apagado, indicando al usuario que active el "MYVU Auto-Send Assistant" en Accesibilidad de Android para envío 100% manos libres.

### Fase 4: Pruebas Unitarias y Verificación
- Crear pruebas unitarias para `extractRecipientAndMessage` cubriendo:
  - `"Matías Castro. Hola hijo, ¿cómo vas? ¿Cómo te fue?"`
  - `"Raúl Castro."`
  - `"Matías Castro dile que voy saliendo"`
  - `"Matias Castro hola hijo"`
- Ejecutar `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.

---

## Criterios de Éxito
1. `"Envía mensaje de WhatsApp a Matías Castro. Hola hijo, ¿cómo vas? ¿Cómo te fue?"` extrae `"Matías Castro"` como destinatario y `"Hola hijo, ¿cómo vas? ¿Cómo te fue?"` como mensaje.
2. El contacto encontrado es el más coincidente sin absorber palabras del mensaje.
3. `AutoSendAccessibilityService` detecta el botón de envío mediante `contentDescription` o View ID y lo pulsa automáticamente cuando el servicio está activo.
4. 100% de pruebas unitarias pasando.
