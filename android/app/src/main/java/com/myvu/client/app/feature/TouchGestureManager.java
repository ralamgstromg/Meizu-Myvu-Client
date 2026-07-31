package com.myvu.client.app.feature;

import android.content.Context;

import com.myvu.client.core.LogBus;
import com.myvu.client.core.Prefs;

/**
 * Handles mapping and dispatching of single touchpad / hardware button (code: 3)
 * gesture actions received from the Meizu MYVU glasses.
 */
public final class TouchGestureManager {
    public static final String ACTION_NONE = "none";
    public static final String ACTION_AI_ASSISTANT = "ai_assistant";
    public static final String ACTION_WEATHER_SYNC = "weather_sync";
    public static final String ACTION_TOGGLE_MIRROR = "toggle_mirror";
    public static final String ACTION_MEDIA_PLAY_PAUSE = "media_play_pause";

    public interface ActionExecutor {
        void executeAiAssistant(int code);
        void executeWeatherSync();
        void executeToggleMirror();
        void executeMediaPlayPause();
    }

    private TouchGestureManager() {}

    /**
     * Evaluates the configured long-press/deep-touch action and executes it immediately via the executor.
     */
    public static void handleTrigger(Context context, int code, ActionExecutor executor) {
        if (executor == null) return;

        String action = context != null ? Prefs.touchpadLongPressAction(context) : ACTION_AI_ASSISTANT;
        LogBus.log("Touchpad / Button trigger received (code=" + code + ") -> Action: " + action);

        switch (action) {
            case ACTION_WEATHER_SYNC:
                executor.executeWeatherSync();
                break;
            case ACTION_TOGGLE_MIRROR:
                executor.executeToggleMirror();
                break;
            case ACTION_MEDIA_PLAY_PAUSE:
                executor.executeMediaPlayPause();
                break;
            case ACTION_AI_ASSISTANT:
            default:
                executor.executeAiAssistant(code);
                break;
            case ACTION_NONE:
                break;
        }
    }
}
