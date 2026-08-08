package com.myvu.client.app;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GlassesEventHandlerTest {

    private InboundRouter inbound;
    private TestDelegate delegate;
    private GlassesEventHandler handler;

    private static class TestDelegate implements GlassesEventHandler.Delegate {
        boolean wakeCalled = false;
        int lastTriggerCode = -1;
        boolean pageClosedCalled = false;
        boolean refreshWeatherCalled = false;
        int lastBattery = -1;
        boolean lastIsCharging = false;
        String lastActionJson = null;

        @Override
        public void wakeRelay() {
            wakeCalled = true;
        }

        @Override
        public void triggerAi(int triggerCode) {
            lastTriggerCode = triggerCode;
        }

        @Override
        public void pageClosed() {
            pageClosedCalled = true;
        }

        @Override
        public void refreshWeather() {
            refreshWeatherCalled = true;
        }

        @Override
        public void updateBattery(int battery, boolean isCharging) {
            lastBattery = battery;
            lastIsCharging = isCharging;
        }

        @Override
        public void sendAction(String actionJson) {
            lastActionJson = actionJson;
        }
    }

    @Before
    public void setUp() {
        inbound = new InboundRouter((actionJson, targetPkg, sourcePkg) -> {});
        delegate = new TestDelegate();
        handler = new GlassesEventHandler(null, inbound, delegate);
    }

    @Test
    public void testAiTriggerNormal() throws Exception {
        inbound.handle("{\"action\":\"ai_trigger\",\"code\":3,\"data\":{\"control\":1}}");

        assertTrue(delegate.wakeCalled);
        assertFalse(delegate.pageClosedCalled);
    }

    @Test
    public void testAiTriggerPageClosed() throws Exception {
        inbound.handle("{\"action\":\"ai_trigger\",\"code\":3,\"data\":{\"control\":0}}");

        assertTrue(delegate.pageClosedCalled);
        assertFalse(delegate.wakeCalled);
    }

    @Test
    public void testWeatherRequest() throws Exception {
        inbound.handle("{\"action\":\"syncWeather\"}");

        assertTrue(delegate.refreshWeatherCalled);
    }

    @Test
    public void testQueryWeatherRequest() throws Exception {
        inbound.handle("{\"action\":\"query_weather\"}");

        assertTrue(delegate.refreshWeatherCalled);
    }

    @Test
    public void testBatteryUpdate() throws Exception {
        inbound.handle("{\"action\":\"battery_update\",\"data\":{\"battery\":85,\"is_charging\":true}}");

        assertEquals(85, delegate.lastBattery);
        assertTrue(delegate.lastIsCharging);
    }
}
