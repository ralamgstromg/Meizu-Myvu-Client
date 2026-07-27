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
            if (text == null || text.trim().isEmpty()) return;
            String recipient = null;
            String message = text.trim();

            if (text.contains(":") || text.contains("|")) {
                String[] parts = text.split("[:|]", 2);
                recipient = parts[0].trim();
                message = parts[1].trim();
            }

            StringBuilder url = new StringBuilder("https://api.whatsapp.com/send?");
            if (recipient != null && !recipient.isEmpty()) {
                String number = lookupContactNumber(recipient);
                if (number != null && !number.isEmpty()) {
                    String cleanNum = number.replaceAll("[^0-9]", "");
                    url.append("phone=").append(cleanNum).append("&");
                }
            }
            url.append("text=").append(URLEncoder.encode(message, "UTF-8"));

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            LogBus.log("voice action -> opened WhatsApp (recipient=" + recipient + ") with text: " + message);
        } catch (Exception e) {
            LogBus.error("could not open WhatsApp", e);
        }
    }

    public void openTelegram(String text) {
        try {
            if (text == null || text.trim().isEmpty()) return;
            String message = text.trim();

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("tg://msg?text=" + URLEncoder.encode(message, "UTF-8")));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            LogBus.log("voice action -> opened Telegram with text: " + message);
        } catch (Exception e) {
            LogBus.error("could not open Telegram", e);
        }
    }

    public void makeCall(String target) {
        try {
            if (target == null || target.trim().isEmpty()) return;
            String cleanTarget = target.trim();
            String number = null;

            if (cleanTarget.matches("^[0-9+#* -]+$")) {
                number = cleanTarget;
            } else {
                number = lookupContactNumber(cleanTarget);
                if (number == null || number.isEmpty()) {
                    // Try stripping common possessives or articles: "a mi ", "mi ", "a "
                    String stripped = cleanTarget.replaceAll("(?i)^(a\\s+)?(mi\\s+)?", "").trim();
                    if (!stripped.isEmpty() && !stripped.equalsIgnoreCase(cleanTarget)) {
                        number = lookupContactNumber(stripped);
                    }
                }
                if (number == null || number.isEmpty()) {
                    // Fallback: try first word (e.g. "amor" from "amor hermosa")
                    String[] parts = cleanTarget.split("\\s+");
                    for (String part : parts) {
                        if (part.length() >= 3) {
                            number = lookupContactNumber(part);
                            if (number != null && !number.isEmpty()) break;
                        }
                    }
                }
            }

            Intent intent;
            if (number != null && !number.isEmpty()) {
                boolean hasCallPerm = context.checkSelfPermission(android.Manifest.permission.CALL_PHONE)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
                if (hasCallPerm) {
                    try {
                        android.telecom.TelecomManager tm = (android.telecom.TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                        if (tm != null) {
                            android.os.Bundle extras = new android.os.Bundle();
                            extras.putBoolean(android.telecom.TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false);
                            tm.placeCall(Uri.parse("tel:" + Uri.encode(number)), extras);
                            LogBus.log("voice action -> TelecomManager placed direct call to " + target + " (" + number + ")");
                            return;
                        }
                    } catch (Exception e) {
                        LogBus.warn("TelecomManager placeCall failed: " + e.getMessage() + ", falling back to Intent");
                    }
                }
                intent = new Intent(hasCallPerm ? Intent.ACTION_CALL : Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                LogBus.log("voice action -> placing " + (hasCallPerm ? "direct call" : "dialer call") + " to " + target + " (" + number + ")");
            } else {
                intent = new Intent(Intent.ACTION_DIAL);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                LogBus.warn("voice action -> contact number not found for " + target + ", opening dialer");
            }
        } catch (Exception e) {
            LogBus.error("could not place call for " + target, e);
        }
    }

    private String lookupContactNumber(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            LogBus.warn("READ_CONTACTS permission not granted -- cannot lookup " + name);
            return null;
        }
        try (android.database.Cursor cursor = context.getContentResolver().query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER, android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                new String[]{"%" + name.trim() + "%"},
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Exception e) {
            LogBus.warn("could not lookup contact: " + e.getMessage());
        }
        return null;
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
        // 1. Remove ACTION tags
        String clean = text.replaceAll("(?i)ACTION:[A-Z_]+(=[^\n]*)?", "");
        // 2. Remove markdown links [text](url) -> text
        clean = clean.replaceAll("\\[([^\\]]+)\\]\\([^\\)]+\\)", "$1");
        // 3. Remove markdown syntax symbols (*, _, `, ~, #, >)
        clean = clean.replaceAll("[*_`~#>]", "");
        // 4. Remove leading bullet dashes/asterisks on newlines
        clean = clean.replaceAll("(?m)^[\\s*\\-]+\\s*", "");
        // 5. Normalize whitespace
        return clean.replaceAll("[ \\t]+", " ").trim();
    }
}
