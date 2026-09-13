package com.myvu.client.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.myvu.client.app.feature.GlassGesture
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.core.TextToSpeechHelper
import com.myvu.client.ui.ConnectActivity

/**
 * Holds the glasses connection for as long as the user wants it up.
 *
 * A foreground service is not optional here: the link must survive the app
 * being backgrounded and the screen locking, and the glasses drop the app relay
 * (then re-request it) whenever the phone side goes quiet.
 */
class MyvuService : Service(), ConnectionManager.Listener {

    private var connection: ConnectionManager? = null
    private var mediaSession: MediaSession? = null
    private var screenOffReceiverRegistered = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                LogBus.log("MyvuService -> Screen off detected: releasing Bluetooth SCO audio and stopping scans")
                com.myvu.client.app.feature.TouchGestureManager.releaseBluetoothSco(this@MyvuService)
                try {
                    BluetoothDeviceManager.getInstance(this@MyvuService).stopScanning()
                } catch (_: Exception) {}
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): MyvuService = this@MyvuService
    }

    private val binder: IBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        Prefs.loggingEnabled(this)
        createNotificationChannel()
        connection = ConnectionManager(this, this)
        active = connection
        setupMediaSession()
        TextToSpeechHelper.init(this)
        BluetoothDeviceManager.getInstance(this)
        AutoSendAccessibilityService.checkAndRestoreOrNotify(this)
        try {
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            screenOffReceiverRegistered = true
        } catch (e: Exception) {
            LogBus.warn("MyvuService -> Failed to register screenOffReceiver: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (ACTION_STOP == action) {
            LogBus.log("MyvuService -> Received ACTION_STOP: performing total disconnection and full service inactivation")
            Prefs.setAutoReconnectEnabled(this, false)
            ServiceWatchdogReceiver.cancelWatchdog(this)
            com.myvu.client.app.feature.TouchGestureManager.releaseBluetoothSco(this)
            try {
                BluetoothDeviceManager.getInstance(this).stopScanning()
                BluetoothDeviceManager.getInstance(this).markAllDevicesDisconnectedBlocking()
            } catch (_: Exception) {}
            try {
                com.myvu.client.health.HealthService.getInstance(this).unregisterHardwareSensor()
            } catch (_: Exception) {}
            try {
                mediaSession?.isActive = false
                mediaSession?.release()
            } catch (_: Throwable) {}
            mediaSession = null
            connection?.stop()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        // startForeground must happen promptly after startForegroundService,
        // and on API 34+ the type is mandatory and must match the manifest.
        startInForeground("Connecting...")

        if (Intent.ACTION_MEDIA_BUTTON == action && intent != null) {
            val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? android.view.KeyEvent
            }
            if (event != null && event.action == android.view.KeyEvent.ACTION_DOWN) {
                mediaSession?.controller?.dispatchMediaButtonEvent(event)
            }
            return START_STICKY
        }

        if (ACTION_START == action || action == null) {
            Prefs.setAutoReconnectEnabled(this, true)
            val mac = if (intent != null && intent.hasExtra(EXTRA_MAC)) {
                intent.getStringExtra(EXTRA_MAC)?.trim()?.ifEmpty { null }
            } else {
                Prefs.targetMac(this).trim().ifEmpty { null }
            }
            if (!mac.isNullOrEmpty()) {
                connection?.start(mac)
            } else {
                // No MAC supplied -> discover the glasses over BLE (auto search).
                connection?.startAutoSearch()
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!Prefs.autoReconnectEnabled(this)) {
            LogBus.log("MyvuService: Task removed but auto-reconnect disabled by user — not restarting service")
            return
        }
        LogBus.log("MyvuService: Task removed from Recents — ensuring service stays alive")
        try {
            com.myvu.client.core.ServiceKeepAliveHelper.ensureServiceRunning(applicationContext)
        } catch (e: Throwable) {
            LogBus.error("MyvuService: Failed to restart on task removed", e)
        }
    }


    private fun startInForeground(status: String) {
        val n = buildNotification(status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Glasses connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the link to the MYVU glasses alive"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ConnectActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("MYVU glasses")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onStateChanged(state: ConnectionState) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(describe(state)))
    }

    /** The bound API the UI drives. */
    fun connection(): ConnectionManager? {
        return connection
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun setupMediaSession() {
        try {
            val session = MediaSession(this, "MyvuGlassesMediaSession")
            session.setCallback(object : MediaSession.Callback() {
                private var lastHookTime = 0L
                private var hookTapCount = 0
                private val handler = android.os.Handler(android.os.Looper.getMainLooper())
                private val hookRunnable = Runnable {
                    val count = hookTapCount
                    hookTapCount = 0
                    val gesture = when {
                        count >= 3 -> GlassGesture.TRIPLE_TAP
                        count == 2 -> GlassGesture.DOUBLE_TAP
                        else -> GlassGesture.TAP
                    }
                    LogBus.log("Bluetooth media button -> $gesture (tap count=$count)")
                    connection?.executeGesture(gesture, gesture.code)
                }

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? android.view.KeyEvent
                    } ?: return super.onMediaButtonEvent(mediaButtonIntent)

                    if (event.action != android.view.KeyEvent.ACTION_DOWN) {
                        return true
                    }

                    LogBus.log("Bluetooth media key event received: keyCode=${event.keyCode}")

                    // If glasses are not connected, route directly to HeadphoneGestureManager for Bluetooth earbuds/headphones
                    if (connection?.state != ConnectionState.READY) {
                        val handled = HeadphoneGestureManager.getInstance(this@MyvuService)
                            .onHeadsetButtonEvent(event.keyCode, event.action)
                        if (handled) return true
                    }
                    when (event.keyCode) {
                        android.view.KeyEvent.KEYCODE_HEADSETHOOK,
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            val now = android.os.SystemClock.uptimeMillis()
                            handler.removeCallbacks(hookRunnable)
                            if (now - lastHookTime < 450L) {
                                hookTapCount++
                            } else {
                                hookTapCount = 1
                            }
                            lastHookTime = now
                            handler.postDelayed(hookRunnable, 350L)
                            return true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            LogBus.log("Bluetooth media key NEXT -> SWIPE_FORWARD / DOUBLE_TAP")
                            connection?.executeGesture(GlassGesture.SWIPE_FORWARD, event.keyCode)
                            return true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            LogBus.log("Bluetooth media key PREVIOUS -> SWIPE_BACKWARD / TRIPLE_TAP")
                            connection?.executeGesture(GlassGesture.SWIPE_BACKWARD, event.keyCode)
                            return true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            connection?.executeGesture(GlassGesture.SWIPE_FORWARD, event.keyCode)
                            return true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            connection?.executeGesture(GlassGesture.SWIPE_BACKWARD, event.keyCode)
                            return true
                        }
                        android.view.KeyEvent.KEYCODE_VOICE_ASSIST,
                        android.view.KeyEvent.KEYCODE_ASSIST -> {
                            connection?.executeGesture(GlassGesture.LONG_PRESS, event.keyCode)
                            return true
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            })

            val state = android.media.session.PlaybackState.Builder()
                .setActions(
                    android.media.session.PlaybackState.ACTION_PLAY or
                    android.media.session.PlaybackState.ACTION_PAUSE or
                    android.media.session.PlaybackState.ACTION_PLAY_PAUSE or
                    android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT or
                    android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    android.media.session.PlaybackState.ACTION_FAST_FORWARD or
                    android.media.session.PlaybackState.ACTION_REWIND
                )
                .setState(android.media.session.PlaybackState.STATE_PAUSED, 0L, 1.0f)
                .build()
            session.setPlaybackState(state)

            val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
            mediaButtonIntent.setClass(this, MyvuService::class.java)
            val pendingIntent = PendingIntent.getService(
                this,
                0,
                mediaButtonIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
            session.setMediaButtonReceiver(pendingIntent)
            session.setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            session.isActive = true
            mediaSession = session
            LogBus.log("MyvuService: MediaSession active for Bluetooth touch gestures")
        } catch (e: Throwable) {
            LogBus.error("MyvuService: Failed to initialize MediaSession", e)
        }
    }

    override fun onDestroy() {
        LogBus.log("service stopping")
        if (screenOffReceiverRegistered) {
            try { unregisterReceiver(screenOffReceiver) } catch (_: Exception) {}
            screenOffReceiverRegistered = false
        }
        com.myvu.client.app.feature.TouchGestureManager.releaseBluetoothSco(this)
        try {
            BluetoothDeviceManager.getInstance(this).stopScanning()
        } catch (_: Exception) {}
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (ignored: Throwable) {
        }
        mediaSession = null
        active = null
        connection?.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START: String = "com.myvu.client.START"
        const val ACTION_STOP: String = "com.myvu.client.STOP"
        const val EXTRA_MAC: String = "mac"

        private const val CHANNEL_ID = "myvu_connection"
        private const val NOTIFICATION_ID = 1

        /**
         * The live connection, for components that run in their OWN service process
         * slot and so cannot bind to us -- notably MirrorNotificationListener, which
         * Android instantiates independently.
         *
         * Null whenever the service is not running, which callers must treat as
         * "not connected" rather than an error.
         */
        @Volatile
        private var active: ConnectionManager? = null

        @JvmStatic
        fun activeConnection(): ConnectionManager? {
            return active
        }

        private fun describe(state: ConnectionState): String {
            return when (state) {
                ConnectionState.BONDING -> "Bonding..."
                ConnectionState.CONNECTING -> "Connecting over BLE..."
                ConnectionState.PAIRING -> "Exchanging keys..."
                ConnectionState.SESSION -> "Starting session..."
                ConnectionState.READY -> "Connected"
                ConnectionState.FAILED -> "Disconnected"
                else -> "Idle"
            }
        }
    }
}
