package com.myvu.client.transport.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.myvu.client.core.LogBus
import com.myvu.client.transport.Transport
import com.myvu.client.transport.TransportListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

@android.annotation.SuppressLint("MissingPermission")
open class BleTransport(
    private val context: Context,
    private val device: BluetoothDevice,
    private val conn: Handler,
    private val listener: Listener?
) : Transport {

    interface Listener {
        fun onReady(transport: BleTransport)
        fun onInternalMessage(pkgType: Int, payload: ByteArray)
        fun onExternalMessage(pkgType: Int, payload: ByteArray)
        fun onDisconnected(reason: String)
    }

    private val gattThread: HandlerThread = HandlerThread("myvu-gatt").apply { start() }
    private val gattHandler: Handler = Handler(gattThread.looper)
    private val queue: GattQueue = GattQueue(gattHandler)

    private var gatt: BluetoothGatt? = null
    private var internalChar: BluetoothGattCharacteristic? = null
    private var externalChar: BluetoothGattCharacteristic? = null
    private var urgentChar: BluetoothGattCharacteristic? = null

    private var internalChannel: BleMessageChannel? = null
    private var externalChannel: BleMessageChannel? = null
    private var heartbeat: BleHeartbeat? = null

    @Volatile
    private var _isConnected: Boolean = false
    override val isConnected: Boolean get() = _isConnected

    @Volatile
    var isReady: Boolean = false
        private set

    override val name: String get() = "BLE"

    private val _payloadFlow = MutableSharedFlow<ByteArray>()
    override val payloadFlow: SharedFlow<ByteArray> = _payloadFlow.asSharedFlow()

    private var disconnectReported: Boolean = false
    private var mtu: Int = DEFAULT_MTU
    private var pendingSubscriptions: Int = 0
    private var connectAttempts: Int = 0

    fun internal(): BleMessageChannel? = internalChannel
    fun external(): BleMessageChannel? = externalChannel
    fun hasUrgentCharacteristic(): Boolean = urgentChar != null

    override fun connect() {
        LogBus.log("BLE connecting to ${device.address}...")
        openGatt()
    }

    private fun openGatt() {
        val g = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        if (g == null) {
            fail("connectGatt returned null")
            return
        }
        gatt = g
        queue.attach(g)
    }

    private fun retryTransientFailure(status: Int): Boolean {
        if (status != GATT_ERROR_133 || connectAttempts >= MAX_CONNECT_ATTEMPTS) return false
        connectAttempts++
        LogBus.warn("BLE connect failed with GATT_ERROR (133) -- retrying ($connectAttempts/$MAX_CONNECT_ATTEMPTS)")
        queue.clear()
        gatt?.close()
        gatt = null
        gattHandler.postDelayed({ openGatt() }, RETRY_DELAY_MS)
        return true
    }

    override fun close() {
        heartbeat?.stop()
        heartbeat = null
        internalChannel?.shutdown()
        externalChannel?.shutdown()
        _isConnected = false
        isReady = false
        queue.clear()
        gattHandler.post {
            gatt?.disconnect()
            gatt?.close()
            gatt = null
            gattThread.quitSafely()
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            gattHandler.post {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _isConnected = true
                    LogBus.log("BLE connected -- requesting MTU $PREFERRED_MTU")
                    queue.enqueue(GattOp.requestMtu(PREFERRED_MTU))
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _isConnected = false
                    isReady = false
                    if (retryTransientFailure(status)) return@post
                    fail(describeDisconnect(status))
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            gattHandler.post {
                val first = (mtu == DEFAULT_MTU)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    mtu = newMtu
                    LogBus.log("BLE MTU = $mtu (DMTU ${dmtu()})")
                    applyDmtu()
                } else {
                    LogBus.warn("MTU request failed (status=$status); staying at $mtu")
                }
                queue.complete(BluetoothGatt.GATT_SUCCESS)
                if (first) {
                    gattHandler.postDelayed({
                        queue.enqueue(GattOp.discoverServices())
                    }, DISCOVER_DELAY_MS)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            gattHandler.post {
                queue.complete(status)
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail("service discovery failed (status=$status)")
                    return@post
                }
                selectChannels()
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            gattHandler.post {
                queue.complete(status)
                pendingSubscriptions--
                if (pendingSubscriptions == 0) onSubscriptionsComplete()
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            gattHandler.post {
                queue.complete(status)
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            dispatchNotification(c.uuid, value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                dispatchNotification(c.uuid, c.value)
            }
        }
    }

    private fun dispatchNotification(uuid: UUID, value: ByteArray?) {
        if (value == null || value.isEmpty()) return
        val copy = value.clone()
        conn.post {
            _payloadFlow.tryEmit(copy)
            val ic = internalChar
            val ec = externalChar
            if (ic != null && uuid == ic.uuid) {
                internalChannel?.feed(copy)
            } else if (ec != null && uuid == ec.uuid) {
                externalChannel?.feed(copy)
            } else {
                LogBus.trace("notification on unexpected char $uuid")
            }
        }
    }

    private fun selectChannels() {
        val g = gatt ?: run {
            fail("GATT instance is null during channel selection")
            return
        }
        val present = mutableListOf<String>()
        for (s in g.services) {
            for (c in s.characteristics) {
                present.add(c.uuid.toString().lowercase())
            }
        }

        for (set in Uuids.CHANNEL_SETS) {
            val inChar = findCharacteristic(set[0])
            val exChar = findCharacteristic(set[1])
            if (inChar == null || exChar == null) continue

            internalChar = inChar
            externalChar = exChar
            urgentChar = findCharacteristic(set[2])
            LogBus.log(
                "BLE channels: internal=${GattOp.shortUuid(set[0])} external=${GattOp.shortUuid(set[1])}" +
                        if (urgentChar != null) " urgent=${GattOp.shortUuid(set[2])}" else " urgent=ABSENT"
            )
            subscribe()
            return
        }

        fail("no known StarryNet channel pair on this device; characteristics=$present")
    }

    private fun findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
        val g = gatt ?: return null
        for (s in g.services) {
            val c = s.getCharacteristic(uuid)
            if (c != null) return c
        }
        return null
    }

    private fun subscribe() {
        val ic = internalChar
        val ec = externalChar
        if (ic == null || ec == null) return

        internalChannel = BleMessageChannel("internal", writerFor(ic), conn) { pkgType, payload ->
            listener?.onInternalMessage(pkgType, payload)
        }
        externalChannel = BleMessageChannel("external", writerFor(ec), conn) { pkgType, payload ->
            listener?.onExternalMessage(pkgType, payload)
        }
        applyDmtu()

        pendingSubscriptions = 2
        queue.enqueue(GattOp.enableNotifications(ic))
        queue.enqueue(GattOp.enableNotifications(ec))
    }

    private fun onSubscriptionsComplete() {
        LogBus.log("BLE subscribed to both channels")
        val uc = urgentChar
        if (uc != null) {
            heartbeat = BleHeartbeat(queue, uc, gattHandler)
            heartbeat?.start()
        } else {
            LogBus.warn("urgent characteristic (0x2022) absent -- no heartbeat, so the glasses' watchdog may drop the link")
        }

        conn.postDelayed({
            if (!_isConnected) return@postDelayed
            isReady = true
            listener?.onReady(this)
        }, LIVENESS_CHECK_MS)
    }

    private fun writerFor(ch: BluetoothGattCharacteristic): BleMessageChannel.Writer {
        return BleMessageChannel.Writer { packet ->
            queue.enqueue(GattOp.write(ch, packet))
        }
    }

    private fun dmtu(): Int = Math.max(BleMessageChannel.MIN_DMTU, mtu - 5)

    private fun applyDmtu() {
        val d = dmtu()
        internalChannel?.setDmtu(d)
        externalChannel?.setDmtu(d)
    }

    private fun fail(reason: String) {
        if (disconnectReported) return
        disconnectReported = true
        _isConnected = false
        isReady = false
        conn.post {
            listener?.onDisconnected(reason)
        }
    }

    override fun send(payload: ByteArray) {
        externalChannel?.send(payload, BlePackets.PKG_COMMON_DATA)
    }

    companion object {
        private const val DEFAULT_MTU = 23
        private const val PREFERRED_MTU = 517
        private const val DISCOVER_DELAY_MS = 600L
        private const val LIVENESS_CHECK_MS = 1500L
        private const val GATT_ERROR_133 = 133
        private const val MAX_CONNECT_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L

        private fun describeDisconnect(status: Int): String {
            return when (status) {
                8 -> "link supervision timeout (glasses went out of range or powered off)"
                19 -> "glasses closed the BLE link. They most likely only accept their currently-bonded phone -- disconnect the glasses in the MYVU app and retry"
                22 -> "link terminated by the local host"
                133 -> "GATT_ERROR (133) -- generic connect failure; usually means the device is not advertising or is already connected elsewhere"
                BluetoothGatt.GATT_SUCCESS -> "disconnected"
                else -> "disconnected (status=$status)"
            }
        }
    }
}
