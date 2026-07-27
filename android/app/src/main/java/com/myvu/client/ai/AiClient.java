package com.myvu.client.ai;

import java.io.IOException;

/**
 * Answers a recognized (or typed) question -- one question in, one answer out.
 *
 * One implementation per provider (see {@link AiProvider}). Clients are cheap,
 * single-turn objects: {@link AiConversation} builds a fresh one for every
 * question, so Settings edits apply without a reconnect.
 */
public interface AiClient {

    /**
     * The shipped default, deliberately provider-neutral. Answers are spoken
     * aloud on a pair of glasses, so length and formatting matter more than
     * usual -- markdown, lists and emoji are read out as literal junk. Public so
     * the Settings screen can show it as the editable text and restore it with
     * "Reset to default".
     */
    String DEFAULT_SYSTEM_PROMPT =
            "Eres un asistente de voz integrado en unas gafas de realidad aumentada (MYVU). "
            + "Responde SIEMPRE en Español de Colombia (es-CO), usando un tono natural, directo y cercano. "
            + "Retorna ÚNICAMENTE texto plano sin ningún formato markdown (sin asteriscos, sin negritas, sin viñetas, sin encabezados, sin código ni emoticones), "
            + "ya que la respuesta se leerá en voz alta y se mostrará en una pantalla HUD pequeña.\n"
            + "Responde en 1 o 2 oraciones cortas. Si no sabes algo, dilo brevemente en lugar de adivinar.\n"
            + "Si el usuario solicita controlar funciones del teléfono, realizar llamadas o enviar mensajes, responde amablemente Y AÑADE SIEMPRE la etiqueta de acción correspondiente al final:\n"
            + "- Ajustar volumen: ACTION:VOLUME=número (0-15)\n"
            + "- Reproducir/Pausar música: ACTION:MEDIA_PLAY\n"
            + "- Siguiente canción: ACTION:MEDIA_NEXT\n"
            + "- Canción anterior: ACTION:MEDIA_PREV\n"
            + "- Enviar WhatsApp: ACTION:WHATSAPP=nombre de contacto o teléfono: texto del mensaje\n"
            + "- Enviar Telegram: ACTION:TELEGRAM=nombre de contacto o teléfono: texto del mensaje\n"
            + "- Llamar a contacto o número: ACTION:CALL=nombre del contacto o número de teléfono";

    /** False disables answering because required provider settings are missing. */
    boolean isConfigured();

    /** Returns the answer text, or throws with a message worth showing. */
    String ask(String question) throws IOException;
}
