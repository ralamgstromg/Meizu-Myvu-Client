package com.myvu.client.protocol

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Faithful port of myvu_client/myvu/session.py (RunAsOne ability/AUTH
 * handshake). Over classic-BT/RFCOMM there is NO ECDH crypto step -- BR/EDR's
 * own link-layer encryption covers security, so this handshake is sent
 * directly (still wrapped in the eaca9353 relay framing, see Rfcomm).
 */
object Session {
    const val AUTH_CLASS_BYTE: Int = 0x02
    const val STREAM_AUTH: Int = 0
    const val STREAM_AUTH_SUCCESS: Int = 12

    private const val DEFAULT_VERSION = "2.40.51"
    private const val DEFAULT_WEIGHT = 233333

    @Throws(JSONException::class)
    private fun abilityAttributesJson(): JSONObject {
        val relay = JSONObject()
            .put("agreementType", 0)
            .put(
                "json", JSONObject()
                    .put("isSupportMapping", false)
                    .put("metaInfo", JSONArray())
                    .put("metaMap", JSONObject())
                    .toString()
            )
            .put("supportTlv", true)
        val air = JSONObject()
            .put("agreementType", 0)
            .put(
                "json", JSONObject()
                    .put(
                        "airMapping", JSONObject()
                            .put("1", "com.upuphone.star.launcher")
                            .put("2", "com.upuphone.thanos.sdk_test")
                    )
                    .toString()
            )
            .put("supportTlv", true)
        return JSONObject()
            .put("abilityRelay", relay.toString())
            .put("abilityAir", air.toString())
    }

    @JvmStatic
    @JvmOverloads
    @Throws(JSONException::class)
    fun buildAuthBean(
        deviceIdHex: String,
        deviceName: String,
        session: String,
        version: String = DEFAULT_VERSION,
        weight: Int = DEFAULT_WEIGHT
    ): JSONObject {
        val abilityList = listOf("abilityRelay", "abilityRelayBypass", "abilityAir", "abilityShare")
        return JSONObject()
            .put("ability", JSONArray(abilityList))
            .put("abilityAttributes", JSONObject().put("abilityAttributes", abilityAttributesJson()))
            .put("agreementType", 0)
            .put("deviceId", deviceIdHex)
            .put("deviceName", deviceName)
            .put("session", session)
            .put("supportTlv", true)
            .put("supportVirtual", false)
            .put("version", version)
            .put("weight", weight)
    }

    @Throws(JSONException::class)
    private fun buildStreamReq(
        streamType: Int,
        deviceIdHex: String,
        deviceName: String,
        session: String
    ): ByteArray {
        val bean = buildAuthBean(deviceIdHex, deviceName, session)
        val beanJson = bean.toString().toByteArray(StandardCharsets.UTF_8)
        val nowMs = System.currentTimeMillis()
        val ts = "timestamp-$nowMs".toByteArray(StandardCharsets.US_ASCII)

        var body = ByteArray(0)
        if (streamType != 0) body = Pb.concat(body, Pb.varintField(1, streamType.toLong()))
        body = Pb.concat(body, Pb.bytes(3, deviceIdHex.toByteArray(StandardCharsets.US_ASCII)))
        body = Pb.concat(body, Pb.bytes(4, beanJson))
        body = Pb.concat(body, Pb.bytes(7, "1.2".toByteArray(StandardCharsets.US_ASCII)))
        body = Pb.concat(body, Pb.bytes(9, ts))
        if (streamType == STREAM_AUTH_SUCCESS) body = Pb.concat(body, Pb.varintField(12, nowMs))
        return Pb.concat(byteArrayOf(AUTH_CLASS_BYTE.toByte()), body)
    }

    /** Phase 1: StreamReq type=AUTH (the initial ability handshake). */
    @JvmStatic
    @Throws(JSONException::class)
    fun buildAbilityMessage(deviceIdHex: String, deviceName: String, session: String): ByteArray {
        return buildStreamReq(STREAM_AUTH, deviceIdHex, deviceName, session)
    }

    /** Phase 2: StreamReq type=AUTH_SUCCESS, sent after the glasses reply. */
    @JvmStatic
    @Throws(JSONException::class)
    fun buildAuthSuccessMessage(deviceIdHex: String, deviceName: String, session: String): ByteArray {
        return buildStreamReq(STREAM_AUTH_SUCCESS, deviceIdHex, deviceName, session)
    }

    @JvmStatic
    fun parseAbilityReply(payload: ByteArray): AbilityReply {
        val body = if (payload.isNotEmpty() && (payload[0].toInt() and 0xFF) == AUTH_CLASS_BYTE) {
            payload.copyOfRange(1, payload.size)
        } else {
            payload
        }
        val f = Pb.parse(body)
        return AbilityReply(
            Pb.firstString(f, 3, "") ?: "",
            Pb.firstString(f, 4, null)
        )
    }
}
