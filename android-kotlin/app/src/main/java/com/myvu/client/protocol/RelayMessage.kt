package com.myvu.client.protocol

/** One decoded relay frame. */
class RelayMessage(
    @JvmField val category: Int,
    @JvmField val msgType: Int,
    @JvmField val msgId: Int,
    @JvmField val needCallback: Int,
    @JvmField val appUniteCode: Int,
    @JvmField val msgBody: ByteArray
)
