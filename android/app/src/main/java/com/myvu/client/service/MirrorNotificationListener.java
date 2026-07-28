package com.myvu.client.service;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import com.myvu.client.app.feature.Notifications;
import com.myvu.client.core.LogBus;
import com.myvu.client.core.Prefs;

import org.json.JSONObject;

/**
 * Mirrors the phone's real notifications onto the lens.
 *
 * This is something the Python client could never do -- it could only push
 * hand-written test notifications. Here we forward actual incoming SMS, chat
 * messages and so on, the way the official app does.
 *
 * Requires the user to grant notification access in system settings; there is
 * no runtime-permission dialog for it (see {@link #isEnabled}).
 */
public class MirrorNotificationListener extends NotificationListenerService {

    private static volatile MirrorNotificationListener instance;

    private final NotificationFilter notificationFilter = new NotificationFilter();
    private final android.os.Handler dismissHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        LogBus.log("MirrorNotificationListener connected");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        if (instance == this) instance = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (!Prefs.mirrorEnabled(this)) return;

        Notification n = sbn.getNotification();
        if (n == null) return;

        // Opt-in only: notifications carry OTPs, 2FA codes and private messages,
        // so nothing is forwarded unless the user picked that app in Settings.
        // isPackageAllowed() also applies the hard block list (system noise, us).
        String pkg = sbn.getPackageName();
        if (!Prefs.isPackageAllowed(this, pkg)) {
            // trace(), not log(): this fires for every notification from every
            // app the user did not opt in, and would drown the on-screen log.
            // It is still in logcat when you need to ask "why not this app?".
            LogBus.trace("not mirroring " + pkg + ": not in the chosen apps");
            return;
        }

        Bundle extras = n.extras;
        if (extras == null) return;
        String title = charSequence(extras, Notification.EXTRA_TITLE);
        String text = charSequence(extras, Notification.EXTRA_TEXT);

        // Filter noise flags (ongoing events, group summaries) and empty content
        if (NotificationFilter.isNoiseOrEmpty(n.flags, title, text)) return;

        // Content-based deduplication (identical title + text within 3s window)
        if (notificationFilter.isDuplicateContent(pkg, title, text)) return;

        // Rate limiting (max 10 per 10s sliding window)
        if (!notificationFilter.allowRateLimit()) {
            LogBus.warn("notification mirroring rate-limited -- dropping one from " + pkg);
            return;
        }

        ConnectionManager connection = MyvuService.activeConnection();
        if (connection == null) {
            LogBus.warn("not mirroring " + appLabel(pkg) + ": not connected to the glasses");
            return;
        }

        if (!connection.isRelayConnected()) {
            connection.wakeRelay();
            LogBus.warn("app relay is DOWN -- attempting reconnect to deliver notification from " + appLabel(pkg));
        }

