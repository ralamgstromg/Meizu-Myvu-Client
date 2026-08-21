package com.myvu.client.core

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import java.util.Currency
import java.util.Locale
import java.util.TimeZone

/** Small typed wrapper over the default SharedPreferences. */
@Suppress("DEPRECATION")
object Prefs {
    private const val KEY_MAC = "target_mac"
    private const val KEY_MIRROR_ENABLED = "mirror_notifications"
    private const val KEY_MIRROR_BLOCKED = "mirror_blocked_packages"
    private const val KEY_MIRROR_ALLOWED = "mirror_allowed_packages"
    private const val KEY_AI_PROVIDER = "ai_provider"
    private const val KEY_STT_PROVIDER = "stt_provider"
    private const val KEY_TTS_PROVIDER = "tts_provider"
    private const val KEY_SYSTEM_PROMPT = "ai_system_prompt"
    private const val KEY_WEATHER_ENABLED = "weather_enabled"
    private const val KEY_WEATHER_PLACE = "weather_place"


    const val DEFAULT_MAC = "2C:6F:4E:00:DC:47"
    const val DEFAULT_LOCAL_AI_ENDPOINT = "http://127.0.0.1:8080/v1/chat/completions"
    const val DEFAULT_LOCAL_STT_ENDPOINT = "http://127.0.0.1:8181/v1/audio/transcriptions"
    const val DEFAULT_WHISPER_CPP_STT_ENDPOINT = "http://127.0.0.1:8282/v1/audio/transcriptions"
    const val DEFAULT_HTTP_TTS_ENDPOINT = "http://10.0.0.2:1236/v1/audio/speech"

