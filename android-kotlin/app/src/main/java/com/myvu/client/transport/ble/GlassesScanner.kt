package com.myvu.client.transport.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.ParcelUuid
import com.myvu.client.core.LogBus
import java.util.Locale
import java.util.UUID

class GlassesScanner(
    private val adapter: BluetoothAdapter,
    private val handler: Handler
) {
    interface Callback {
        fun onFound(device: BluetoothDevice, name: String?)
        fun onTimeout()
        fun onError(reason: String)
    }

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var callback: Callback? = null
    private var active: Boolean = false

    @JvmOverloads
    fun start(cb: Callback, timeoutMs: Long = DEFAULT_TIMEOUT_MS, attemptCount: Int = 1) {
        this.callback = cb
        val leScanner = adapter.bluetoothLeScanner
        scanner = leScanner
        if (leScanner == null) {
            cb.onError("BLE scanning is unavailable (adapter off?)")
            return
        }

        val sc = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!active) return
                if (matches(result)) {
                    val d = result.device
                    val name = nameOf(result)
                    LogBus.log("found glasses: ${name ?: "(no name)"} ${d.address}")
                    stop()
                    callback?.onFound(d, name)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                if (!active) return
                stop()
                callback?.onError("BLE scan failed (code $errorCode)")
            }
        }
        scanCallback = sc

        val mode = when {
            attemptCount <= 1 -> ScanSettings.SCAN_MODE_LOW_LATENCY
            attemptCount <= 3 -> ScanSettings.SCAN_MODE_BALANCED
            else -> ScanSettings.SCAN_MODE_LOW_POWER
        }

        val settings = ScanSettings.Builder()
            .setScanMode(mode)
            .build()
        active = true
        try {
            val filters = mutableListOf<ScanFilter>()
            filters.add(ScanFilter.Builder().setServiceUuid(ParcelUuid(ADV_SERVICE)).build())
            filters.add(ScanFilter.Builder().setServiceUuid(ParcelUuid(GATT_SERVICE)).build())
            leScanner.startScan(filters, settings, sc)
            LogBus.log("scanning for glasses (mode=$mode, attempt=$attemptCount) with hardware filters...")
        } catch (e: SecurityException) {
            active = false
            cb.onError("missing the Bluetooth scan permission")
            return
        }
        handler.postDelayed(timeoutRunnable, timeoutMs)
    }

    private val timeoutRunnable = Runnable {
        if (!active) return@Runnable
        val cb = callback
        stop()
        cb?.onTimeout()
    }

    fun stop() {
        handler.removeCallbacks(timeoutRunnable)
        if (active && scanner != null && scanCallback != null) {
            try {
                scanner?.stopScan(scanCallback)
            } catch (ignored: Exception) {
            }
        }
        active = false
    }

    private fun matches(r: ScanResult): Boolean {
        val name = nameOf(r)
        if (name != null) {
            val u = name.uppercase(Locale.US)
            if (u.contains("MYVU")) return true
        }
        val rec: ScanRecord? = r.scanRecord
        if (rec != null) {
            val uuids = rec.serviceUuids
            if (uuids != null) {
                for (pu in uuids) {
                    val id = pu.uuid
                    if (ADV_SERVICE == id || GATT_SERVICE == id) return true
                }
            }
        }
        return false
    }

    private fun nameOf(r: ScanResult): String? {
        val rec = r.scanRecord
        val adv = rec?.deviceName
        if (!adv.isNullOrEmpty()) return adv
        return try {
            r.device.name
        } catch (e: SecurityException) {
            null
        }
    }

    companion object {
        private val ADV_SERVICE: UUID = uuid16(0x0bd3)
        private val GATT_SERVICE: UUID = uuid16(0x0bd1)
        private const val DEFAULT_TIMEOUT_MS: Long = 12000

        private fun uuid16(i: Int): UUID {
            return UUID.fromString(String.format(Locale.US, "0000%04x-0000-1000-8000-00805f9b34fb", i))
        }
    }
}
