package com.myvu.client.ai;

/**
 * Determines security policy and confirmation requirements for native AI actions.
 */
public final class ActionPolicy {
    private ActionPolicy() {}

    /**
     * Returns true if the action is sensitive (e.g. placing calls, sending messages)
     * and should require confirmation before execution.
     */
    public static boolean isSensitive(ActionCommand.Type type) {
        if (type == null) return false;
        switch (type) {
            case CALL:
            case WHATSAPP:
            case TELEGRAM:
            case SUMMARY:
                return true;
            case VOLUME:
            case OPENTUNE_PLAY:
            case OPENTUNE_SEARCH:
            case MEDIA_PLAY_PAUSE:
            case MEDIA_NEXT:
            case MEDIA_PREV:
            case SEARCH:
            case ALARM:
            case TIMER:
            case NAVIGATE:
            case CALENDAR:
            case NOTE:
            case UNKNOWN:
            default:
                return false;
        }
    }
}
