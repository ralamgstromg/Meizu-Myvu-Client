package com.myvu.client.core

import android.content.Context

/**
 * Single source of truth for glasses display & audio settings (Brightness, Volume, Standby FOV Position).
 * Unifies configuration across Controls dashboard (ConnectActivity), Settings (SettingsActivity), and BLE/RFCOMM session setup.
 */
object GlassesConfig {

    /** Default levels */
    const val DEFAULT_BRIGHTNESS = 3  // Range: 1 to 5
    const val DEFAULT_VOLUME = 11     // Range: 0 to 15
    const val DEFAULT_STANDBY_POS = 0 // Range: 0 to 3

    @JvmStatic
    fun getBrightness(context: Context): Int {
        return Prefs.brightness(context)
    }

    @JvmStatic
    fun setBrightness(context: Context, brightness: Int) {
        val clamped = brightness.coerceIn(1, 5)
        Prefs.setBrightness(context, clamped)
        invokeConnectionMethod("setBrightness", clamped)
    }

    @JvmStatic
    fun getVolume(context: Context): Int {
        return Prefs.volume(context)
    }

    @JvmStatic
    fun setVolume(context: Context, volume: Int) {
        val clamped = volume.coerceIn(0, 15)
        Prefs.setVolume(context, clamped)
        invokeConnectionMethod("setVolume", clamped)
    }

    @JvmStatic
    fun getStandbyPosition(context: Context): Int {
        return Prefs.standbyPosition(context)
    }

    @JvmStatic
    fun setStandbyPosition(context: Context, position: Int) {
        val clamped = position.coerceIn(0, 3)
        Prefs.setStandbyPosition(context, clamped)
        invokeConnectionMethod("setStandbyPosition", clamped)
    }

    @JvmStatic
    fun getScreenOffTime(context: Context): Int {
        return Prefs.screenOffTime(context)
    }

    @JvmStatic
    fun setScreenOffTime(context: Context, seconds: Int) {
        val clamped = seconds.coerceIn(3, 60)
        Prefs.setScreenOffTime(context, clamped)
        invokeConnectionMethod("setScreenOffTime", clamped)
    }

    @JvmStatic
    fun getNotificationDuration(context: Context): Int {
        return Prefs.notificationDuration(context)
    }

    @JvmStatic
    fun setNotificationDuration(context: Context, seconds: Int) {
        val clamped = seconds.coerceIn(1, 30)
        Prefs.setNotificationDuration(context, clamped)
    }

    private fun invokeConnectionMethod(methodName: String, param: Int) {
        try {
            val serviceClazz = Class.forName("com.myvu.client.service.MyvuService")
            val activeMethod = serviceClazz.getMethod("activeConnection")
            val activeConn = activeMethod.invoke(null) ?: return
            val connMethod = activeConn.javaClass.getMethod(methodName, Int::class.javaPrimitiveType)
            connMethod.invoke(activeConn, param)
        } catch (ignored: Throwable) {
            // MyvuService / ConnectionManager may not be active or instantiated yet
        }
    }
}
