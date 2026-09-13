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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val notificationFilter = NotificationFilter()
    private val dismissHandler = Handler(Looper.getMainLooper())
    private val pendingDismisses = java.util.concurrent.ConcurrentHashMap<String, Runnable>()
    private val recentlyDismissed = java.util.concurrent.ConcurrentHashMap<String, Long>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        _instance = WeakReference(this)
        LogBus.log("MirrorNotificationListener connected")
        scanActiveHealthNotifications()
    }

    fun scanActiveHealthNotifications() {
        try {
            val active = activeNotifications ?: return
            for (sbn in active) {
                val pkg = sbn.packageName
                val n = sbn.notification ?: continue
                val ext = n.extras ?: continue
                val t = charSequence(ext, Notification.EXTRA_TITLE)
                val txt = charSequence(ext, Notification.EXTRA_TEXT)
                val big = charSequence(ext, Notification.EXTRA_BIG_TEXT)
                val sub = charSequence(ext, Notification.EXTRA_SUB_TEXT)
                val combined = "$t $txt $big $sub"
                com.myvu.client.health.HealthService.getInstance(this)
                    .parseNotificationForHealthMetrics(pkg, t, combined)
            }
        } catch (e: Exception) {
            LogBus.warn("MirrorNotificationListener -> Error scanning active health notifications: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!Prefs.mirrorEnabled(this)) return

        val n = sbn.notification ?: return

        // Opt-in only: notifications carry OTPs, 2FA codes and private messages,
        // so nothing is forwarded unless the user picked that app in Settings.
        // isPackageAllowed() also applies the hard block list (system noise, us).
        val pkg = sbn.packageName

        // Sync health metrics from wearable notifications even if not mirrored to HUD
        n.extras?.let { ext ->
            val t = charSequence(ext, Notification.EXTRA_TITLE)
            val txt = charSequence(ext, Notification.EXTRA_TEXT)
            val big = charSequence(ext, Notification.EXTRA_BIG_TEXT)
            val sub = charSequence(ext, Notification.EXTRA_SUB_TEXT)
            val combined = "$t $txt $big $sub"
            try {
                com.myvu.client.health.HealthService.getInstance(this)
                    .parseNotificationForHealthMetrics(pkg, t, combined)
            } catch (ignored: Exception) {}
        }

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

        // Route notifications according to each connected device's configuration:
        // - Visual (HUD): Shown on Smart Glasses if visual notifications are enabled for the glasses.
        // - Audio (TTS): Spoken aloud if any active connected device has audio/TTS notifications enabled.
        serviceScope.launch {
            try {
                val db = com.myvu.client.database.AppDatabase.getInstance(this@MirrorNotificationListener)
                val dao = db.bluetoothDeviceDao()
                val connectedDevices = dao.getConnectedDevices()
                val activeDevices = if (connectedDevices.isNotEmpty()) connectedDevices else listOfNotNull(dao.getActiveConnectedDevice())

                // 1. Audio handling (TTS Readout)
                val audioDevice = activeDevices.firstOrNull { it.isAudioNotificationEnabled() }
                if (audioDevice != null) {
                    val app = appLabel(pkg)
                    val ttsText = if (!title.isNullOrBlank() && !text.isNullOrBlank()) {
                        "De $app: $title. $text"
                    } else if (!title.isNullOrBlank()) {
                        "De $app: $title"
                    } else {
                        "Notificación de $app: $text"
                    }
                    com.myvu.client.core.TextToSpeechHelper.init(this@MirrorNotificationListener)
                    com.myvu.client.core.TextToSpeechHelper.speak(ttsText)
                    LogBus.log("TTS read notification for [${audioDevice.name}] (mode=${audioDevice.notificationMode}) from $app: $ttsText")
                }

                // 2. Visual handling (Glasses HUD Display)
                val connection = MyvuService.activeConnection()
                if (connection == null) {
                    LogBus.trace("not mirroring ${appLabel(pkg)} to glasses: not connected to glasses")
                    return@launch
                }

                val glassesDevice = activeDevices.find { it.deviceType == com.myvu.client.data.BluetoothDeviceType.SMART_GLASSES.name }
                    ?: dao.getConnectedDeviceByType(com.myvu.client.data.BluetoothDeviceType.SMART_GLASSES.name)
                    ?: dao.getAllDevices().find { it.deviceType == com.myvu.client.data.BluetoothDeviceType.SMART_GLASSES.name }

                val shouldShowVisual = glassesDevice?.isVisualNotificationEnabled() ?: true
                if (!shouldShowVisual) {
                    LogBus.log("Smart glasses notification visual handling is disabled (${glassesDevice?.notificationMode}) -- skipping HUD mirror")
                    return@launch
                }

                // Deliver formatted notification to glasses HUD
                val displayTitle = if (TextUtils.isEmpty(title)) appLabel(pkg) else NotificationFilter.truncate(title)
                val displayText = NotificationFilter.truncate(text ?: "")
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
                LogBus.log("mirrored notification to HUD from ${appLabel(pkg)}: $displayTitle")

                val durationSec = GlassesConfig.getNotificationDuration(this@MirrorNotificationListener)
                if (durationSec > 0) {
                    pendingDismisses.remove(notifId)?.let { dismissHandler.removeCallbacks(it) }
                    val runnable = Runnable {
                        pendingDismisses.remove(notifId)
                        val active = MyvuService.activeConnection()
                        sendDismissSafely(active, notifId)
                    }
                    pendingDismisses[notifId] = runnable
                    dismissHandler.postDelayed(runnable, durationSec * 1000L)
                }
            } catch (e: Exception) {
                LogBus.error("could not process or mirror notification", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null || !Prefs.mirrorEnabled(this)) return
        // Same gate as the show path -- never dismiss what we never mirrored.
        if (!Prefs.isPackageAllowed(this, sbn.packageName)) return
        val notifId = Notifications.notificationId(sbn.packageName, sbn.id)
        pendingDismisses.remove(notifId)?.let { dismissHandler.removeCallbacks(it) }
        val connection = MyvuService.activeConnection() ?: return
        sendDismissSafely(connection, notifId)
    }

    private fun sendDismissSafely(connection: ConnectionManager?, notifId: String) {
        if (connection == null) return
        val now = System.currentTimeMillis()
        val last = recentlyDismissed[notifId] ?: 0L
        if (now - last < 2500L) {
            return // Evitar ráfagas duplicadas de DISMISS para el mismo ID dentro de 2.5s
        }
        recentlyDismissed[notifId] = now
        if (recentlyDismissed.size > 50) {
            recentlyDismissed.entries.removeIf { now - it.value > 10000L }
        }
        try {
            connection.sendAction(Notifications.buildDismiss(notifId))
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
            val listener = instance ?: return "Activa el acceso a notificaciones en los ajustes del teléfono para leer tus mensajes pendientes."

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
                    if (sbn.isOngoing) continue // Ignorar reproductores multimedia y servicios en curso
                    val pkg = sbn.packageName ?: continue

                    val matches = when {
                        cat.contains("email") || cat.contains("correo") ->
                            pkg.contains("outlook") || pkg.contains("gm") || pkg.contains("mail")
                        cat.contains("whatsapp") -> pkg.contains("whatsapp")
                        cat.contains("telegram") -> pkg.contains("telegram")
                        else -> !pkg.contains("com.myvu.client") && !pkg.contains("android")
                    }

                    if (!matches) continue

                    val n = sbn.notification
                    val extras = n.extras ?: continue

                    // Extraer título y remitente
                    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                        ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim()
                        ?: ""

                    // Extraer cuerpo principal o mensajes de chat de WhatsApp/Telegram
                    var body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
                        ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
                        ?: ""

                    // Si es un chat con múltiples líneas (InboxStyle o MessagingStyle)
                    if (body.isEmpty()) {
                        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                        if (textLines != null && textLines.isNotEmpty()) {
                            body = textLines.filterNotNull().joinToString(". ") { it.toString().trim() }
                        }
                    }

                    if (title.isEmpty() && body.isEmpty()) continue

                    count++
                    val appName = try {
                        val pm = listener.packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (ignored: Exception) {
                        if (pkg.contains("whatsapp")) "WhatsApp" else if (pkg.contains("telegram")) "Telegram" else "Notificación"
                    }

                    val prefix = if (title.isNotBlank()) title else appName
                    sb.append("• ").append(prefix)
                    if (body.isNotBlank()) {
                        sb.append(": ").append(body)
                    }
                    sb.append("\n")

                    if (count >= 6) break
                }

                val catPlural = when (cat) {
                    "correo", "correos", "email" -> "correos"
                    "mensaje", "mensajes" -> "mensajes"
                    "" -> "notificaciones"
                    else -> if (cat.endsWith("s")) cat else "${cat}s"
                }
                val catSingular = when (cat) {
                    "correo", "correos", "email" -> "correo"
                    "mensaje", "mensajes" -> "mensaje"
                    "notificaciones", "" -> "notificación"
                    else -> if (cat.endsWith("s")) cat.substring(0, cat.length - 1) else cat
                }

                if (count == 0) {
                    "No tienes $catPlural pendientes por leer."
                } else if (count == 1) {
                    "Tienes 1 $catSingular pendiente:\n" + sb.toString().trim()
                } else {
                    "Tienes $count $catPlural pendientes:\n" + sb.toString().trim()
                }
            } catch (e: Exception) {
                LogBus.error("could not fetch active notifications", e)
                "Error al consultar las notificaciones del teléfono."
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

        @JvmStatic
        fun syncActiveHealth(context: Context) {
            val listener = _instance?.get() ?: return
            listener.scanActiveHealthNotifications()
        }
    }
}
