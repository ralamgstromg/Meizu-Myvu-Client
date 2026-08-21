# Documentación del Skill Engine y Chat IA en Android (Aura)

## Arquitectura de Skills

La aplicación Android para los lentes Meizu MYVU integra un motor de habilidades (**Skill Engine**) dinámico e interpretable tanto por el agente **Aura** como por la interfaz de usuario en el teléfono.

### Componentes Principales:
1. **`Skill.kt`**: Define el modelo de datos (`Skill`, `SkillParameter`, `SkillResult`, `SkillHandler`).
2. **`SkillParser.kt`**: Lee la definición en Markdown/YAML Frontmatter de los archivos `SKILL.md`.
3. **`SkillLoader.kt`**: Carga dinámicamente las habilidades disponibles desde `assets/skills/built-in/...`.
4. **`SkillRegistry.kt`**: Mantiene el registro de habilidades activas y genera el *Prompt* de sistema para Aura.
5. **`SkillExecutor.kt`**: Intercepta etiquetas `[SKILL: id_habilidad {...}]` en la respuesta de la IA y ejecuta la lógica nativa en Kotlin.

---

## Interfaz Productiva de Chat IA (`ChatActivity`)

Se ha rediseñado la interfaz del Chat para maximizar la productividad y permitir un acceso rápido a todas las habilidades:

### Envío Instantáneo por Tecla Enter / Intro (Sin botón de envío):
1. **Ajuste de `inputType`**: Removido `textMultiLine` en favor de `android:inputType="textCapSentences"` con `android:imeOptions="actionSend"`. Esto transforma la tecla de retorno del teclado virtual Android en el botón **Enviar**.
2. **Combinación Triple de Intercepción**:
   - **`OnEditorActionListener`**: Captura las acciones `IME_ACTION_SEND`, `IME_ACTION_DONE`, `IME_ACTION_GO`, `IME_NULL` y la tecla Enter del teclado virtual.
   - **`OnKeyListener`**: Captura la tecla Enter de teclados físicos (Bluetooth/USB).
   - **`TextWatcher` Interceptor**: Detecta la inserción de cualquier carácter de salto de línea `\n` en cualquier teclado secundario, limpia el texto e invoca `sendUserQuery(...)` inmediatamente.

### Barra de Acceso Rápido a Skills (Quick Skills Toolbar):
- **`⚡ Skills`**: Despliega un menú emergente con la lista completa de todas las habilidades registradas en el motor.
- **Chips de Acceso Directo**:
  - 📞 **Llamar**: `Llamar a [contacto]`
  - 💬 **WhatsApp**: `Enviar whatsapp a [contacto]`
  - ✉️ **Email**: `Enviar email a [destinatario]`
  - ✈️ **Telegram**: `Enviar telegram a [contacto]`
  - 🔍 **Google**: `Buscar en Google [tema]`
  - 📖 **Wiki**: `Buscar en Wikipedia [tema]`
  - ☀️ **Clima**: `Clima en [ciudad]`
  - 💵 **Divisas**: `Convertir 100 USD a COP`
  - 📝 **Nota**: `Crear nota con titulo: [titulo]`
  - ⏰ **Recordatorio**: `Recordar en 30 minutos: [asunto]`
  - 🎙️ **Grabar IA**: `Iniciar grabacion de voz IA`
