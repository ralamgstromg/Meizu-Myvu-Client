package com.myvu.client.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.myvu.client.core.LogBus
import java.util.ArrayDeque

/**
 * Universal Accessibility Service to automatically click the "Send" button when any messaging app
 * (WhatsApp, Telegram, SMS / Google Messages, Samsung Messages, Signal) is launched by the Myvu AI Assistant.
 */
class AutoSendAccessibilityService : AccessibilityService() {

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                LogBus.log("AutoSendAccessibilityService -> Device unlocked (USER_PRESENT) detected! Bursting auto-send retries")
                if (isAutoSendActive) {
                    scheduleBurstRetries(shortBurst = true)
                }
                if (isGeminiLiveActive) {
                    scheduleBurstGeminiLiveRetries()
                }
                if (isGeminiVoiceActive) {
                    scheduleBurstGeminiVoiceRetries()
                }
            }
        }
    }

    private var isReceiverRegistered = false

    companion object {
        @Volatile
        var activeInstance: AutoSendAccessibilityService? = null

        @Volatile
        var isAutoSendActive: Boolean = false

        @Volatile
        var targetPackage: String? = null

        @Volatile
        var autoSendExpiresAt: Long = 0L

        var shouldAutoSendWhatsApp: Boolean
            get() = isAutoSendActive && (targetPackage == null || targetPackage!!.contains("whatsapp"))
            set(v) {
                if (v) triggerWhatsAppAutoSend() else if (targetPackage?.contains("whatsapp") == true) isAutoSendActive = false
            }

        @Volatile
        var isAutoCallActive: Boolean = false

        @Volatile
        var isGeminiLiveActive: Boolean = false

        @Volatile
        var isGeminiVoiceActive: Boolean = false

        var shouldAutoSendTelegram: Boolean
            get() = isAutoSendActive && (targetPackage == null || targetPackage!!.contains("telegram"))
            set(v) {
                if (v) triggerTelegramAutoSend() else if (targetPackage?.contains("telegram") == true) isAutoSendActive = false
            }

        fun isServiceRunning(): Boolean = activeInstance != null

        fun triggerAutoCall(packageName: String = "com.whatsapp", isDeviceLocked: Boolean = false, timeoutMs: Long = 0L) {
            val duration = if (timeoutMs > 0) timeoutMs else if (isDeviceLocked) 45000L else 15000L
            // Asegurar que cualquier envío previo de mensaje pendiente no interfiera con la llamada
            isAutoSendActive = false
            isAutoCallActive = true
            isGeminiLiveActive = false
            isGeminiVoiceActive = false
            targetPackage = packageName
            autoSendExpiresAt = System.currentTimeMillis() + duration
            LogBus.log("AutoSendAccessibilityService -> Triggered auto-call (pkg='$packageName', locked=$isDeviceLocked, duration=${duration}ms)")
            scheduleBurstCallRetries(shortBurst = false)
        }

        fun triggerGeminiLiveAutoStart(isDeviceLocked: Boolean = false, timeoutMs: Long = 0L) {
            val duration = if (timeoutMs > 0) timeoutMs else if (isDeviceLocked) 45000L else 15000L
            isAutoSendActive = false
            isAutoCallActive = false
            isGeminiVoiceActive = false
            isGeminiLiveActive = true
            targetPackage = "com.google.android.apps.bard"
            autoSendExpiresAt = System.currentTimeMillis() + duration
            LogBus.log("AutoSendAccessibilityService -> Triggered Gemini Live auto-start (duration=${duration}ms, ready=${activeInstance != null})")
            scheduleBurstGeminiLiveRetries()
        }

        fun triggerGeminiVoiceAutoStart(isDeviceLocked: Boolean = false, timeoutMs: Long = 0L) {
            val duration = if (timeoutMs > 0) timeoutMs else if (isDeviceLocked) 45000L else 15000L
            isAutoSendActive = false
            isAutoCallActive = false
            isGeminiLiveActive = false
            isGeminiVoiceActive = true
            targetPackage = "com.google.android.apps.bard"
            autoSendExpiresAt = System.currentTimeMillis() + duration
            LogBus.log("AutoSendAccessibilityService -> Triggered Gemini Voice auto-start (duration=${duration}ms, ready=${activeInstance != null})")
            scheduleBurstGeminiVoiceRetries()
        }

        const val NOTIFICATION_ID_ACCESSIBILITY_DISABLED = 9122
        const val CHANNEL_ID_ACCESSIBILITY_ALERT = "myvu_accessibility_alert"
        const val ADB_GRANT_COMMAND = "adb shell pm grant com.myvu.client android.permission.WRITE_SECURE_SETTINGS"

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            if (activeInstance != null) return true
            return try {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                if (am == null || !am.isEnabled) return false
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                enabledServices.contains(context.packageName + "/" + AutoSendAccessibilityService::class.java.canonicalName) ||
                        enabledServices.contains(context.packageName + "/" + AutoSendAccessibilityService::class.java.name) ||
                        enabledServices.contains(AutoSendAccessibilityService::class.java.simpleName)
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Automatically reactivates the Accessibility Service programmatically if the application
         * has been granted android.permission.WRITE_SECURE_SETTINGS via ADB or root.
         */
        fun autoEnableIfPermitted(context: Context): Boolean {
            return try {
                val cr = context.contentResolver
                val serviceComponent = "${context.packageName}/${AutoSendAccessibilityService::class.java.name}"
                val enabledServices = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                val success = if (!enabledServices.contains(serviceComponent)) {
                    val newServices = if (enabledServices.isBlank()) serviceComponent else "$enabledServices:$serviceComponent"
                    Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newServices)
                } else {
                    true
                }
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                LogBus.log("AutoSendAccessibilityService -> Successfully auto-enabled accessibility service via Secure Settings (success=$success)")
                cancelDisabledNotification(context)
                true
            } catch (e: SecurityException) {
                LogBus.warn("AutoSendAccessibilityService -> Cannot auto-enable without WRITE_SECURE_SETTINGS: ${e.message}")
                false
            } catch (e: Exception) {
                LogBus.warn("AutoSendAccessibilityService -> Unexpected error auto-enabling service: ${e.message}")
                false
            }
        }

        /**
         * Posts a high-priority heads-up notification directing the user directly to the
         * Accessibility Settings screen so they can re-enable the service with a single tap.
         */
        fun notifyAccessibilityDisabled(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID_ACCESSIBILITY_ALERT,
                        "MYVU Alertas de Accesibilidad",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Notificaciones para reactivar el servicio de accesibilidad tras actualizaciones"
                        setShowBadge(true)
                    }
                    nm.createNotificationChannel(channel)
                }

                val openSettingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID_ACCESSIBILITY_DISABLED,
                    openSettingsIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ID_ACCESSIBILITY_ALERT)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("⚠️ MYVU Auto-Send Assistant desactivado")
                    .setContentText("Toca para reactivar el servicio tras la actualización")
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText("Android desactivó el servicio de accesibilidad de MYVU al actualizar. Toca aquí para reactivarlo en Ajustes y mantener el envío automático de WhatsApp/Telegram.")
                    )
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()

                nm.notify(NOTIFICATION_ID_ACCESSIBILITY_DISABLED, notification)
                LogBus.log("AutoSendAccessibilityService -> Posted notification for disabled accessibility service")
            } catch (e: Exception) {
                LogBus.warn("AutoSendAccessibilityService -> Failed to post accessibility disabled notification: ${e.message}")
            }
        }

        fun cancelDisabledNotification(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID_ACCESSIBILITY_DISABLED)
            } catch (e: Exception) {
                // Ignored
            }
        }

        /**
         * Comprehensive watchdog: Checks if the service is running. If not, attempts silent auto-enable
         * via WRITE_SECURE_SETTINGS. If still disabled, posts a heads-up notification.
         */
        fun checkAndRestoreOrNotify(context: Context): Boolean {
            if (isAccessibilityServiceEnabled(context)) {
                cancelDisabledNotification(context)
                return true
            }
            val restored = autoEnableIfPermitted(context)
            if (restored && isAccessibilityServiceEnabled(context)) {
                cancelDisabledNotification(context)
                return true
            }
            notifyAccessibilityDisabled(context)
            return false
        }

        fun triggerWhatsAppAutoSend(isLocked: Boolean = false) {
            triggerAutoSend("com.whatsapp", isDeviceLocked = isLocked)
        }

        fun triggerTelegramAutoSend(isLocked: Boolean = false) {
            triggerAutoSend("org.telegram.messenger", isDeviceLocked = isLocked)
        }

        fun triggerSmsAutoSend(isLocked: Boolean = false) {
            triggerAutoSend("com.google.android.apps.messaging", isDeviceLocked = isLocked)
        }

        /**
         * Universal auto-send trigger for any messaging app.
         * Kept active for 45s if device is locked (waiting for user biometric/PIN unlock),
         * or 15s if device is already unlocked.
         */
        fun triggerAutoSend(packageName: String? = null, isDeviceLocked: Boolean = false, timeoutMs: Long = 0L) {
            val duration = if (timeoutMs > 0) timeoutMs else if (isDeviceLocked) 45000L else 15000L
            isAutoSendActive = true
            targetPackage = packageName
            autoSendExpiresAt = System.currentTimeMillis() + duration
            LogBus.log("AutoSendAccessibilityService -> Triggered universal auto-send (pkg='$packageName', locked=$isDeviceLocked, duration=${duration}ms, serviceReady=${activeInstance != null})")

            scheduleBurstRetries(shortBurst = false)

            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                if (isAutoSendActive && System.currentTimeMillis() >= autoSendExpiresAt) {
                    LogBus.log("AutoSendAccessibilityService -> Auto-send window timed out for '$packageName'")
                    isAutoSendActive = false
                    targetPackage = null
                }
            }, duration)
        }

        fun scheduleBurstRetries(shortBurst: Boolean = false) {
            val delays = if (shortBurst) {
                longArrayOf(100L, 250L, 500L, 900L, 1400L, 2100L, 3000L)
            } else {
                longArrayOf(300L, 700L, 1200L, 1800L, 2600L, 3600L, 5000L, 7000L, 10000L, 14000L, 19000L, 25000L, 32000L, 40000L)
            }
            val handler = Handler(Looper.getMainLooper())
            for (d in delays) {
                handler.postDelayed({
                    if (isAutoSendActive && System.currentTimeMillis() < autoSendExpiresAt) {
                        activeInstance?.let { service ->
                            try {
                                val root = service.rootInActiveWindow
                                if (root != null) {
                                    val clicked = service.findAndClickSendButton(root, targetPackage)
                                    if (clicked) {
                                        isAutoSendActive = false
                                        targetPackage = null
                                    }
                                }
                            } catch (e: Exception) {
                                LogBus.warn("AutoSendAccessibilityService -> Retry click failed: ${e.message}")
                            }
                        }
                    }
                }, d)
            }
        }

        fun scheduleBurstCallRetries(shortBurst: Boolean = false) {
            val delays = if (shortBurst) {
                longArrayOf(200L, 500L, 1000L, 1600L, 2500L, 3500L)
            } else {
                longArrayOf(400L, 900L, 1500L, 2200L, 3000L, 4500L, 6500L, 9000L, 12000L, 16000L, 22000L)
            }
            val handler = Handler(Looper.getMainLooper())
            for (d in delays) {
                handler.postDelayed({
                    if (isAutoCallActive && System.currentTimeMillis() < autoSendExpiresAt) {
                        activeInstance?.let { service ->
                            try {
                                val root = service.rootInActiveWindow
                                if (root != null) {
                                    val clicked = service.findAndClickCallButton(root, targetPackage)
                                    if (clicked) {
                                        isAutoCallActive = false
                                        targetPackage = null
                                    }
                                }
                            } catch (e: Exception) {
                                LogBus.warn("AutoSendAccessibilityService -> Retry call click failed: ${e.message}")
                            }
                        }
                    }
                }, d)
            }
        }

        fun isGeminiAppWindow(pkg: String?): Boolean {
            if (pkg == null) return false
            val p = pkg.lowercase()
            if (p.contains("launcher")) return false
            return p.contains("bard") || p.contains("googlequicksearchbox")
        }

        fun scheduleBurstGeminiLiveRetries() {
            val delays = longArrayOf(150L, 350L, 700L, 1200L, 1800L, 2500L, 3500L, 5000L, 7000L, 9500L, 12500L)
            val handler = Handler(Looper.getMainLooper())
            for (d in delays) {
                handler.postDelayed({
                    if (isGeminiLiveActive && System.currentTimeMillis() < autoSendExpiresAt) {
                        activeInstance?.let { service ->
                            try {
                                var clicked = false
                                val root = service.rootInActiveWindow
                                val rootPkg = root?.packageName?.toString() ?: ""
                                if (root != null && isGeminiAppWindow(rootPkg)) {
                                    clicked = service.findAndClickGeminiLiveButton(root)
                                }
                                if (!clicked) {
                                    for (window in service.windows) {
                                        val wRoot = window.root ?: continue
                                        val wPkg = wRoot.packageName?.toString() ?: ""
                                        if (isGeminiAppWindow(wPkg)) {
                                            if (service.findAndClickGeminiLiveButton(wRoot)) {
                                                clicked = true
                                                break
                                            }
                                        }
                                    }
                                }
                                if (clicked) {
                                    isGeminiLiveActive = false
                                    targetPackage = null
                                }
                            } catch (e: Exception) {
                                LogBus.warn("AutoSendAccessibilityService -> Retry Gemini Live click failed: ${e.message}")
                            }
                        }
                    }
                }, d)
            }
        }

        fun scheduleBurstGeminiVoiceRetries() {
            val delays = longArrayOf(150L, 350L, 700L, 1200L, 1800L, 2500L, 3500L, 5000L, 7000L, 9500L, 12500L)
            val handler = Handler(Looper.getMainLooper())
            for (d in delays) {
                handler.postDelayed({
                    if (isGeminiVoiceActive && System.currentTimeMillis() < autoSendExpiresAt) {
                        activeInstance?.let { service ->
                            try {
                                var clicked = false
                                val root = service.rootInActiveWindow
                                val rootPkg = root?.packageName?.toString() ?: ""
                                if (root != null && isGeminiAppWindow(rootPkg)) {
                                    clicked = service.findAndClickGeminiMicButton(root)
                                }
                                if (!clicked) {
                                    for (window in service.windows) {
                                        val wRoot = window.root ?: continue
                                        val wPkg = wRoot.packageName?.toString() ?: ""
                                        if (isGeminiAppWindow(wPkg)) {
                                            if (service.findAndClickGeminiMicButton(wRoot)) {
                                                clicked = true
                                                break
                                            }
                                        }
                                    }
                                }
                                if (clicked) {
                                    isGeminiVoiceActive = false
                                    targetPackage = null
                                }
                            } catch (e: Exception) {
                                LogBus.warn("AutoSendAccessibilityService -> Retry Gemini Voice click failed: ${e.message}")
                            }
                        }
                    }
                }, d)
            }
        }

        fun isMessagingPackage(pkg: String): Boolean {
            val p = pkg.lowercase()
            return p.contains("whatsapp") ||
                    p.contains("telegram") ||
                    p.contains("messaging") ||
                    p.contains("mms") ||
                    p.contains("securesms") ||
                    p.contains("orca")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        cancelDisabledNotification(this)
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            packageNames = null // Listen to all apps
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info

        try {
            if (!isReceiverRegistered) {
                val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(unlockReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    registerReceiver(unlockReceiver, filter)
                }
                isReceiverRegistered = true
                LogBus.log("AutoSendAccessibilityService -> Registered unlock broadcast receiver")
            }
        } catch (e: Exception) {
            LogBus.warn("AutoSendAccessibilityService -> Could not register unlock receiver: ${e.message}")
        }

        LogBus.log("AutoSendAccessibilityService connected and ready (Universal Mode)")
    }

    override fun onInterrupt() {
        LogBus.warn("AutoSendAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance == this) {
            activeInstance = null
        }
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(unlockReceiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                // Ignore
            }
        }
        LogBus.log("AutoSendAccessibilityService destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (System.currentTimeMillis() > autoSendExpiresAt) {
            isAutoSendActive = false
            isAutoCallActive = false
            isGeminiLiveActive = false
            isGeminiVoiceActive = false
            targetPackage = null
            return
        }

        val pkg = event.packageName?.toString() ?: return
        val target = targetPackage

        // 1. Auto Call button handling
        if (isAutoCallActive) {
            val isTargetCallApp = target == null || pkg == target || pkg.contains(target) || target.contains(pkg)
            if (isTargetCallApp) {
                val root = rootInActiveWindow ?: return
                val clicked = findAndClickCallButton(root, pkg)
                if (clicked) {
                    isAutoCallActive = false
                    targetPackage = null
                }
            }
            return
        }

        // 2. Gemini Live button handling
        if (isGeminiLiveActive) {
            if (isGeminiAppWindow(pkg)) {
                var clicked = false
                val root = rootInActiveWindow
                if (root != null) {
                    clicked = findAndClickGeminiLiveButton(root)
                }
                if (!clicked && event.source != null) {
                    clicked = findAndClickGeminiLiveButton(event.source)
                }
                if (!clicked) {
                    for (window in windows) {
                        val wRoot = window.root ?: continue
                        val wPkg = wRoot.packageName?.toString() ?: ""
                        if (isGeminiAppWindow(wPkg)) {
                            if (findAndClickGeminiLiveButton(wRoot)) {
                                clicked = true
                                break
                            }
                        }
                    }
                }
                if (clicked) {
                    isGeminiLiveActive = false
                    targetPackage = null
                }
            }
            return
        }

        // 3. Gemini Voice Mic button handling
        if (isGeminiVoiceActive) {
            if (isGeminiAppWindow(pkg)) {
                var clicked = false
                val root = rootInActiveWindow
                if (root != null) {
                    clicked = findAndClickGeminiMicButton(root)
                }
                if (!clicked && event.source != null) {
                    clicked = findAndClickGeminiMicButton(event.source)
                }
                if (!clicked) {
                    for (window in windows) {
                        val wRoot = window.root ?: continue
                        val wPkg = wRoot.packageName?.toString() ?: ""
                        if (isGeminiAppWindow(wPkg)) {
                            if (findAndClickGeminiMicButton(wRoot)) {
                                clicked = true
                                break
                            }
                        }
                    }
                }
                if (clicked) {
                    isGeminiVoiceActive = false
                    targetPackage = null
                }
            }
            return
        }

        // 4. Auto Send message button handling
        if (!isAutoSendActive) return
        val isTargetApp = target == null || pkg == target || pkg.contains(target) || target.contains(pkg) || isMessagingPackage(pkg)
        if (isTargetApp) {
            val root = rootInActiveWindow ?: return
            val clicked = findAndClickSendButton(root, pkg)
            if (clicked) {
                isAutoSendActive = false
                targetPackage = null
            }
        }
    }

    fun findAndClickGeminiLiveButton(root: AccessibilityNodeInfo?, targetPkg: String? = null): Boolean {
        if (root == null) return false
        val rootPkg = root.packageName?.toString()?.lowercase() ?: ""
        if (rootPkg.contains("launcher")) {
            return false
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        val targetLiveIds = listOf(
            "live_button", "btn_live", "gemini_live", "live", "waveform", "mic_live",
            "sparkle", "action_live", "voice_mode", "live_chat_button", "voice_sheet_live_entrypoint",
            "live_fab", "live_entrypoint", "gemini_live_button"
        )

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            // Always enqueue children first so no subtrees are pruned
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }

            val nodePkg = node.packageName?.toString()?.lowercase() ?: ""
            if (nodePkg.contains("launcher")) {
                continue
            }

            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            if (viewId.contains("search_widget") || viewId.contains("ghost_voice") || viewId.contains("widget_ghost")) {
                continue
            }

            val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            val text = node.text?.toString()?.lowercase()?.trim() ?: ""

            val matchesId = targetLiveIds.any { viewId.endsWith(it) || viewId.contains(it) }
            val matchesDesc = desc in listOf(
                "live", "gemini live", "iniciar live", "live chat", "conversación live",
                "hablar en directo", "conversación en tiempo real", "en vivo", "abrir live",
                "start live", "live voice", "modo conversación", "open gemini live"
            ) || desc.startsWith("live") || desc.contains("gemini live") || desc.contains("open gemini live") ||
                    desc.contains("waveform")
            val matchesText = text in listOf("live", "gemini live", "iniciar live", "en vivo", "live chat")

            if (matchesId || matchesDesc || matchesText) {
                var clicked = false
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    LogBus.log("AutoSendAccessibilityService -> Clicked Gemini Live button via ACTION_CLICK (viewId='$viewId', desc='$desc', text='$text')")
                    clicked = true
                }
                if (!clicked) {
                    var parent = node.parent
                    var depth = 0
                    while (parent != null && depth < 4) {
                        if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            LogBus.log("AutoSendAccessibilityService -> Clicked parent Gemini Live button via ACTION_CLICK (viewId='$viewId', desc='$desc')")
                            clicked = true
                            break
                        }
                        parent = parent.parent
                        depth++
                    }
                }

                // Coordinate tap for Jetpack Compose support
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0) {
                        val path = Path().apply {
                            moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
                        }
                        val gesture = GestureDescription.Builder()
                            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
                            .build()
                        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                            override fun onCompleted(gestureDescription: GestureDescription?) {
                                LogBus.log("AutoSendAccessibilityService -> Coordinate tap completed on Gemini Live at (${rect.centerX()}, ${rect.centerY()})")
                            }
                            override fun onCancelled(gestureDescription: GestureDescription?) {
                                LogBus.warn("AutoSendAccessibilityService -> Coordinate tap cancelled on Gemini Live at (${rect.centerX()}, ${rect.centerY()})")
                            }
                        }, null)
                        if (dispatched) {
                            LogBus.log("AutoSendAccessibilityService -> Dispatched coordinate tap on Gemini Live button at (${rect.centerX()}, ${rect.centerY()})")
                            clicked = true
                        }
                    }
                }

                if (clicked) {
                    return true
                }
            }
        }
        return false
    }

    fun findAndClickGeminiMicButton(root: AccessibilityNodeInfo?, targetPkg: String? = null): Boolean {
        if (root == null) return false
        val rootPkg = root.packageName?.toString()?.lowercase() ?: ""
        if (rootPkg.contains("launcher")) {
            return false
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        val targetMicIds = listOf(
            "mic", "microphone", "voice_input", "btn_mic",
            "dictation", "mic_button", "voice_action", "voice_button", "dictation_button",
            "text_input_voice_icon", "chat_input_voice_button",
            "sparkle_mic", "audio_input", "record_audio", "speech_to_text", "voice_fab",
            "mic_icon", "action_mic"
        )

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            // Always enqueue children first so no subtrees are pruned
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }

            val nodePkg = node.packageName?.toString()?.lowercase() ?: ""
            if (nodePkg.contains("launcher")) {
                continue
            }

            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            if (viewId.contains("search_widget") || viewId.contains("ghost_voice") || viewId.contains("widget_ghost")) {
                continue
            }

            val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            val text = node.text?.toString()?.lowercase()?.trim() ?: ""

            // Exclude Google Search widget text/desc (e.g. "búsqueda por voz", "buscar por voz", "voice search")
            if (desc == "búsqueda por voz" || desc == "voice search" || desc == "buscar por voz" ||
                text == "búsqueda por voz" || text == "voice search") {
                continue
            }

            val matchesId = targetMicIds.any { viewId.endsWith(it) || viewId.contains(it) }
            val matchesDesc = desc in listOf(
                "mic", "micrófono", "hablar", "entrada de voz", "dictar",
                "usar micrófono", "usar el micrófono", "abrir micrófono",
                "dictado por voz", "voice input", "use microphone", "use mic",
                "record audio", "speak", "tap to speak",
                "grabar audio", "pulsar para hablar", "habla", "dictado"
            ) || desc.startsWith("usar el mic") || desc.startsWith("use mic") ||
                    desc.contains("micrófono") || desc.contains("microphone") ||
                    desc.contains("entrada de voz") || desc.contains("voice input") ||
                    desc.contains("habla para") || desc.contains("speak to")
            val matchesText = text in listOf("hablar", "dictar", "mic", "speak", "escuchar") ||
                    text.contains("micrófono") || text.contains("habla")

            if (matchesId || matchesDesc || matchesText) {
                var clicked = false
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    LogBus.log("AutoSendAccessibilityService -> Clicked Gemini Voice mic button via ACTION_CLICK (viewId='$viewId', desc='$desc', text='$text')")
                    clicked = true
                }
                if (!clicked) {
                    var parent = node.parent
                    var depth = 0
                    while (parent != null && depth < 4) {
                        if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            LogBus.log("AutoSendAccessibilityService -> Clicked parent Gemini Voice mic button via ACTION_CLICK (viewId='$viewId', desc='$desc')")
                            clicked = true
                            break
                        }
                        parent = parent.parent
                        depth++
                    }
                }

                // Coordinate tap for Jetpack Compose support
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0) {
                        val path = Path().apply {
                            moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
                        }
                        val gesture = GestureDescription.Builder()
                            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
                            .build()
                        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                            override fun onCompleted(gestureDescription: GestureDescription?) {
                                LogBus.log("AutoSendAccessibilityService -> Coordinate tap completed on Gemini mic at (${rect.centerX()}, ${rect.centerY()})")
                            }
                            override fun onCancelled(gestureDescription: GestureDescription?) {
                                LogBus.warn("AutoSendAccessibilityService -> Coordinate tap cancelled on Gemini mic at (${rect.centerX()}, ${rect.centerY()})")
                            }
                        }, null)
                        if (dispatched) {
                            LogBus.log("AutoSendAccessibilityService -> Dispatched coordinate tap on Gemini mic button at (${rect.centerX()}, ${rect.centerY()})")
                            clicked = true
                        }
                    }
                }

                if (clicked) {
                    return true
                }
            }
        }
        return false
    }

    fun findAndClickCallButton(root: AccessibilityNodeInfo?, targetPkg: String? = null): Boolean {
        if (root == null) return false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        val targetCallIds = listOf(
            "voice_call", "video_call", "call", "btn_call", "action_call", "menu_call", "call_button",
            "com.whatsapp:id/voice_call", "com.whatsapp:id/video_call", "com.whatsapp.w4b:id/voice_call",
            "org.telegram.messenger:id/menu_call"
        )

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            val text = node.text?.toString()?.lowercase()?.trim() ?: ""

            val matchesId = targetCallIds.any { viewId.endsWith(it) || viewId == it }
            val matchesDesc = desc in listOf("llamada de voz", "llamada", "llamar", "voice call", "call", "iniciar llamada", "audio call") ||
                    desc.startsWith("llamar a") || desc.startsWith("llamada a")
            val matchesText = text in listOf("llamar", "call", "iniciar llamada")

            if (matchesId || matchesDesc || matchesText) {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    LogBus.log("AutoSendAccessibilityService -> Clicked voice call button (viewId='$viewId', desc='$desc', text='$text')")
                    return true
                }
                var parent = node.parent
                var depth = 0
                while (parent != null && depth < 4) {
                    if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        LogBus.log("AutoSendAccessibilityService -> Clicked parent voice call button (viewId='$viewId', desc='$desc')")
                        return true
                    }
                    parent = parent.parent
                    depth++
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }
        }
        return false
    }

    fun findAndClickSendButton(root: AccessibilityNodeInfo?, isWhatsApp: Boolean): Boolean {
        return findAndClickSendButton(root, if (isWhatsApp) "com.whatsapp" else "org.telegram.messenger")
    }

    fun findAndClickSendButton(root: AccessibilityNodeInfo?, targetPkg: String? = null): Boolean {
        if (root == null) return false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        val targetIds = listOf(
            "send", "send_button", "conversation_send_button",
            "send_message_button", "send_message_button_icon", "compose_send_button",
            "btn_send", "action_send",
            "com.whatsapp:id/send", "com.whatsapp.w4b:id/send",
            "org.telegram.messenger:id/send_button",
            "com.google.android.apps.messaging:id/send_message_button_icon",
            "com.google.android.apps.messaging:id/send_message_button",
            "com.samsung.android.messaging:id/send_button"
        )

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            val text = node.text?.toString()?.lowercase()?.trim() ?: ""

            // Anti-voice exclusion: never click microphone / voice recording buttons
            val isVoice = desc.contains("voz") || desc.contains("voice") ||
                    desc.contains("audio") || desc.contains("grabar") ||
                    desc.contains("record") || viewId.contains("voice") ||
                    viewId.contains("mic") || desc == "mensaje de voz" ||
                    desc == "voice message"

            // Anti-invitation exclusion: never click the WhatsApp "Enviar invitación por SMS" button
            // that appears when the recipient does not have WhatsApp.
            val isInvitation = desc.contains("invitación") || desc.contains("invitation") ||
                    text.contains("invitación") || text.contains("invitation") ||
                    text == "enviar invitación por sms" || text == "send invitation via sms" ||
                    viewId.contains("invite")

            if (!isVoice && !isInvitation) {
                val matchesId = targetIds.any { viewId.endsWith(it) || viewId == it }
                val matchesDesc = desc in listOf("enviar", "send", "enviar mensaje", "send message") ||
                        desc.startsWith("enviar ") || desc.startsWith("send ")
                val matchesText = text in listOf("enviar", "send", "enviar mensaje", "send message")

                if (matchesId || matchesDesc || matchesText) {
                    // 1. Direct node click
                    if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        LogBus.log("AutoSendAccessibilityService -> Clicked send button (viewId='$viewId', desc='$desc', text='$text')")
                        return true
                    }

                    // 2. Traversal of clickable parents
                    var parent = node.parent
                    var depth = 0
                    while (parent != null && depth < 4) {
                        if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            LogBus.log("AutoSendAccessibilityService -> Clicked parent send button (viewId='$viewId', desc='$desc')")
                            return true
                        }
                        parent = parent.parent
                        depth++
                    }

                    // 3. Fallback gesture dispatch (direct coordinate tap on button center)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (rect.width() > 0 && rect.height() > 0) {
                            val path = Path().apply {
                                moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
                            }
                            val gesture = GestureDescription.Builder()
                                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                                .build()
                            val dispatched = dispatchGesture(gesture, null, null)
                            if (dispatched) {
                                LogBus.log("AutoSendAccessibilityService -> Dispatched gesture tap at (${rect.centerX()}, ${rect.centerY()})")
                                return true
                            }
                        }
                    }
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }
        }

        return false
    }
}
