package com.myvu.client.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import com.myvu.client.app.feature.Notifications
import com.myvu.client.core.GlassesConfig
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import java.lang.ref.WeakReference
import org.json.JSONObject

/**
 * Mirrors the phone's real notifications onto the lens.
 *
 * This is something the Python client could never do -- it could only push
 * hand-written test notifications. Here we forward actual incoming SMS, chat
 * messages and so on, the way the official app does.
 *
 * Requires the user to grant notification access in system settings; there is
 * no runtime-permission dialog for it (see [isEnabled]).
 */
class MirrorNotificationListener : NotificationListenerService() {

    private val notificationFilter = NotificationFilter()
    private val dismissHandler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        super.onListenerConnected()
        _instance = WeakReference(this)
        LogBus.log("MirrorNotificationListener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!Prefs.mirrorEnabled(this)) return

        val n = sbn.notification ?: return

        // Opt-in only: notifications carry OTPs, 2FA codes and private messages,
        // so nothing is forwarded unless the user picked that app in Settings.
        // isPackageAllowed() also applies the hard block list (system noise, us).
        val pkg = sbn.packageName
        if (!Prefs.isPackageAllowed(this, pkg)) {
            // trace(), not log(): this fires for every notification from every
            // app the user did not opt in, and would drown the on-screen log.
            // It is still in logcat when you need to ask "why not this app?".
            LogBus.trace("not mirroring $pkg: not in the chosen apps")
            return
        }

        val extras = n.extras ?: return
        val title = charSequence(extras, Notification.EXTRA_TITLE)
        val text = charSequence(extras, Notification.EXTRA_TEXT)

        // Filter noise flags (ongoing events, group summaries) and empty content
        if (NotificationFilter.isNoiseOrEmpty(n.flags, title, text)) return

        // Content-based deduplication (identical title + text within 3s window)
        if (notificationFilter.isDuplicateContent(pkg, title, text)) return

        // Rate limiting (max 10 per 10s sliding window)
        if (!notificationFilter.allowRateLimit()) {
            LogBus.warn("notification mirroring rate-limited -- dropping one from $pkg")
            return
        }

        val connection = MyvuService.activeConnection()
        if (connection == null) {
            LogBus.warn("not mirroring ${appLabel(pkg)}: not connected to the glasses")
            return
        }

        if (!connection.isRelayConnected()) {
            connection.wakeRelay()
            LogBus.warn("app relay is DOWN -- attempting reconnect to deliver notification from ${appLabel(pkg)}")
        }

        try {
            // Smart text formatting / truncation for lens display (max 120 chars)
            val displayTitle = if (TextUtils.isEmpty(title)) appLabel(pkg) else NotificationFilter.truncate(title)
            val displayText = NotificationFilter.truncate(text ?: "")

            // The id is derived from package + numeric id, NOT sbn.getKey().
            // See Notifications.notificationId -- passing the platform key here
            // made the glasses reboot on every mirrored notification.
            val notifId = Notifications.notificationId(pkg, sbn.id)
            val entry: JSONObject = Notifications.entry(
                pkg,
                sbn.id,
                displayTitle,
                displayText,
                appLabel(pkg),
                sbn.postTime,
                false
            )
            connection.sendAction(Notifications.buildShow(entry))
            LogBus.log("mirrored notification from ${appLabel(pkg)}: $displayTitle")

            val durationSec = GlassesConfig.getNotificationDuration(this)
            if (durationSec > 0) {
                dismissHandler.postDelayed({
                    try {
                        val active = MyvuService.activeConnection()
                        active?.sendAction(Notifications.buildDismiss(notifId))
                    } catch (ignored: Exception) {
                    }
                }, durationSec * 1000L)
            }
        } catch (e: Exception) {
            LogBus.error("could not mirror a notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null || !Prefs.mirrorEnabled(this)) return
        // Same gate as the show path -- never dismiss what we never mirrored.
        if (!Prefs.isPackageAllowed(this, sbn.packageName)) return
        val connection = MyvuService.activeConnection() ?: return
        try {
            // Must match the id used when showing it, or the dismiss is a no-op.
            connection.sendAction(
                Notifications.buildDismiss(
                    Notifications.notificationId(sbn.packageName, sbn.id)
                )
            )
        } catch (e: Exception) {
            LogBus.error("could not dismiss a mirrored notification", e)
        }
    }

    // ------------------------------------------------------------ helpers

    private fun charSequence(extras: Bundle, key: String): String? {
        val cs = extras.getCharSequence(key)
        return cs?.toString()
    }

    private fun appLabel(pkg: String): String {
        return try {
            packageManager
                .getApplicationLabel(packageManager.getApplicationInfo(pkg, 0))
                .toString()
        } catch (e: Exception) {
            pkg
        }
    }

    companion object {
        @Volatile
        private var _instance: WeakReference<MirrorNotificationListener>? = null

        val instance: MirrorNotificationListener?
            get() = _instance?.get()

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
        @JvmStatic
        fun requestRebindIfEnabled(context: Context) {
            if (!isEnabled(context)) return
            val cn = ComponentName(context, MirrorNotificationListener::class.java)
            try {
                // requestRebind() alone is NOT enough: it is meant to pair with
                // requestUnbind(), and does nothing when the system dropped us for
                // another reason (an app update). Cycling the component's enabled
                // state forces the system to re-evaluate and bind it again.
                val pm = context.packageManager
                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                requestRebind(cn)
            } catch (e: Exception) {
                LogBus.trace("could not request a listener rebind: $e")
            }
        }

        @JvmStatic
        fun getUnreadSummary(category: String?): String {
            val listener = instance ?: return "No hay acceso a las notificaciones del teléfono."

            return try {
                val sbns = listener.activeNotifications
                if (sbns == null || sbns.isEmpty()) {
                    return "No tienes mensajes ni notificaciones pendientes."
                }

                val sb = StringBuilder()
                var count = 0
                val cat = category?.lowercase() ?: ""

                for (sbn in sbns) {
                    if (sbn?.notification == null) continue
                    val pkg = sbn.packageName ?: continue

                    val matches = when {
                        cat.contains("email") || cat.contains("correo") ->
                            pkg.contains("outlook") || pkg.contains("gm") || pkg.contains("mail")
                        cat.contains("whatsapp") -> pkg.contains("whatsapp")
                        cat.contains("telegram") -> pkg.contains("telegram")
                        else -> true
                    }

                    if (!matches) continue

                    val n = sbn.notification
                    val extras = n.extras ?: continue

                    val titleCs = extras.getCharSequence(Notification.EXTRA_TITLE)
                    val textCs = extras.getCharSequence(Notification.EXTRA_TEXT)

                    val title = titleCs?.toString() ?: ""
                    val text = textCs?.toString() ?: ""

                    if (title.isEmpty() && text.isEmpty()) continue

                    count++
                    sb.append("- De ").append(title).append(": ").append(text).append("\n")
                    if (count >= 8) break
                }

                if (count == 0) {
                    "No tienes ${if (cat.isEmpty()) "notificaciones" else cat} pendientes por leer."
                } else {
                    sb.toString()
                }
            } catch (e: Exception) {
                LogBus.error("could not fetch active notifications", e)
                "Error al leer las notificaciones del teléfono."
            }
        }

        @JvmStatic
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            if (TextUtils.isEmpty(flat)) return false
            val self = ComponentName(context, MirrorNotificationListener::class.java).flattenToString()
            for (entry in flat.split(":")) {
                if (entry == self) return true
            }
            return false
        }

        @JvmStatic
        fun settingsIntent(): Intent {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
    }
}
