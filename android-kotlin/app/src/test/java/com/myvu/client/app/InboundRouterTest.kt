package com.myvu.client.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.ArrayList

class InboundRouterTest {

    private data class Sent(
        val json: String,
        val target: String,
        val source: String
    )

    private val sent = ArrayList<Sent>()
    private lateinit var router: InboundRouter

    @Before
    fun setUp() {
        sent.clear()
        router = InboundRouter(object : InboundRouter.Sender {
            override fun send(actionJson: String, targetPkg: String, sourcePkg: String) {
                sent.add(Sent(actionJson, targetPkg, sourcePkg))
            }
        })
    }

    @Test
    fun findsEmbeddedJsonInsideProtobufNoise() {
        val body = "  com.upuphone.star.launcher*{\"action\":\"volume\",\"value\":12}0 "
        val found = InboundRouter.findJsonObjects(body)
        assertEquals(1, found.size)
        assertEquals("{\"action\":\"volume\",\"value\":12}", found[0])
    }

    @Test
    fun handlesNestedBraces() {
        val body = "junk{\"a\":{\"b\":{\"c\":1}}}more"
        val found = InboundRouter.findJsonObjects(body)
        assertEquals(1, found.size)
        assertEquals("{\"a\":{\"b\":{\"c\":1}}}", found[0])
    }

    @Test
    fun unbalancedBracesAreIgnored() {
        assertTrue(InboundRouter.findJsonObjects("{\"a\":1").isEmpty())
        assertEquals(1, InboundRouter.findJsonObjects("}{\"a\":1}").size)
    }

    @Test
    fun launchAppRequestIsAckedOnTheInterconnectChannel() {
        router.handle(
            "{\"type\":11,\"data\":{\"appId\":\"com.upuphone.ar.navi.glass\"," +
                    "\"menuId\":\"m1\",\"requestId\":\"r7\",\"code\":0,\"success\":false}}"
        )

        assertEquals(1, sent.size)
        val s = sent[0]
        assertEquals(AppLayer.PKG_INTERCONNECT, s.target)
        assertEquals(AppLayer.PKG_INTERCONNECT, s.source)

        val reply = JSONObject(s.json)
        assertEquals(12, reply.getInt("type"))
        val data = reply.getJSONObject("data")
        assertEquals("com.upuphone.ar.navi.glass", data.getString("appId"))
        assertEquals(200, data.getInt("code"))
        assertTrue(data.getBoolean("success"))
        assertEquals("m1", data.getString("menuId"))
        assertEquals("r7", data.getString("requestId"))
    }

    @Test
    fun launchAppRequestWithoutAppIdIsIgnored() {
        router.handle("{\"type\":11,\"data\":{\"menuId\":\"m1\"}}")
        assertTrue(sent.isEmpty())
    }

    @Test
    fun ourOwnType12AckIsNotEchoedBack() {
        router.handle("{\"type\":12,\"data\":{\"appId\":\"x\",\"code\":200}}")
        assertTrue(sent.isEmpty())
    }

    @Test
    fun timeSyncRequestIsAnswered() {
        router.handle("{\"action\":\"SyncOffSetTime\"}")

        assertEquals(1, sent.size)
        val reply = JSONObject(sent[0].json)
        assertEquals("SyncOffSetTime", reply.getString("action"))
        assertNotNull(reply.getJSONObject("data").getString("syncTimeData"))
        assertEquals(AppLayer.PKG_LAUNCHER, sent[0].target)
    }

    @Test
    fun aTimePayloadIsNotTreatedAsARequest() {
        router.handle(
            "{\"action\":\"SyncOffSetTime\",\"data\":" +
                    "{\"syncTimeData\":\"1784485077995\",\"timeZoneOffSet\":10800000}}"
        )
        assertTrue(sent.isEmpty())
    }

    @Test
    fun aiButtonAndWakeWordAreSurfaced() {
        val codes = ArrayList<Int>()
        router.setAiTriggerListener(object : InboundRouter.AiTriggerListener {
            override fun onAiTrigger(code: Int, payload: JSONObject?) {
                codes.add(code)
            }
        })

        router.handle("{\"code\":3,\"payload\":{\"control\":1}}")
        router.handle("{\"code\":7,\"payload\":{}}")
        router.handle("{\"code\":101,\"payload\":{}}")

        assertEquals(2, codes.size)
        assertEquals(3, codes[0])
        assertEquals(7, codes[1])
        assertTrue(sent.isEmpty())
    }

    @Test
    fun syncGlassBatteryInfoFiresListener() {
        val levels = ArrayList<Int>()
        router.setBatteryUpdateListener(object : InboundRouter.BatteryUpdateListener {
            override fun onBatteryUpdated(battery: Int, isCharging: Boolean) {
                levels.add(battery)
            }
        })

        router.handle("{\"action\":\"sync_glass_battery_info\",\"value\":\"{\\\"isCharging\\\":false,\\\"battery\\\":21}\"}")

        assertEquals(1, levels.size)
        assertEquals(21, levels[0])
    }

    @Test
    fun getAirGlassInfoFiresListener() {
        val levels = ArrayList<Int>()
        router.setBatteryUpdateListener(object : InboundRouter.BatteryUpdateListener {
            override fun onBatteryUpdated(battery: Int, isCharging: Boolean) {
                levels.add(battery)
            }
        })

        router.handle("{\"action\":\"air_ota\",\"data\":{\"action\":\"get_air_glass_info\",\"value\":\"{\\\"battery\\\":18}\"}}")

        assertEquals(1, levels.size)
        assertEquals(18, levels[0])
    }

    @Test
    fun malformedBodiesDoNotThrow() {
        router.handle("")
        router.handle("no json here at all")
        router.handle("{not valid json}")
        assertTrue(sent.isEmpty())
    }
}
