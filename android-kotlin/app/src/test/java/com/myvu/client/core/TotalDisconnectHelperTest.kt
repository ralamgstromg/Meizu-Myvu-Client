package com.myvu.client.core

import android.content.Context
import com.myvu.client.data.BluetoothDeviceEntity
import com.myvu.client.data.BluetoothDeviceType
import com.myvu.client.database.AppDatabase
import com.myvu.client.service.BluetoothDeviceManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TotalDisconnectHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun testAppDatabaseSchemaIntegrityVersion5() {
        val db = AppDatabase.getInstance(context)
        assertNotNull(db)

        val entity = BluetoothDeviceEntity(
            macAddress = "11:22:33:44:55:66",
            name = "Test Glasses",
            deviceType = BluetoothDeviceType.SMART_GLASSES.name,
            isConnected = true,
            activeListeningEnabled = false
        )

        runBlocking {
            val dao = db.bluetoothDeviceDao()
            dao.insertOrUpdate(entity)
            val loaded = dao.getDevice("11:22:33:44:55:66")
            assertNotNull(loaded)
            assertEquals("Test Glasses", loaded?.name)
            assertFalse(loaded?.activeListeningEnabled ?: true)
        }
    }

    @Test
    fun testTotalDisconnectDisablesAutoReconnect() {
        Prefs.setAutoReconnectEnabled(context, true)
        assertEquals(true, Prefs.autoReconnectEnabled(context))

        TotalDisconnectHelper.performTotalDisconnect(context, showToast = false)

        assertEquals(false, Prefs.autoReconnectEnabled(context))
    }

    @Test
    fun testTotalDisconnectMarksAllDevicesDisconnected() {
        val db = AppDatabase.getInstance(context)
        val dao = db.bluetoothDeviceDao()

        val dev1 = BluetoothDeviceEntity(
            macAddress = "AA:BB:CC:11:22:33",
            name = "MYVU AR Glasses",
            deviceType = BluetoothDeviceType.SMART_GLASSES.name,
            isConnected = true
        )
        val dev2 = BluetoothDeviceEntity(
            macAddress = "DD:EE:FF:44:55:66",
            name = "Bluetooth Earbuds",
            deviceType = BluetoothDeviceType.HEADPHONES.name,
            isConnected = true
        )

        runBlocking {
            dao.insertOrUpdate(dev1)
            dao.insertOrUpdate(dev2)
            BluetoothDeviceManager.getInstance(context).refreshActiveDevice()
        }

        runBlocking {
            val bdm = BluetoothDeviceManager.getInstance(context)
            bdm.markAllDevicesDisconnected()

            val all = dao.getAllDevices()
            assertEquals(2, all.size)
            assertFalse(all[0].isConnected)
            assertFalse(all[1].isConnected)
            assertNull(bdm.activeDevice.value)
        }
    }
}
