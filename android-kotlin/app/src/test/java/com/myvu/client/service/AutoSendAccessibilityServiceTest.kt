package com.myvu.client.service

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AutoSendAccessibilityServiceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        AutoSendAccessibilityService.isAutoSendActive = false
        AutoSendAccessibilityService.targetPackage = null
    }

    @Test
    fun testIsMessagingPackageIdentification() {
        // WhatsApp & Business
        assertTrue(AutoSendAccessibilityService.isMessagingPackage("com.whatsapp"))
        assertTrue(AutoSendAccessibilityService.isMessagingPackage("com.whatsapp.w4b"))

        // Telegram
        assertTrue(AutoSendAccessibilityService.isMessagingPackage("org.telegram.messenger"))
        assertTrue(AutoSendAccessibilityService.isMessagingPackage("org.telegram.messenger.web"))

        // SMS / Google Messages / Samsung Messages
        assertTrue(AutoSendAccessibilityService.isMessagingPackage("com.google.android.apps.messaging"))
        assertTrue(AutoSendAccessibilityService.isMessagingPackage("com.samsung.android.messaging"))
        assertTrue(AutoSendAccessibilityService.isMessagingPackage("com.android.mms"))

        // Signal & Facebook Messenger
        assertTrue(AutoSendAccessibilityService.isMessagingPackage("org.thoughtcrime.securesms"))
        assertTrue(AutoSendAccessibilityService.isMessagingPackage("com.facebook.orca"))

        // Non-messaging apps
        assertFalse(AutoSendAccessibilityService.isMessagingPackage("com.google.android.youtube"))
        assertFalse(AutoSendAccessibilityService.isMessagingPackage("com.spotify.music"))
        assertFalse(AutoSendAccessibilityService.isMessagingPackage("com.android.settings"))
    }

    @Test
    fun testTriggerAutoSendWindowDurations() {
        val now = System.currentTimeMillis()

        // Unlocked device default: 15s
        AutoSendAccessibilityService.triggerAutoSend("com.whatsapp", isDeviceLocked = false)
        assertTrue(AutoSendAccessibilityService.isAutoSendActive)
        assertEquals("com.whatsapp", AutoSendAccessibilityService.targetPackage)
        assertTrue(AutoSendAccessibilityService.autoSendExpiresAt >= now + 14000L)
        assertTrue(AutoSendAccessibilityService.autoSendExpiresAt <= now + 16000L)

        // Locked device default: 45s
        AutoSendAccessibilityService.triggerAutoSend("com.google.android.apps.messaging", isDeviceLocked = true)
        assertTrue(AutoSendAccessibilityService.isAutoSendActive)
        assertEquals("com.google.android.apps.messaging", AutoSendAccessibilityService.targetPackage)
        assertTrue(AutoSendAccessibilityService.autoSendExpiresAt >= now + 44000L)
        assertTrue(AutoSendAccessibilityService.autoSendExpiresAt <= now + 46000L)
    }

    @Test
    fun testNullRootSafeReturn() {
        val service = AutoSendAccessibilityService()
        assertFalse(service.findAndClickSendButton(null, "com.whatsapp"))
        assertFalse(service.findAndClickSendButton(null, isWhatsApp = true))
        assertFalse(service.findAndClickSendButton(null, isWhatsApp = false))
    }

    @Test
    fun testAccessibilityServiceCheckSafelyHandlesContext() {
        // Should not crash and return false in test environment when no service is running
        AutoSendAccessibilityService.activeInstance = null
        val isEnabled = AutoSendAccessibilityService.isAccessibilityServiceEnabled(context)
        assertFalse(isEnabled)
    }

    @Test
    fun testAutoEnableIfPermittedAndWatchdog() {
        val enabled = AutoSendAccessibilityService.autoEnableIfPermitted(context)
        assertTrue(enabled)

        AutoSendAccessibilityService.notifyAccessibilityDisabled(context)
        AutoSendAccessibilityService.cancelDisabledNotification(context)

        val result = AutoSendAccessibilityService.checkAndRestoreOrNotify(context)
        // With settings updated, checkAndRestoreOrNotify handles without crashing
        assertNotNull(result)
    }

    @Test
    fun testActiveInstanceBypassesSettingsCheck() {
        AutoSendAccessibilityService.activeInstance = null
        assertFalse(AutoSendAccessibilityService.isAccessibilityServiceEnabled(context))
        val mockService = AutoSendAccessibilityService()
        AutoSendAccessibilityService.activeInstance = mockService
        assertTrue(AutoSendAccessibilityService.isAccessibilityServiceEnabled(context))
        AutoSendAccessibilityService.activeInstance = null
    }
}
