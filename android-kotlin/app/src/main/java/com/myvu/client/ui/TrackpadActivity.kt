package com.myvu.client.ui

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.myvu.client.R
import com.myvu.client.app.feature.Trackpad
import com.myvu.client.service.ConnectionManager
import com.myvu.client.service.ConnectionState
import com.myvu.client.service.MyvuService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TrackpadActivity : AppCompatActivity(), TrackpadView.Listener {

    private var service: MyvuService? = null
    private var bound: Boolean = false
    private var padStarted: Boolean = false

    private lateinit var trackpad: TrackpadView
    private var status: TextView? = null
    private var gestureHint: TextView? = null
    private var statusDot: View? = null
    private var vibrator: Vibrator? = null

    private var stateJob: Job? = null
    private var lastDotColor: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    private val resetHintRunnable = Runnable {
        gestureHint?.text = getString(R.string.trackpad_gesture_hint)
        gestureHint?.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant_obsidian))
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val s = (binder as MyvuService.LocalBinder).getService()
            service = s
            bound = true
            val conn = s.connection()
            if (conn != null) {
                observeConnectionState(conn)
                renderState(conn.state())
                if (conn.state() == ConnectionState.READY) {
                    conn.wakeRelay()
                    startPad()
                }
            } else {
                renderState(ConnectionState.IDLE)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            bound = false
            stateJob?.cancel()
            stateJob = null
            stopPad()
            renderState(ConnectionState.IDLE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trackpad)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        trackpad = findViewById(R.id.trackpad)
        status = findViewById(R.id.txtTrackpadStatus)
        gestureHint = findViewById(R.id.txtGestureHint)
        statusDot = findViewById(R.id.trackpadStatusDot)

        lastDotColor = ContextCompat.getColor(this, R.color.state_idle)

        trackpad.setListener(this)
        findViewById<View>(R.id.btnTrackpadBack).setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, MyvuService::class.java), serviceConnection, 0)
    }

    override fun onStop() {
        super.onStop()
        mainHandler.removeCallbacks(resetHintRunnable)
        stateJob?.cancel()
        stateJob = null
        stopPad()
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
    }

    private fun observeConnectionState(conn: ConnectionManager) {
        stateJob?.cancel()
        stateJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                conn.stateFlow.collect { state ->
                    renderState(state)
                    if (state == ConnectionState.READY) {
                        conn.wakeRelay()
                        startPad()
                    } else {
                        stopPad()
                    }
                }
            }
        }
    }

    private fun connected(): Boolean {
        return bound && service?.connection()?.state() == ConnectionState.READY
    }

    private fun startPad() {
        if (padStarted || !connected()) return
        val conn = service?.connection() ?: return
        conn.wakeRelay()
        conn.trackpadStart()
        padStarted = true
    }

    private fun stopPad() {
        if (!padStarted) return
        service?.connection()?.trackpadStop()
        padStarted = false
    }

    private fun renderState(state: ConnectionState) {
        when (state) {
            ConnectionState.READY -> {
                status?.text = getString(R.string.trackpad_status_connected)
                status?.setTextColor(ContextCompat.getColor(this, R.color.cyber_teal))
                animateDot(ContextCompat.getColor(this, R.color.state_ready))
            }
            ConnectionState.CONNECTING,
            ConnectionState.BONDING,
            ConnectionState.PAIRING,
            ConnectionState.SESSION -> {
                status?.text = getString(R.string.trackpad_status_connecting)
                status?.setTextColor(ContextCompat.getColor(this, R.color.cyber_teal_dim))
                animateDot(ContextCompat.getColor(this, R.color.state_connecting))
            }
            ConnectionState.FAILED -> {
                status?.text = getString(R.string.trackpad_status_failed)
                status?.setTextColor(ContextCompat.getColor(this, R.color.cyber_neon_red))
                animateDot(ContextCompat.getColor(this, R.color.state_failed))
            }
            ConnectionState.IDLE -> {
                status?.text = getString(R.string.trackpad_status_not_connected)
                status?.setTextColor(ContextCompat.getColor(this, R.color.state_idle))
                animateDot(ContextCompat.getColor(this, R.color.state_idle))
            }
        }
    }

    private fun animateDot(target: Int) {
        val dot = statusDot ?: return
        val anim = ValueAnimator.ofObject(ArgbEvaluator(), lastDotColor, target)
        anim.duration = 300
        anim.addUpdateListener { a ->
            val c = a.animatedValue as Int
            val bg = dot.background
            if (bg is GradientDrawable) {
                (bg.mutate() as GradientDrawable).setColor(c)
            } else {
                bg?.mutate()?.setTint(c)
            }
        }
        anim.start()
        lastDotColor = target

        dot.clearAnimation()
        if (target == ContextCompat.getColor(this, R.color.state_ready)) {
            ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.45f, 1f).apply {
                duration = 1600
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        } else {
            dot.alpha = 1f
        }
    }

    private fun guard(): Boolean {
        if (connected()) return true
        renderState(service?.connection()?.state() ?: ConnectionState.IDLE)
        showDisconnectedFeedback()
        return false
    }

    private fun showDisconnectedFeedback() {
        status?.text = getString(R.string.trackpad_status_require_connection)
        status?.setTextColor(ContextCompat.getColor(this, R.color.cyber_neon_red))
        tickWarning()
    }

    private fun flashGestureFeedback(label: String) {
        mainHandler.removeCallbacks(resetHintRunnable)
        gestureHint?.text = label
        gestureHint?.setTextColor(ContextCompat.getColor(this, R.color.cyber_teal))
        mainHandler.postDelayed(resetHintRunnable, 1200L)
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

    private fun tickWarning() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(45)
            }
        } catch (ignored: Exception) {
        }
    }

    override fun onTap() {
        if (!guard()) return
        tick()
        flashGestureFeedback("⚡ Tap (Select)")
        service?.connection()?.trackpadClick()
    }

    override fun onDoubleTap() {
        if (!guard()) return
        tick()
        flashGestureFeedback("⚡ Double Tap")
        service?.connection()?.trackpadDoubleClick()
    }

    override fun onLongPress() {
        if (!guard()) return
        tick()
        flashGestureFeedback("⚡ Long Press")
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
        tick()
        val dirLabel = when (direction) {
            Trackpad.SWIPE_RIGHT -> "⚡ Swipe Right ›"
            Trackpad.SWIPE_LEFT -> "⚡ Swipe Left ‹"
            Trackpad.SWIPE_UP -> "⚡ Swipe Up ˆ"
            Trackpad.SWIPE_DOWN -> "⚡ Swipe Down ˇ"
            else -> "⚡ Swipe"
        }
        flashGestureFeedback(dirLabel)
        service?.connection()?.trackpadSwipe(direction, startX, startY, endX, endY, speedX, speedY)
    }
}
