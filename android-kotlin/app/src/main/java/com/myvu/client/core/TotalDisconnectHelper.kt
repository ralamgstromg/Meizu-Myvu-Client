package com.myvu.client.core

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.myvu.client.app.feature.TouchGestureManager
import com.myvu.client.health.HealthService
import com.myvu.client.service.BluetoothDeviceManager
import com.myvu.client.service.MyvuService
import com.myvu.client.service.ServiceWatchdogReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Universal helper to execute a total disconnection and completely inactivate
 * all background services, radios, and sensors across all devices.
 */
object TotalDisconnectHelper {

    fun performTotalDisconnect(
        context: Context,
        showToast: Boolean = true,
        onComplete: (() -> Unit)? = null
    ) {
        LogBus.log("TotalDisconnectHelper: INITIATING TOTAL DISCONNECT FOR ALL DEVICES & SERVICES")

        // 1. Disable auto-reconnect permanently until user explicitly requests connection again
        Prefs.setAutoReconnectEnabled(context, false)

        // 2. Cancel watchdog alarm and any keep-alive mechanisms
        ServiceWatchdogReceiver.cancelWatchdog(context)

        // 3. Immediately release any Bluetooth SCO audio channels
        TouchGestureManager.releaseBluetoothSco(context)

        // 4. Inactivate hardware sensors (step counter / detector)
        try {
            HealthService.getInstance(context).unregisterHardwareSensor()
        } catch (e: Exception) {
            LogBus.warn("TotalDisconnectHelper: Error unregistering health sensor: ${e.message}")
        }

        // 5. Inactivate all device connections in Room DB & stop active scans
        val bdm = BluetoothDeviceManager.getInstance(context)
        bdm.stopScanning()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                bdm.markAllDevicesDisconnected()
            } catch (e: Exception) {
                LogBus.warn("TotalDisconnectHelper: Error marking devices disconnected: ${e.message}")
            }
        }

        // 6. Stop MyvuService (GATT, RFCOMM, Relay, Audio Profiles, MediaSession, Foreground Notification)
        try {
            val stopIntent = Intent(context, MyvuService::class.java).setAction(MyvuService.ACTION_STOP)
            context.startService(stopIntent)
        } catch (e: Exception) {
            LogBus.error("TotalDisconnectHelper: Error sending ACTION_STOP to MyvuService", e)
        }

        if (showToast) {
            Toast.makeText(
                context,
                "Desconexión total realizada. Todos los servicios han sido inactivados.",
                Toast.LENGTH_LONG
            ).show()
        }

        onComplete?.invoke()
    }
}
