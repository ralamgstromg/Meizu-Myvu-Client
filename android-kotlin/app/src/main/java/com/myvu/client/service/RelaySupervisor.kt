package com.myvu.client.service

import android.os.Handler
import com.myvu.client.core.LogBus

/**
 * Keeps the classic-BT app relay alive.
 *
 * The glasses own the relay's lifecycle: they drop it when idle and ask for it
 * back with CMD_SPP_SERVER_REQUEST_CONNECT. Port of run_glasses.relay_supervisor
 * -- a 3s poll (so silent drops are noticed too) plus an explicit wake on that
 * command, with a bounded retry run.
 */
class RelaySupervisor(
    private val conn: Handler,
    private val delegate: Delegate
) {
    interface Delegate {
        /** True when the relay is up and does not need attention. */
        fun isRelayConnected(): Boolean

        /** True when we know where the relay lives (the per-session UUID). */
        fun canConnectRelay(): Boolean

        fun connectRelay()
    }

    private var running = false
    private var attempt = 0

    /** Wall clock of the last connect attempt, for rate limiting. */
    private var lastAttemptAt: Long = 0

    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            val connected = check()
            conn.postDelayed(this, if (connected) CONNECTED_POLL_MS else DISCONNECTED_POLL_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        attempt = 0
        conn.postDelayed(poll, DISCONNECTED_POLL_MS)
    }

    fun stop() {
        running = false
        conn.removeCallbacks(poll)
    }

    /** Called when the glasses explicitly ask for the relay (cmd 71). */
    fun wake() {
        if (!running) return
        // A fresh request means the glasses want it NOW, so reset the retry
        // budget rather than staying in a backed-off state.
        attempt = 0
        check()
    }

    /** Called when the relay drops, so the next poll retries promptly. */
    fun onRelayLost() {
        attempt = 0
    }

    private fun check(): Boolean {
        if (!running) return false
        if (delegate.isRelayConnected()) {
            attempt = 0
            return true
        }
        if (!delegate.canConnectRelay()) {
            // No UUID yet; the glasses will sync one over BLE shortly.
            return false
        }
        // Hard rate limit. The glasses emit bursts of state-change messages and
        // each one wakes us; without this the retries collapsed into a tight
        // connect/close loop that hammered the device.
        val now = System.currentTimeMillis()
        if (now - lastAttemptAt > RESET_ATTEMPTS_AFTER_MS) {
            attempt = 0 // Reset retry budget after cooldown
        }
        if (now - lastAttemptAt < BACKOFF_MS) return false
        if (attempt >= MAX_ATTEMPTS) return false

        attempt++
        lastAttemptAt = now
        LogBus.log(
            "app relay down -- reconnecting (attempt $attempt/$MAX_ATTEMPTS)"
        )
        delegate.connectRelay()

        if (attempt >= MAX_ATTEMPTS) {
            LogBus.warn(
                "relay reconnect gave up after $MAX_ATTEMPTS attempts; will retry on next notification or cooldown"
            )
        }
        return false
    }

    companion object {
        private const val CONNECTED_POLL_MS = 60000L
        private const val DISCONNECTED_POLL_MS = 3000L
        private const val BACKOFF_MS = 3000L
        private const val MAX_ATTEMPTS = 6
        private const val RESET_ATTEMPTS_AFTER_MS = 30000L

        /** Backoff spacing, exposed so the caller can schedule its own retry. */
        @JvmStatic
        fun backoffMs(): Long {
            return BACKOFF_MS
        }
    }
}
