package com.myvu.client.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.myvu.client.core.LogBus

/**
 * Optional Accessibility Service to automatically click the "Send" button when WhatsApp/Telegram
 * is launched by the Myvu AI Assistant.
 */
class AutoSendAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var shouldAutoSendWhatsApp: Boolean = false

        @Volatile
        var shouldAutoSendTelegram: Boolean = false

        fun triggerWhatsAppAutoSend() {
            shouldAutoSendWhatsApp = true
            // Reset flag after 5 seconds to prevent accidental sends
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                shouldAutoSendWhatsApp = false
            }, 5000L)
        }

        fun triggerTelegramAutoSend() {
            shouldAutoSendTelegram = true
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                shouldAutoSendTelegram = false
            }, 5000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            packageNames = arrayOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger")
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        serviceInfo = info
        LogBus.log("AutoSendAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        if ((pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") && shouldAutoSendWhatsApp) {
            findAndClickSendButton(rootInActiveWindow, listOf("send", "com.whatsapp:id/send"), listOf("enviar", "send"))
            shouldAutoSendWhatsApp = false
        } else if (pkg == "org.telegram.messenger" && shouldAutoSendTelegram) {
            findAndClickSendButton(rootInActiveWindow, listOf("send", "org.telegram.messenger:id/send_button"), listOf("enviar", "send"))
            shouldAutoSendTelegram = false
        }
    }

    private fun findAndClickSendButton(root: AccessibilityNodeInfo?, targetIds: List<String>, targetTexts: List<String>): Boolean {
        if (root == null) return false

        // 1. Search by View ID
        for (id in targetIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        LogBus.log("AutoSendAccessibilityService -> Clicked send button by viewId '$id'")
                        return true
                    }
                }
            }
        }

        // 2. Search by Content Description or Text
        for (text in targetTexts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        LogBus.log("AutoSendAccessibilityService -> Clicked send button by text '$text'")
                        return true
                    }
                    var parent = node.parent
                    while (parent != null) {
                        if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            LogBus.log("AutoSendAccessibilityService -> Clicked parent send button by text '$text'")
                            return true
                        }
                        parent = parent.parent
                    }
                }
            }
        }

        return false
    }

    override fun onInterrupt() {}
}
