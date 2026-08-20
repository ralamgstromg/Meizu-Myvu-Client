package com.myvu.client.service

import android.os.Handler
import com.myvu.client.core.LogBus
import kotlin.math.min

/**
 * Keeps the classic-BT app relay alive.
 *
 * Employs an exponential backoff schedule when disconnected (5s -> 10s -> 20s -> 40s -> 60s max)
 * to minimize CPU wakeups and conserve phone battery when glasses are turned off or out of range.
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
            val nextDelay = if (connected) {
                CONNECTED_POLL_MS
            } else {
                calculateBackoffDelay(attempt)
            }
            conn.postDelayed(this, nextDelay)
        }
    }

    fun start() {
        if (running) return
        running = true
        attempt = 0
        conn.postDelayed(poll, INITIAL_DISCONNECTED_POLL_MS)
    }

    fun stop() {
        running = false
        conn.removeCallbacks(poll)
    }

    /** Called when the glasses explicitly ask for the relay (cmd 71). */
    fun wake() {
        if (!running) return
        attempt = 0
        conn.removeCallbacks(poll)
        check()
        conn.postDelayed(poll, INITIAL_DISCONNECTED_POLL_MS)
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
            return false
        }

        val now = System.currentTimeMillis()
        if (now - lastAttemptAt > RESET_ATTEMPTS_AFTER_MS) {
            attempt = 0
        }
        val minInterval = calculateBackoffDelay(attempt)
        if (now - lastAttemptAt < minInterval) return false
        if (attempt >= MAX_ATTEMPTS) return false

        attempt++
        lastAttemptAt = now
        LogBus.log(
            "app relay down -- reconnecting (attempt $attempt/$MAX_ATTEMPTS)"
        )
        delegate.connectRelay()

        if (attempt >= MAX_ATTEMPTS) {
            LogBus.warn(
                "relay reconnect gave up after $MAX_ATTEMPTS attempts; backing off to ${MAX_DISCONNECTED_POLL_MS / 1000}s poll"
            )
        }
        return false
    }

    companion object {
        private const val CONNECTED_POLL_MS = 60000L
        private const val INITIAL_DISCONNECTED_POLL_MS = 5000L
        private const val MAX_DISCONNECTED_POLL_MS = 60000L
        private const val MAX_ATTEMPTS = 6
        private const val RESET_ATTEMPTS_AFTER_MS = 30000L

        @JvmStatic
        fun calculateBackoffDelay(attemptCount: Int): Long {
            if (attemptCount <= 0) return INITIAL_DISCONNECTED_POLL_MS
            val shift = min(attemptCount, 4)
            val factor = 1L shl shift
            return min(MAX_DISCONNECTED_POLL_MS, INITIAL_DISCONNECTED_POLL_MS * factor)
        }

        /** Backoff spacing, exposed so the caller can schedule its own retry. */
        @JvmStatic
        fun backoffMs(): Long {
            return INITIAL_DISCONNECTED_POLL_MS
        }
    }
}
