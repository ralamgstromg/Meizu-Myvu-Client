package com.myvu.client.core

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

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
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_GEMINI_MODEL = "gemini_model"
    const val DEFAULT_GEMINI_MODEL = "gemini-2.0-flash"
    private const val KEY_GEMINI_FALLBACK_POLICY = "gemini_fallback_policy"
    private const val KEY_USE_LOCAL_GEMMA = "use_local_gemma"
    private const val KEY_GEMMA_MODEL_ID = "gemma_model_id"
    private const val KEY_GEMMA_HF_TOKEN = "gemma_hf_token"
    private const val KEY_GEMMA_CUSTOM_URL = "gemma_custom_url"

    const val DEFAULT_MAC = "2C:6F:4E:00:DC:47"
    const val DEFAULT_LOCAL_AI_ENDPOINT = "http://10.0.0.2:1234/v1/chat/completions"
    const val DEFAULT_LOCAL_STT_ENDPOINT = "http://10.0.0.2:1235/v1/audio/transcriptions"
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
    fun geminiFallbackPolicy(c: Context): String {
        return prefs(c).getString(KEY_GEMINI_FALLBACK_POLICY, "nano_then_api") ?: "nano_then_api"
    }

    @JvmStatic
    fun setGeminiFallbackPolicy(c: Context, policyId: String) {
        prefs(c).edit().putString(KEY_GEMINI_FALLBACK_POLICY, policyId).apply()
    }

    @JvmStatic
    fun useLocalGemmaIfAvailable(c: Context): Boolean {
        return prefs(c).getBoolean(KEY_USE_LOCAL_GEMMA, false)
    }

    @JvmStatic
    fun setUseLocalGemmaIfAvailable(c: Context, enable: Boolean) {
        prefs(c).edit().putBoolean(KEY_USE_LOCAL_GEMMA, enable).apply()
    }

    @JvmStatic
    fun gemmaModelId(c: Context): String {
        return prefs(c).getString(KEY_GEMMA_MODEL_ID, "gemma-4-e2b-it-int4") ?: "gemma-4-e2b-it-int4"
    }

    @JvmStatic
    fun setGemmaModelId(c: Context, id: String) {
        prefs(c).edit().putString(KEY_GEMMA_MODEL_ID, id).apply()
    }

    @JvmStatic
    fun gemmaHfToken(c: Context): String {
        return SecurePrefs.getSecret(c, KEY_GEMMA_HF_TOKEN, "")
    }

    @JvmStatic
    fun setGemmaHfToken(c: Context, token: String) {
        SecurePrefs.setSecret(c, KEY_GEMMA_HF_TOKEN, token)
    }

    @JvmStatic
    fun gemmaCustomUrl(c: Context): String {
        return prefs(c).getString(KEY_GEMMA_CUSTOM_URL, "") ?: ""
    }

    @JvmStatic
    fun setGemmaCustomUrl(c: Context, url: String) {
        prefs(c).edit().putString(KEY_GEMMA_CUSTOM_URL, url).apply()
    }

    @JvmStatic
    fun geminiApiKey(c: Context): String {
        val value = SecurePrefs.getSecret(c, KEY_GEMINI_API_KEY, "")
        if (value.isNotEmpty()) return value
        val legacy = prefs(c).getString(KEY_GEMINI_API_KEY, "") ?: ""
        if (legacy.isNotEmpty()) {
            SecurePrefs.setSecret(c, KEY_GEMINI_API_KEY, legacy)
            prefs(c).edit().remove(KEY_GEMINI_API_KEY).apply()
        }
        return legacy
    }

    @JvmStatic
    fun setGeminiApiKey(c: Context, value: String) {
        SecurePrefs.setSecret(c, KEY_GEMINI_API_KEY, value)
        prefs(c).edit().remove(KEY_GEMINI_API_KEY).apply()
    }

    @JvmStatic
    fun geminiModel(c: Context): String {
        return prefs(c).getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_GEMINI_MODEL
    }

    @JvmStatic
    fun setGeminiModel(c: Context, model: String) {
        prefs(c).edit().putString(KEY_GEMINI_MODEL, model.trim()).apply()
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
        val defaultValue = if ("local" == providerId) DEFAULT_LOCAL_AI_ENDPOINT else ""
        return prefs(c).getString("ai_endpoint_$providerId", defaultValue) ?: defaultValue
    }

    @JvmStatic
    fun setAiEndpoint(c: Context, providerId: String, endpoint: String) {
        prefs(c).edit().putString("ai_endpoint_$providerId", endpoint).apply()
    }

    @JvmStatic
    fun sttProvider(c: Context): String {
        return prefs(c).getString(KEY_STT_PROVIDER, "groq") ?: "groq"
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
        val defaultValue = if ("local" == providerId) DEFAULT_LOCAL_STT_ENDPOINT else ""
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
        val custom = prefs(c).getString("custom_system_prompt", null)
            ?: prefs(c).getString(KEY_SYSTEM_PROMPT, null)
        return if (!custom.isNullOrBlank()) custom else com.myvu.client.ai.AiClient.DEFAULT_SYSTEM_PROMPT
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
    fun touchpadDoubleTapAction(c: Context): String {
        return prefs(c).getString("touchpad_double_tap_action", "media_play_pause") ?: "media_play_pause"
    }

    @JvmStatic
    fun setTouchpadDoubleTapAction(c: Context, action: String) {
        prefs(c).edit().putString("touchpad_double_tap_action", action).apply()
    }

    @JvmStatic
    fun touchpadTripleTapAction(c: Context): String {
        return prefs(c).getString("touchpad_triple_tap_action", "ai_assistant") ?: "ai_assistant"
    }

    @JvmStatic
    fun setTouchpadTripleTapAction(c: Context, action: String) {
        prefs(c).edit().putString("touchpad_triple_tap_action", action).apply()
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
