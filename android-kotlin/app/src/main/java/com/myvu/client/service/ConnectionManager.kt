package com.myvu.client.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.myvu.client.ai.AiConversation
import com.myvu.client.app.AppLayer
import com.myvu.client.app.InboundRouter
import com.myvu.client.app.RelaySession
import com.myvu.client.app.feature.AiProtocol
import com.myvu.client.app.feature.ClockSync
import com.myvu.client.app.feature.Notifications
import com.myvu.client.app.feature.SystemSettings
import com.myvu.client.app.feature.Teleprompter
import com.myvu.client.app.feature.TouchGestureManager
import com.myvu.client.app.feature.Trackpad
import com.myvu.client.core.GlassesConfig
import com.myvu.client.core.Hex
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.crypto.StarryCrypto
import com.myvu.client.nav.FusedLocationSource
import com.myvu.client.nav.NavSession
import com.myvu.client.protocol.AbilityReply
import com.myvu.client.protocol.InitBurst
import com.myvu.client.protocol.MsgType
import com.myvu.client.protocol.Relay
import com.myvu.client.protocol.RelayMessage
import com.myvu.client.protocol.Session
import com.myvu.client.protocol.link.DeviceId
import com.myvu.client.protocol.link.DeviceInfo
import com.myvu.client.protocol.link.LinkCommands
import com.myvu.client.protocol.link.LinkMessage
import com.myvu.client.protocol.link.LinkProtocol
import com.myvu.client.transport.Transport
import com.myvu.client.transport.TransportListener
import com.myvu.client.transport.ble.BlePackets
import com.myvu.client.transport.ble.BlePairing
import com.myvu.client.transport.ble.BleTransport
import com.myvu.client.transport.ble.GlassesScanner
import com.myvu.client.transport.bt.RfcommTransport
import com.myvu.client.weather.WeatherSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.Deque
import java.util.Locale
import java.util.UUID

/**
 * Owns the connection to the glasses and every piece of protocol state.
 *
 * THREADING: all protocol state lives on the single "myvu-conn" thread, so
 * nothing in protocol/ or app/ needs locking. Transports post inbound payloads
 * here; the UI posts outbound requests here.
 *
 * TWO TRANSPORTS RUN AT ONCE, and the order is not a preference:
 *
 *  1. BLE first. The glasses' classic radio does not answer a page until BLE has
 *     brought them up -- a cold createBond just times out after ~13s with no
 *     ACL. BLE carries the ECDH bond and, crucially, is the ONLY place the
 *     app-relay's address is announced (CMD_SPP_SERVER_UUID_SYNC), because the
 *     glasses regenerate that UUID every session.
 *  2. RFCOMM second, to that per-session UUID. This is the link that actually
 *     carries app traffic; the fixed "channel 13" in early captures answered the
 *     handshake but never ACKed a single app message.
 *
 * Each transport runs its own independent RunAsOne session (see RelaySession).
 */
