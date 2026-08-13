package com.myvu.client.transport.ble

import android.bluetooth.BluetoothGatt
import android.os.Handler
import com.myvu.client.core.LogBus
import java.util.ArrayDeque
import java.util.Deque

class GattQueue(private val gatt: Handler) {

    private val queue: Deque<GattOp> = ArrayDeque()
    private var connection: BluetoothGatt? = null
    private var pending: GattOp? = null
    private var retries: Int = 0
    private var closed: Boolean = false

    private val watchdog = Runnable {
        val op = pending ?: return@Runnable
        LogBus.warn("GATT op timed out: ${op.describe()}")
        pending = null
        retries = 0
        dispatchNext()
    }

    fun attach(connection: BluetoothGatt) {
        this.connection = connection
        this.closed = false
    }

    fun enqueue(op: GattOp) {
        gatt.post {
            if (closed) return@post
            queue.addLast(op)
            if (pending == null) dispatchNext()
        }
    }

    fun complete(status: Int) {
        val op = pending ?: return
        gatt.removeCallbacks(watchdog)
        if (status != BluetoothGatt.GATT_SUCCESS) {
            LogBus.warn("GATT op failed (status=$status): ${op.describe()}")
        } else {
            LogBus.trace("gatt ok: ${op.describe()}")
        }
        pending = null
        retries = 0
        dispatchNext()
    }

    private fun dispatchNext() {
        if (closed || pending != null) return
        val op = queue.pollFirst() ?: return

        val g = connection
        if (g == null) {
            LogBus.warn("dropping ${op.describe()}: no GATT connection")
            return
        }

        pending = op
        val accepted = op.execute(g)
        if (!accepted) {
            pending = null
            if (retries < MAX_RETRIES) {
                retries++
                queue.addFirst(op)
                gatt.postDelayed({ dispatchNext() }, RETRY_DELAY_MS)
            } else {
                LogBus.warn("giving up on ${op.describe()} after $MAX_RETRIES retries")
                retries = 0
                dispatchNext()
            }
            return
        }
        gatt.postDelayed(watchdog, WATCHDOG_MS)
    }

    fun clear() {
        gatt.post {
            closed = true
            gatt.removeCallbacks(watchdog)
            queue.clear()
            pending = null
            connection = null
        }
    }

    companion object {
        private const val WATCHDOG_MS = 5000L
        private const val MAX_RETRIES = 5
        private const val RETRY_DELAY_MS = 30L
    }
}
