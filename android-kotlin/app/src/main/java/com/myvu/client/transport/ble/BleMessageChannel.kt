package com.myvu.client.transport.ble

import android.os.Handler
import com.myvu.client.core.LogBus

class BleMessageChannel @JvmOverloads constructor(
    private val label: String,
    private val writer: Writer,
    private val conn: Handler? = null,
    private val receiver: Receiver? = null
) {
    fun interface Writer {
        fun write(packet: ByteArray)
    }

    fun interface Receiver {
        fun onMessage(pkgType: Int, payload: ByteArray)
    }

    fun interface AckCallback {
        fun onAck(status: Int)
    }

    private val rx = BleReassembler()

    var dmtu: Int = MIN_DMTU
        private set
    private var pendingDmtu: Int = -1

    private var singleAckWaiter: AckCallback? = null
    private var ctrAckWaiter: AckCallback? = null
    private var singleAckTimeout: Runnable? = null
    private var ctrAckTimeout: Runnable? = null

    fun setDmtu(newDmtu: Int) {
        val v = Math.max(MIN_DMTU, newDmtu)
        if (rx.isActive) {
            pendingDmtu = v
        } else {
            dmtu = v
        }
    }

    private fun applyPendingDmtu() {
        if (pendingDmtu > 0 && !rx.isActive) {
            dmtu = pendingDmtu
            pendingDmtu = -1
        }
    }

    fun sendSingleAcked(payload: ByteArray, pkgType: Int, cb: AckCallback) {
        if (payload.size > dmtu) {
            sendCtrAcked(payload, pkgType, cb)
            return
        }
        armSingleAck(cb)
        writer.write(BlePackets.singlePacket(pkgType, payload))
    }

    fun sendCtrAcked(payload: ByteArray, pkgType: Int, cb: AckCallback) {
        val frameCount = frameCountFor(payload.size)
        armCtrAck { status ->
            if (status != BlePackets.ACK_READY) {
                cb.onAck(status)
                return@armCtrAck
            }
            armCtrAck(cb)
            writeFragments(payload, 0, frameCount)
        }
        writer.write(BlePackets.ctrPacket(frameCount, pkgType))
    }

    fun sendSingleNoAck(payload: ByteArray, pkgType: Int) {
        writer.write(BlePackets.singleNoAckPacket(pkgType, payload))
    }

    fun sendFast(payload: ByteArray, pkgType: Int) {
        val frameCount = frameCountFor(payload.size)
        writer.write(BlePackets.fastCtrPacket(frameCount, pkgType))
        writeFragments(payload, 0, frameCount)
    }

    fun sendMix(payload: ByteArray, pkgType: Int) {
        val firstLen = Math.min(payload.size, Math.max(0, dmtu - 4))
        val first = payload.copyOfRange(0, firstLen)
        val rest = payload.copyOfRange(firstLen, payload.size)
        val frameCount = if (rest.isNotEmpty()) frameCountFor(rest.size) else 0

        writer.write(BlePackets.mixCtrPacket(frameCount, pkgType, first))
        writeFragments(rest, 0, frameCount)
    }

    fun send(payload: ByteArray, pkgType: Int) {
        if (payload.size <= dmtu) {
            sendSingleNoAck(payload, pkgType)
        } else {
            sendMix(payload, pkgType)
        }
    }

    private fun writeFragments(data: ByteArray, startIndex: Int, frameCount: Int) {
        for (idx in startIndex until frameCount) {
            val from = idx * dmtu
            val to = Math.min(data.size, from + dmtu)
            if (from >= to) break
            writer.write(BlePackets.dataPacket(idx + 1, data.copyOfRange(from, to)))
        }
    }

    private fun frameCountFor(length: Int): Int {
        return Math.max(1, (length + dmtu - 1) / dmtu)
    }

    fun feed(raw: ByteArray) {
        val p = BlePackets.parse(raw)

        if (p.isData) {
            val full = rx.add(p.sn, p.value)
            if (full != null) {
                val pkgType = rx.pkgType()
                applyPendingDmtu()
                deliver(pkgType, full)
            }
            return
        }

        when (p.type) {
            BlePackets.TYPE_SINGLE_CMD -> {
                writer.write(BlePackets.singleAckPacket(BlePackets.ACK_SUCCESS))
                deliver(p.pkgType(), p.value)
            }
            BlePackets.TYPE_SINGLE_CMD_NO_ACK -> {
                deliver(p.pkgType(), p.value)
            }
            BlePackets.TYPE_SINGLE_ACK -> {
                resolveSingleAck(p.ackStatus())
            }
            BlePackets.TYPE_CMD -> {
                rx.start(p.frameCount(), p.pkgType())
                writer.write(BlePackets.ackPacket(BlePackets.ACK_READY))
            }
            BlePackets.TYPE_FAST_CTR -> {
                rx.start(p.frameCount(), p.pkgType())
            }
            BlePackets.TYPE_MIX_CTR -> {
                rx.start(p.frameCount(), p.pkgType(), p.value)
                if (p.frameCount() == 0) {
                    rx.isActive = false
                    applyPendingDmtu()
                    deliver(p.pkgType(), p.value)
                }
            }
            BlePackets.TYPE_ACK -> {
                resolveCtrAck(p.ackStatus())
            }
            else -> {
                LogBus.trace("$label <- unhandled packet type ${p.type}")
            }
        }
    }

    private fun deliver(pkgType: Int, payload: ByteArray) {
        receiver?.onMessage(pkgType, payload)
    }

    private fun armSingleAck(cb: AckCallback) {
        cancelSingleAck()
        singleAckWaiter = cb
        val to = Runnable {
            val waiter = singleAckWaiter
            singleAckWaiter = null
            singleAckTimeout = null
            if (waiter != null) {
                LogBus.warn("$label: single ACK timed out")
                waiter.onAck(BlePackets.ACK_TIMEOUT)
            }
        }
        singleAckTimeout = to
        conn?.postDelayed(to, ACK_TIMEOUT_MS)
    }

    private fun resolveSingleAck(status: Int) {
        val cb = singleAckWaiter
        cancelSingleAck()
        cb?.onAck(status)
    }

    private fun cancelSingleAck() {
        singleAckTimeout?.let { conn?.removeCallbacks(it) }
        singleAckTimeout = null
        singleAckWaiter = null
    }

    private fun armCtrAck(cb: AckCallback) {
        cancelCtrAck()
        ctrAckWaiter = cb
        val to = Runnable {
            val waiter = ctrAckWaiter
            ctrAckWaiter = null
            ctrAckTimeout = null
            if (waiter != null) {
                LogBus.warn("$label: CTR ACK timed out")
                waiter.onAck(BlePackets.ACK_TIMEOUT)
            }
        }
        ctrAckTimeout = to
        conn?.postDelayed(to, ACK_TIMEOUT_MS)
    }

    private fun resolveCtrAck(status: Int) {
        val cb = ctrAckWaiter
        cancelCtrAck()
        cb?.onAck(status)
    }

    private fun cancelCtrAck() {
        ctrAckTimeout?.let { conn?.removeCallbacks(it) }
        ctrAckTimeout = null
        ctrAckWaiter = null
    }

    fun shutdown() {
        cancelSingleAck()
        cancelCtrAck()
        rx.reset()
    }

    companion object {
        private const val ACK_TIMEOUT_MS = 6000L
        const val MIN_DMTU = 18
    }
}
