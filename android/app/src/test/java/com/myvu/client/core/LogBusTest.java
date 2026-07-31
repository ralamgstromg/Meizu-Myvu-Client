package com.myvu.client.core;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LogBusTest {

    @Before
    public void setUp() {
        LogBus.clear();
        LogBus.setEnabled(true);
    }

    @Test
    public void testLoggingWhenEnabled() {
        LogBus.setEnabled(true);
        LogBus.log("test line 1");
        assertEquals(1, LogBus.history().size());
        assertTrue(LogBus.history().get(0).contains("test line 1"));
    }

    @Test
    public void testLoggingWhenDisabled() {
        LogBus.setEnabled(false);
        assertFalse(LogBus.isEnabled());
        LogBus.log("should not be logged");
        LogBus.warn("should not be logged warn");
        LogBus.error("should not be logged error", null);
        assertEquals(0, LogBus.history().size());
    }

    @Test
    public void testDisablingClearsExistingLogs() {
        LogBus.log("existing line");
        assertEquals(1, LogBus.history().size());

        LogBus.setEnabled(false);
        assertEquals(0, LogBus.history().size());
    }
}
