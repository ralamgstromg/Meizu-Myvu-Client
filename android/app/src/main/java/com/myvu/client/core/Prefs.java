package com.myvu.client.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Small typed wrapper over the default SharedPreferences. */
public final class Prefs {
    private Prefs() {}

    private static final String KEY_MAC = "target_mac";
    private static final String KEY_MIRROR_ENABLED = "mirror_notifications";
    private static final String KEY_MIRROR_BLOCKED = "mirror_blocked_packages";
    private static final String KEY_MIRROR_ALLOWED = "mirror_allowed_packages";
    private static final String KEY_AI_PROVIDER = "ai_provider";
    private static final String KEY_STT_PROVIDER = "stt_provider";
    private static final String KEY_TTS_PROVIDER = "tts_provider";
    private static final String KEY_SYSTEM_PROMPT = "ai_system_prompt";
    private static final String KEY_WEATHER_ENABLED = "weather_enabled";
    private static final String KEY_WEATHER_PLACE = "weather_place";

    public static final String DEFAULT_MAC = "2C:6F:4E:00:DC:47";
    public static final String DEFAULT_LOCAL_AI_ENDPOINT =
            "http://10.0.0.2:1234/v1/chat/completions";
    public static final String DEFAULT_LOCAL_STT_ENDPOINT =
            "http://10.0.0.2:1235/v1/audio/transcriptions";
    public static final String DEFAULT_HTTP_TTS_ENDPOINT =
            "http://10.0.0.2:1236/v1/audio/speech";

    /**
     * Packages whose notifications are never mirrored. Ongoing/system chatter
     * would otherwise flood the relay and starve the ACK path.
     */
    private static final Set<String> DEFAULT_BLOCKED = Collections.unmodifiableSet(
            new HashSet<>(java.util.Arrays.asList(
                    "android",
                    "com.android.systemui",
                    "com.myvu.client",            // our own foreground-service notice
                    "com.upuphone.star.launcher.intl")));

