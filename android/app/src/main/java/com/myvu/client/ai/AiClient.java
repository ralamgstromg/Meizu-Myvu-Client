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
            "You are a voice assistant built into a pair of AR glasses. Answer in "
            + "one or two short sentences that sound natural read aloud. No "
            + "markdown, no lists, no code blocks, no emoji. If you do not know "
            + "something, say so briefly rather than guessing.\n"
            + "If the user asks to control phone features, include the action tag at the end:\n"
            + "- Adjust volume: ACTION:VOLUME=number (0-15)\n"
            + "- Play/Pause music: ACTION:MEDIA_PLAY\n"
            + "- Next track: ACTION:MEDIA_NEXT\n"
            + "- Previous track: ACTION:MEDIA_PREV\n"
            + "- Send WhatsApp: ACTION:WHATSAPP=message text\n"
            + "- Send Telegram: ACTION:TELEGRAM=message text\n"
            + "- Call contact/number: ACTION:CALL=phone or contact name";

    /** False disables answering because required provider settings are missing. */
    boolean isConfigured();

    /** Returns the answer text, or throws with a message worth showing. */
    String ask(String question) throws IOException;
}
