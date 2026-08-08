package com.myvu.client.ai;

import android.content.Context;
import com.myvu.client.app.feature.HudMessageQueue;
import com.myvu.client.core.LogBus;

/**
 * Manages live real-time voice translation pipeline for the MYVU AR HUD.
 */
public class TranslationSession {
    private final Context context;
    private final HudMessageQueue hudQueue = new HudMessageQueue();
    private boolean active = false;
    private String targetLanguage = "es";

    public interface TranslationListener {
        void onTranslationFrame(String translatedText);
    }

    private TranslationListener listener;

    public TranslationSession(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    public void setListener(TranslationListener listener) {
        this.listener = listener;
    }

    public boolean isActive() {
        return active;
    }

    public void start(String targetLang) {
        this.targetLanguage = targetLang != null ? targetLang : "es";
        this.active = true;
        LogBus.log("TranslationSession started (targetLang=" + targetLanguage + ")");
    }

    public void processAudioChunk(byte[] pcmData, String recognizedText) {
        if (!active || recognizedText == null || recognizedText.trim().isEmpty()) return;

        // Queue and dispatch formatted translation frames to HUD
        hudQueue.enqueue(recognizedText);
        while (hudQueue.hasNext()) {
            String textFrame = hudQueue.pollNext();
            if (listener != null) {
                listener.onTranslationFrame(textFrame);
            }
        }
    }

    public void stop() {
        if (!active) return;
        active = false;
        hudQueue.clear();
        LogBus.log("TranslationSession stopped");
    }
}
