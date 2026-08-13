package com.myvu.client.protocol

/** Owns the outgoing msgId counter (starts at 1) and builds/ACKs frames. */
class RelaySequencer {
    var outId: Int = 0
        private set

    @JvmField
    var lastRecvId: Int = 0

    fun nextId(): Int {
        outId += 1
        return outId
    }

    @JvmOverloads
    fun dataFrame(
        msgBody: ByteArray,
        category: Int = Relay.DEFAULT_CATEGORY,
        needCallback: Int = 1,
        appUniteCode: Int = 1
    ): ByteArray {
        return Relay.buildFrame(category, MsgType.SEND, nextId(), needCallback, appUniteCode, msgBody)
    }

    fun ackFrame(forMsg: RelayMessage): ByteArray {
        val inner = TlvBox()
        inner.putByte(TlvTags.MSG_TYPE, MsgType.SEND_SUCCESS)
        inner.putInt(TlvTags.MSG_ID, forMsg.msgId)
        val outer = TlvBox()
        outer.putByte(TlvTags.CATEGORY, forMsg.category)
        outer.putBox(TlvTags.PAYLOAD, inner)
        return Pb.concat(byteArrayOf(Relay.FRAME_PREFIX.toByte()), outer.serialize())
    }
}
