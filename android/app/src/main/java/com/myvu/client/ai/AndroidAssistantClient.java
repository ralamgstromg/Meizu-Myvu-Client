package com.myvu.client.ai;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.myvu.client.core.LogBus;

/**
 * Delegates voice queries directly to the Gemini App or native Android Assistant.
 */
public class AndroidAssistantClient implements AiClient {

    private static final String GEMINI_PKG_BARD = "com.google.android.apps.bard";
    private static final String GEMINI_PKG_ASSISTANT = "com.google.android.apps.googleassistant";

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
        if (question == null || question.trim().isEmpty()) {
            return "Solicitud vacía.";
        }

        // 1. Launch Google Assistant voice command directly
        try {
            Intent voiceIntent = new Intent(Intent.ACTION_VOICE_COMMAND);
            voiceIntent.setPackage("com.google.android.googlequicksearchbox");
            voiceIntent.putExtra(SearchManager.QUERY, question);
            voiceIntent.putExtra("query", question);
            voiceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(voiceIntent);
            LogBus.log("launched Google Assistant voice command for: " + question);
            return "Abriendo Asistente de Google para: " + question;
        } catch (Exception e) {
            LogBus.warn("could not launch Google Assistant voice command: " + e.getMessage());
        }

        // 2. Fallback to generic ACTION_VOICE_COMMAND
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
