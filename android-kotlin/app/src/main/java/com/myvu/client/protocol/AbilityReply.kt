package com.myvu.client.protocol

/** Decoded ability-handshake reply. */
class AbilityReply(
    @JvmField val deviceId: String,
    /** Null when the glasses omitted the auth bean. */
    @JvmField val authBeanRaw: String?
)
