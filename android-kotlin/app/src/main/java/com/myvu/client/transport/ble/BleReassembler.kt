package com.myvu.client.transport.ble

import com.myvu.client.core.BufferPool
import java.io.ByteArrayOutputStream

class BleReassembler {

    var frameCount: Int = 0
        private set
    private var pkgType: Int = -1
    private var header: ByteArray = byteArrayOf()
    private val frames: MutableMap<Int, ByteArray> = hashMapOf()

    var isActive: Boolean = false

    fun reset() {
        frameCount = 0
        pkgType = -1
        header = byteArrayOf()
        for (frame in frames.values) {
            BufferPool.recycle(frame)
        }
        frames.clear()
        isActive = false
    }

    @JvmOverloads
    fun start(frameCount: Int, pkgType: Int, header: ByteArray? = null) {
        reset()
        this.frameCount = frameCount
        this.pkgType = pkgType
        this.header = header ?: byteArrayOf()
        this.isActive = true
    }

    fun pkgType(): Int = pkgType

    fun add(seq: Int, payload: ByteArray): ByteArray? {
        frames[seq] = payload
        if (frameCount > 0 && frames.size >= frameCount) {
            val out = ByteArrayOutputStream()
            out.write(header, 0, header.size)
            for (i in 1..frameCount) {
                val f = frames[i]
                if (f != null) {
                    out.write(f, 0, f.size)
                    BufferPool.recycle(f)
                }
            }
            frames.clear()
            isActive = false
            return out.toByteArray()
        }
        return null
    }
}
