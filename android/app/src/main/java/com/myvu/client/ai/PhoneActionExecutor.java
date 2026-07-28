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

        // 2. Media control & OpenTune Integration
        if (lower.contains("action:opentune_play=")) {
            String query = extractValue(aiText, "ACTION:OPENTUNE_PLAY=");
            playFromSearchInOpenTune(query);
        } else if (lower.contains("action:opentune_search=")) {
            String query = extractValue(aiText, "ACTION:OPENTUNE_SEARCH=");
            playFromSearchInOpenTune(query);
        } else if (lower.contains("action:opentune_pause")) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
        } else if (lower.contains("action:opentune_resume")) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
        } else if (lower.contains("action:opentune_next") || lower.contains("action:media_next")) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
        } else if (lower.contains("action:opentune_prev") || lower.contains("action:media_prev")) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        } else if (lower.contains("action:opentune_repeat")) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_RECORD);
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

        // 6. Web Search
        if (lower.contains("action:search=")) {
            String query = extractValue(aiText, "ACTION:SEARCH=");
            openWebSearch(query);
        }

        // 7. Alarms
        if (lower.contains("action:alarm=")) {
            String alarmVal = extractValue(aiText, "ACTION:ALARM=");
            setAlarm(alarmVal);
        }

        // 8. Timers
        if (lower.contains("action:timer=")) {
            String timerVal = extractValue(aiText, "ACTION:TIMER=");
            setTimer(timerVal);
        }

        // 9. GPS Navigation
        if (lower.contains("action:navigate=")) {
            String dest = extractValue(aiText, "ACTION:NAVIGATE=");
            startNavigation(dest);
        }

        // 10. Calendar Events (General & Specific Accounts)
        if (lower.contains("action:calendar_outlook=")) {
            String eventVal = extractValue(aiText, "ACTION:CALENDAR_OUTLOOK=");
            addOutlookCalendarEvent(eventVal);
        } else if (lower.contains("action:calendar_google=")) {
            String eventVal = extractValue(aiText, "ACTION:CALENDAR_GOOGLE=");
            addGoogleCalendarEvent(eventVal);
        } else if (lower.contains("action:calendar=")) {
            String eventVal = extractValue(aiText, "ACTION:CALENDAR=");
            addCalendarEvent(eventVal);
        }

        // 11. Notes (Google Keep vs Quick Notes)
        if (lower.contains("action:note_keep=")) {
            String noteText = extractValue(aiText, "ACTION:NOTE_KEEP=");
            createKeepNote(noteText);
        } else if (lower.contains("action:note=")) {
            String noteText = extractValue(aiText, "ACTION:NOTE=");
            createNote(noteText);
        }

        // 12. Specific Reminders
        if (lower.contains("action:reminder=")) {
            String remVal = extractValue(aiText, "ACTION:REMINDER=");
            createSpecificReminder(remVal);
        }

        // 13. Summarize pending unread notifications (Email, WhatsApp, Telegram, All)
        if (lower.contains("action:summary=")) {
            String cat = extractValue(aiText, "ACTION:SUMMARY=");
            String summary = com.myvu.client.service.MirrorNotificationListener.getUnreadSummary(cat);
            return stripActionTags(aiText) + "\n\n" + summary;
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

    public void playFromSearchInOpenTune(String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
                return;
            }
            Intent intent = new Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH);
            intent.putExtra(android.app.SearchManager.QUERY, query.trim());
            intent.putExtra(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            String[] openTunePkgs = new String[]{
                "com.opentune.app", "org.opentune.android", "com.opentune.music",
                "com.vibe.opentune", "com.github.opentune", "com.opentune"
            };
            boolean launched = false;
            for (String pkg : openTunePkgs) {
                try {
                    Intent pkgIntent = new Intent(intent);
                    pkgIntent.setPackage(pkg);
                    context.startActivity(pkgIntent);
                    LogBus.log("voice action -> launched OpenTune (" + pkg + ") search/play for: " + query);
                    launched = true;
                    break;
                } catch (Exception ignored) {}
            }
            if (!launched) {
                context.startActivity(intent);
                LogBus.log("voice action -> launched generic media play from search for: " + query);
            }
        } catch (Exception e) {
            LogBus.error("could not play in OpenTune for " + query, e);
        }
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

    public void openWebSearch(String query) {
        try {
            if (query == null || query.trim().isEmpty()) return;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(query.trim(), "UTF-8")));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            LogBus.log("voice action -> opened web search for: " + query);
        } catch (Exception e) {
            LogBus.error("could not open web search", e);
        }
    }

    public void setAlarm(String val) {
        try {
            if (val == null || val.trim().isEmpty()) return;
            String message = "Alarma";
            String timeStr = val.trim();
            if (val.contains(":") || val.contains("|")) {
                String[] parts = val.split("[:|]", 2);
                timeStr = parts[0].trim();
                message = parts[1].trim();
            }
            String[] timeParts = timeStr.split("[:\\.]");
            int hour = Integer.parseInt(timeParts[0].trim());
            int minute = timeParts.length > 1 ? Integer.parseInt(timeParts[1].trim()) : 0;

            Intent intent = new Intent(android.provider.AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour);
            intent.putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute);
            intent.putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message);
            intent.putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            LogBus.log("voice action -> alarm set for " + hour + ":" + minute + " message: " + message);
        } catch (Exception e) {
            LogBus.error("could not set alarm for " + val, e);
        }
    }

    public void setTimer(String val) {
        try {
            if (val == null || val.trim().isEmpty()) return;
            String message = "Temporizador";
            String durationStr = val.trim();
            if (val.contains(":") || val.contains("|")) {
                String[] parts = val.split("[:|]", 2);
                durationStr = parts[0].trim();
                message = parts[1].trim();
            }
            int seconds = Integer.parseInt(durationStr.replaceAll("[^0-9]", ""));

            Intent intent = new Intent(android.provider.AlarmClock.ACTION_SET_TIMER);
            intent.putExtra(android.provider.AlarmClock.EXTRA_LENGTH, seconds);
            intent.putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message);
            intent.putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            LogBus.log("voice action -> timer set for " + seconds + "s message: " + message);
        } catch (Exception e) {
            LogBus.error("could not set timer for " + val, e);
        }
    }

    public void startNavigation(String destination) {
        try {
            if (destination == null || destination.trim().isEmpty()) return;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + URLEncoder.encode(destination.trim(), "UTF-8")));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            LogBus.log("voice action -> started GPS navigation to: " + destination);
        } catch (Exception e) {
            LogBus.error("could not start navigation for " + destination, e);
        }
    }

    public void addOutlookCalendarEvent(String val) {
        try {
            if (val == null || val.trim().isEmpty()) return;
            String title = val.trim();
            if (val.contains(":") || val.contains("|")) {
                String[] parts = val.split("[:|]", 2);
                title = parts[1].trim();
            }

            Intent intent = new Intent(Intent.ACTION_INSERT);
            intent.setData(android.provider.CalendarContract.Events.CONTENT_URI);
            intent.putExtra(android.provider.CalendarContract.Events.TITLE, title);
            intent.setPackage("com.microsoft.office.outlook");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
                LogBus.log("voice action -> added Outlook calendar event: " + title);
            } catch (Exception e) {
                // Fallback if Outlook package is not installed: open standard calendar chooser
                addCalendarEvent(val);
            }
        } catch (Exception e) {
            LogBus.error("could not add Outlook calendar event for " + val, e);
        }
    }

    public void addGoogleCalendarEvent(String val) {
        try {
            if (val == null || val.trim().isEmpty()) return;
            String title = val.trim();
            if (val.contains(":") || val.contains("|")) {
                String[] parts = val.split("[:|]", 2);
                title = parts[1].trim();
            }

            Intent intent = new Intent(Intent.ACTION_INSERT);
            intent.setData(android.provider.CalendarContract.Events.CONTENT_URI);
            intent.putExtra(android.provider.CalendarContract.Events.TITLE, title);
            intent.setPackage("com.google.android.calendar");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
                LogBus.log("voice action -> added Google calendar event: " + title);
            } catch (Exception e) {
                addCalendarEvent(val);
            }
        } catch (Exception e) {
            LogBus.error("could not add Google calendar event for " + val, e);
        }
    }

    public void addCalendarEvent(String val) {
        try {
            if (val == null || val.trim().isEmpty()) return;
            String title = val.trim();
            if (val.contains(":") || val.contains("|")) {
                String[] parts = val.split("[:|]", 2);
                title = parts[1].trim();
            }

            Intent intent = new Intent(Intent.ACTION_INSERT);
            intent.setData(android.provider.CalendarContract.Events.CONTENT_URI);
            intent.putExtra(android.provider.CalendarContract.Events.TITLE, title);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            LogBus.log("voice action -> added calendar event: " + title);
        } catch (Exception e) {
            LogBus.error("could not add calendar event for " + val, e);
        }
    }

    public void createKeepNote(String text) {
        try {
            if (text == null || text.trim().isEmpty()) return;
            Intent intent = new Intent("com.google.android.keep.action.CREATE_NOTE");
            intent.setPackage("com.google.android.keep");
            intent.putExtra(Intent.EXTRA_TEXT, text.trim());
            intent.setType("text/plain");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
                LogBus.log("voice action -> created note in Google Keep: " + text);
            } catch (Exception e) {
                // Fallback if Keep package is not installed: create general note
                createNote(text);
            }
        } catch (Exception e) {
            LogBus.error("could not create Keep note for " + text, e);
        }
    }

    public void createSpecificReminder(String val) {
        try {
            if (val == null || val.trim().isEmpty()) return;
            String message = val.trim();
            if (val.contains(":") || val.contains("|")) {
                String[] parts = val.split("[:|]", 2);
                message = parts[1].trim();
            }

            // Set a timer/alarm reminder via AlarmClock
            Intent intent = new Intent(android.provider.AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message);
            intent.putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            LogBus.log("voice action -> created specific reminder: " + message);
        } catch (Exception e) {
            LogBus.error("could not create reminder for " + val, e);
        }
    }

    public void createNote(String text) {
        try {
            if (text == null || text.trim().isEmpty()) return;
            Intent intent = new Intent("android.intent.action.CREATE_NOTE");
            intent.putExtra(Intent.EXTRA_TEXT, text.trim());
            intent.setType("text/plain");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (Exception e) {
                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, text.trim());
                sendIntent.setType("text/plain");
                sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(Intent.createChooser(sendIntent, "Guardar nota").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            LogBus.log("voice action -> created quick note: " + text);
        } catch (Exception e) {
            LogBus.error("could not create note for " + text, e);
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