    private val DEFAULT_BLOCKED: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.myvu.client", // our own foreground-service notice
        "com.upuphone.star.launcher.intl"
    )

    private fun prefs(c: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(c.applicationContext)
    }

    @JvmStatic
    fun targetMac(c: Context): String {
        return prefs(c).getString(KEY_MAC, DEFAULT_MAC) ?: DEFAULT_MAC
    }

    @JvmStatic
    fun setTargetMac(c: Context, mac: String) {
        prefs(c).edit().putString(KEY_MAC, mac).apply()
    }

    @JvmStatic
    fun mirrorEnabled(c: Context): Boolean {
        return prefs(c).getBoolean(KEY_MIRROR_ENABLED, true)
    }

    @JvmStatic
    fun setMirrorEnabled(c: Context, enabled: Boolean) {
        prefs(c).edit().putBoolean(KEY_MIRROR_ENABLED, enabled).apply()
    }

    @JvmStatic
    fun blockedPackages(c: Context): Set<String> {
        val stored = prefs(c).getStringSet(KEY_MIRROR_BLOCKED, null)
        return stored ?: DEFAULT_BLOCKED
    }

    @JvmStatic
    fun setBlockedPackages(c: Context, packages: Set<String>) {
        prefs(c).edit().putStringSet(KEY_MIRROR_BLOCKED, packages).apply()
    }

    @JvmStatic
    fun allowedPackages(c: Context): Set<String> {
        val stored = prefs(c).getStringSet(KEY_MIRROR_ALLOWED, null)
        return stored ?: emptySet()
    }

    @JvmStatic
    fun setAllowedPackages(c: Context, packages: Set<String>) {
        prefs(c).edit().putStringSet(KEY_MIRROR_ALLOWED, HashSet(packages)).apply()
    }

    @JvmStatic
    fun isPackageAllowed(c: Context, pkg: String?): Boolean {
        return pkg != null && !blockedPackages(c).contains(pkg) && allowedPackages(c).contains(pkg)
    }

    @JvmStatic
    fun aiProvider(c: Context): String {
        return prefs(c).getString(KEY_AI_PROVIDER, "claude") ?: "claude"
    }

    @JvmStatic
    fun setAiProvider(c: Context, providerId: String) {
        prefs(c).edit().putString(KEY_AI_PROVIDER, providerId).apply()
    }



    @JvmStatic
    fun aiApiKey(c: Context, providerId: String): String {
        val key = "${providerId}_api_key"
        val valStr = SecurePrefs.getSecret(c, key, "")
        if (valStr.isEmpty()) {
            val oldVal = prefs(c).getString(key, "") ?: ""
            if (oldVal.isNotEmpty()) {
                SecurePrefs.setSecret(c, key, oldVal)
                prefs(c).edit().remove(key).apply()
                return oldVal
            }
        }
        return valStr
    }

    @JvmStatic
    fun setAiApiKey(c: Context, providerId: String, key: String) {
        val prefKey = "${providerId}_api_key"
        SecurePrefs.setSecret(c, prefKey, key)
        prefs(c).edit().remove(prefKey).apply()
    }

    @JvmStatic
    fun aiModel(c: Context, providerId: String): String {
        return prefs(c).getString("ai_model_$providerId", "") ?: ""
    }

    @JvmStatic
    fun setAiModel(c: Context, providerId: String, model: String) {
        prefs(c).edit().putString("ai_model_$providerId", model).apply()
    }

    @JvmStatic
    fun aiEndpoint(c: Context, providerId: String): String {
        val defaultValue = if ("local" == providerId || "pocket_llm" == providerId) DEFAULT_LOCAL_AI_ENDPOINT else ""
        return prefs(c).getString("ai_endpoint_$providerId", defaultValue) ?: defaultValue
    }

    @JvmStatic
    fun setAiEndpoint(c: Context, providerId: String, endpoint: String) {
        prefs(c).edit().putString("ai_endpoint_$providerId", endpoint).apply()
    }

    @JvmStatic
    fun useAndroidStt(c: Context): Boolean {
        return prefs(c).getBoolean("use_android_stt", true)
    }

    @JvmStatic
    fun setUseAndroidStt(c: Context, use: Boolean) {
        prefs(c).edit().putBoolean("use_android_stt", use).apply()
    }

    @JvmStatic
    fun whisperModelId(c: Context): String {
        return prefs(c).getString("whisper_model_id", "whisper-large-v3-turbo-i4") ?: "whisper-large-v3-turbo-i4"
    }

    @JvmStatic
    fun setWhisperModelId(c: Context, modelId: String) {
        prefs(c).edit().putString("whisper_model_id", modelId).apply()
    }

    @JvmStatic
    fun sttLanguage(c: Context): String {
        return prefs(c).getString("stt_language", "es") ?: "es"
    }

    @JvmStatic
    fun setSttLanguage(c: Context, lang: String) {
        prefs(c).edit().putString("stt_language", lang).apply()
    }

    @JvmStatic
    fun rawSttProvider(c: Context): String {
        return prefs(c).getString(KEY_STT_PROVIDER, "local") ?: "local"
    }

    @JvmStatic
    fun sttProvider(c: Context): String {
        val prov = prefs(c).getString(KEY_STT_PROVIDER, "local") ?: "local"
        return if (prov == "android") "local" else prov
    }

    @JvmStatic
    fun setSttProvider(c: Context, providerId: String) {
        prefs(c).edit().putString(KEY_STT_PROVIDER, providerId).apply()
    }

    @JvmStatic
    fun sttApiKey(c: Context, providerId: String): String {
        val key = if ("groq" == providerId) "groq_api_key" else "stt_${providerId}_api_key"
        val valStr = SecurePrefs.getSecret(c, key, "")
        if (valStr.isEmpty()) {
            val oldVal = prefs(c).getString(key, "") ?: ""
            if (oldVal.isNotEmpty()) {
                SecurePrefs.setSecret(c, key, oldVal)
                prefs(c).edit().remove(key).apply()
                return oldVal
            }
        }
        return valStr
    }

    @JvmStatic
    fun setSttApiKey(c: Context, providerId: String, key: String) {
        val prefName = if ("groq" == providerId) "groq_api_key" else "stt_${providerId}_api_key"
        SecurePrefs.setSecret(c, prefName, key)
        prefs(c).edit().remove(prefName).apply()
    }

    @JvmStatic
    fun sttEndpoint(c: Context, providerId: String): String {
        val defaultValue = when (providerId) {
            "local" -> DEFAULT_LOCAL_STT_ENDPOINT
            "whisper_cpp" -> DEFAULT_WHISPER_CPP_STT_ENDPOINT
            else -> ""
        }
        return prefs(c).getString("stt_endpoint_$providerId", defaultValue) ?: defaultValue
    }

    @JvmStatic
    fun setSttEndpoint(c: Context, providerId: String, endpoint: String) {
        prefs(c).edit().putString("stt_endpoint_$providerId", endpoint).apply()
    }

    @JvmStatic
    fun sttModel(c: Context, providerId: String): String {
        return prefs(c).getString("stt_model_$providerId", "") ?: ""
    }

    @JvmStatic
    fun setSttModel(c: Context, providerId: String, model: String) {
        prefs(c).edit().putString("stt_model_$providerId", model).apply()
    }

    @JvmStatic
    fun ttsProvider(c: Context): String {
        return prefs(c).getString(KEY_TTS_PROVIDER, "system") ?: "system"
    }

    @JvmStatic
    fun aiResponseMode(c: Context): String {
        return prefs(c).getString("ai_response_mode", "voice_and_visual") ?: "voice_and_visual"
    }

    @JvmStatic
    fun setAiResponseMode(c: Context, mode: String) {
        prefs(c).edit().putString("ai_response_mode", mode).apply()
    }

    @JvmStatic
    fun setTtsProvider(c: Context, providerId: String) {
        prefs(c).edit().putString(KEY_TTS_PROVIDER, providerId).apply()
    }

    @JvmStatic
    fun ttsEndpoint(c: Context): String {
        return prefs(c).getString("tts_http_endpoint", DEFAULT_HTTP_TTS_ENDPOINT) ?: DEFAULT_HTTP_TTS_ENDPOINT
    }

    @JvmStatic
    fun setTtsEndpoint(c: Context, endpoint: String) {
        prefs(c).edit().putString("tts_http_endpoint", endpoint).apply()
    }

    @JvmStatic
    fun ttsApiKey(c: Context): String {
        val key = "tts_http_api_key"
        val valStr = SecurePrefs.getSecret(c, key, "")
        if (valStr.isEmpty()) {
            val oldVal = prefs(c).getString(key, "") ?: ""
            if (oldVal.isNotEmpty()) {
                SecurePrefs.setSecret(c, key, oldVal)
                prefs(c).edit().remove(key).apply()
                return oldVal
            }
        }
        return valStr
    }

    @JvmStatic
    fun setTtsApiKey(c: Context, key: String) {
        val prefKey = "tts_http_api_key"
        SecurePrefs.setSecret(c, prefKey, key)
        prefs(c).edit().remove(prefKey).apply()
    }

    @JvmStatic
    fun ttsModel(c: Context): String {
        return prefs(c).getString("tts_http_model", "") ?: ""
    }

    @JvmStatic
    fun setTtsModel(c: Context, model: String) {
        prefs(c).edit().putString("tts_http_model", model).apply()
    }

    @JvmStatic
    fun ttsVoice(c: Context): String {
        return prefs(c).getString("tts_http_voice", "") ?: ""
    }

    @JvmStatic
    fun setTtsVoice(c: Context, voice: String) {
        prefs(c).edit().putString("tts_http_voice", voice).apply()
    }

    @JvmStatic
    fun systemPrompt(c: Context): String {
        val stored = prefs(c).getString("custom_system_prompt", null) ?: prefs(c).getString(KEY_SYSTEM_PROMPT, null)
        val template = stored?.ifBlank { null } ?: com.myvu.client.ai.AiClient.DEFAULT_SYSTEM_PROMPT
        val locale = Locale("es", "CO")
        val langTag = "es-CO"
        val langName = "Español (Colombia)"
        val country = "Colombia"
        val currencyCode = "COP"
        val currencySymbol = "$"
        val tz = TimeZone.getDefault()
        val tzName = tz.id

        return template
            .replace("{COUNTRY}", country)
            .replace("{CURRENCY_CODE}", currencyCode)
            .replace("{CURRENCY_SYMBOL}", currencySymbol)
            .replace("{TIMEZONE}", tzName)
            .replace("{STT_LANGUAGE}", langTag)
            .replace("{LOCALE}", langTag)
            .replace("{LANGUAGE}", langTag)
            .replace("{LANGUAGE_NAME}", langName)
    }

    @JvmStatic
    fun setSystemPrompt(c: Context, prompt: String) {
        val trimmed = prompt.trim()
        prefs(c).edit()
            .putString("custom_system_prompt", trimmed)
            .putString(KEY_SYSTEM_PROMPT, trimmed)
            .apply()
    }

    @JvmStatic
    fun weatherEnabled(c: Context): Boolean {
        return prefs(c).getBoolean(KEY_WEATHER_ENABLED, true)
    }

    @JvmStatic
    fun setWeatherEnabled(c: Context, enabled: Boolean) {
        prefs(c).edit().putBoolean(KEY_WEATHER_ENABLED, enabled).apply()
    }

    @JvmStatic
    fun ignoreSsl(c: Context): Boolean {
        return prefs(c).getBoolean("ai_ignore_ssl", false)
    }

    @JvmStatic
    fun setIgnoreSsl(c: Context, ignore: Boolean) {
        prefs(c).edit().putBoolean("ai_ignore_ssl", ignore).apply()
    }

    @JvmStatic
    fun brightness(c: Context): Int {
        return prefs(c).getInt("glasses_brightness", 2)
    }

    @JvmStatic
    fun setBrightness(c: Context, brightness: Int) {
        val clamped = brightness.coerceIn(1, 5)
        prefs(c).edit().putInt("glasses_brightness", clamped).apply()
    }

    @JvmStatic
    fun volume(c: Context): Int {
        return prefs(c).getInt("glasses_volume", 12)
    }

    @JvmStatic
    fun setVolume(c: Context, volume: Int) {
        val clamped = volume.coerceIn(0, 15)
        prefs(c).edit().putInt("glasses_volume", clamped).apply()
    }

    @JvmStatic
    fun standbyPosition(c: Context): Int {
        return prefs(c).getInt("glasses_standby_position", 0)
    }

    @JvmStatic
    fun setStandbyPosition(c: Context, position: Int) {
        val clamped = position.coerceIn(0, 3)
        prefs(c).edit().putInt("glasses_standby_position", clamped).apply()
    }

    @JvmStatic
    fun screenOffTime(c: Context): Int {
        return prefs(c).getInt("glasses_screen_off_time", 10)
    }

    @JvmStatic
    fun setScreenOffTime(c: Context, seconds: Int) {
        val clamped = seconds.coerceIn(3, 60)
        prefs(c).edit().putInt("glasses_screen_off_time", clamped).apply()
    }

    @JvmStatic
    fun notificationDuration(c: Context): Int {
        return prefs(c).getInt("notification_display_duration", 5)
    }

    @JvmStatic
    fun setNotificationDuration(c: Context, seconds: Int) {
        val clamped = seconds.coerceIn(1, 30)
        prefs(c).edit().putInt("notification_display_duration", clamped).apply()
    }

    @JvmStatic
    fun wifiEnabled(c: Context): Boolean {
        return prefs(c).getBoolean("system_wifi_enabled", false)
    }

    @JvmStatic
    fun setWifiEnabled(c: Context, enabled: Boolean) {
        prefs(c).edit().putBoolean("system_wifi_enabled", enabled).apply()
    }

    @JvmStatic
    fun zenModeEnabled(c: Context): Boolean {
        return prefs(c).getBoolean("system_zen_mode_enabled", false)
    }

    @JvmStatic
    fun setZenModeEnabled(c: Context, enabled: Boolean) {
        prefs(c).edit().putBoolean("system_zen_mode_enabled", enabled).apply()
    }

    @JvmStatic
    fun wearDetectionEnabled(c: Context): Boolean {
        return prefs(c).getBoolean("system_wear_detection_enabled", true)
    }

    @JvmStatic
    fun setWearDetectionEnabled(c: Context, enabled: Boolean) {
        prefs(c).edit().putBoolean("system_wear_detection_enabled", enabled).apply()
    }

    @JvmStatic
    fun musicTouchPanelEnabled(c: Context): Boolean {
        return prefs(c).getBoolean("system_music_tp_enabled", true)
    }

    @JvmStatic
    fun setMusicTouchPanelEnabled(c: Context, enabled: Boolean) {
        prefs(c).edit().putBoolean("system_music_tp_enabled", enabled).apply()
    }

    @JvmStatic
    fun airModeEnabled(c: Context): Boolean {
        return prefs(c).getBoolean("system_air_mode_enabled", false)
    }

    @JvmStatic
    fun setAirModeEnabled(c: Context, enabled: Boolean) {
        prefs(c).edit().putBoolean("system_air_mode_enabled", enabled).apply()
    }

    @JvmStatic
    fun weatherPlace(c: Context): String {
        return prefs(c).getString(KEY_WEATHER_PLACE, "") ?: ""
    }

    @JvmStatic
    fun setWeatherPlace(c: Context, place: String) {
        prefs(c).edit().putString(KEY_WEATHER_PLACE, place).apply()
    }

    @JvmStatic
    fun voiceWakeupEnabled(c: Context): Boolean {
        return prefs(c).getBoolean("voice_wakeup_enabled", false)
    }

    @JvmStatic
    fun setVoiceWakeupEnabled(c: Context, enabled: Boolean) {
        prefs(c).edit().putBoolean("voice_wakeup_enabled", enabled).apply()
    }

    @JvmStatic
    fun weatherIntervalMinutes(c: Context): Int {
        return prefs(c).getInt("weather_interval_minutes", 60)
    }

    @JvmStatic
    fun setWeatherIntervalMinutes(c: Context, minutes: Int) {
        val clamped = minutes.coerceIn(15, 720)
        prefs(c).edit().putInt("weather_interval_minutes", clamped).apply()
    }

    @JvmStatic
    fun loggingEnabled(c: Context): Boolean {
        val enabled = prefs(c).getBoolean("logging_enabled", true)
        LogBus.isEnabled = enabled
        return enabled
    }

    @JvmStatic
    fun setLoggingEnabled(c: Context, enabled: Boolean) {
        prefs(c).edit().putBoolean("logging_enabled", enabled).apply()
        LogBus.isEnabled = enabled
    }

    @JvmStatic
    fun touchpadTapAction(c: Context): String {
        return prefs(c).getString("touchpad_tap_action", "none") ?: "none"
    }

    @JvmStatic
    fun setTouchpadTapAction(c: Context, action: String) {
        prefs(c).edit().putString("touchpad_tap_action", action).apply()
    }

    @JvmStatic
    fun touchpadDoubleTapAction(c: Context): String {
        return prefs(c).getString("touchpad_double_tap_action", "media_play_pause") ?: "media_play_pause"
    }

    @JvmStatic
    fun setTouchpadDoubleTapAction(c: Context, action: String) {
        prefs(c).edit().putString("touchpad_double_tap_action", action).apply()
    }

    @JvmStatic
    fun touchpadTripleTapAction(c: Context): String {
        return prefs(c).getString("touchpad_triple_tap_action", "phone_assistant") ?: "phone_assistant"
    }

    @JvmStatic
    fun setTouchpadTripleTapAction(c: Context, action: String) {
        prefs(c).edit().putString("touchpad_triple_tap_action", action).apply()
    }

    @JvmStatic
    fun touchpadSwipeForwardAction(c: Context): String {
        return prefs(c).getString("touchpad_swipe_forward_action", "media_next") ?: "media_next"
    }

    @JvmStatic
    fun setTouchpadSwipeForwardAction(c: Context, action: String) {
        prefs(c).edit().putString("touchpad_swipe_forward_action", action).apply()
    }

    @JvmStatic
    fun touchpadSwipeBackwardAction(c: Context): String {
        return prefs(c).getString("touchpad_swipe_backward_action", "media_prev") ?: "media_prev"
    }

    @JvmStatic
    fun setTouchpadSwipeBackwardAction(c: Context, action: String) {
        prefs(c).edit().putString("touchpad_swipe_backward_action", action).apply()
    }

    @JvmStatic
    fun touchpadLongPressAction(c: Context): String {
        return prefs(c).getString("touchpad_long_press_action", "ai_assistant") ?: "ai_assistant"
    }

    @JvmStatic
    fun setTouchpadLongPressAction(c: Context, action: String) {
        prefs(c).edit().putString("touchpad_long_press_action", action).apply()
    }
}
