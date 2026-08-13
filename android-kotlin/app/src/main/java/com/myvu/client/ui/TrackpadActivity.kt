package com.myvu.client.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.myvu.client.R
import com.myvu.client.service.ConnectionState
import com.myvu.client.service.MyvuService

class TrackpadActivity : AppCompatActivity(), TrackpadView.Listener {

    private var service: MyvuService? = null
    private var bound: Boolean = false
    private var padStarted: Boolean = false

    private lateinit var trackpad: TrackpadView
    private var status: TextView? = null
    private var vibrator: Vibrator? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as MyvuService.LocalBinder).getService()
            bound = true
            updateStatus()
            startPad()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            bound = false
            updateStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trackpad)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        trackpad = findViewById(R.id.trackpad)
        status = findViewById(R.id.txtTrackpadStatus)
        trackpad.setListener(this)
        findViewById<android.view.View>(R.id.btnTrackpadBack).setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, MyvuService::class.java), serviceConnection, 0)
    }

    override fun onStop() {
        super.onStop()
        stopPad()
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
    }

    private fun connected(): Boolean {
        return bound && service?.connection()?.state() == ConnectionState.READY
    }

    private fun startPad() {
        if (padStarted || !connected()) return
        service?.connection()?.trackpadStart()
        padStarted = true
    }

    private fun stopPad() {
        if (!padStarted || !connected()) {
            padStarted = false
            return
        }
        service?.connection()?.trackpadStop()
        padStarted = false
    }

    private fun updateStatus() {
        status?.text = if (connected()) {
            "Connected · tap, swipe, long-press to control the glasses"
        } else {
            "Glasses not connected"
        }
    }

    private fun guard(): Boolean {
        if (connected()) return true
        updateStatus()
        return false
    }

    private fun tick() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(18)
            }
        } catch (ignored: Exception) {
        }
    }

    override fun onTap() {
        if (!guard()) return
        tick()
        service?.connection()?.trackpadClick()
    }

    override fun onDoubleTap() {
        if (!guard()) return
        tick()
        service?.connection()?.trackpadDoubleClick()
    }

    override fun onLongPress() {
        if (!guard()) return
        tick()
        service?.connection()?.trackpadLongPress()
    }

    override fun onSwipe(
        direction: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        speedX: Float,
        speedY: Float
    ) {
        if (!guard()) return
        service?.connection()?.trackpadSwipe(direction, startX, startY, endX, endY, speedX, speedY)
    }
}