class ConnectionManager(
    context: Context,
    private val listener: Listener?
) : BleTransport.Listener, RelaySupervisor.Delegate {

    fun interface Listener {
        fun onStateChanged(state: ConnectionState)
    }

    private val context: Context = context.applicationContext

    private val connThread: HandlerThread = HandlerThread("myvu-conn").apply { start() }
    private val conn: Handler = Handler(connThread.looper)

    private val _stateFlow = MutableStateFlow(ConnectionState.IDLE)
    val stateFlow: StateFlow<ConnectionState> = _stateFlow.asStateFlow()

    private var sessionStartTimeMs: Long = 0L

    var state: ConnectionState
        get() = _stateFlow.value
        private set(s) {
            val old = _stateFlow.value
            if (s == ConnectionState.READY && old != ConnectionState.READY) {
                sessionStartTimeMs = android.os.SystemClock.elapsedRealtime()
            } else if (s != ConnectionState.READY) {
                sessionStartTimeMs = 0L
            }
            _stateFlow.value = s
            listener?.onStateChanged(s)
        }

    fun state(): ConnectionState = state

    fun connectedUptimeMs(): Long {
        if (state == ConnectionState.READY && sessionStartTimeMs > 0L) {
            return android.os.SystemClock.elapsedRealtime() - sessionStartTimeMs
        }
        return 0L
    }

    private var targetMac: String? = null
    private var device: BluetoothDevice? = null
    private var ownId: ByteArray? = null
    private var ownMac: String? = null
    private var sessionId: String? = null

    private var ble: BleTransport? = null
    private var pairing: BlePairing? = null
    /** BLE discovery for the "auto search" connect path; null unless searching. */
    private var scanner: GlassesScanner? = null
    private var glassesInfoVal: DeviceInfo? = null

    fun glassesInfo(): DeviceInfo? = glassesInfoVal

    /**
     * NOT final: a reconnect must start from a fresh sequencer. The glasses
     * track the last received msgId and discard anything that looks stale, so
     * reusing this across connections would make the second session's traffic
     * be silently dropped.
     */
    private var bleSession = RelaySession()

    private var rfcomm: RfcommTransport? = null
    private var rfSession: RelaySession? = null
    /** Learned over BLE; the address of the real app-relay channel. */
    private var sppUuidVal: String? = null

    fun sppUuid(): String? = sppUuidVal

    /** True from the moment we open the relay socket until its session is ready. */
    private var relayEstablishing = false

    private var supervisor: RelaySupervisor? = null

    private class PendingAction(
        val actionJson: String,
        val targetPkg: String,
        val sourcePkg: String
    )

    private val pendingNotifications: Deque<PendingAction> = ArrayDeque()

    /**
     * Connects the standard HFP/A2DP audio profiles so the glasses light their
     * own "phone connected" indicator. Kept across auto-reconnects; closed only
     * on shutdown. See AudioProfiles for the permission caveats.
     */
    private var audioProfiles: AudioProfiles? = null
    private var audioProfilesAttempted = false

    /**
     * The ECDH material from the BLE bond, retained so we can push an updated
     * DeviceInfo (WRITE_SWITCH_INFO) when the audio profiles connect after the
     * bond -- the btStatus in the first DeviceInfo is only ACL-level, since the
     * profiles come up seconds later.
     */
    private var bondKey: ByteArray? = null
    private var bondIv: ByteArray? = null
    private var bondMode = 0
    /** Last btStatus we told the glasses; suppresses redundant re-sends. */
    private var lastSentBtStatus = LinkCommands.BTSTATUS_DEFAULT

    /** Answers glasses-initiated requests (launch-app, time sync, AI triggers). */
    private val inbound: InboundRouter = InboundRouter { actionJson, targetPkg, sourcePkg ->
        sendActionNow(actionJson, targetPkg, sourcePkg)
    }

    fun inboundRouter(): InboundRouter = inbound

    init {
        // The glasses' AI button (code:3) and wake word (code:7) both land here.
        inbound.setAiTriggerListener { code, payload ->
            // control:0 is the button RELEASE / page close. It must NOT
            // abort a turn already in flight -- the release arrives moments
            // after the press -- so it only marks the conversation to end
            // at the next turn boundary.
            if (payload != null && payload.optInt("control", 1) == 0) {
                ai?.onPageClosed()
                return@setAiTriggerListener
            }
            // The glasses' mic audio only flows over the app relay. With
            // the relay down (its retry budget spent), a press listened to
            // nothing and timed out with "0 packets in" -- so treat the
            // press like the glasses asking for the relay back.
            supervisor?.wake()

            TouchGestureManager.handleTrigger(this.context, code, createGestureActionExecutor())
        }

        inbound.setTouchGestureListener { gestureType, rawCode, _ ->
            TouchGestureManager.handleGesture(this.context, gestureType, rawCode, createGestureActionExecutor())
        }

        inbound.setWeatherRequestListener {
            weather().refresh()
        }

        inbound.setBatteryUpdateListener { battery, isCharging ->
            updateGlassesBattery(battery, isCharging)
        }
    }

    private fun createGestureActionExecutor(): TouchGestureManager.ActionExecutor {
        return object : TouchGestureManager.ActionExecutor {
            override fun executeAiAssistant(triggerCode: Int) {
                ai().onTrigger(triggerCode)
            }

            override fun executePhoneAssistant() {
                TouchGestureManager.launchPhoneAssistant(this@ConnectionManager.context)
                try {
                    sendAction(Notifications.buildShow("MYVU", "Asistente activado"))
                } catch (ignored: Exception) {
                }
            }

            override fun executeWeatherSync() {
                weather().refresh()
                try {
                    sendAction(Notifications.buildShow("MYVU", "Actualizando clima..."))
                } catch (ignored: Exception) {
                }
            }

            override fun executeToggleMirror() {
                val enabled = !Prefs.mirrorEnabled(this@ConnectionManager.context)
                Prefs.setMirrorEnabled(this@ConnectionManager.context, enabled)
                LogBus.log("Touchpad gesture -> Notification mirroring " + (if (enabled) "ON" else "OFF"))
                try {
                    sendAction(Notifications.buildShow("MYVU", "Espejo notificaciones: " + (if (enabled) "Activado" else "Desactivado")))
                } catch (ignored: Exception) {
                }
            }

            override fun executeMediaPlayPause() {
                LogBus.log("Touchpad gesture -> Media Play/Pause")
                TouchGestureManager.sendMediaKey(this@ConnectionManager.context, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                try {
                    sendAction(Notifications.buildShow("MYVU", "Música: Play / Pausa"))
                } catch (ignored: Exception) {
                }
            }

            override fun executeMediaNext() {
                LogBus.log("Touchpad gesture -> Media Next")
                TouchGestureManager.sendMediaKey(this@ConnectionManager.context, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                try {
                    sendAction(Notifications.buildShow("MYVU", "Música: Siguiente"))
                } catch (ignored: Exception) {
                }
            }

            override fun executeMediaPrevious() {
                LogBus.log("Touchpad gesture -> Media Previous")
                TouchGestureManager.sendMediaKey(this@ConnectionManager.context, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                try {
                    sendAction(Notifications.buildShow("MYVU", "Música: Anterior"))
                } catch (ignored: Exception) {
                }
            }

            override fun executeOpenTeleprompter() {
                LogBus.log("Touchpad gesture -> Open Teleprompter")
                try {
                    sendAction(Teleprompter.buildOpen("", "MYVU"))
                } catch (ignored: Exception) {
                }
            }

            override fun executeZenMode() {
                val enabled = !Prefs.zenModeEnabled(this@ConnectionManager.context)
                Prefs.setZenModeEnabled(this@ConnectionManager.context, enabled)
                LogBus.log("Touchpad gesture -> Zen mode " + if (enabled) "ON" else "OFF")
                try {
                    setZenMode(enabled)
                    sendAction(Notifications.buildShow("MYVU", "Modo Zen: " + if (enabled) "Activado" else "Desactivado"))
                } catch (ignored: Exception) {
                }
            }

            override fun executeNone() {
                LogBus.log("Touchpad gesture -> None")
            }
        }
    }

    fun connHandler(): Handler = conn

    private val batteryQueryTask = object : Runnable {
        override fun run() {
            if (state == ConnectionState.READY) {
                queryBatteryInfo()
                conn.postDelayed(this, 15 * 60 * 1000L) // 15 min periodic check
            }
        }
    }

    fun queryBatteryInfo() {
        if (state == ConnectionState.READY) {
            try {
                sendActionNow(SystemSettings.query("get_device_info"))
            } catch (e: Exception) {
                LogBus.error("could not query battery info", e)
            }
        }
    }

    @JvmOverloads
    fun updateGlassesBattery(battery: Int, isCharging: Boolean = false) {
        if (battery < 0 || battery > 100) return
        conn.post {
            val current = glassesInfoVal
            if (current == null) {
                glassesInfoVal = DeviceInfo("", "", "", "5001", "MYVU", battery, 0)
                LogBus.log("glasses battery set: $battery%")
                listener?.onStateChanged(state)
            } else if (current.battery != battery) {
                glassesInfoVal = DeviceInfo(
                    current.btMac, current.companyId,
                    current.categoryId, current.modelId, current.name,
                    battery, current.btStatus
                )
                LogBus.log("glasses battery updated: $battery%")
                listener?.onStateChanged(state)
            }
        }
    }

    fun start(mac: String) {
        conn.post {
            // A second START (repeat tap, service restart, redelivered
            // intent) must not stand up a parallel BLE stack against the
            // same glasses -- they accept one central at a time.
            if (state != ConnectionState.IDLE && state != ConnectionState.FAILED) {
                LogBus.trace("connect ignored: already $state")
                listener?.onStateChanged(state)
                return@post
            }
            userStopped = false
            cancelReconnect()
            targetMac = mac
            beginConnect()
        }
    }

    /**
     * The "auto search" connect path: scan for a MYVU device over BLE, then
     * connect to whatever we find -- so the user doesn't have to know the MAC.
     * Falls back to a bonded MYVU device if none is advertising (they may be
     * bonded but asleep; the page attempt will wake or fail cleanly).
     */
    fun startAutoSearch() {
        conn.post {
            if (state != ConnectionState.IDLE && state != ConnectionState.FAILED) {
                LogBus.trace("auto-search ignored: already $state")
                listener?.onStateChanged(state)
                return@post
            }
            userStopped = false
            cancelReconnect()
            beginAutoSearch()
        }
    }

    private fun beginAutoSearch() {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        if (adapter == null) {
            failHard("no Bluetooth adapter on this device")
            return
        }
        if (!adapter.isEnabled) {
            failHard("Bluetooth is off -- turn it on and reconnect")
            return
        }
        // Reuse CONNECTING: the pairing overlay already reads this as
        // "Searching for your glasses", which is exactly what we're doing.
        state = ConnectionState.CONNECTING
        if (scanner == null) scanner = GlassesScanner(adapter, conn)
        scanner?.start(object : GlassesScanner.Callback {
            override fun onFound(device: BluetoothDevice, name: String?) {
                conn.post { connectTo(device.address) }
            }

            override fun onTimeout() {
                conn.post {
                    val bonded = firstBondedGlasses(adapter)
                    if (bonded != null) {
                        LogBus.log(
                            "no advertisement seen; trying the bonded glasses ${bonded.address}"
                        )
                        connectTo(bonded.address)
                    } else {
                        failHard(
                            "couldn't find your glasses -- make sure they're on and nearby, then try again"
                        )
                    }
                }
            }

            override fun onError(reason: String) {
                conn.post { failHard("auto-search failed: $reason") }
            }
        })
    }

    /** Adopt a discovered/bonded MAC and run the normal connect flow. */
    private fun connectTo(mac: String) {
        if (state != ConnectionState.CONNECTING) return // cancelled meanwhile
        targetMac = mac
        Prefs.setTargetMac(context, mac)
        beginConnect()
    }

    private fun firstBondedGlasses(adapter: BluetoothAdapter): BluetoothDevice? {
        try {
            for (d in adapter.bondedDevices) {
                val n = d.name
                if (n != null && n.uppercase(Locale.US).contains("MYVU")) return d
            }
        } catch (ignored: SecurityException) {
        }
        return null
    }

    fun stop() {
        conn.post {
            userStopped = true
            cancelReconnect()
            teardown()
            state = ConnectionState.IDLE
        }
    }

    fun shutdown() {
        stop()
        conn.post {
            audioProfiles?.close()
            audioProfiles = null
        }
        connThread.quitSafely()
    }

    private fun teardown() {
        scanner?.stop()
        // Fully release AI conversation (executor threads + TTS engine binding)
        ai?.shutdown()
        ai = null
        // Release weather sync
        weather?.stop()
        weather = null
        // Release navigation session
        nav?.stop()
        nav = null
        supervisor?.stop()
        supervisor = null
        closeRelay()
        ble?.close()
        ble = null
        pairing?.cancel()
        pairing = null
        sppUuidVal = null
        // Bond keys are per-session; a new BLE bond derives fresh ones. Drop them
        // so a late profile-state event can't resend with a stale key.
        bondKey = null
        bondIv = null
        lastSentBtStatus = LinkCommands.BTSTATUS_DEFAULT
        audioProfilesAttempted = false
    }

    private fun closeRelay() {
        rfcomm?.close()
        rfcomm = null
        rfSession = null
        relayEstablishing = false
        conn.removeCallbacks(relayEstablishTimeout)
    }

    // ------------------------------------------------------------ connect

    private fun beginConnect() {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        if (adapter == null) {
            failHard("no Bluetooth adapter on this device")
            return
        }
        if (!adapter.isEnabled) {
            failHard("Bluetooth is off -- turn it on and reconnect")
            return
        }

        val macStr = localIdentity(adapter)
        ownMac = macStr
        val idBytes = DeviceId.macToBytes(macStr)
        ownId = idBytes
        val sessId = deriveSession(idBytes)
        sessionId = sessId
        LogBus.log("target=$targetMac ownId=${Hex.encode(idBytes)} session=$sessId")

        val target = targetMac ?: run {
            failHard("target MAC is null")
            return
        }
        val dev = adapter.getRemoteDevice(target)
        device = dev
        // Stand up (once) the classic-audio profile manager. Reused across
        // reconnects so the profile proxies bind a single time.
        if (audioProfiles == null) {
            audioProfiles = AudioProfiles(context, adapter, target, audioListener)
        }
        // Fresh relay state per connection attempt (see the field comment).
        bleSession = RelaySession()

        state = ConnectionState.CONNECTING
        val bleTrans = BleTransport(context, dev, conn, this)
        ble = bleTrans
        bleTrans.connect()
    }

    /**
     * The identity we advertise. BluetoothAdapter.getAddress() has returned a
     * fixed placeholder since Android 6 for privacy reasons, so this is a
     * stand-in rather than our real MAC. Confirmed on hardware that the glasses
     * accept it -- they only use it to key the session.
     */
    private fun localIdentity(adapter: BluetoothAdapter): String {
        val addr = adapter.address
        if (addr == null || addr.isEmpty() || "02:00:00:00:00:00" == addr) {
            return "AA:BB:CC:DD:EE:FF"
        }
        return addr
    }

    // ------------------------------------------------ BleTransport.Listener

    override fun onReady(transport: BleTransport) {
        LogBus.log("BLE link stable -- starting the ECDH bond")
        state = ConnectionState.PAIRING
        // Report the truthful status now: the BLE ACL is up, so at least
        // CONNECTED_ACL holds; if the audio profiles happen to already be
        // connected (a warm reconnect), advertise that instead. We upgrade this
        // to HFP/A2DP later, once connectAudioProfiles() takes effect.
        val btStatus = audioProfiles?.currentBtStatus() ?: LinkCommands.BTSTATUS_CONNECTED_ACL
        lastSentBtStatus = btStatus
        val id = ownId ?: return
        val mac = ownMac ?: return
        val p = BlePairing(
            transport, conn, id, mac, DEVICE_NAME, btStatus,
            object : BlePairing.Callback {
                override fun onPaired(glasses: DeviceInfo) {
                    glassesInfoVal = glasses
                    bondKey = pairing?.sharedSecret
                    bondIv = pairing?.iv
                    bondMode = pairing?.encryptMode ?: 0
                    pairing = null
                    establishBleSession()
                }

                override fun onFailed(reason: String) {
                    pairing = null
                    fail("BLE pairing failed: $reason")
                }
            }
        )
        pairing = p
        p.start()
    }

    override fun onInternalMessage(pkgType: Int, payload: ByteArray) {
        // While the bond is running, the pairing state machine consumes these.
        if (pairing?.onInternalMessage(payload) == true) return

        val msg: LinkMessage = try {
            LinkProtocol.parse(payload)
        } catch (e: Exception) {
            LogBus.trace("internal <- unparseable (${payload.size}B)")
            return
        }

        when (msg.cmd) {
            LinkCommands.CMD_SPP_SERVER_UUID_SYNC -> handleSppUuidSync(msg.data)
            LinkCommands.CMD_SPP_SERVER_REQUEST_CONNECT -> {
                LogBus.trace("<- SPP_SERVER_REQUEST_CONNECT")
                supervisor?.wake()
            }
            LinkCommands.CMD_SPP_SERVER_REQUEST_STATE_OPEN -> {
                LogBus.trace("<- SPP server open")
                supervisor?.wake()
            }
            LinkCommands.CMD_SPP_SERVER_REQUEST_STATE_CLOSE -> {
                if (relayEstablishing) {
                    LogBus.trace("<- SPP server close (stale; relay still establishing)")
                } else {
                    LogBus.log("<- SPP server closed by the glasses -- dropping the relay")
                    closeRelay()
                    supervisor?.onRelayLost()
                }
            }
            else -> {
                LogBus.trace("internal <- LinkProtocol cmd=${msg.cmd} (${msg.data.size}B)")
            }
        }
    }

    /**
     * The app relay lives at a random 16-bit UUID the glasses regenerate every
     * session and announce only here. Nothing else tells us where to connect.
     */
    private fun handleSppUuidSync(data: ByteArray) {
        val uuid: String = try {
            LinkProtocol.sppShortUuidToString(data)
        } catch (e: Exception) {
            LogBus.warn("bad SPP UUID payload: ${Hex.encode(data)}")
            return
        }
        if (uuid == sppUuidVal) {
            LogBus.trace("<- SPP_SERVER_UUID_SYNC (unchanged)")
            return
        }
        // A different UUID means a new relay instance: the old socket is dead.
        if (sppUuidVal != null) {
            LogBus.log("relay UUID changed $sppUuidVal -> $uuid")
            closeRelay()
        }
        sppUuidVal = uuid
        LogBus.log("<- SPP_SERVER_UUID_SYNC: uuid=$sppUuidVal")
        supervisor?.wake()
    }

    override fun onExternalMessage(pkgType: Int, payload: ByteArray) {
        routePayload(payload, bleSession, null)
    }

    override fun onDisconnected(reason: String) {
        LogBus.warn("BLE $reason")
        teardown()
        state = ConnectionState.FAILED
        scheduleReconnect("BLE link dropped")
    }

    // ---------------------------------------------------------- reconnect

    private var userStopped = false
    private var reconnectAttempt = 0
    private val reconnectRunnable = Runnable {
        if (userStopped || state == ConnectionState.READY) return@Runnable
        LogBus.log("reconnecting to the glasses (attempt $reconnectAttempt)")
        beginConnect()
    }

    private fun scheduleReconnect(why: String) {
        if (userStopped) return
        conn.removeCallbacks(reconnectRunnable)
        reconnectAttempt++
        val delay = Math.min(
            RECONNECT_MAX_MS,
            RECONNECT_BASE_MS * (1L shl Math.min(5, reconnectAttempt - 1))
        )
        LogBus.log("$why -- retrying in ${delay / 1000}s")
        conn.postDelayed(reconnectRunnable, delay)
    }

    private fun cancelReconnect() {
        conn.removeCallbacks(reconnectRunnable)
        reconnectAttempt = 0
    }

    // ------------------------------------------------------- BLE session

    private fun establishBleSession() {
        state = ConnectionState.SESSION
        sendAbility(bleSession, null)
    }

    // --------------------------------------------------- RFCOMM (relay)

    override fun isRelayConnected(): Boolean {
        if (relayEstablishing) return true // handshake in flight; give it time
        val r = rfcomm
        val s = rfSession
        return r != null && r.isConnected && s != null && s.ready
    }

    fun wakeRelay() {
        conn.post { supervisor?.wake() }
    }

    override fun canConnectRelay(): Boolean {
        return sppUuidVal != null && device != null && !relayEstablishing
    }

    override fun connectRelay() {
        if (relayEstablishing) return
        closeRelay()
        relayEstablishing = true

        val uuidStr = sppUuidVal ?: return
        val dev = device ?: return
        val uuid = UUID.fromString(uuidStr)
        LogBus.log("opening the app relay: RFCOMM -> $uuid")
        val session = RelaySession()
        rfSession = session
        val transport = RfcommTransport(dev, uuid, relayListener, conn)
        rfcomm = transport
        transport.connect()

        conn.postDelayed(relayEstablishTimeout, RELAY_ESTABLISH_TIMEOUT_MS)
    }

    private val relayEstablishTimeout = Runnable {
        if (!relayEstablishing) return@Runnable
        relayEstablishing = false
        if (rfSession?.ready == true) return@Runnable
        LogBus.warn(
            "app relay did not finish its handshake within ${RELAY_ESTABLISH_TIMEOUT_MS / 1000}s -- retrying"
        )
        closeRelay()
        supervisor?.onRelayLost()
    }

    private fun relayEstablished() {
        relayEstablishing = false
        conn.removeCallbacks(relayEstablishTimeout)
    }

    private val relayListener = object : TransportListener {
        override fun onConnected(transport: Transport) {
            LogBus.log("app relay connected -- running its own session handshake")
            sendAbility(rfSession, transport)
        }

        override fun onPayload(transport: Transport, payload: ByteArray) {
            routePayload(payload, rfSession, transport)
        }

        override fun onDisconnected(transport: Transport, cause: Throwable?) {
            relayEstablished()
            if (cause != null) {
                LogBus.warn("app relay lost: ${cause.javaClass.simpleName}: ${cause.message}")
            } else {
                LogBus.log("app relay closed")
            }
            if (rfcomm === transport) {
                rfcomm = null
                rfSession = null
            }
            supervisor?.onRelayLost()
        }
    }

    // ------------------------------------------- session handshake (both)

    private fun sendAbility(session: RelaySession?, transport: Transport?) {
        if (session == null) return
        val id = ownId ?: return
        val sess = sessionId ?: return
        try {
            val msg = Session.buildAbilityMessage(Hex.encode(id), DEVICE_NAME, sess)
            LogBus.log("-> ability handshake (session=$sess)")
            sendOn(transport, msg)
        } catch (e: Exception) {
            LogBus.error("could not build the ability message", e)
        }
    }

    private fun routePayload(payload: ByteArray, session: RelaySession?, transport: Transport?) {
        if (session == null) return

        if (payload.isNotEmpty() && (payload[0].toInt() and 0xFF) == Session.AUTH_CLASS_BYTE) {
            handleAbilityReply(payload, session, transport)
            return
        }

        val m = Relay.parseFrame(payload)
        if (m == null) {
            LogBus.trace(
                "<- unparsed ${payload.size}B ${Hex.encode(payload, 0, Math.min(32, payload.size))}"
            )
            return
        }
        handleRelayMessage(m, session, transport)
    }

    private fun handleAbilityReply(payload: ByteArray, session: RelaySession, transport: Transport?) {
        val reply: AbilityReply = Session.parseAbilityReply(payload)
        if (session.authConfirmed) {
            LogBus.trace("<- duplicate ability reply ignored")
            return
        }
        session.authConfirmed = true

        LogBus.log("<- ability reply from deviceId=${reply.deviceId}")
        val id = ownId ?: return
        val sess = sessionId ?: return
        try {
            val confirm = Session.buildAuthSuccessMessage(Hex.encode(id), DEVICE_NAME, sess)
            LogBus.log("-> AUTH_SUCCESS")
            sendOn(transport, confirm)
        } catch (e: Exception) {
            LogBus.error("could not build AUTH_SUCCESS", e)
            return
        }
        conn.postDelayed({ sendInitBurst(session, transport) }, 500)
    }

    private fun handleRelayMessage(m: RelayMessage, session: RelaySession, transport: Transport?) {
        if (m.msgType == MsgType.SEND_SUCCESS) {
            LogBus.trace("<- ack msgId=${m.msgId}${if (transport != null) " [relay]" else " [ble]"}")
            return
        }
        if (m.msgType == MsgType.SEND) {
            session.seq.lastRecvId = m.msgId

            if (isAudioFrame(m.msgBody)) {
                if (m.needCallback != 0) sendOn(transport, session.seq.ackFrame(m))
                ai?.onAudioFrame(m.msgBody)
                if (++audioFrameCount % 200 == 0) {
                    LogBus.trace("received $audioFrameCount glasses mic frames")
                }
                return
            }

            val body = String(m.msgBody, StandardCharsets.UTF_8)
            LogBus.log("<- msgId=${m.msgId} ${truncate(body, 200)}")
            if (m.needCallback != 0) sendOn(transport, session.seq.ackFrame(m))
            inbound.handle(body)
            return
        }
        LogBus.trace("<- relay msgType=${m.msgType} msgId=${m.msgId}")
    }

    // --------------------------------------------------------- init burst

    private fun sendInitBurst(session: RelaySession, transport: Transport?) {
        val entries: List<InitBurst.Entry> = try {
            val inputStream: InputStream = context.assets.open(InitBurst.ASSET_NAME)
            val list = InitBurst.load(inputStream)
            inputStream.close()
            list
        } catch (e: Exception) {
            LogBus.error("could not read ${InitBurst.ASSET_NAME}", e)
            return
        }
        LogBus.log(
            "-> init burst (${entries.size} messages${if (transport != null) ", relay)" else ", ble)"}"
        )
        scheduleInitMessage(entries, 0, session, transport)
    }

    private fun scheduleInitMessage(
        entries: List<InitBurst.Entry>,
        index: Int,
        session: RelaySession,
        transport: Transport?
    ) {
        if (index >= entries.size) {
            session.ready = true
            LogBus.log(
                "init burst complete -- ${if (transport != null) "app relay ready" else "BLE session ready"}"
            )
            onSessionReady(transport)
            return
        }
        if (!isUsable(transport)) {
            LogBus.warn(
                "link dropped during the init burst at message $index -- session left incomplete"
            )
            if (transport != null) {
                closeRelay()
                supervisor?.onRelayLost()
            } else {
                fail("BLE init burst did not complete")
            }
            return
        }

        val e = entries[index]
        sendOn(
            transport,
            session.seq.dataFrame(e.msgBody, e.category, e.needCallback, e.appUniteCode)
        )
        conn.postDelayed({
            scheduleInitMessage(entries, index + 1, session, transport)
        }, if (transport != null) 80L else 150L)
    }

    private fun onSessionReady(transport: Transport?) {
        state = ConnectionState.READY
        cancelReconnect()
        if (transport != null) {
            relayEstablished()
            while (!pendingNotifications.isEmpty()) {
                val p = pendingNotifications.pollFirst() ?: break
                LogBus.log("flushing queued notification over RFCOMM: ${truncate(p.actionJson, 80)}")
                sendActionNow(p.actionJson, p.targetPkg, p.sourcePkg)
            }
        }

        val relayExpected = (transport == null) && (sppUuidVal != null)
        if (!relayExpected) {
            applyDefaults()
            if (audioProfiles != null && !audioProfilesAttempted) {
                audioProfilesAttempted = true
                audioProfiles?.connect(device)
            }
        }

        if (transport == null) {
            if (supervisor == null) {
                supervisor = RelaySupervisor(conn, this)
                supervisor?.start()
            }
            supervisor?.wake()
        }
    }

    private fun applyDefaults() {
        conn.postDelayed({
            try {
                sendActionNow(ClockSync.build())
            } catch (ignored: Exception) {
            }
        }, 100)
        conn.postDelayed({
            try {
                sendActionNow(SystemSettings.setScreenOffTime(GlassesConfig.getScreenOffTime(context)))
            } catch (ignored: Exception) {
            }
            try {
                sendActionNow(SystemSettings.setBrightness(GlassesConfig.getBrightness(context)))
            } catch (ignored: Exception) {
            }
            try {
                sendActionNow(SystemSettings.setVolume(GlassesConfig.getVolume(context)))
            } catch (ignored: Exception) {
            }
        }, 250)
        conn.postDelayed({
            try {
                sendActionNow(SystemSettings.setStandbyPosition(GlassesConfig.getStandbyPosition(context)))
            } catch (ignored: Exception) {
            }
            try {
                sendActionNow(SystemSettings.setZenMode(Prefs.zenModeEnabled(context)))
            } catch (ignored: Exception) {
            }
            try {
                sendActionNow(SystemSettings.setWearDetection(Prefs.wearDetectionEnabled(context)))
            } catch (ignored: Exception) {
            }
            try {
                sendActionNow(SystemSettings.setMusicTpControl(Prefs.musicTouchPanelEnabled(context)))
            } catch (ignored: Exception) {
            }
            try {
                if (Prefs.airModeEnabled(context)) sendActionNow(SystemSettings.setAirMode(true))
            } catch (ignored: Exception) {
            }
            try {
                sendActionNow(SystemSettings.toggleWifi(Prefs.wifiEnabled(context)))
            } catch (ignored: Exception) {
            }
            try {
                sendActionNow(SystemSettings.setLanguage("es", "ES"))
            } catch (ignored: Exception) {
            }
            try {
                sendActionNow(AiProtocol.assistantConfig(Prefs.voiceWakeupEnabled(context)), AiProtocol.PKG, AiProtocol.PKG)
            } catch (ignored: Exception) {
            }
        }, 400)
        conn.postDelayed({
            if (Prefs.weatherEnabled(context)) {
                weather().start()
            }
            conn.removeCallbacks(batteryQueryTask)
            conn.post(batteryQueryTask)
        }, 650)
    }

    // ------------------------------------------------- classic audio profiles

    private val audioListener = AudioProfiles.Listener { btStatus ->
        conn.post { onBtStatusChanged(btStatus) }
    }

    private fun onBtStatusChanged(btStatus: Int) {
        if (btStatus == lastSentBtStatus) return
        LogBus.log("BT audio status changed -> ${btStatusName(btStatus)}; updating the glasses")
        resendDeviceInfo(btStatus)
    }

    private fun resendDeviceInfo(btStatus: Int) {
        val key = bondKey ?: return
        val iv = bondIv ?: return
        val mac = ownMac ?: return
        val id = ownId ?: return
        val b = ble ?: return
        if (!b.isConnected) return

        try {
            val info = DeviceInfo.build(
                mac.uppercase(Locale.US), "", CATEGORY_ID, "", DEVICE_NAME, 100, btStatus
            )
            val inner = StarryCrypto.encrypt(info, key, iv, bondMode)
            val wsi = LinkProtocol.writeSwitchInfo(inner, 0)
            val outer = StarryCrypto.encrypt(wsi, key, iv, bondMode)
            val msg = LinkProtocol.build(id, LinkCommands.CMD_WRITE_SWITCH_INFO, outer)
            LogBus.log("-> WRITE_SWITCH_INFO btStatus=${btStatusName(btStatus)} (${msg.size}B)")
            lastSentBtStatus = btStatus
            b.internal()?.sendSingleAcked(msg, BlePackets.PKG_STARRY_DATA) { status ->
                if (status != BlePackets.ACK_SUCCESS) {
                    LogBus.warn("btStatus update was not acked (status=$status)")
                }
            }
        } catch (e: Exception) {
            LogBus.error("could not resend DeviceInfo btStatus", e)
        }
    }

    // --------------------------------------------------------- app layer

    fun sendAction(actionJson: String) {
        sendAction(actionJson, AppLayer.PKG_LAUNCHER, AppLayer.PKG_LAUNCHER)
    }

    fun sendAction(actionJson: String, targetPkg: String, sourcePkg: String) {
        conn.post { sendActionNow(actionJson, targetPkg, sourcePkg) }
    }

    private fun sendActionNow(actionJson: String) {
        sendActionNow(actionJson, AppLayer.PKG_LAUNCHER, AppLayer.PKG_LAUNCHER)
    }

    private fun sendActionNow(actionJson: String, targetPkg: String, sourcePkg: String) {
        val session = activeSession()
        val transport = activeTransport()

        val isNotification = actionJson.contains("SHOW_NOTIFICATION")
        if (isNotification && transport == null) {
            if (relayEstablishing || canConnectRelay()) {
                if (pendingNotifications.size < 5) {
                    pendingNotifications.add(PendingAction(actionJson, targetPkg, sourcePkg))
                    LogBus.warn("app relay not ready -- queued notification for RFCOMM delivery")
                }
                wakeRelay()
                return
            }
        }

        if (session == null || !session.ready) {
            LogBus.warn("no ready session -- action dropped")
            return
        }
        sendOn(
            transport,
            session.seq.dataFrame(
                session.appLayer.buildSendActionBody(actionJson, targetPkg, sourcePkg)
            )
        )
        LogBus.log(
            "-> action msgId=${session.seq.outId}${if (transport != null) " [relay] " else " [ble] "}${truncate(actionJson, 120)}"
        )
    }

    // ------------------------------------------------------- feature API

    fun openTeleprompter(text: String, title: String) {
        conn.post {
            try {
                sendActionNow(
                    Teleprompter.buildOpen(text, title),
                    AppLayer.PKG_LAUNCHER, AppLayer.PKG_TICI
                )
                conn.postDelayed({
                    try {
                        sendActionNow(
                            Teleprompter.buildContent(text, title),
                            AppLayer.PKG_LAUNCHER, AppLayer.PKG_TICI
                        )
                    } catch (e: Exception) {
                        LogBus.error("teleprompter content failed", e)
                    }
                }, Teleprompter.OPEN_TO_CONTENT_DELAY_MS)
            } catch (e: Exception) {
                LogBus.error("teleprompter open failed", e)
            }
        }
    }

    fun teleprompterHighlight(index: Int, title: String) {
        try {
            sendAction(
                Teleprompter.buildHighlight(index, title),
                AppLayer.PKG_LAUNCHER, AppLayer.PKG_TICI
            )
        } catch (e: Exception) {
            LogBus.error("teleprompter highlight failed", e)
        }
    }

    // ----------------------------------------------------------- trackpad

    fun trackpadStart() { sendAction(Trackpad.start()) }
    fun trackpadStop() { sendAction(Trackpad.stop()) }
    fun trackpadClick() { sendAction(Trackpad.click()) }
    fun trackpadDoubleClick() { sendAction(Trackpad.doubleClick()) }
    fun trackpadLongPress() { sendAction(Trackpad.longPress()) }

    fun trackpadSwipe(
        direction: Int, startX: Float, startY: Float,
        endX: Float, endY: Float, speedX: Float, speedY: Float
    ) {
        sendAction(Trackpad.swipe(direction, startX, startY, endX, endY, speedX, speedY))
    }

    fun setVolume(value: Int) { safeSend(safeVolume(value)) }
    fun setBrightness(value: Int) { safeSend(safeBrightness(value)) }
    fun toggleWifi(on: Boolean) { safeSend(safeToggleWifi(on)) }
    fun setStandbyPosition(position: Int) { safeSend(safeStandby(position)) }
    fun setZenMode(on: Boolean) { safeSend(safeZen(on)) }
    fun setAirMode(on: Boolean) { safeSend(safeAir(on)) }
    fun setWearDetection(on: Boolean) { safeSend(safeWear(on)) }
    fun setMusicTpControl(on: Boolean) { safeSend(safeMusicTp(on)) }
    fun setScreenOffTime(seconds: Int) { safeSend(safeScreenOff(seconds)) }
    fun setDeviceName(name: String) { safeSend(safeDeviceName(name)) }
    fun setLanguage(language: String, country: String) { safeSend(safeLanguage(language, country)) }
    fun syncTime() { safeSend(safeClockSync()) }
    fun sendRaw(actionJson: String) { sendAction(actionJson) }
    fun askAi(question: String) { ai().askText(question) }

    private fun safeVolume(v: Int): String? = try { SystemSettings.setVolume(v) } catch (e: Exception) { null }
    private fun safeBrightness(v: Int): String? = try { SystemSettings.setBrightness(v) } catch (e: Exception) { null }
    private fun safeToggleWifi(on: Boolean): String? = try { SystemSettings.toggleWifi(on) } catch (e: Exception) { null }
    private fun safeStandby(p: Int): String? = try { SystemSettings.setStandbyPosition(p) } catch (e: Exception) { null }
    private fun safeZen(on: Boolean): String? = try { SystemSettings.setZenMode(on) } catch (e: Exception) { null }
    private fun safeAir(on: Boolean): String? = try { SystemSettings.setAirMode(on) } catch (e: Exception) { null }
    private fun safeWear(on: Boolean): String? = try { SystemSettings.setWearDetection(on) } catch (e: Exception) { null }
    private fun safeMusicTp(on: Boolean): String? = try { SystemSettings.setMusicTpControl(on) } catch (e: Exception) { null }
    private fun safeScreenOff(s: Int): String? = try { SystemSettings.setScreenOffTime(s) } catch (e: Exception) { null }
    private fun safeDeviceName(n: String): String? = try { SystemSettings.setDeviceName(n) } catch (e: Exception) { null }
    private fun safeLanguage(l: String, c: String): String? = try { SystemSettings.setLanguage(l, c) } catch (e: Exception) { null }
    private fun safeClockSync(): String? = try { ClockSync.build() } catch (e: Exception) { null }

    private fun safeSend(actionJson: String?) {
        if (actionJson != null) sendAction(actionJson)
    }

    // ------------------------------------------------------- AI assistant

    private var ai: AiConversation? = null

    fun ai(): AiConversation {
        val existing = ai
        if (existing != null) return existing
        val newAi = AiConversation(context) { actionJson, targetPkg, sourcePkg ->
            sendAction(actionJson, targetPkg, sourcePkg)
        }
        ai = newAi
        return newAi
    }

    // ------------------------------------------------------------ navigation

    private var weather: WeatherSync? = null

    fun weather(): WeatherSync {
        val existing = weather
        if (existing != null) return existing
        val newWeather = WeatherSync(
            context, conn,
            { actionJson -> sendActionNow(actionJson) },
            FusedLocationSource(context)
        )
        weather = newWeather
        return newWeather
    }

    fun syncWeatherNow() {
        conn.post { weather().start() }
    }

    private var nav: NavSession? = null

    fun nav(): NavSession {
        val existing = nav
        if (existing != null) return existing
        val newNav = NavSession(
            context, conn,
            { actionJson, targetPkg, sourcePkg -> sendAction(actionJson, targetPkg, sourcePkg) },
            FusedLocationSource(context)
        )
        nav = newNav
        return newNav
    }

    fun query(subAction: String) {
        try {
            sendAction(SystemSettings.query(subAction))
        } catch (e: Exception) {
            LogBus.error("query failed", e)
        }
    }

    fun sendTestNotification(title: String, body: String) {
        try {
            sendAction(AppLayer.buildNotificationAction(title, body))
        } catch (e: Exception) {
            LogBus.error("could not build the notification", e)
        }
    }

    private fun activeSession(): RelaySession? {
        val rf = rfSession
        if (rf != null && rf.ready && isRelayConnected()) {
            relayFallbackWarned = false
            return rf
        }
        if (bleSession.ready) {
            if (!relayFallbackWarned) {
                relayFallbackWarned = true
                LogBus.warn(
                    "app relay is DOWN -- falling back to BLE. Features that " +
                        "need the relay (nav HUD, teleprompter) may not respond."
                )
            }
            return bleSession
        }
        return null
    }

    private var relayFallbackWarned = false

    private fun activeTransport(): Transport? {
        val rf = rfSession
        if (rf != null && rf.ready && isRelayConnected()) return rfcomm
        return null
    }

    private fun sendOn(transport: Transport?, payload: ByteArray) {
        if (transport != null) {
            transport.send(payload)
        } else {
            val b = ble
            if (b != null && b.isReady) {
                b.external()?.send(payload, BlePackets.PKG_COMMON_DATA)
            }
        }
    }

    private fun isUsable(transport: Transport?): Boolean {
        return if (transport != null) transport.isConnected else (ble?.isReady == true)
    }

    private fun fail(why: String) {
        LogBus.warn(why)
        teardown()
        state = ConnectionState.FAILED
        scheduleReconnect("connection failed")
    }

    private fun failHard(why: String) {
        LogBus.warn(why)
        cancelReconnect()
        teardown()
        state = ConnectionState.FAILED
    }

    private var audioFrameCount = 0

    companion object {
        private const val DEVICE_NAME = "MyvuAndroid"
        private const val CATEGORY_ID = "9999"
        private const val RELAY_ESTABLISH_TIMEOUT_MS = 30000L
        private const val RECONNECT_BASE_MS = 2000L
        private const val RECONNECT_MAX_MS = 60000L

        private val AUDIO_MARKER = "\"code\":109".toByteArray(StandardCharsets.US_ASCII)

        private fun deriveSession(id: ByteArray): String {
            val v = ((id[id.size - 2].toInt() and 0xFF) shl 8) or (id[id.size - 1].toInt() and 0xFF)
            return v.toString()
        }

        private fun btStatusName(s: Int): String {
            return when (s) {
                LinkCommands.BTSTATUS_CONNECTED_ACL -> "ACL"
                LinkCommands.BTSTATUS_CONNECTED_HFP -> "HFP"
                LinkCommands.BTSTATUS_CONNECTED_A2DP -> "A2DP"
                else -> s.toString()
            }
        }

        private fun truncate(s: String?, n: Int): String {
            if (s == null) return "null"
            return if (s.length <= n) s else s.substring(0, n) + "..."
        }

        private fun isAudioFrame(body: ByteArray): Boolean {
            if (body.size < AUDIO_MARKER.size) return false
            outer@ for (i in 0..body.size - AUDIO_MARKER.size) {
                for (j in AUDIO_MARKER.indices) {
                    if (body[i + j] != AUDIO_MARKER[j]) continue@outer
                }
                return true
            }
            return false
        }
    }
}
