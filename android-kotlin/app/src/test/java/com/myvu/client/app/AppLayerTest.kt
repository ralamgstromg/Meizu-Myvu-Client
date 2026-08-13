package com.myvu.client.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLayerTest {

    private lateinit var inbound: InboundRouter
    private lateinit var delegate: TestDelegate
    private lateinit var handler: GlassesEventHandler

    private class TestDelegate : GlassesEventHandler.Delegate {
        var wakeCalled = false
        var lastTriggerCode = -1
        var pageClosedCalled = false
        var refreshWeatherCalled = false
        var lastBattery = -1
        var lastIsCharging = false
        var lastActionJson: String? = null

        override fun wakeRelay() {
            wakeCalled = true
        }

        override fun triggerAi(triggerCode: Int) {
            lastTriggerCode = triggerCode
        }

        override fun pageClosed() {
            pageClosedCalled = true
        }

        override fun refreshWeather() {
            refreshWeatherCalled = true
        }

        override fun updateBattery(battery: Int, isCharging: Boolean) {
            lastBattery = battery
            lastIsCharging = isCharging
        }

        override fun sendAction(actionJson: String) {
            lastActionJson = actionJson
        }
    }

    @Before
    fun setUp() {
        inbound = InboundRouter(object : InboundRouter.Sender {
            override fun send(actionJson: String, targetPkg: String, sourcePkg: String) {}
        })
        delegate = TestDelegate()
        handler = GlassesEventHandler(null, inbound, delegate)
    }

    @Test
    fun testAppLayerConstants() {
        assertEquals("com.upuphone.star.launcher", AppLayer.PKG_LAUNCHER)
        assertEquals("com.upuphone.xr.interconnect", AppLayer.PKG_INTERCONNECT)
        assertEquals("com.myvu.client", AppLayer.PKG_SELF)
    }

    @Test
    fun testRelaySessionState() {
        val session = RelaySession()
        assertFalse(session.authConfirmed)
        assertFalse(session.ready)

        session.authConfirmed = true
        session.ready = true
        assertTrue(session.authConfirmed)
        assertTrue(session.ready)
    }

    @Test
    fun testAiTriggerNormal() {
        inbound.handle("{\"action\":\"ai_trigger\",\"code\":3,\"data\":{\"control\":1}}")
        assertTrue(delegate.wakeCalled)
        assertFalse(delegate.pageClosedCalled)
    }

    @Test
    fun testAiTriggerPageClosed() {
        inbound.handle("{\"action\":\"ai_trigger\",\"code\":3,\"data\":{\"control\":0}}")
        assertTrue(delegate.pageClosedCalled)
        assertFalse(delegate.wakeCalled)
    }

    @Test
    fun testWeatherRequest() {
        inbound.handle("{\"action\":\"syncWeather\"}")
        assertTrue(delegate.refreshWeatherCalled)
    }

    @Test
    fun testQueryWeatherRequest() {
        inbound.handle("{\"action\":\"query_weather\"}")
        assertTrue(delegate.refreshWeatherCalled)
    }
}
