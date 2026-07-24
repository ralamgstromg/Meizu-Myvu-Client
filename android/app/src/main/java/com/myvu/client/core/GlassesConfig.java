package com.myvu.client.core;

import android.content.Context;

import com.myvu.client.service.ConnectionManager;
import com.myvu.client.service.MyvuService;

/**
 * Single source of truth for glasses display & audio settings (Brightness, Volume, Standby FOV Position).
 * Unifies configuration across Controls dashboard (ConnectActivity), Settings (SettingsActivity), and BLE/RFCOMM session setup.
 */
public final class GlassesConfig {
    private GlassesConfig() {}

    /** Default levels */
    public static final int DEFAULT_BRIGHTNESS = 3;  // Range: 1 to 5
    public static final int DEFAULT_VOLUME = 11;     // Range: 0 to 15
    public static final int DEFAULT_STANDBY_POS = 0; // Range: 0 to 3

    public static int getBrightness(Context context) {
        return Prefs.brightness(context);
    }

    public static void setBrightness(Context context, int brightness) {
        int clamped = Math.max(1, Math.min(5, brightness));
        Prefs.setBrightness(context, clamped);
        ConnectionManager c = MyvuService.activeConnection();
        if (c != null) {
            c.setBrightness(clamped);
        }
    }

    public static int getVolume(Context context) {
        return Prefs.volume(context);
    }

    public static void setVolume(Context context, int volume) {
        int clamped = Math.max(0, Math.min(15, volume));
        Prefs.setVolume(context, clamped);
        ConnectionManager c = MyvuService.activeConnection();
        if (c != null) {
            c.setVolume(clamped);
        }
    }

    public static int getStandbyPosition(Context context) {
        return Prefs.standbyPosition(context);
    }

    public static void setStandbyPosition(Context context, int position) {
        int clamped = Math.max(0, Math.min(3, position));
        Prefs.setStandbyPosition(context, clamped);
        ConnectionManager c = MyvuService.activeConnection();
        if (c != null) {
            c.setStandbyPosition(clamped);
        }
    }

    public static int getScreenOffTime(Context context) {
        return Prefs.screenOffTime(context);
    }

    public static void setScreenOffTime(Context context, int seconds) {
        int clamped = Math.max(3, Math.min(60, seconds));
        Prefs.setScreenOffTime(context, clamped);
        ConnectionManager c = MyvuService.activeConnection();
        if (c != null) {
            c.setScreenOffTime(clamped);
        }
    }

    public static int getNotificationDuration(Context context) {
        return Prefs.notificationDuration(context);
    }

    public static void setNotificationDuration(Context context, int seconds) {
        int clamped = Math.max(1, Math.min(30, seconds));
        Prefs.setNotificationDuration(context, clamped);
    }
}
