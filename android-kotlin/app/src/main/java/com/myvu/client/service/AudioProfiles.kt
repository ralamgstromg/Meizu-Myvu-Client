package com.myvu.client.service

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.myvu.client.core.LogBus
import com.myvu.client.protocol.link.LinkCommands
import java.lang.reflect.Method

/**
 * Brings up the standard classic-audio profiles (HFP/HSP + A2DP) to the glasses.
 *
 * WHY THIS EXISTS: the glasses light their "phone connected" indicator only when
 * a real phone is attached over the ordinary headset profiles -- the same signal
 * any Bluetooth headset uses. Our BLE bond + custom RFCOMM app-relay is enough to
 * *send* features (notifications, teleprompter and nav all render), but it is
 * invisible to the glasses' own connection state. The official app connects these
 * profiles explicitly (see the decompiled BrEdrHfpManager / BrEdrA2dpManager); the
 * Python client leaned on Windows holding HFP after the glasses were paired as an
 * audio device. Neither happens for us, so we do it here.
 *
 * PERMISSIONS: connect() / setConnectionPolicy() / setActiveDevice() on the
 * profile proxies are @SystemApi, gated by BLUETOOTH_PRIVILEGED, and hidden from
 * the SDK -- a normal app cannot link against them. So every mutating call is
 * reflective and best-effort: a SecurityException or a blocked-reflection
 * NoSuchMethodError just means this build won't let us force the link, and we
 * fall back to (a) the connection-policy nudge, which the system honours on the
 * next ACL, and (b) reporting the true status over the app layer, which we fully
 * control. The official app is written to tolerate being unprivileged the same
 * way (its checkHfpConnectionPolicy returns true when it lacks the permission).
 *
 * getConnectionState(), by contrast, is public SDK, so status reads are direct.
 */
