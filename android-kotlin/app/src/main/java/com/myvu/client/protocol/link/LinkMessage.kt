package com.myvu.client.protocol.link

/** A decoded LinkProtocol message: {1:deviceId, 2:cmd, 3:data}. */
class LinkMessage(
    @JvmField val deviceId: ByteArray,
    @JvmField val cmd: Int,
    @JvmField val data: ByteArray
)
