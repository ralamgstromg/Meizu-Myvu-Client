package com.myvu.client.transport

interface TransportListener {
    fun onConnected(transport: Transport)
    fun onPayload(transport: Transport, payload: ByteArray)
    fun onDisconnected(transport: Transport, cause: Throwable?)
}
