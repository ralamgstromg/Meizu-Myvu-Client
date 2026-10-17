package com.myvu.client.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationKeyTest {

    @Test
    fun sameTextBuiltAtDifferentTimesHasSameKey() {
        val first = AppLayer.buildNotificationAction("WhatsApp", "Hola")
        Thread.sleep(1100) // id and crateTime change with the clock
        val second = AppLayer.buildNotificationAction("WhatsApp", "Hola")

        assertNotEquals(first, second)
        assertEquals(AppLayer.notificationKey(first), AppLayer.notificationKey(second))
    }

    @Test
    fun differentTextHasDifferentKey() {
        val a = AppLayer.notificationKey(AppLayer.buildNotificationAction("WhatsApp", "Hola"))
        val b = AppLayer.notificationKey(AppLayer.buildNotificationAction("WhatsApp", "Adiós"))

        assertNotEquals(a, b)
    }

    @Test
    fun nonNotificationActionHasNoKey() {
        assertNull(AppLayer.notificationKey("""{"action":"SyncOffSetTime","data":{}}"""))
    }
}
