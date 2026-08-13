package com.myvu.client.transport.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.os.Handler
import com.myvu.client.core.LogBus

class BleHeartbeat @JvmOverloads constructor(
    private val queue: GattQueue?,
    private val urgentChar: BluetoothGattCharacteristic?,
    scheduler: Scheduler? = null,
    timeProvider: TimeProvider? = null
) {
    interface Scheduler {
        fun postDelayed(runnable: Runnable, delayMs: Long)
        fun removeCallbacks(runnable: Runnable)
    }

    fun interface TimeProvider {
        fun currentTimeMillis(): Long
    }

    private val scheduler: Scheduler = scheduler ?: HandlerScheduler(null)
    private val timeProvider: TimeProvider = timeProvider ?: TimeProvider { System.currentTimeMillis() }

    constructor(queue: GattQueue?, urgentChar: BluetoothGattCharacteristic?, gatt: Handler) : this(
        queue,
        urgentChar,
        HandlerScheduler(gatt),
        null
    )

    var isRunning: Boolean = false
        private set
    private var count: Int = 0
    private var lastDataActivityTime: Long = 0

    private val tick = object : Runnable {
        override fun run() {
            if (!isRunning) return
            if (queue != null && urgentChar != null) {
                queue.enqueue(GattOp.write(urgentChar, HEARTBEAT_DATA))
            }
            count++
            val currentInterval = interval
            if (count == 1) {
                LogBus.log("BLE heartbeat active (every ${currentInterval / 1000}s)")
            }
            this@BleHeartbeat.scheduler.postDelayed(this, currentInterval)
        }
    }

    fun notifyDataActivity() {
        lastDataActivityTime = timeProvider.currentTimeMillis()
    }

    val isDataActive: Boolean
        get() = (timeProvider.currentTimeMillis() - lastDataActivityTime) < ACTIVE_DATA_TIMEOUT_MS

    val interval: Long
        get() = if (isDataActive) EXTENDED_INTERVAL_MS else STANDARD_INTERVAL_MS

    fun start() {
        if (isRunning) return
        isRunning = true
        count = 0
        scheduler.postDelayed(tick, interval)
    }

    fun stop() {
        isRunning = false
        scheduler.removeCallbacks(tick)
    }

    private class HandlerScheduler(private val handler: Handler?) : Scheduler {
        override fun postDelayed(runnable: Runnable, delayMs: Long) {
            handler?.postDelayed(runnable, delayMs)
        }

        override fun removeCallbacks(runnable: Runnable) {
            handler?.removeCallbacks(runnable)
        }
    }

    companion object {
        @JvmField
        val HEARTBEAT_DATA: ByteArray = byteArrayOf(0, 0, 9, 16, 0)
        const val STANDARD_INTERVAL_MS: Long = 10000
        const val EXTENDED_INTERVAL_MS: Long = 15000
        const val ACTIVE_DATA_TIMEOUT_MS: Long = 15000
    }
}