class AudioProfiles(
    context: Context,
    private val adapter: BluetoothAdapter,
    private val targetMac: String,
    private val listener: Listener?
) {
    fun interface Listener {
        /** Fired when the HFP or A2DP connection state to the glasses changes. */
        fun onStatusChanged(btStatus: Int)
    }

    private val context: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var headset: BluetoothHeadset? = null
    private var a2dp: BluetoothA2dp? = null
    private var pendingDevice: BluetoothDevice? = null
    private var receiverRegistered = false

    init {
        bindProxies()
        registerReceiver()
    }

    fun bindProxies() {
        mainHandler.post {
            try {
                val hfpOk = if (headset == null) adapter.getProfileProxy(context, proxyListener, BluetoothProfile.HEADSET) else true
                val a2dpOk = if (a2dp == null) adapter.getProfileProxy(context, proxyListener, BluetoothProfile.A2DP) else true
                LogBus.log("AudioProfiles: getProfileProxy -> HFP=$hfpOk, A2DP=$a2dpOk")
                if (!hfpOk || !a2dpOk) {
                    mainHandler.postDelayed({
                        val hRetry = if (headset == null) adapter.getProfileProxy(context, proxyListener, BluetoothProfile.HEADSET) else true
                        val aRetry = if (a2dp == null) adapter.getProfileProxy(context, proxyListener, BluetoothProfile.A2DP) else true
                        LogBus.log("AudioProfiles: getProfileProxy retry -> HFP=$hRetry, A2DP=$aRetry")
                    }, 2500L)
                }
            } catch (e: Exception) {
                LogBus.warn("AudioProfiles: Exception requesting profile proxies: ${e.message}")
            }
        }
    }

    private val proxyListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HEADSET) {
                headset = proxy as BluetoothHeadset
                LogBus.log("AudioProfiles: HFP proxy bound")
                pendingDevice?.let { dev -> tryConnect("HFP", headset, dev) }
            } else if (profile == BluetoothProfile.A2DP) {
                a2dp = proxy as BluetoothA2dp
                LogBus.log("AudioProfiles: A2DP proxy bound")
                pendingDevice?.let { dev -> tryConnect("A2DP", a2dp, dev) }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) headset = null
            else if (profile == BluetoothProfile.A2DP) a2dp = null
        }
    }

    private fun resolveTargetDevice(device: BluetoothDevice): BluetoothDevice {
        try {
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                return device
            }
            // Search bonded devices for a device matching targetMac or named "MYVU"
            for (d in adapter.bondedDevices) {
                if (d.address.equals(device.address, ignoreCase = true) || d.address.equals(targetMac, ignoreCase = true)) {
                    return d
                }
                val n = d.name
                if (n != null && n.uppercase(java.util.Locale.US).contains("MYVU")) {
                    LogBus.log("AudioProfiles: Resolved bonded audio device '${d.name}' (${d.address}) for glasses")
                    return d
                }
            }
        } catch (e: SecurityException) {
            LogBus.warn("AudioProfiles: Permission check failed querying bonded devices: ${e.message}")
        }
        return device
    }

    /**
     * Best-effort: allow, then connect, the audio profiles to the glasses. Safe
     * to call more than once (a no-op once the profiles are already connected).
     */
    fun connect(device: BluetoothDevice?) {
        if (device == null) return
        val target = resolveTargetDevice(device)
        pendingDevice = target

        // If device is not bonded in Android settings, request bonding so Android recognizes it as a headset
        try {
            if (target.bondState == BluetoothDevice.BOND_NONE) {
                LogBus.log("AudioProfiles: Device not yet bonded in Android -- initiating createBond() for audio on ${target.address}")
                target.createBond()
            }
        } catch (e: SecurityException) {
            LogBus.warn("AudioProfiles: createBond permission check: ${e.message}")
        }

        LogBus.log(
            "AudioProfiles: Connecting classic audio profiles (HFP + A2DP) to ${target.name ?: target.address}"
        )
        tryConnect("HFP", headset, target)
        tryConnect("A2DP", a2dp, target)
    }

    private fun tryConnect(tag: String, proxy: BluetoothProfile?, device: BluetoothDevice) {
        if (proxy == null) {
            LogBus.log("AudioProfiles: $tag proxy binding in progress -- queued for auto-connect")
            bindProxies()
            return
        }
        if (getState(proxy, device) == BluetoothProfile.STATE_CONNECTED) {
            LogBus.log("AudioProfiles: $tag already connected to ${device.address}")
            return
        }
        val policy = invoke2(proxy, "setConnectionPolicy", device, CONNECTION_POLICY_ALLOWED)
        val priority = invoke2(proxy, "setPriority", device, PRIORITY_AUTO_CONNECT)
        val connect = invoke1(proxy, "connect", device)
        LogBus.log(
            "AudioProfiles: $tag connect requested for ${device.address} (policy=$policy, priority=$priority, connect=$connect)"
        )
    }

    fun disconnect(device: BluetoothDevice?) {
        if (device == null) return
        val target = resolveTargetDevice(device)
        tryDisconnect("HFP", headset, target)
        tryDisconnect("A2DP", a2dp, target)
    }

    private fun tryDisconnect(tag: String, proxy: BluetoothProfile?, device: BluetoothDevice) {
        if (proxy == null) return
        if (getState(proxy, device) == BluetoothProfile.STATE_DISCONNECTED) return
        val disconnect = invoke1(proxy, "disconnect", device)
        LogBus.log("AudioProfiles: $tag disconnect requested for ${device.address} (result=$disconnect)")
    }

    /**
     * The truthful btStatus to advertise to the glasses right now: the highest
     * classic-audio profile that is actually connected, else ACL.
     */
    fun currentBtStatus(): Int {
        val dev = pendingDevice ?: try {
            if (targetMac.isNotEmpty()) adapter.getRemoteDevice(targetMac) else null
        } catch (e: Exception) {
            null
        }
        if (dev == null) return LinkCommands.BTSTATUS_CONNECTED_ACL
        val target = resolveTargetDevice(dev)
        if (a2dp != null && getState(a2dp, target) == BluetoothProfile.STATE_CONNECTED) {
            return LinkCommands.BTSTATUS_CONNECTED_A2DP
        }
        if (headset != null && getState(headset, target) == BluetoothProfile.STATE_CONNECTED) {
            return LinkCommands.BTSTATUS_CONNECTED_HFP
        }
        return LinkCommands.BTSTATUS_CONNECTED_ACL
    }

    // ------------------------------------------------- connection-state events

    private fun registerReceiver() {
        val filter = IntentFilter()
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            stateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            @Suppress("DEPRECATION")
            val d = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            if (d == null || !d.address.equalsIgnoreCase(targetMac)) return
            val st = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
            val which = if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED == intent.action) "A2DP" else "HFP"
            LogBus.trace("$which state -> $st")
            if (st == BluetoothProfile.STATE_CONNECTED || st == BluetoothProfile.STATE_DISCONNECTED) {
                listener?.onStatusChanged(currentBtStatus())
            }
        }
    }

    fun close() {
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(stateReceiver)
            } catch (ignored: Exception) {
            }
            receiverRegistered = false
        }
        headset?.let {
            adapter.closeProfileProxy(BluetoothProfile.HEADSET, it)
            headset = null
        }
        a2dp?.let {
            adapter.closeProfileProxy(BluetoothProfile.A2DP, it)
            a2dp = null
        }
    }

    companion object {
        /** Hidden BluetoothProfile.CONNECTION_POLICY_ALLOWED. */
        private const val CONNECTION_POLICY_ALLOWED = 100
        /** Hidden BluetoothProfile.PRIORITY_AUTO_CONNECT. */
        private const val PRIORITY_AUTO_CONNECT = 1000

        private fun String.equalsIgnoreCase(other: String?): Boolean =
            this.equals(other, ignoreCase = true)

        private fun getState(proxy: BluetoothProfile?, device: BluetoothDevice?): Int {
            return try {
                proxy?.getConnectionState(device) ?: BluetoothProfile.STATE_DISCONNECTED
            } catch (t: Throwable) {
                BluetoothProfile.STATE_DISCONNECTED
            }
        }

        // ------------------------------------------------------------- reflection

        private fun invoke1(proxy: Any, name: String, device: BluetoothDevice): Boolean {
            return try {
                val m: Method = proxy.javaClass.getMethod(name, BluetoothDevice::class.java)
                val r = m.invoke(proxy, device)
                r !is Boolean || r
            } catch (t: Throwable) {
                LogBus.trace("$name reflection unavailable: ${t.javaClass.simpleName}")
                false
            }
        }

        private fun invoke2(proxy: Any, name: String, device: BluetoothDevice, arg: Int): Boolean {
            return try {
                val m: Method = proxy.javaClass.getMethod(name, BluetoothDevice::class.java, Int::class.javaPrimitiveType)
                val r = m.invoke(proxy, device, arg)
                r !is Boolean || r
            } catch (t: Throwable) {
                LogBus.trace("$name reflection unavailable: ${t.javaClass.simpleName}")
                false
            }
        }
    }
}