    private static SharedPreferences prefs(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c.getApplicationContext());
    }

    public static String targetMac(Context c) {
        return prefs(c).getString(KEY_MAC, DEFAULT_MAC);
    }

    public static void setTargetMac(Context c, String mac) {
        prefs(c).edit().putString(KEY_MAC, mac).apply();
    }

    public static boolean mirrorEnabled(Context c) {
        return prefs(c).getBoolean(KEY_MIRROR_ENABLED, true);
    }

    public static void setMirrorEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_MIRROR_ENABLED, enabled).apply();
    }

    public static Set<String> blockedPackages(Context c) {
        Set<String> stored = prefs(c).getStringSet(KEY_MIRROR_BLOCKED, null);
        return stored != null ? stored : DEFAULT_BLOCKED;
    }

    public static void setBlockedPackages(Context c, Set<String> packages) {
        prefs(c).edit().putStringSet(KEY_MIRROR_BLOCKED, packages).apply();
    }

    /**
     * Apps the user has opted IN to mirroring, by package name.
     *
     * Deliberately an allowlist, not a blocklist: notifications carry OTPs, 2FA
     * codes and private messages, and forwarding everything by default sends all
     * of that to the glasses. Empty therefore means "mirror nothing" -- the safe
     * reading of "the user has not chosen yet".
     */
    public static Set<String> allowedPackages(Context c) {
        Set<String> stored = prefs(c).getStringSet(KEY_MIRROR_ALLOWED, null);
        return stored != null ? stored : Collections.<String>emptySet();
    }

    public static void setAllowedPackages(Context c, Set<String> packages) {
        // Copy: SharedPreferences must not be handed a set the caller keeps mutating.
        prefs(c).edit().putStringSet(KEY_MIRROR_ALLOWED, new HashSet<>(packages)).apply();
    }

    /** True when {@code pkg} is opted in AND not hard-blocked. */
    public static boolean isPackageAllowed(Context c, String pkg) {
        return pkg != null && !blockedPackages(c).contains(pkg)
                && allowedPackages(c).contains(pkg);
    }

    /**
     * Which backend answers questions: an {@code AiProvider.id}. The literal
     * default keeps this class free of a dependency on the ai package --
     * {@code AiProvider.fromId} falls back to Claude for unknown ids anyway.
     */
    public static String aiProvider(Context c) {
        return prefs(c).getString(KEY_AI_PROVIDER, "claude");
    }

    public static void setAiProvider(Context c, String providerId) {
        prefs(c).edit().putString(KEY_AI_PROVIDER, providerId).apply();
    }

    /**
     * Per-provider API key. Never hard-code one: keys are entered by the user
     * at runtime. The pref name derives from the provider id -- "claude" yields
     * claude_api_key, the name that predates provider choice, so keys entered
     * before this setting existed survive.
     */
    public static String aiApiKey(Context c, String providerId) {
        return prefs(c).getString(providerId + "_api_key", "");
    }

    public static void setAiApiKey(Context c, String providerId, String key) {
        prefs(c).edit().putString(providerId + "_api_key", key).apply();
    }

    /** Per-provider model override. Empty means the provider's shipped default. */
    public static String aiModel(Context c, String providerId) {
        return prefs(c).getString("ai_model_" + providerId, "");
    }

    public static void setAiModel(Context c, String providerId, String model) {
        prefs(c).edit().putString("ai_model_" + providerId, model).apply();
    }

    public static String aiEndpoint(Context c, String providerId) {
        String defaultValue = "local".equals(providerId) ? DEFAULT_LOCAL_AI_ENDPOINT : "";
        return prefs(c).getString("ai_endpoint_" + providerId, defaultValue);
    }

    public static void setAiEndpoint(Context c, String providerId, String endpoint) {
        prefs(c).edit().putString("ai_endpoint_" + providerId, endpoint).apply();
    }

    public static String sttProvider(Context c) {
        return prefs(c).getString(KEY_STT_PROVIDER, "groq");
    }

    public static void setSttProvider(Context c, String providerId) {
        prefs(c).edit().putString(KEY_STT_PROVIDER, providerId).apply();
    }

    public static String sttApiKey(Context c, String providerId) {
        String key = "groq".equals(providerId) ? "groq_api_key"
                : "stt_" + providerId + "_api_key";
        return prefs(c).getString(key, "");
    }

    public static void setSttApiKey(Context c, String providerId, String key) {
        String prefName = "groq".equals(providerId) ? "groq_api_key"
                : "stt_" + providerId + "_api_key";
        prefs(c).edit().putString(prefName, key).apply();
    }

    public static String sttEndpoint(Context c, String providerId) {
        String defaultValue = "local".equals(providerId) ? DEFAULT_LOCAL_STT_ENDPOINT : "";
        return prefs(c).getString("stt_endpoint_" + providerId, defaultValue);
    }

    public static void setSttEndpoint(Context c, String providerId, String endpoint) {
        prefs(c).edit().putString("stt_endpoint_" + providerId, endpoint).apply();
    }

    public static String sttModel(Context c, String providerId) {
        return prefs(c).getString("stt_model_" + providerId, "");
    }

    public static void setSttModel(Context c, String providerId, String model) {
        prefs(c).edit().putString("stt_model_" + providerId, model).apply();
    }

    public static String ttsProvider(Context c) {
        return prefs(c).getString(KEY_TTS_PROVIDER, "system");
    }

    public static void setTtsProvider(Context c, String providerId) {
        prefs(c).edit().putString(KEY_TTS_PROVIDER, providerId).apply();
    }

    public static String ttsEndpoint(Context c) {
        return prefs(c).getString("tts_http_endpoint", DEFAULT_HTTP_TTS_ENDPOINT);
    }

    public static void setTtsEndpoint(Context c, String endpoint) {
        prefs(c).edit().putString("tts_http_endpoint", endpoint).apply();
    }

    public static String ttsApiKey(Context c) {
        return prefs(c).getString("tts_http_api_key", "");
    }

    public static void setTtsApiKey(Context c, String key) {
        prefs(c).edit().putString("tts_http_api_key", key).apply();
    }

    public static String ttsModel(Context c) {
        return prefs(c).getString("tts_http_model", "");
    }

    public static void setTtsModel(Context c, String model) {
        prefs(c).edit().putString("tts_http_model", model).apply();
    }

    public static String ttsVoice(Context c) {
        return prefs(c).getString("tts_http_voice", "");
    }

    public static void setTtsVoice(Context c, String voice) {
        prefs(c).edit().putString("tts_http_voice", voice).apply();
    }

    /**
     * The assistant's system prompt, as customised in Settings. Empty means
     * "not customised" -- ClaudeClient then falls back to its own default, so
     * the shipped wording lives with the AI code rather than being copied here.
     * Read fresh on every turn, so an edit applies to the next question.
     */
    public static String systemPrompt(Context c) {
        return prefs(c).getString(KEY_SYSTEM_PROMPT, "");
    }

    public static void setSystemPrompt(Context c, String prompt) {
        prefs(c).edit().putString(KEY_SYSTEM_PROMPT, prompt).apply();
    }

    /** Whether to push weather to the glasses. On by default -- it needs no key. */
    public static boolean weatherEnabled(Context c) {
        return prefs(c).getBoolean(KEY_WEATHER_ENABLED, true);
    }

    public static void setWeatherEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_WEATHER_ENABLED, enabled).apply();
    }

    public static boolean ignoreSsl(Context c) {
        return prefs(c).getBoolean("ai_ignore_ssl", false);
    }

    public static void setIgnoreSsl(Context c, boolean ignore) {
        prefs(c).edit().putBoolean("ai_ignore_ssl", ignore).apply();
    }

    public static int brightness(Context c) {
        return prefs(c).getInt("glasses_brightness", 2);
    }

    public static void setBrightness(Context c, int brightness) {
        int clamped = Math.max(1, Math.min(5, brightness));
        prefs(c).edit().putInt("glasses_brightness", clamped).apply();
    }

    public static int volume(Context c) {
        return prefs(c).getInt("glasses_volume", 12);
    }

    public static void setVolume(Context c, int volume) {
        int clamped = Math.max(0, Math.min(15, volume));
        prefs(c).edit().putInt("glasses_volume", clamped).apply();
    }

    public static int standbyPosition(Context c) {
        return prefs(c).getInt("glasses_standby_position", 0);
    }

    public static void setStandbyPosition(Context c, int position) {
        int clamped = Math.max(0, Math.min(3, position));
        prefs(c).edit().putInt("glasses_standby_position", clamped).apply();
    }

    public static int screenOffTime(Context c) {
        return prefs(c).getInt("glasses_screen_off_time", 10);
    }

    public static void setScreenOffTime(Context c, int seconds) {
        int clamped = Math.max(3, Math.min(60, seconds));
        prefs(c).edit().putInt("glasses_screen_off_time", clamped).apply();
    }

    public static int notificationDuration(Context c) {
        return prefs(c).getInt("notification_display_duration", 5);
    }

    public static void setNotificationDuration(Context c, int seconds) {
        int clamped = Math.max(1, Math.min(30, seconds));
        prefs(c).edit().putInt("notification_display_duration", clamped).apply();
    }

    public static boolean wifiEnabled(Context c) {
        return prefs(c).getBoolean("system_wifi_enabled", false);
    }

    public static void setWifiEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("system_wifi_enabled", enabled).apply();
    }

    public static boolean zenModeEnabled(Context c) {
        return prefs(c).getBoolean("system_zen_mode_enabled", false);
    }

    public static void setZenModeEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("system_zen_mode_enabled", enabled).apply();
    }

    public static boolean wearDetectionEnabled(Context c) {
        return prefs(c).getBoolean("system_wear_detection_enabled", true);
    }

    public static void setWearDetectionEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("system_wear_detection_enabled", enabled).apply();
    }

    public static boolean musicTouchPanelEnabled(Context c) {
        return prefs(c).getBoolean("system_music_tp_enabled", true);
    }

    public static void setMusicTouchPanelEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("system_music_tp_enabled", enabled).apply();
    }

    public static boolean airModeEnabled(Context c) {
        return prefs(c).getBoolean("system_air_mode_enabled", false);
    }

    public static void setAirModeEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("system_air_mode_enabled", enabled).apply();
    }

    /**
     * Optional place to report weather for -- a city name or "lat,lon". Blank
     * means "use the phone's location", which is the normal case; this exists
     * for when location permission is denied or a fixed city is preferred.
     */
    public static String weatherPlace(Context c) {
        return prefs(c).getString(KEY_WEATHER_PLACE, "");
    }

    public static void setWeatherPlace(Context c, String place) {
        prefs(c).edit().putString(KEY_WEATHER_PLACE, place).apply();
    }

    public static boolean voiceWakeupEnabled(Context c) {
        return prefs(c).getBoolean("voice_wakeup_enabled", false);
    }

    public static void setVoiceWakeupEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("voice_wakeup_enabled", enabled).apply();
    }

    public static int weatherIntervalMinutes(Context c) {
        return prefs(c).getInt("weather_interval_minutes", 60);
    }

    public static void setWeatherIntervalMinutes(Context c, int minutes) {
        int clamped = Math.max(15, Math.min(720, minutes));
        prefs(c).edit().putInt("weather_interval_minutes", clamped).apply();
    }

    public static boolean loggingEnabled(Context c) {
        boolean enabled = prefs(c).getBoolean("logging_enabled", true);
        LogBus.setEnabled(enabled);
        return enabled;
    }

    public static void setLoggingEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("logging_enabled", enabled).apply();
        LogBus.setEnabled(enabled);
    }

    public static String touchpadDoubleTapAction(Context c) {
        return prefs(c).getString("touchpad_double_tap_action", "media_play_pause");
    }

    public static void setTouchpadDoubleTapAction(Context c, String action) {
        prefs(c).edit().putString("touchpad_double_tap_action", action).apply();
    }

    public static String touchpadTripleTapAction(Context c) {
        return prefs(c).getString("touchpad_triple_tap_action", "ai_assistant");
    }

    public static void setTouchpadTripleTapAction(Context c, String action) {
        prefs(c).edit().putString("touchpad_triple_tap_action", action).apply();
    }

    public static String touchpadLongPressAction(Context c) {
        return prefs(c).getString("touchpad_long_press_action", "ai_assistant");
    }

    public static void setTouchpadLongPressAction(Context c, String action) {
        prefs(c).edit().putString("touchpad_long_press_action", action).apply();
    }
}
