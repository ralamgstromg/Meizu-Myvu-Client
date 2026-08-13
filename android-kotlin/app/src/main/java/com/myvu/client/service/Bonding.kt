package com.myvu.client.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.myvu.client.core.LogBus
import java.lang.reflect.Method
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

/**
 * BR/EDR bonding, mirroring
 * com.upuphone.starrynet.core.bredr.BrEdrDeviceManager from the decompiled app.
 *
 * CURRENTLY UNUSED, deliberately. Measured on hardware: calling createBond()
 * before any BLE contact never completes -- 13s with no ACL, no SSP request and
 * sdp_attempts=0, i.e. a plain page timeout, because the glasses' classic radio
 * does not page-scan until BLE has woken them. Once ConnectionManager brings BLE
 * up and runs the ECDH bond, the BR/EDR bond appears on its own (confirmed:
 * the device shows as bonded [DUAL] afterwards), so RFCOMM needs no explicit
 * bonding step.
 *
 * Kept because it encodes the exact hidden-API call the official app uses, in
 * case a device or firmware turns up that does require an explicit bond.
 */
class Bonding(private val context: Context) {

    /** Size-1 queue: the first bond outcome wins, later ones are ignored. */
    @Volatile
    private var pending: BlockingQueue<Boolean>? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_PAIRING_REQUEST == action) {
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
                LogBus.log(
                    "pairing request (variant=$variant) -- auto-confirming, " +
                        "matching the real phone's silent-confirm behaviour"
                )
                try {
                    device?.setPairingConfirmation(true)
                } catch (e: SecurityException) {
                    LogBus.error("setPairingConfirmation failed", e)
                }
                abortBroadcast()
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                LogBus.log("bond state -> ${describeBondState(state)}")
                val q = pending
                if (q != null) {
                    if (state == BluetoothDevice.BOND_BONDED) q.offer(java.lang.Boolean.TRUE)
                    if (state == BluetoothDevice.BOND_NONE) q.offer(java.lang.Boolean.FALSE)
                }
            }
        }
    }

    fun register() {
        val pairing = IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST)
        // Beat the system dialog to the broadcast so we can auto-confirm.
        pairing.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        context.registerReceiver(receiver, pairing)
        context.registerReceiver(
            receiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )
    }

    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (ignored: IllegalArgumentException) {
            // Not registered; nothing to do.
        }
    }

    /**
     * Ensures a BR/EDR bond, blocking up to 30s. Must not be called from the
     * connection thread -- run it on a worker.
     */
    @Throws(InterruptedException::class)
    fun ensureBonded(device: BluetoothDevice): Boolean {
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            LogBus.log("already bonded")
            return true
        }

        val q: BlockingQueue<Boolean> = ArrayBlockingQueue(1)
        pending = q
        try {
            val started = invokeCreateBrEdrBond(device)
            LogBus.log("createBond returned $started -- waiting for bond state")
            val ok = q.poll(BOND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (ok == null) {
                LogBus.warn("bonding timed out after ${BOND_TIMEOUT_SECONDS}s")
                return false
            }
            if (!ok) {
                LogBus.warn("bonding failed (BOND_NONE)")
                return false
            }
            LogBus.log("bonded")
            return true
        } finally {
            pending = null
        }
    }

    companion object {
        private const val BOND_TIMEOUT_SECONDS = 30L

        /**
         * Calls the hidden createBond(int transport) with TRANSPORT_BREDR, exactly
         * as BrEdrDeviceManager.invokeCreateBrEdrBond() does.
         *
         * The public no-arg createBond() lets Android choose a transport, which is
         * ambiguous for a dual-mode device we may have just seen over BLE -- that
         * ambiguity is why earlier plain-createBond attempts paged the wrong way
         * and timed out. Falls back to the public method if reflection is blocked.
         */
        private fun invokeCreateBrEdrBond(device: BluetoothDevice): Boolean {
            return try {
                val m: Method = device.javaClass.getMethod("createBond", Int::class.javaPrimitiveType)
                val result = m.invoke(device, BluetoothDevice.TRANSPORT_BREDR)
                result is Boolean && result
            } catch (e: Exception) {
                LogBus.warn(
                    "createBond(TRANSPORT_BREDR) unavailable (${e.javaClass.simpleName}) -- falling back to public createBond()"
                )
                device.createBond()
            }
        }

        @JvmStatic
        fun describeBondState(state: Int): String {
            return when (state) {
                BluetoothDevice.BOND_NONE -> "BOND_NONE"
                BluetoothDevice.BOND_BONDING -> "BOND_BONDING"
                BluetoothDevice.BOND_BONDED -> "BOND_BONDED"
                else -> "unknown($state)"
            }
        }
    }
}
