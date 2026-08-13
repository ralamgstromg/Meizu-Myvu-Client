package com.myvu.client.transport.ble

import android.os.Handler
import com.myvu.client.core.Hex
import com.myvu.client.core.LogBus
import com.myvu.client.crypto.EcKeyPair
import com.myvu.client.crypto.StarryCrypto
import com.myvu.client.protocol.link.DeviceInfo
import com.myvu.client.protocol.link.LinkCommands
import com.myvu.client.protocol.link.LinkMessage
import com.myvu.client.protocol.link.LinkProtocol
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class BlePairing(
    private val transport: BleTransport,
    private val conn: Handler,
    private val ownId: ByteArray,
    private val ownMac: String,
    private val deviceName: String,
    private val btStatus: Int,
    private val callback: Callback
) {
    interface Callback {
        fun onPaired(glasses: DeviceInfo)
        fun onFailed(reason: String)
    }

    private var keyPair: EcKeyPair? = null
    var sharedSecret: ByteArray? = null
        private set
    var iv: ByteArray? = null
        private set
    var encryptMode: Int = StarryCrypto.SYMMETRIC_V3_GCM
        private set

    private enum class Step { IDLE, AWAIT_VERSION, AWAIT_KEY_REPLY, DONE }
    private var step = Step.IDLE

    private val timeout = Runnable {
        failed("timed out after ${PAIRING_TIMEOUT_MS / 1000}s waiting at step $step")
    }

    fun start() {
        try {
            val own = JSONObject()
                .put("i", Hex.encode(ownId))
                .put("v", CONNECT_VERSION)
                .put("e", OWN_ENCRYPT_SUPPORT)
                .put("m", 512)
                .put("b", BLE_VERSION)
                .put("c", CATEGORY_ID)
            val payload = own.toString().toByteArray(StandardCharsets.UTF_8)

            step = Step.AWAIT_VERSION
            conn.postDelayed(timeout, PAIRING_TIMEOUT_MS)
            LogBus.log("-> version negotiation $own")
            transport.internal()?.sendFast(payload, BlePackets.PKG_STARRY_DATA_INIT)
        } catch (e: Exception) {
            failed("could not build the version payload: $e")
        }
    }

    fun onInternalMessage(payload: ByteArray): Boolean {
        return when (step) {
            Step.AWAIT_VERSION -> {
                handleVersionReply(payload)
                true
            }
            Step.AWAIT_KEY_REPLY -> {
                handleKeyReply(payload)
                true
            }
            else -> false
        }
    }

    private fun handleVersionReply(payload: ByteArray) {
        try {
            val peer = JSONObject(String(payload, StandardCharsets.UTF_8))
            encryptMode = peer.optInt("e", StarryCrypto.SYMMETRIC_V3_GCM)
            LogBus.log("<- version reply $peer (AES mode ${modeName(encryptMode)})")
            exchangeKeys()
        } catch (e: Exception) {
            failed("unparseable version reply: $e")
        }
    }

    private fun exchangeKeys() {
        try {
            val kp = EcKeyPair.generate()
            keyPair = kp
            val wsk = LinkProtocol.writeSwitchKey(kp.publicSpkiDer(), ownId)
            val msg = LinkProtocol.build(ownId, LinkCommands.CMD_WRITE_SWITCH_KEY, wsk)

            step = Step.AWAIT_KEY_REPLY
            LogBus.log("-> WRITE_SWITCH_KEY (${msg.size}B)")
            transport.internal()?.sendSingleAcked(msg, BlePackets.PKG_STARRY_DATA) { status ->
                if (status != BlePackets.ACK_SUCCESS) {
                    failed("key write was not acked (status=$status)")
                }
            }
        } catch (e: Exception) {
            failed("key generation failed: $e")
        }
    }

    private fun handleKeyReply(payload: ByteArray) {
        try {
            val reply = LinkProtocol.parse(payload)
            if (reply.cmd != LinkCommands.CMD_WRITE_SWITCH_KEY) {
                LogBus.trace("ignoring LinkProtocol cmd=${reply.cmd} during pairing")
                return
            }

            val parts = LinkProtocol.parseWriteSwitchKey(reply.data)
            val keyField = parts[0]
            val encryptedInfo = parts[1]
            if (keyField.size <= 16) {
                failed("key field too short (${keyField.size}B)")
                return
            }

            val peerPub = keyField.copyOfRange(0, keyField.size - 16)
            val derivedIv = keyField.copyOfRange(keyField.size - 16, keyField.size)
            iv = derivedIv

            val kp = keyPair ?: throw IllegalStateException("EcKeyPair is null")
            val secret = kp.sharedSecret(peerPub)
            sharedSecret = secret
            LogBus.log("ECDH shared secret derived (${secret.size}B)")

            val infoBytes = StarryCrypto.decrypt(encryptedInfo, secret, derivedIv, encryptMode)
            val glasses = DeviceInfo.parse(infoBytes)
            LogBus.log("<- Glasses: $glasses")

            sendOwnDeviceInfo(glasses)
        } catch (e: Exception) {
            failed(
                "key exchange failed (${e.javaClass.simpleName}: ${e.message}). A garbled DeviceInfo here usually means the negotiated AES mode or the SPKI encoding is wrong."
            )
        }
    }

    private fun sendOwnDeviceInfo(glasses: DeviceInfo) {
        try {
            val info = DeviceInfo.build(
                ownMac.uppercase(), "", CATEGORY_ID, "", deviceName, 100, btStatus
            )

            val secret = sharedSecret ?: throw IllegalStateException("sharedSecret is null")
            val currentIv = iv ?: throw IllegalStateException("iv is null")

            val inner = StarryCrypto.encrypt(info, secret, currentIv, encryptMode)
            val wsi = LinkProtocol.writeSwitchInfo(inner, 0)
            val outer = StarryCrypto.encrypt(wsi, secret, currentIv, encryptMode)
            val msg = LinkProtocol.build(ownId, LinkCommands.CMD_WRITE_SWITCH_INFO, outer)

            LogBus.log("-> WRITE_SWITCH_INFO (${msg.size}B)")
            step = Step.DONE
            transport.internal()?.sendSingleAcked(msg, BlePackets.PKG_STARRY_DATA) { status ->
                if (status != BlePackets.ACK_SUCCESS) {
                    failed("info write was not acked (status=$status)")
                    return@sendSingleAcked
                }
                conn.removeCallbacks(timeout)
                LogBus.log("BLE bond established")
                callback.onPaired(glasses)
            }
        } catch (e: Exception) {
            failed("could not send our DeviceInfo: $e")
        }
    }

    private fun failed(reason: String) {
        if (step == Step.DONE) return
        step = Step.DONE
        conn.removeCallbacks(timeout)
        callback.onFailed(reason)
    }

    fun cancel() {
        step = Step.DONE
        conn.removeCallbacks(timeout)
    }

    companion object {
        private const val CONNECT_VERSION = 3
        private const val BLE_VERSION = 2
        private const val CATEGORY_ID = "9999"
        private const val OWN_ENCRYPT_SUPPORT = 5
        private const val PAIRING_TIMEOUT_MS = 20000L

        private fun modeName(mode: Int): String {
            return when (mode) {
                StarryCrypto.SYMMETRIC_V1_CBC -> "CBC"
                StarryCrypto.SYMMETRIC_V2_CTR -> "CTR"
                else -> "GCM"
            }
        }
    }
}