        try {
            // Smart text formatting / truncation for lens display (max 120 chars)
            String displayTitle = TextUtils.isEmpty(title) ? appLabel(pkg) : NotificationFilter.truncate(title);
            String displayText = NotificationFilter.truncate(text == null ? "" : text);

            // The id is derived from package + numeric id, NOT sbn.getKey().
            // See Notifications.notificationId -- passing the platform key here
            // made the glasses reboot on every mirrored notification.
            final String notifId = Notifications.notificationId(pkg, sbn.getId());
            JSONObject entry = Notifications.entry(
                    pkg,
                    sbn.getId(),
                    displayTitle,
                    displayText,
                    appLabel(pkg),
                    sbn.getPostTime(),
                    false);
            connection.sendAction(Notifications.buildShow(entry));
            LogBus.log("mirrored notification from " + appLabel(pkg) + ": " + displayTitle);

            int durationSec = com.myvu.client.core.GlassesConfig.getNotificationDuration(this);
            if (durationSec > 0) {
                dismissHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ConnectionManager active = MyvuService.activeConnection();
                            if (active != null) {
                                active.sendAction(Notifications.buildDismiss(notifId));
                            }
                        } catch (Exception ignored) {}
                    }
                }, durationSec * 1000L);
            }
        } catch (Exception e) {
            LogBus.error("could not mirror a notification", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || !Prefs.mirrorEnabled(this)) return;
        // Same gate as the show path -- never dismiss what we never mirrored.
        if (!Prefs.isPackageAllowed(this, sbn.getPackageName())) return;
        ConnectionManager connection = MyvuService.activeConnection();
        if (connection == null) return;
        try {
            // Must match the id used when showing it, or the dismiss is a no-op.
            connection.sendAction(Notifications.buildDismiss(
                    Notifications.notificationId(sbn.getPackageName(), sbn.getId())));
        } catch (Exception e) {
            LogBus.error("could not dismiss a mirrored notification", e);
        }
    }

    // ------------------------------------------------------------ helpers

    private String charSequence(Bundle extras, String key) {
        CharSequence cs = extras.getCharSequence(key);
        return cs != null ? cs.toString() : null;
    }

    private String appLabel(String pkg) {
        try {
            return getPackageManager()
                    .getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0))
                    .toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    // -------------------------------------------------- permission plumbing

    /**
     * Notification access is granted in system settings, not by a runtime
     * dialog, so the UI has to check the state itself and deep-link there.
     */
    /**
     * Ask the system to (re)bind this listener.
     *
     * Reinstalling or updating the app leaves the listener ENABLED but UNBOUND:
     * notification access still shows as granted in system settings, yet
     * onNotificationPosted never fires again, so mirroring silently stops until
     * the user toggles the permission off and on. Requesting a rebind on startup
     * makes that heal itself. No-op when access was never granted.
     */
    public static void requestRebindIfEnabled(Context context) {
        if (!isEnabled(context)) return;
        ComponentName cn = new ComponentName(context, MirrorNotificationListener.class);
        try {
            // requestRebind() alone is NOT enough: it is meant to pair with
            // requestUnbind(), and does nothing when the system dropped us for
            // another reason (an app update). Cycling the component's enabled
            // state forces the system to re-evaluate and bind it again.
            PackageManager pm = context.getPackageManager();
            pm.setComponentEnabledSetting(cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(cn,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            NotificationListenerService.requestRebind(cn);
        } catch (Exception e) {
            LogBus.trace("could not request a listener rebind: " + e);
        }
    }

    public static String getUnreadSummary(String category) {
        MirrorNotificationListener listener = instance;
        if (listener == null) return "No hay acceso a las notificaciones del teléfono.";

        try {
            StatusBarNotification[] sbns = listener.getActiveNotifications();
            if (sbns == null || sbns.length == 0) {
                return "No tienes mensajes ni notificaciones pendientes.";
            }

            StringBuilder sb = new StringBuilder();
            int count = 0;
            String cat = category != null ? category.toLowerCase() : "";

            for (StatusBarNotification sbn : sbns) {
                if (sbn == null || sbn.getNotification() == null) continue;
                String pkg = sbn.getPackageName();
                if (pkg == null) continue;

                boolean matches = false;
                if (cat.contains("email") || cat.contains("correo")) {
                    matches = pkg.contains("outlook") || pkg.contains("gm") || pkg.contains("mail");
                } else if (cat.contains("whatsapp")) {
                    matches = pkg.contains("whatsapp");
                } else if (cat.contains("telegram")) {
                    matches = pkg.contains("telegram");
                } else {
                    matches = true;
                }

                if (!matches) continue;

                Notification n = sbn.getNotification();
                Bundle extras = n.extras;
                if (extras == null) continue;

                CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
                CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);

                String title = titleCs != null ? titleCs.toString() : "";
                String text = textCs != null ? textCs.toString() : "";

                if (title.isEmpty() && text.isEmpty()) continue;

                count++;
                sb.append("- De ").append(title).append(": ").append(text).append("\n");
                if (count >= 8) break;
            }

            if (count == 0) {
                return "No tienes " + (cat.isEmpty() ? "notificaciones" : cat) + " pendientes por leer.";
            }
            return sb.toString();
        } catch (Exception e) {
            LogBus.error("could not fetch active notifications", e);
            return "Error al leer las notificaciones del teléfono.";
        }
    }

    public static boolean isEnabled(Context context) {
        String flat = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(flat)) return false;
        String self = new ComponentName(context, MirrorNotificationListener.class)
                .flattenToString();
        for (String entry : flat.split(":")) {
            if (entry.equals(self)) return true;
        }
        return false;
    }

    public static Intent settingsIntent() {
        return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
    }
}
