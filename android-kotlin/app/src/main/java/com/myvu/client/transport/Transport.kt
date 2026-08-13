package com.myvu.client.transport

import kotlinx.coroutines.flow.Flow

interface Transport {
    fun connect()
    fun send(payload: ByteArray)
    fun close()
    val isConnected: Boolean
    val name: String

    val payloadFlow: Flow<ByteArray>? get() = null
}
