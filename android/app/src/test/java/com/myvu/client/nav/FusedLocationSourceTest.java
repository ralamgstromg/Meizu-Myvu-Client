package com.myvu.client.nav;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FusedLocationSourceTest {

    @Test
    public void testFastSpeedInterval() {
        // Fast movement (> 2.0 m/s) -> 2000ms
        assertEquals(FusedLocationSource.FAST_INTERVAL_MS, FusedLocationSource.calculateIntervalForSpeed(2.1f));
        assertEquals(FusedLocationSource.FAST_INTERVAL_MS, FusedLocationSource.calculateIntervalForSpeed(10.0f));
    }

    @Test
    public void testSlowSpeedInterval() {
        // Slow movement (0.5 to 2.0 m/s) -> 5000ms
        assertEquals(FusedLocationSource.SLOW_INTERVAL_MS, FusedLocationSource.calculateIntervalForSpeed(2.0f));
        assertEquals(FusedLocationSource.SLOW_INTERVAL_MS, FusedLocationSource.calculateIntervalForSpeed(1.2f));
        assertEquals(FusedLocationSource.SLOW_INTERVAL_MS, FusedLocationSource.calculateIntervalForSpeed(0.5f));
    }

    @Test
    public void testStationarySpeedInterval() {
        // Stationary (< 0.5 m/s or invalid speed -1) -> 15000ms
        assertEquals(FusedLocationSource.STATIONARY_INTERVAL_MS, FusedLocationSource.calculateIntervalForSpeed(0.49f));
        assertEquals(FusedLocationSource.STATIONARY_INTERVAL_MS, FusedLocationSource.calculateIntervalForSpeed(0.0f));
        assertEquals(FusedLocationSource.STATIONARY_INTERVAL_MS, FusedLocationSource.calculateIntervalForSpeed(-1.0f));
    }
}
