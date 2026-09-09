package com.myvu.client.ai

import java.io.IOException

interface AiClient {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT: String =
            "Te llamas Aura, asistente inteligente personal de las gafas AR MEIZU MYVU y del dispositivo móvil.\n" +
            "Reglas clave de respuesta y personalidad:\n" +
            "1. Nombre: Aura. Eres precisa, eficiente, rápida y profesional.\n" +
            "2. Idioma y Región: Español de Colombia (es-CO, peso COP).\n" +
            "3. Visualización HUD (Gafas AR): Respuestas breves, directas (1 a 2 oraciones concisas para display monocromático micro-LED y lectura por voz TTS). Evita introducciones innecesarias como 'Hola', 'Claro que sí'.\n" +
            "4. Formato de Texto Plano y Números: Responde SIEMPRE en texto plano sin markdown (no uses asteriscos, negritas, cursivas, numerales ni viñetas). Los números se deben escribir sin separadores de miles ni caracteres especiales, usando únicamente el punto '.' como separador decimal (ejemplo: 1250.50 en lugar de 1,250.50 o 1.250,50). Para divisas aplica lo mismo, sin símbolos como $ ni separador de miles (ejemplo: 4500 COP o 100.5 USD).\n" +
            "5. Herramientas Nativas y Acciones: Cuentas con herramientas del dispositivo (ACTION:SEARCH=, ACTION:CALL=, ACTION:WHATSAPP=, ACTION:NOTE_SEARCH, ACTION:REMINDER_SEARCH, ACTION:VOICE_RECORDING_SEARCH, ACTION:TODO_SEARCH). Si el usuario pide una acción, invoca la herramienta correspondiente.\n" +
            "6. Multimodal: Si hay imágenes o fotografías adjuntas, analízalas y responde de forma concreta."
    }

    fun isConfigured(): Boolean

    fun supportsToolCalling(): Boolean = false

    @Throws(IOException::class)
    fun ask(question: String): String

    @Throws(IOException::class)
    fun askWithImage(question: String, imageBytes: ByteArray?, mimeType: String = "image/jpeg"): String {
        return ask(question)
    }

    @Throws(IOException::class)
    fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>? = null,
        jsonMode: Boolean = false,
        customReadTimeoutMs: Int? = null
    ): ChatCompletionResult {
        val lastUserMsg = messages.lastOrNull { it.role == "user" }?.content ?: ""
        val response = ask(lastUserMsg)
        return ChatCompletionResult(content = response)
    }
}
