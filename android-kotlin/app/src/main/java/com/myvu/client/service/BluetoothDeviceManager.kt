package com.myvu.client.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.myvu.client.core.GlassesConfig
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.data.BluetoothDeviceEntity
import com.myvu.client.data.BluetoothDeviceType
import com.myvu.client.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Universal Bluetooth Device Manager.
 * Detects, classifies, registers, and tracks connections for all Bluetooth devices
 * (Smart Glasses, Headphones/Earbuds, and Generic Bluetooth Wearables).
 */
class BluetoothDeviceManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db = AppDatabase.getInstance(context)
    private val dao = db.bluetoothDeviceDao()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceEntity>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDeviceEntity>> = _discoveredDevices.asStateFlow()

    private val _activeDevice = MutableStateFlow<BluetoothDeviceEntity?>(null)
    val activeDevice: StateFlow<BluetoothDeviceEntity?> = _activeDevice.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val scanHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scanTimeoutRunnable = Runnable {
        LogBus.log("BluetoothDeviceManager -> Scan timeout reached (12s); stopping discovery")
        stopScanning()
    }

    private var receiverRegistered = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (device != null) {
                        LogBus.log("BluetoothDeviceManager -> Device connected: ${device.name ?: "Unknown"} (${device.address})")
                        val battery = readDeviceBattery(device)
                        handleDeviceConnectionChanged(device, true, battery)
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (device != null) {
                        LogBus.log("BluetoothDeviceManager -> Device disconnected: ${device.name ?: "Unknown"} (${device.address})")
                        handleDeviceConnectionChanged(device, false)
                    }
                }
                "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> {
                    val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                    if (device != null && level in 0..100) {
                        LogBus.log("BluetoothDeviceManager -> Battery changed for ${device.name ?: device.address}: $level%")
                        handleDeviceBatteryChanged(device.address, level)
                    }
                }
                "android.bluetooth.headset.action.VENDOR_SPECIFIC_HEADSET_EVENT" -> {
                    val cmd = intent.getStringExtra("android.bluetooth.headset.extra.VENDOR_SPECIFIC_HEADSET_EVENT_CMD")
                    if (cmd == "+IPHONEACCEV" && device != null) {
                        val args = intent.getSerializableExtra("android.bluetooth.headset.extra.VENDOR_SPECIFIC_HEADSET_EVENT_ARGS") as? Array<*>
                        val level = parseAppleBatteryArgs(args)
                        if (level != null && level in 0..100) {
                            LogBus.log("BluetoothDeviceManager -> Headset battery (+IPHONEACCEV) for ${device.name ?: device.address}: $level%")
                            handleDeviceBatteryChanged(device.address, level)
                        }
                    }
                }
                BluetoothDevice.ACTION_FOUND -> {
                    if (device != null) {
                        handleDeviceDiscovered(device)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    scanHandler.removeCallbacks(scanTimeoutRunnable)
                    _isScanning.value = false
                    LogBus.log("BluetoothDeviceManager -> Discovery finished")
                }
            }
        }
    }

    init {
        registerReceiver()
        syncPairedDevices()
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            addAction("android.bluetooth.headset.action.VENDOR_SPECIFIC_HEADSET_EVENT")
        }
        context.registerReceiver(bluetoothReceiver, filter)
        receiverRegistered = true
    }

    /**
     * Classifies a Bluetooth device into SMART_GLASSES, HEADPHONES, or GENERIC.
     */
    fun classifyDevice(name: String?, bluetoothClass: BluetoothClass?, macAddress: String? = null): BluetoothDeviceType {
        val configuredMac = Prefs.targetMac(context).lowercase()
        return classifyDeviceSimple(name, bluetoothClass, configuredMac, macAddress)
    }

    /**
     * Reads system paired devices and saves them into the local database with their classified category.
     */
    @SuppressLint("MissingPermission")
    fun syncPairedDevices() {
        if (!hasBluetoothPermission()) {
            LogBus.warn("BluetoothDeviceManager -> Missing BLUETOOTH_CONNECT permission")
            return
        }

        scope.launch {
            try {
                val bonded = bluetoothAdapter?.bondedDevices ?: emptySet()
                for (bDevice in bonded) {
                    val name = bDevice.name ?: "Dispositivo Bluetooth"
                    val mac = bDevice.address
                    val type = classifyDevice(name, bDevice.bluetoothClass, mac)
                    val battery = readDeviceBattery(bDevice)

                    val existing = dao.getDevice(mac)
                    if (existing == null) {
                        val newEntity = BluetoothDeviceEntity(
                            macAddress = mac,
                            name = name,
                            deviceType = type.name,
                            isConnected = false,
                            batteryLevel = battery,
                            lastConnectedTime = System.currentTimeMillis()
                        )
                        dao.insertOrUpdate(newEntity)
                    } else {
                        // Preserve user customized gestures and update name/type/battery if needed
                        dao.insertOrUpdate(
                            existing.copy(
                                name = name,
                                deviceType = if (existing.deviceType == BluetoothDeviceType.GENERIC.name) type.name else existing.deviceType,
                                batteryLevel = battery ?: existing.batteryLevel
                            )
                        )
                    }
                }
                refreshActiveDevice()
            } catch (e: Exception) {
                LogBus.error("BluetoothDeviceManager -> Failed to sync paired devices", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleDeviceDiscovered(device: BluetoothDevice) {
        if (!hasBluetoothPermission()) return
        val name = device.name ?: return
        val mac = device.address
        val type = classifyDevice(name, device.bluetoothClass, mac)

        val entity = BluetoothDeviceEntity(
            macAddress = mac,
            name = name,
            deviceType = type.name,
            isConnected = false,
            lastConnectedTime = System.currentTimeMillis()
        )

        val current = _discoveredDevices.value.toMutableList()
        if (current.none { it.macAddress.equals(mac, ignoreCase = true) }) {
            current.add(entity)
            _discoveredDevices.value = current
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleDeviceConnectionChanged(device: BluetoothDevice, connected: Boolean, battery: Int? = null) {
        scope.launch {
            val mac = device.address
            val name = device.name ?: "Dispositivo Bluetooth"
            val type = classifyDevice(name, device.bluetoothClass, mac)
            val batt = battery ?: readDeviceBattery(device)

            val existing = dao.getDevice(mac)
            if (existing != null) {
                dao.updateConnectionState(mac, connected)
                if (batt != null && batt in 0..100) {
                    dao.updateBatteryLevel(mac, batt)
                }
            } else {
                val newEntity = BluetoothDeviceEntity(
                    macAddress = mac,
                    name = name,
                    deviceType = type.name,
                    isConnected = connected,
                    batteryLevel = batt,
                    lastConnectedTime = System.currentTimeMillis()
                )
                dao.insertOrUpdate(newEntity)
            }
            refreshActiveDevice()
        }
    }

    @SuppressLint("MissingPermission")
    fun readDeviceBattery(device: BluetoothDevice): Int? {
        return try {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val level = method.invoke(device) as? Int
            if (level != null && level in 0..100) level else null
        } catch (_: Throwable) {
            null
        }
    }


    fun handleDeviceBatteryChanged(mac: String, battery: Int) {
        if (battery !in 0..100) return
        scope.launch {
            dao.updateBatteryLevel(mac, battery)
            refreshActiveDevice()
        }
    }

    fun updateGlassesBatteryLevel(mac: String?, battery: Int) {
        if (battery !in 0..100) return
        scope.launch {
            try {
                val target = mac?.takeIf { it.isNotBlank() }
                    ?: dao.getConnectedDeviceByType(BluetoothDeviceType.SMART_GLASSES.name)?.macAddress
                    ?: dao.getAllDevices().find { it.deviceType == BluetoothDeviceType.SMART_GLASSES.name }?.macAddress

                if (target != null) {
                    dao.updateBatteryLevel(target, battery)
                    LogBus.log("BluetoothDeviceManager -> Updated glasses battery: $battery% for $target")
                } else {
                    val defaultMac = Prefs.targetMac(context).ifBlank { "MYVU-GLASSES-01" }
                    val entity = BluetoothDeviceEntity(
                        macAddress = defaultMac,
                        name = "Meizu MYVU AR",
                        deviceType = BluetoothDeviceType.SMART_GLASSES.name,
                        isConnected = true,
                        batteryLevel = battery
                    )
                    dao.insertOrUpdate(entity)
                }
                refreshActiveDevice()
            } catch (e: Exception) {
                LogBus.warn("BluetoothDeviceManager -> Failed to update glasses battery: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshAllDeviceBatteries() {
        if (!hasBluetoothPermission()) return
        scope.launch {
            try {
                val bonded = bluetoothAdapter?.bondedDevices ?: emptySet()
                for (bDevice in bonded) {
                    val battery = readDeviceBattery(bDevice)
                    if (battery != null && battery in 0..100) {
                        dao.updateBatteryLevel(bDevice.address, battery)
                    }
                }
                // Check if glasses are connected via ConnectionManager
                val glassesInfo = com.myvu.client.service.MyvuService.activeConnection()?.glassesInfo()
                if (glassesInfo != null && glassesInfo.battery in 0..100) {
                    val glassesMac = glassesInfo.btMac.takeIf { it.isNotBlank() } ?: Prefs.targetMac(context)
                    if (glassesMac.isNotBlank()) {
                        dao.updateBatteryLevel(glassesMac, glassesInfo.battery)
                    }
                }
                refreshActiveDevice()
            } catch (e: Exception) {
                LogBus.warn("BluetoothDeviceManager -> Error refreshing device batteries: ${e.message}")
            }
        }
    }

    suspend fun refreshActiveDevice() {
        val active = dao.getActiveConnectedDevice()
        _activeDevice.value = active
    }

    /**
     * Per-device active listening flag. Returns `false` (disabled) unless the device
     * entity has `activeListeningEnabled = true`. Falls back to the global Prefs flag
     * if no connected device is found (for backward-compatibility).
     */
    suspend fun isActiveListeningEnabled(): Boolean {
        val device = dao.getActiveConnectedDevice()
        return device?.activeListeningEnabled ?: Prefs.continuousDialogueEnabled(context)
    }

    /**
     * Blocking variant for callers outside coroutine scope (e.g., Handler Runnables).
     */
    fun isActiveListeningEnabledBlocking(): Boolean {
        return kotlinx.coroutines.runBlocking { isActiveListeningEnabled() }
    }

    /**
     * Inactivates all devices in the database, setting isConnected = false.
     * Used for total disconnection and full system shutdown.
     */
    suspend fun markAllDevicesDisconnected() {
        dao.markAllDisconnected()
        _activeDevice.value = null
        val updated = dao.getAllDevices()
        _discoveredDevices.value = updated
        LogBus.log("BluetoothDeviceManager: All devices marked as disconnected in database")
    }

    fun markAllDevicesDisconnectedBlocking() {
        kotlinx.coroutines.runBlocking { markAllDevicesDisconnected() }
    }

    /**
     * Start Bluetooth Classic & BLE discovery with an automatic 12-second safety timeout
     * to prevent CPU and radio power drain.
     */
    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (!hasBluetoothPermission()) return
        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        scanHandler.removeCallbacks(scanTimeoutRunnable)
        scanHandler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MS)
        bluetoothAdapter?.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        scanHandler.removeCallbacks(scanTimeoutRunnable)
        if (!hasBluetoothPermission()) return
        _isScanning.value = false
        bluetoothAdapter?.cancelDiscovery()
    }

    suspend fun registerDevice(device: BluetoothDeviceEntity) {
        dao.insertOrUpdate(device)
        refreshActiveDevice()
    }

    suspend fun updateDeviceGestures(
        mac: String,
        tap1: String,
        tap2: String,
        tap3: String,
        longPress: String,
        tts: Boolean,
        readNotifs: Boolean,
        notificationMode: String = if (readNotifs) com.myvu.client.data.DeviceNotificationMode.AUDIO_ONLY.name else com.myvu.client.data.DeviceNotificationMode.NONE.name,
        activeListening: Boolean? = null
    ) {
        val dev = dao.getDevice(mac) ?: BluetoothDeviceEntity(
            macAddress = mac,
            name = "Dispositivo Bluetooth",
            deviceType = BluetoothDeviceType.HEADPHONES.name
        )
        dao.insertOrUpdate(
            dev.copy(
                tap1Action = tap1,
                tap2Action = tap2,
                tap3Action = tap3,
                longPressAction = longPress,
                ttsEnabled = tts,
                autoReadNotifications = readNotifs,
                notificationMode = notificationMode,
                activeListeningEnabled = activeListening ?: dev.activeListeningEnabled
            )
        )
        refreshActiveDevice()
    }

    suspend fun updateGlassesGestures(
        mac: String,
        tap: String,
        doubleTap: String,
        tripleTap: String,
        swipeForward: String,
        swipeBackward: String,
        longPress: String,
        actionButton: String = "VOICE_AI_FIXED",
        hudBrightness: Int = 80,
        notificationMode: String = com.myvu.client.data.DeviceNotificationMode.BOTH.name,
        activeListening: Boolean? = null
    ) {
        val resolvedMac = mac.takeIf { it.isNotBlank() }
            ?: com.myvu.client.core.Prefs.targetMac(context).takeIf { it.isNotBlank() }
            ?: dao.getConnectedDeviceByType(BluetoothDeviceType.SMART_GLASSES.name)?.macAddress
            ?: dao.getAllDevices().find {
                it.deviceType == BluetoothDeviceType.SMART_GLASSES.name ||
                it.name.contains("MYVU", ignoreCase = true)
            }?.macAddress
            ?: "MYVU-GLASSES-01"

        val dev = (if (mac.isNotBlank()) dao.getDevice(mac) else null)
            ?: dao.getDevice(resolvedMac)
            ?: dao.getAllDevices().find {
                it.deviceType == BluetoothDeviceType.SMART_GLASSES.name ||
                it.name.contains("MYVU", ignoreCase = true)
            }

        val entityToSave = (dev ?: BluetoothDeviceEntity(
            macAddress = resolvedMac,
            name = "Meizu MYVU AR",
            deviceType = BluetoothDeviceType.SMART_GLASSES.name
        )).copy(
            macAddress = dev?.macAddress ?: resolvedMac,
            tap1Action = tap,
            tap2Action = doubleTap,
            tap3Action = tripleTap,
            swipeForwardAction = swipeForward,
            swipeBackwardAction = swipeBackward,
            longPressAction = longPress,
            actionButtonAction = actionButton,
            hudBrightness = hudBrightness,
            notificationMode = notificationMode,
            activeListeningEnabled = activeListening ?: dev?.activeListeningEnabled ?: false
        )
        dao.insertOrUpdate(entityToSave)

        com.myvu.client.core.Prefs.setTouchpadTapAction(context, tap)
        com.myvu.client.core.Prefs.setTouchpadDoubleTapAction(context, doubleTap)
        com.myvu.client.core.Prefs.setTouchpadTripleTapAction(context, tripleTap)
        com.myvu.client.core.Prefs.setTouchpadSwipeForwardAction(context, swipeForward)
        com.myvu.client.core.Prefs.setTouchpadSwipeBackwardAction(context, swipeBackward)
        com.myvu.client.core.Prefs.setTouchpadLongPressAction(context, longPress)
        com.myvu.client.core.Prefs.setGlassesActionButtonAction(context, actionButton)
        if (resolvedMac.isNotBlank()) {
            com.myvu.client.core.Prefs.setTargetMac(context, resolvedMac)
        }
        refreshActiveDevice()
    }

    suspend fun updateDeviceNotificationMode(mac: String, notificationMode: String) {
        val dev = dao.getDevice(mac) ?: return
        dao.insertOrUpdate(
            dev.copy(
                notificationMode = notificationMode,
                autoReadNotifications = (notificationMode == com.myvu.client.data.DeviceNotificationMode.AUDIO_ONLY.name ||
                        notificationMode == com.myvu.client.data.DeviceNotificationMode.BOTH.name)
            )
        )
        refreshActiveDevice()
    }

    fun getAllDevicesFlow() = dao.getAllDevicesFlow()

    companion object {
        @Volatile
        private var INSTANCE: BluetoothDeviceManager? = null

        fun getInstance(context: Context): BluetoothDeviceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BluetoothDeviceManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun classifyDeviceSimple(
            name: String?,
            bluetoothClass: BluetoothClass?,
            configuredMac: String? = null,
            macAddress: String? = null
        ): BluetoothDeviceType {
            val cleanName = (name ?: "").lowercase()

            // 1. Check if it is the configured or recognized Smart Glasses
            if (macAddress != null && configuredMac != null &&
                macAddress.equals(configuredMac, ignoreCase = true) && configuredMac.isNotBlank()) {
                return BluetoothDeviceType.SMART_GLASSES
            }
            if (cleanName.contains("myvu") || cleanName.contains("glasses") || cleanName.contains("rayneo") ||
                cleanName.contains("rokid") || cleanName.contains("xreal")) {
                return BluetoothDeviceType.SMART_GLASSES
            }

            // 2. Check Audio / Video device classes for Headphones / Earbuds / Headsets
            if (bluetoothClass != null) {
                val major = bluetoothClass.majorDeviceClass
                val deviceClass = bluetoothClass.deviceClass
                if (major == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                    return BluetoothDeviceType.HEADPHONES
                }
                if (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
                    deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                    deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE ||
                    deviceClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER) {
                    return BluetoothDeviceType.HEADPHONES
                }
            }

            // 3. Keyword heuristics in device name
            val headphoneKeywords = listOf(
                "buds", "airpods", "freebuds", "headset", "headphones", "earbuds", "earphone",
                "audio", "wh-", "wf-", "tune", "soundcore", "galaxy buds", "redmi", "anker",
                "jbl", "sony", "bose", "sennheiser", "beats", "shokz", "tws", "qcy", "haylou",
                "jabot", "airpod", "headphone"
            )
            if (headphoneKeywords.any { cleanName.contains(it) }) {
                return BluetoothDeviceType.HEADPHONES
            }

            return BluetoothDeviceType.GENERIC
        }

        const val SCAN_TIMEOUT_MS: Long = 12000L

        fun parseAppleBatteryArgs(args: Array<*>?): Int? {
            if (args == null || args.size < 3) return null
            return try {
                val key = (args[1] as? Number)?.toInt() ?: return null
                if (key == 1) {
                    val level0to9 = (args[2] as? Number)?.toInt() ?: return null
                    if (level0to9 in 0..9) {
                        (level0to9 + 1) * 10
                    } else null
                } else null
            } catch (_: Throwable) {
                null
            }
        }
    }
}
