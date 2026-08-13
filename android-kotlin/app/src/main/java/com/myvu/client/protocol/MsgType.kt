package com.myvu.client.protocol

/** Values for TlvTags.MSG_TYPE. */
object MsgType {
    const val OPEN: Int = 1
    const val CLOSE: Int = 2
    const val SEND: Int = 3
    const val SEND_SUCCESS: Int = 4
    const val SEND_FAIL: Int = 5
    const val OPEN_SUCCESS: Int = 6
    const val OPEN_FAIL: Int = 7
    const val OPEN_PAGE: Int = 8
}
