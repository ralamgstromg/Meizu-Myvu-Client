package com.myvu.client.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (ACTION_STOP == action) {
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

        if (ACTION_START == action) {
            val mac = intent.getStringExtra(EXTRA_MAC)
            if (!mac.isNullOrEmpty()) {
                connection?.start(mac)
            } else {
                // No MAC supplied -> discover the glasses over BLE (auto search).
                connection?.startAutoSearch()
            }
        }
        // REDELIVER rather than STICKY: a sticky restart hands us a null intent,
        // so we would come back as a foreground service with no MAC and no way
        // to reconnect. Redelivering the original START keeps restarts useful,
        // and ConnectionManager.start() ignores a duplicate.
        return START_REDELIVER_INTENT
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

    override fun onDestroy() {
        LogBus.log("service stopping")
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
