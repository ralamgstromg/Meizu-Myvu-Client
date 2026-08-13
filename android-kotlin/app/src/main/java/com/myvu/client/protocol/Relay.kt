package com.myvu.client.protocol

/**
 * Faithful port of myvu_client/myvu/relay.py (RunAsOne relay / SuperMessage layer).
 */
object Relay {
    const val FRAME_PREFIX: Int = 0x01
    const val DEFAULT_CATEGORY: Int = 3

    /** Returns null if the buffer is not a relay frame. */
    @JvmStatic
    fun parseFrame(raw: ByteArray): RelayMessage? {
        if (raw.isEmpty() || (raw[0].toInt() and 0xFF) != FRAME_PREFIX) return null
        val outer = TlvBox.parse(raw.copyOfRange(1, raw.size))
        val cat = outer.getByte(TlvTags.CATEGORY)
        val payload = outer.getBytes(TlvTags.PAYLOAD) ?: return null
        val inner = TlvBox.parse(payload)
        return RelayMessage(
            cat ?: DEFAULT_CATEGORY,
            inner.getByte(TlvTags.MSG_TYPE) ?: 0,
            inner.getInt(TlvTags.MSG_ID) ?: 0,
            inner.getByte(TlvTags.NEED_CALLBACK) ?: 0,
            inner.getByte(TlvTags.APP_UNITE_CODE) ?: 0,
            inner.getBytes(TlvTags.MSG_BODY) ?: ByteArray(0)
        )
    }

    @JvmStatic
    fun buildFrame(
        category: Int,
        msgType: Int,
        msgId: Int,
        needCallback: Int,
        appUniteCode: Int,
        msgBody: ByteArray
    ): ByteArray {
        val inner = TlvBox()
        inner.putByte(TlvTags.MSG_TYPE, msgType)
        inner.putInt(TlvTags.MSG_ID, msgId)
        inner.putByte(TlvTags.NEED_CALLBACK, needCallback)
        inner.putByte(TlvTags.APP_UNITE_CODE, appUniteCode)
        if (msgBody.isNotEmpty()) inner.putBytes(TlvTags.MSG_BODY, msgBody)
        val outer = TlvBox()
        outer.putByte(TlvTags.CATEGORY, category)
        outer.putBox(TlvTags.PAYLOAD, inner)
        return Pb.concat(byteArrayOf(FRAME_PREFIX.toByte()), outer.serialize())
    }
}
