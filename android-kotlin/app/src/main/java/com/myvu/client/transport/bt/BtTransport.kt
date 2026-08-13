package com.myvu.client.transport.bt

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import com.myvu.client.core.LogBus
import com.myvu.client.transport.Transport
import com.myvu.client.transport.TransportListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

open class BtTransport @JvmOverloads constructor(
    private val device: BluetoothDevice,
    private val serviceUuid: UUID = DEFAULT_SPP_UUID,
    private val listener: TransportListener? = null,
    private val connHandler: Handler? = null
) : Transport {

    @Volatile
    private var socket: BluetoothSocket? = null

    @Volatile
    private var _isConnected = false
    override val isConnected: Boolean get() = _isConnected

    override val name: String get() = "RFCOMM"

    private val closing = AtomicBoolean(false)
    private val disconnectReported = AtomicBoolean(false)

    // Bounded to 256 frames. Excess frames are dropped with a warning log
    // rather than accumulating indefinitely under RFCOMM congestion.
    private val txChannel = Channel<ByteArray>(256)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _payloadFlow = MutableSharedFlow<ByteArray>()
    override val payloadFlow: SharedFlow<ByteArray> = _payloadFlow.asSharedFlow()

    override fun connect() {
        scope.launch {
            try {
                openSocket()
            } catch (e: Exception) {
                reportDisconnected(e)
            }
        }
    }

    private suspend fun openSocket() {
        val s = connectWithFallback()
        socket = s
        _isConnected = true
        LogBus.log("RFCOMM connected (uuid=$serviceUuid)")

        startRx(s)
        startTx(s)

        connHandler?.post {
            listener?.onConnected(this)
        }
    }

    private suspend fun connectWithFallback(): BluetoothSocket = withContext(Dispatchers.IO) {
        val secure = device.createRfcommSocketToServiceRecord(serviceUuid)
        try {
            secure.connect()
            return@withContext secure
        } catch (e: IOException) {
            closeQuietly(secure)
            if (closing.get()) throw e

            LogBus.warn("secure RFCOMM connect failed (${e.message}) -- retrying insecure after delay")
            try {
                kotlinx.coroutines.delay(1000)
            } catch (ie: Exception) {
                throw IOException("interrupted waiting for RFCOMM retry", ie)
            }
        }

        val insecure = device.createInsecureRfcommSocketToServiceRecord(serviceUuid)
        try {
            insecure.connect()
            return@withContext insecure
        } catch (e: IOException) {
            closeQuietly(insecure)
            if (closing.get()) throw e

            LogBus.warn("insecure RFCOMM connect failed (${e.message}) -- retrying reflection channel")
            try {
                kotlinx.coroutines.delay(1000)
                val reflection = device.javaClass
                    .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 13) as? BluetoothSocket
                if (reflection != null) {
                    reflection.connect()
                    return@withContext reflection
                }
            } catch (reflectionErr: Exception) {
                LogBus.warn("reflection RFCOMM connect failed: ${reflectionErr.message}")
            }

            if (looksLikeWedgedStack(e)) {
                throw IOException(
                    "the phone's Bluetooth stack has a stale RFCOMM connection for this device (MCB stuck open). Toggle Bluetooth off and on to clear it.",
                    e
                )
            }
            throw e
        }
    }

    private fun startRx(s: BluetoothSocket) {
        scope.launch(Dispatchers.IO) {
            val reassembler = FrameReassembler()
            val buf = ByteArray(4096)
            try {
                val input = s.inputStream
                while (!closing.get() && isActive) {
                    val n = input.read(buf)
                    if (n < 0) throw IOException("stream closed by peer")
                    if (n == 0) continue

                    val frames = reassembler.feed(buf.copyOfRange(0, n))
                    for (frame in frames) {
                        _payloadFlow.emit(frame)
                        connHandler?.post {
                            listener?.onPayload(this@BtTransport, frame)
                        }
                    }
                }
            } catch (e: Exception) {
                if (!closing.get()) reportDisconnected(e)
            }
            if (closing.get()) reportDisconnected(null)
        }
    }

    private fun startTx(s: BluetoothSocket) {
        scope.launch(Dispatchers.IO) {
            try {
                val output = s.outputStream
                for (payload in txChannel) {
                    if (!isActive) break
                    output.write(RfcommFraming.encodeFrame(payload))
                    output.flush()
                }
            } catch (e: Exception) {
                if (!closing.get()) reportDisconnected(e)
            }
        }
    }

    override fun send(payload: ByteArray) {
        if (!_isConnected) {
            LogBus.warn("send on a closed RFCOMM link -- dropping ${payload.size}B")
            return
        }
        val sendResult = txChannel.trySend(payload)
        if (sendResult.isFailure) {
            LogBus.warn("BtTransport: TX queue full -- dropping frame (RFCOMM congested)")
        }
    }

    override fun close() {
        if (!closing.compareAndSet(false, true)) return
        _isConnected = false
        txChannel.close()
        try {
            socket?.close()
        } catch (ignored: IOException) {
        }
        scope.cancel()
    }

    private fun reportDisconnected(cause: Throwable?) {
        if (!disconnectReported.compareAndSet(false, true)) return
        _isConnected = false
        connHandler?.post {
            listener?.onDisconnected(this, cause)
        }
    }

    companion object {
        @JvmField
        val DEFAULT_SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private fun looksLikeWedgedStack(e: IOException): Boolean {
            val msg = e.message
            return msg != null && msg.contains("read failed, socket might closed")
        }

        private fun closeQuietly(s: BluetoothSocket?) {
            try {
                s?.close()
            } catch (ignored: IOException) {
            }
        }
    }
}

typealias RfcommTransport = BtTransport
