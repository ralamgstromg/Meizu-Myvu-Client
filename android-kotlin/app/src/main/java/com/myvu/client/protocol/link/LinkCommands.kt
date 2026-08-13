package com.myvu.client.protocol.link

/**
 * COMMAND enum from starry_link_encrypt.proto, confirmed against the decompiled
 * official app's Starry.StarryLinkEncrypt.COMMAND.
 */
object LinkCommands {
    const val CMD_INIT: Int = 0
    const val CMD_ENSURE: Int = 1
    const val CMD_UN_BONDED: Int = 2
    const val CMD_READ_SWITCH_KEY: Int = 10
    const val CMD_WRITE_SWITCH_KEY: Int = 11
    const val CMD_READ_SWITCH_INFO: Int = 12
    const val CMD_WRITE_SWITCH_INFO: Int = 13
    const val CMD_BOND_MSG_CHANGE: Int = 14
    const val CMD_AUTH_STATUE: Int = 18
    const val CMD_AUTH_MESSAGE: Int = 19

    /**
     * The classic-BT (RFCOMM) app-relay channel is NOT a fixed channel number:
     * the glasses generate a random 16-bit UUID per session and sync it to the
     * phone over BLE with this command before any SPP connect is attempted.
     */
    const val CMD_SPP_SERVER_UUID_SYNC: Int = 70
    /** Glasses asking the phone to (re)open the relay -- drives RelaySupervisor. */
    const val CMD_SPP_SERVER_REQUEST_CONNECT: Int = 71
    const val CMD_SPP_SERVER_REQUEST_STATE_OPEN: Int = 72
    const val CMD_SPP_SERVER_REQUEST_STATE_CLOSE: Int = 73

    // ---- BTSTATUS enum (DeviceInfo.btStatus) ----
    const val BTSTATUS_DEFAULT: Int = 0
    const val BTSTATUS_BOND: Int = 1
    const val BTSTATUS_BONDING: Int = 2
    const val BTSTATUS_NOBOND: Int = 3
    const val BTSTATUS_CONNECTED_ACL: Int = 4
    const val BTSTATUS_CONNECTED_HFP: Int = 5
    const val BTSTATUS_CONNECTED_A2DP: Int = 6
    const val BTSTATUS_DISCONNECTED: Int = 7
    const val BTSTATUS_NO_CONNECTED_BT: Int = 8
    const val BTSTATUS_EXIST_CONNECTED_BT: Int = 9
    const val BTSTATUS_CONNECT_FAIL: Int = 10
    const val BTSTATUS_BOND_CANCEL_OR_TIMEOUT: Int = 11
}
