package com.myvu.client.ai;

/**
 * Represents a parsed native action command requested by AI voice response.
 */
public class ActionCommand {
    public enum Type {
        VOLUME,
        OPENTUNE_PLAY,
        OPENTUNE_SEARCH,
        MEDIA_PLAY_PAUSE,
        MEDIA_NEXT,
        MEDIA_PREV,
        WHATSAPP,
        TELEGRAM,
        CALL,
        SEARCH,
        ALARM,
        TIMER,
        NAVIGATE,
        CALENDAR,
        NOTE,
        SUMMARY,
        UNKNOWN
    }

    private final Type type;
    private final String rawParam;
    private final boolean requiresConfirmation;

    public ActionCommand(Type type, String rawParam, boolean requiresConfirmation) {
        this.type = type;
        this.rawParam = rawParam != null ? rawParam.trim() : "";
        this.requiresConfirmation = requiresConfirmation;
    }

    public Type getType() { return type; }
    public String getRawParam() { return rawParam; }
    public boolean requiresConfirmation() { return requiresConfirmation; }
}
