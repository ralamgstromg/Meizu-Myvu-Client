package com.myvu.client.transport.ble

class BleParsedPacket(val sn: Int) {
    var type: Int = -1
    var command: Int = -1
    val params: MutableList<Int> = mutableListOf()
    var value: ByteArray = byteArrayOf()

    val isData: Boolean get() = sn != 0

    fun pkgType(): Int = command

    fun frameCount(): Int = if (params.isEmpty()) 0 else params[0]

    fun ackStatus(): Int = command
}
