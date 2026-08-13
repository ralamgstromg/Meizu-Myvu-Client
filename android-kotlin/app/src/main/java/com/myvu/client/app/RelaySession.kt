package com.myvu.client.app

import com.myvu.client.protocol.RelaySequencer

/**
 * Per-transport relay state.
 *
 * BLE and RFCOMM each run their own independent RunAsOne session, exactly as
 * the Python client does with separate MyvuClient / MyvuRfcommClient objects.
 * Both the relay msgId sequence and the app msgId counter are per-connection:
 * the glasses track the last received sequence number and discard anything that
 * looks stale, so a reconnect MUST start from a fresh instance rather than
 * continuing the old numbering.
 */
class RelaySession {
    @JvmField
    val seq = RelaySequencer()

    @JvmField
    val appLayer = AppLayer()

    /**
     * Set the first time we answer an ability reply.
     */
    @JvmField
    var authConfirmed: Boolean = false

    /** Set once the ability/AUTH_SUCCESS handshake and init burst have completed. */
    @JvmField
    var ready: Boolean = false
}
