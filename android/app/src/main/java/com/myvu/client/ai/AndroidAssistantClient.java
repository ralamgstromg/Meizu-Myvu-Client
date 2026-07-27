package com.myvu.client.ai;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.myvu.client.core.LogBus;

/**
 * Delegates voice queries directly to the Gemini App or native Android Assistant.
 */
public class AndroidAssistantClient implements AiClient {

    private final Context context;

    public AndroidAssistantClient(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String ask(String question) {
        // 1. Dispatch KEYCODE_VOICE_ASSIST to system AudioManager.
        // This is the standard Bluetooth headset system trigger that opens Google Assistant or Gemini in active listening mode on Android.
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                long now = SystemClock.uptimeMillis();
                KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, 0);
                KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOICE_ASSIST, 0);
                am.dispatchMediaKeyEvent(down);
                am.dispatchMediaKeyEvent(up);
                LogBus.log("dispatched KEYCODE_VOICE_ASSIST for system Voice Assistant (Google/Gemini)");
            }
        } catch (Exception e) {
            LogBus.warn("could not dispatch KEYCODE_VOICE_ASSIST: " + e.getMessage());
        }

        // 2. Launch Google Assistant voice command Intent as fallback
        try {
            Intent voiceIntent = new Intent(Intent.ACTION_VOICE_COMMAND);
            voiceIntent.setPackage("com.google.android.googlequicksearchbox");
            if (question != null && !question.trim().isEmpty()) {
                voiceIntent.putExtra(SearchManager.QUERY, question);
                voiceIntent.putExtra("query", question);
            }
            voiceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(voiceIntent);
            LogBus.log("launched Google Assistant voice command for: " + question);
            return "Activando Asistente de Google / Gemini por defecto...";
        } catch (Exception e) {
            LogBus.warn("could not launch Google Assistant voice command: " + e.getMessage());
        }

        // 3. Fallback to generic ACTION_VOICE_COMMAND
        try {
            Intent voiceIntent = new Intent(Intent.ACTION_VOICE_COMMAND);
            voiceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(voiceIntent);
            LogBus.log("launched generic ACTION_VOICE_COMMAND");
            return "Abriendo Asistente de Voz...";
        } catch (Exception e) {
            LogBus.warn("could not launch generic ACTION_VOICE_COMMAND: " + e.getMessage());
        }

        return "No se pudo abrir el Asistente de Android.";
    }
}
