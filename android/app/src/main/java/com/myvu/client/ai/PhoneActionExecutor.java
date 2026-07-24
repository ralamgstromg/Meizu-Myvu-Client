package com.myvu.client.ai;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.myvu.client.core.LogBus;

import java.net.URLEncoder;

/**
 * Executes system & phone actions requested by voice via Gemini / AI.
 * Supports volume adjustments, media control, WhatsApp, Telegram, calls, and SMS.
 */
public class PhoneActionExecutor {

    private final Context context;
    private final AudioManager audioManager;

    public PhoneActionExecutor(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * Inspects the AI text for action tags. Executes matching actions and returns clean text
     * suitable for display on the glasses and TTS speech.
     */
    public String processAndExecute(String aiText) {
        if (aiText == null || aiText.isEmpty()) return aiText;

        String lower = aiText.toLowerCase();

        // 1. Volume control
        if (lower.contains("action:volume=")) {
            try {
                String valStr = extractValue(aiText, "ACTION:VOLUME=");
                int vol = Integer.parseInt(valStr);
                setVolume(vol);
            } catch (Exception ignored) {}
        }

        // 2. Media control
        if (lower.contains("action:media_next")) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
        } else if (lower.contains("action:media_prev")) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        } else if (lower.contains("action:media_play")) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        }

        // 3. WhatsApp
        if (lower.contains("action:whatsapp=")) {
            String text = extractValue(aiText, "ACTION:WHATSAPP=");
            openWhatsApp(text);
        }

        // 4. Telegram
        if (lower.contains("action:telegram=")) {
            String text = extractValue(aiText, "ACTION:TELEGRAM=");
            openTelegram(text);
        }

        // 5. Calls / Dialing
        if (lower.contains("action:call=")) {
            String target = extractValue(aiText, "ACTION:CALL=");
            makeCall(target);
        }

        return stripActionTags(aiText);
    }

    public void setVolume(int level) {
        if (audioManager == null) return;
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int target = Math.max(0, Math.min(level, max));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI);
        LogBus.log("voice action -> phone volume set to " + target + "/" + max);
    }

    public void sendMediaKey(int keyCode) {
        if (audioManager == null) return;
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
        KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
        audioManager.dispatchMediaKeyEvent(down);
        audioManager.dispatchMediaKeyEvent(up);
        LogBus.log("voice action -> sent media key " + keyCode);
    }

    public void openWhatsApp(String text) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://api.whatsapp.com/send?text=" + URLEncoder.encode(text, "UTF-8")));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            LogBus.log("voice action -> opened WhatsApp with text: " + text);
        } catch (Exception e) {
            LogBus.error("could not open WhatsApp", e);
        }
    }

    public void openTelegram(String text) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("tg://msg?text=" + URLEncoder.encode(text, "UTF-8")));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            LogBus.log("voice action -> opened Telegram with text: " + text);
        } catch (Exception e) {
            LogBus.error("could not open Telegram", e);
        }
    }

    public void makeCall(String target) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            if (target.matches("^[0-9+#* -]+$")) {
                intent.setData(Uri.parse("tel:" + Uri.encode(target)));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            LogBus.log("voice action -> opened dialer for " + target);
        } catch (Exception e) {
            LogBus.error("could not open dialer", e);
        }
    }

    private String extractValue(String text, String tag) {
        int idx = text.toUpperCase().indexOf(tag.toUpperCase());
        if (idx == -1) return "";
        int start = idx + tag.length();
        int end = text.indexOf("\n", start);
        if (end == -1) end = text.length();
        return text.substring(start, end).trim();
    }

    private String stripActionTags(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)ACTION:[A-Z_]+(=[^\n]*)?", "").trim();
    }
}
