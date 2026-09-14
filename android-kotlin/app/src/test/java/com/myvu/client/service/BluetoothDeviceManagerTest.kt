package com.myvu.client.service

import android.bluetooth.BluetoothClass
import com.myvu.client.data.BluetoothDeviceType
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothDeviceManagerTest {

    @Test
    fun testClassifySmartGlassesByName() {
        val type1 = BluetoothDeviceManager.classifyDeviceSimple("MYVU AR Glasses", null)
        val type2 = BluetoothDeviceManager.classifyDeviceSimple("RayNeo X2", null)
        val type3 = BluetoothDeviceManager.classifyDeviceSimple("Rokid Air Glasses", null)

        assertEquals(BluetoothDeviceType.SMART_GLASSES, type1)
        assertEquals(BluetoothDeviceType.SMART_GLASSES, type2)
        assertEquals(BluetoothDeviceType.SMART_GLASSES, type3)
    }

    @Test
    fun testClassifyHeadphonesByNameKeywords() {
        val buds = BluetoothDeviceManager.classifyDeviceSimple("Samsung Galaxy Buds2 Pro", null)
        val airpods = BluetoothDeviceManager.classifyDeviceSimple("AirPods de Raúl", null)
        val sony = BluetoothDeviceManager.classifyDeviceSimple("Sony WH-1000XM5", null)
        val jbl = BluetoothDeviceManager.classifyDeviceSimple("JBL Tune 510BT", null)
        val freebuds = BluetoothDeviceManager.classifyDeviceSimple("HUAWEI FreeBuds 5i", null)

        assertEquals(BluetoothDeviceType.HEADPHONES, buds)
        assertEquals(BluetoothDeviceType.HEADPHONES, airpods)
        assertEquals(BluetoothDeviceType.HEADPHONES, sony)
        assertEquals(BluetoothDeviceType.HEADPHONES, jbl)
        assertEquals(BluetoothDeviceType.HEADPHONES, freebuds)
    }

    @Test
    fun testClassifyGenericDevice() {
        val watch = BluetoothDeviceManager.classifyDeviceSimple("Xiaomi Smart Band 8", null)
        val tag = BluetoothDeviceManager.classifyDeviceSimple("SmartTag", null)

        assertEquals(BluetoothDeviceType.GENERIC, watch)
        assertEquals(BluetoothDeviceType.GENERIC, tag)
    }

    @Test
    fun testDeviceNotificationModes() {
        val bothDev = com.myvu.client.data.BluetoothDeviceEntity(
            macAddress = "AA:BB:CC:DD:EE:01",
            name = "MYVU Glasses",
            notificationMode = com.myvu.client.data.DeviceNotificationMode.BOTH.name
        )
        org.junit.Assert.assertTrue(bothDev.isVisualNotificationEnabled())
        org.junit.Assert.assertTrue(bothDev.isAudioNotificationEnabled())

        val visualOnlyDev = com.myvu.client.data.BluetoothDeviceEntity(
            macAddress = "AA:BB:CC:DD:EE:02",
            name = "Silent HUD Glasses",
            notificationMode = com.myvu.client.data.DeviceNotificationMode.VISUAL_ONLY.name
        )
        org.junit.Assert.assertTrue(visualOnlyDev.isVisualNotificationEnabled())
        org.junit.Assert.assertFalse(visualOnlyDev.isAudioNotificationEnabled())

        val audioOnlyDev = com.myvu.client.data.BluetoothDeviceEntity(
            macAddress = "AA:BB:CC:DD:EE:03",
            name = "Sony Headphones",
            notificationMode = com.myvu.client.data.DeviceNotificationMode.AUDIO_ONLY.name
        )
        org.junit.Assert.assertFalse(audioOnlyDev.isVisualNotificationEnabled())
        org.junit.Assert.assertTrue(audioOnlyDev.isAudioNotificationEnabled())

        val noneDev = com.myvu.client.data.BluetoothDeviceEntity(
            macAddress = "AA:BB:CC:DD:EE:04",
            name = "Muted Device",
            notificationMode = com.myvu.client.data.DeviceNotificationMode.NONE.name
        )
        org.junit.Assert.assertFalse(noneDev.isVisualNotificationEnabled())
        org.junit.Assert.assertFalse(noneDev.isAudioNotificationEnabled())
    }

    @Test
    fun testDeviceNotificationModeEnumHelpers() {
        val fromBoth = com.myvu.client.data.DeviceNotificationMode.fromId("BOTH")
        val fromVisual = com.myvu.client.data.DeviceNotificationMode.fromId("VISUAL_ONLY")
        val fromAudio = com.myvu.client.data.DeviceNotificationMode.fromId("AUDIO_ONLY")
        val fromNone = com.myvu.client.data.DeviceNotificationMode.fromId("NONE")
        val fallback = com.myvu.client.data.DeviceNotificationMode.fromId("UNKNOWN")

        assertEquals(com.myvu.client.data.DeviceNotificationMode.BOTH, fromBoth)
        assertEquals(com.myvu.client.data.DeviceNotificationMode.VISUAL_ONLY, fromVisual)
        assertEquals(com.myvu.client.data.DeviceNotificationMode.AUDIO_ONLY, fromAudio)
        assertEquals(com.myvu.client.data.DeviceNotificationMode.NONE, fromNone)
        assertEquals(com.myvu.client.data.DeviceNotificationMode.BOTH, fallback)

        assertEquals(4, com.myvu.client.data.DeviceNotificationMode.getLabels().size)
    }

    @Test
    fun testParseAppleBatteryArgs() {
        // HFP +IPHONEACCEV payload format: [num_key_pairs, key1, val1, ...]
        // Key 1 = Battery Level (0-9 scale mapping to 10%-100%)
        val args100 = arrayOf<Any>(1, 1, 9)
        val args50 = arrayOf<Any>(1, 1, 4)
        val args10 = arrayOf<Any>(1, 1, 0)
        val argsInvalidKey = arrayOf<Any>(1, 2, 9)
        val argsOutOfRange = arrayOf<Any>(1, 1, 15)
        val argsEmpty = arrayOf<Any>()

        assertEquals(100, BluetoothDeviceManager.parseAppleBatteryArgs(args100))
        assertEquals(50, BluetoothDeviceManager.parseAppleBatteryArgs(args50))
        assertEquals(10, BluetoothDeviceManager.parseAppleBatteryArgs(args10))
        assertEquals(null, BluetoothDeviceManager.parseAppleBatteryArgs(argsInvalidKey))
        assertEquals(null, BluetoothDeviceManager.parseAppleBatteryArgs(argsOutOfRange))
        assertEquals(null, BluetoothDeviceManager.parseAppleBatteryArgs(argsEmpty))
        assertEquals(null, BluetoothDeviceManager.parseAppleBatteryArgs(null))
    }

    @Test
    fun testBluetoothDeviceEntityBatteryField() {
        val entity = com.myvu.client.data.BluetoothDeviceEntity(
            macAddress = "11:22:33:44:55:66",
            name = "Test Buds",
            batteryLevel = 80
        )
        assertEquals(80, entity.batteryLevel)
        org.junit.Assert.assertFalse(entity.isPrimary)
    }

    @Test
    fun testBluetoothDeviceEntityIsPrimaryField() {
        val primaryEntity = com.myvu.client.data.BluetoothDeviceEntity(
            macAddress = "11:22:33:44:55:77",
            name = "MYVU Glasses",
            isPrimary = true
        )
        org.junit.Assert.assertTrue(primaryEntity.isPrimary)

        val updated = primaryEntity.copy(isPrimary = false)
        org.junit.Assert.assertFalse(updated.isPrimary)
    }
}
