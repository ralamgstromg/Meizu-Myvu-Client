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

        PackageManager pm = context.getPackageManager();

        // 1. Try launching official Gemini app directly (com.google.android.apps.bard)
        try {
            Intent geminiIntent = pm.getLaunchIntentForPackage(GEMINI_PKG_BARD);
            if (geminiIntent != null) {
                geminiIntent.putExtra(SearchManager.QUERY, question);
                geminiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(geminiIntent);
                LogBus.log("launched Gemini app directly for query: " + question);
                return "Abriendo la app de Gemini: " + question;
            }
        } catch (Exception e) {
            LogBus.warn("could not launch Gemini package directly");
        }

        // 2. Try launching Google Assistant / Gemini launcher (com.google.android.apps.googleassistant)
        try {
            Intent assistantIntent = pm.getLaunchIntentForPackage(GEMINI_PKG_ASSISTANT);
            if (assistantIntent != null) {
                assistantIntent.putExtra(SearchManager.QUERY, question);
                assistantIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(assistantIntent);
                LogBus.log("launched Google Assistant package for query: " + question);
                return "Abriendo Asistente Gemini: " + question;
            }
        } catch (Exception e) {
            LogBus.warn("could not launch Google Assistant package directly");
        }

        // 3. Fallback: VOICE_COMMAND with package explicitly set to Gemini or system voice command
        try {
            Intent voiceIntent = new Intent(Intent.ACTION_VOICE_COMMAND);
            voiceIntent.setPackage(GEMINI_PKG_BARD);
            voiceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(voiceIntent);
            LogBus.log("launched ACTION_VOICE_COMMAND for Gemini");
            return "Abriendo Asistente de Voz Gemini.";
        } catch (Exception e1) {
            try {
                Intent voiceIntent = new Intent(Intent.ACTION_VOICE_COMMAND);
                voiceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(voiceIntent);
                LogBus.log("launched system ACTION_VOICE_COMMAND fallback");
                return "Abriendo Asistente del sistema.";
            } catch (Exception e2) {
                LogBus.error("could not launch voice assistant", e2);
                return "Error al abrir el asistente.";
            }
        }
    }
}
