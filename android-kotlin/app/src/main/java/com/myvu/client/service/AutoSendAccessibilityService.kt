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
            targetPackage = packageName
            autoSendExpiresAt = System.currentTimeMillis() + duration
            LogBus.log("AutoSendAccessibilityService -> Triggered auto-call (pkg='$packageName', locked=$isDeviceLocked, duration=${duration}ms)")
            scheduleBurstCallRetries(shortBurst = false)
        }

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            return try {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                if (am == null || !am.isEnabled) return false
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                enabledServices.contains(context.packageName + "/" + AutoSendAccessibilityService::class.java.canonicalName) ||
                        enabledServices.contains(AutoSendAccessibilityService::class.java.simpleName)
            } catch (e: Exception) {
                false
            }
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (System.currentTimeMillis() > autoSendExpiresAt) {
            isAutoSendActive = false
            isAutoCallActive = false
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

        // 2. Auto Send message button handling
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

            // Anti-voice exclusion: never click microphone / voice recording
            val isVoice = desc.contains("voz") || desc.contains("voice") ||
                    desc.contains("audio") || desc.contains("grabar") ||
                    desc.contains("record") || viewId.contains("voice") ||
                    viewId.contains("mic") || desc == "mensaje de voz" ||
                    desc == "voice message"

            if (!isVoice) {
                val matchesId = targetIds.any { viewId.endsWith(it) || viewId == it }
                val matchesDesc = desc in listOf("enviar", "send", "enviar sms", "enviar mms", "enviar mensaje", "send message") ||
                        desc.startsWith("enviar ") || desc.startsWith("send ")
                val matchesText = text in listOf("enviar", "send", "enviar sms", "send sms")

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

    override fun onInterrupt() {}
}
